package org.grobid.trainer;

import org.apache.commons.io.FileUtils;

import org.grobid.core.GrobidModels;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.engines.EngineParsers;
import org.grobid.core.engines.SoftwareParser;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.features.FeaturesVectorSoftware;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.layout.Block;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.PDFAnnotation;
import org.grobid.core.lexicon.SoftwareLexicon;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.utilities.Pair;
import org.grobid.core.utilities.SoftwareConfiguration;
import org.grobid.core.utilities.XMLUtilities;
import org.grobid.trainer.evaluation.EvaluationUtilities;
import org.grobid.core.engines.tagging.GenericTagger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;
import org.w3c.dom.ls.*;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.*;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.GrobidPropertyKeys;
import org.grobid.core.engines.tagging.GrobidCRFEngine;

import me.tongfei.progressbar.*;

/**
 * Training of the software entity recognition model
 *
 * @author Patrice
 */
public class SoftwareTrainer extends AbstractTrainer {

    protected SoftwareLexicon softwareLexicon = null;

    protected SoftwareConfiguration conf = null;

    public SoftwareTrainer() {
        this(0.00001, 20, 0);

        epsilon = 0.00001;
        window = 30;
        nbMaxIterations = 1500;
    }

    public SoftwareTrainer(double epsilon, int window, int nbMaxIterations) {
        super(GrobidModels.SOFTWARE);

        // adjusting CRF training parameters for this model
        this.epsilon = epsilon;
        this.window = window;
        //this.nbMaxIterations = nbMaxIterations;
        this.nbMaxIterations = 2000;
        softwareLexicon = SoftwareLexicon.getInstance();
    }

    public void setSoftwareConf(SoftwareConfiguration conf) {
        this.conf = conf;
    }

    /**
     * Add the selected features to the model training for software entities. For grobid-software, 
     * we can have two types of training files: XML/TEI files where text content is annotated with
     * software entities, and PDF files where the entities are annotated with an additional
     * PDf layer. The two types of training files suppose two different process in order to
     * generate the CRF training file.
     */
    @Override
    public int createCRFPPData(File sourcePathLabel,
                               File outputPath) {
        return createCRFPPData(sourcePathLabel, outputPath, null, 1.0);
    }

    /**
     * Add the selected features to the model training for software entities. Split
     * automatically all available labeled data into training and evaluation data
     * according to a given split ratio.
     */
    @Override
    public int createCRFPPData(final File corpusDir,
                               final File trainingOutputPath,
                               final File evalOutputPath,
                               double splitRatio) {
        return createCRFPPData(corpusDir, trainingOutputPath, evalOutputPath, splitRatio, true);
    }

    public int createCRFPPData(final File corpusDir,
                               final File trainingOutputPath,
                               final File evalOutputPath,
                               double splitRatio,
                               boolean splitRandom) {
        return createCRFPPData(corpusDir, trainingOutputPath, evalOutputPath, splitRatio, true, 0);
    }


    /**
     * CRF training data here are produced from the unique training TEI file containing labelled paragraphs only
     *
     * Negative example modes: 
     * 0 -> no added negative examples
     * 1 -> random negative examples
     * 2 -> erroneously predicted negative examples
     *
     */
    public int createCRFPPData(final File corpusDir,
                               final File trainingOutputPath,
                               final File evalOutputPath,
                               double splitRatio,
                               boolean splitRandom, 
                               int negativeMode) {
        int totalExamples = 0;
        Writer writerTraining = null;
        Writer writerEvaluation = null;
        try {
            System.out.println("labeled corpus path: " + corpusDir.getPath());
            System.out.println("training data path: " + trainingOutputPath.getPath());
            if (evalOutputPath != null)
                System.out.println("evaluation data path: " + evalOutputPath.getPath());

            // the file for writing the training data
            writerTraining = new OutputStreamWriter(new FileOutputStream(trainingOutputPath), "UTF8");

            // the file for writing the evaluation data
            if (evalOutputPath != null)
                writerEvaluation = new OutputStreamWriter(new FileOutputStream(evalOutputPath), "UTF8");

            // the active writer
            Writer writer = null;

            // this ratio this the minimum proportion of token with non default label, it is used to 
            // decide to keep or not a paragraph without any entities in the training data
            //double ratioNegativeSample = 0.01;

            // get a factory for SAX parser
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SoftwareAnnotationSaxHandler handler = new SoftwareAnnotationSaxHandler();
            
            // train and split / 10-fold cross evaluation
            //final String corpus_file_name = "softcite_corpus-full.tei.xml";
            
            // cross domain eval
            //final String corpus_file_name = "softcite_corpus_pmc.tei.xml";
            //final String corpus_file_name = "softcite_corpus_econ.tei.xml";

            // train fixed, no negative - optional negative sampling following negative mode
            //final String corpus_file_name = "softcite_corpus-full.working.tei.xml";
            
            // eval holdout
            final String corpus_file_name = "softcite_corpus-full.holdout-complete.tei.xml";
            //final String corpus_file_name = "softcite_corpus-full.holdout.tei.xml";

            File thefile = new File(corpusDir.getPath() + File.separator + corpus_file_name);
            if (!thefile.exists()) {
                System.out.println("The XML TEI corpus training document does not exist: " + 
                    corpusDir.getPath() + File.separator + corpus_file_name);
            } else {
                //get a new instance of parser
                SAXParser p = spf.newSAXParser();
                p.parse(thefile, handler);

                List<List<List<Pair<String, String>>>> allLabeled = handler.getAllLabeledResult();
                //labeled = subSample(labeled, ratioNegativeSample);

                int n = 0;
                for(List<List<Pair<String, String>>> docLabeled : allLabeled) {
                    for(List<Pair<String, String>> labeled : docLabeled) {

                        // we need to add now the features to the labeled tokens
                        List<Pair<String, String>> bufferLabeled = null;
                        int pos = 0;

                        // segmentation into training/evaluation is done file by file
                        if (splitRandom) {
                            if (Math.random() <= splitRatio)
                                writer = writerTraining;
                            else
                                writer = writerEvaluation;
                        } else {
                            if ((double) n / allLabeled.size() <= splitRatio)
                                writer = writerTraining;
                            else
                                writer = writerEvaluation;
                        }

                        // let's iterate by defined CRF input (separated by new line)
                        while (pos < labeled.size()) {
                            bufferLabeled = new ArrayList<>();
                            while (pos < labeled.size()) {
                                if (labeled.get(pos).getA().equals("\n")) {
                                    pos++;
                                    break;
                                }
                                bufferLabeled.add(labeled.get(pos));
                                pos++;
                            }

                            if (bufferLabeled.size() == 0)
                                continue;

                            List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNamesVectorLabeled(bufferLabeled);
                            List<OffsetPosition> urlPositions = softwareLexicon.tokenPositionsUrlVectorLabeled(bufferLabeled);

                            addFeatures(bufferLabeled, writer, softwareTokenPositions, urlPositions);
                            writer.write("\n");
                        }

                        writer.write("\n");
                        n++;
                    }
                }

                totalExamples = n;

                if (negativeMode != 0) {
                    // inject negative examples, depending on the selected mode
                    final String negative_corpus_file_name = "softcite.all.negative.working.tei.xml";
                    
                    String relativePath = corpusDir.getPath() + File.separator + negative_corpus_file_name;
                    String absolutePath = FileSystems.getDefault().getPath(relativePath).normalize().toAbsolutePath().toString();
                    File negativeCorpusFile = new File(absolutePath);

                    if (!negativeCorpusFile.exists()) {
                        System.out.println("The XML TEI negative corpus training document does not exist: " + 
                            corpusDir.getPath() + File.separator + negative_corpus_file_name);
                    } else {
                        int addedNegative = 0;
                        relativePath = corpusDir.getPath() + File.separator + "selected.negative.tei.xml";
                        absolutePath = FileSystems.getDefault().getPath(relativePath).normalize().toAbsolutePath().toString();
                        File outputXMLFile =  new File(absolutePath);

                        // negativeMode is 0 -> do nothing special

                        if (negativeMode == 1) {
                            addedNegative = randomNegativeExamples(negativeCorpusFile, 30000, outputXMLFile);
                        } else if (negativeMode == 2) {
                            addedNegative = selectNegativeExamples(negativeCorpusFile, 35000, outputXMLFile);
                        }
                        System.out.println("Number of injected negative examples: " + addedNegative);
                        if (addedNegative > 0) {
                            handler = new SoftwareAnnotationSaxHandler();
                            p = spf.newSAXParser();
                            p.parse(outputXMLFile, handler);

                            allLabeled = handler.getAllLabeledResult();
                            n = 0;
                            for(List<List<Pair<String, String>>> docLabeled : allLabeled) {
                                for(List<Pair<String, String>> labeled : docLabeled) {

                                    // we need to add now the features to the labeled tokens
                                    List<Pair<String, String>> bufferLabeled = null;
                                    int pos = 0;

                                    // segmentation into training/evaluation is done file by file
                                    if (splitRandom) {
                                        if (Math.random() <= splitRatio)
                                            writer = writerTraining;
                                        else
                                            writer = writerEvaluation;
                                    } else {
                                        if ((double) n / allLabeled.size() <= splitRatio)
                                            writer = writerTraining;
                                        else
                                            writer = writerEvaluation;
                                    }

                                    // let's iterate by defined CRF input (separated by new line)
                                    while (pos < labeled.size()) {
                                        bufferLabeled = new ArrayList<>();
                                        while (pos < labeled.size()) {
                                            if (labeled.get(pos).getA().equals("\n")) {
                                                pos++;
                                                break;
                                            }
                                            bufferLabeled.add(labeled.get(pos));
                                            pos++;
                                        }

                                        if (bufferLabeled.size() == 0)
                                            continue;

                                        List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNamesVectorLabeled(bufferLabeled);
                                        List<OffsetPosition> urlPositions = softwareLexicon.tokenPositionsUrlVectorLabeled(bufferLabeled);

                                        addFeatures(bufferLabeled, writer, softwareTokenPositions, urlPositions);
                                        writer.write("\n");
                                    }

                                    writer.write("\n");
                                    n++;
                                }
                            }

                            totalExamples += n;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new GrobidException("An exception occured while training GROBID.", e);
        } finally {
            try {
                if (writerTraining != null)
                    writerTraining.close();
                if (writerEvaluation != null)
                    writerEvaluation.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return totalExamples;
    }


    /**
     * CRF training data here are produced from the training TEI files generated for each PDF file, containing 
     * all content, labelled and unlabelled paragraphs. 
     */
    public int createCRFPPDataCollection(final File corpusDir,
                               final File trainingOutputPath,
                               final File evalOutputPath,
                               double splitRatio,
                               boolean splitRandom) {
        int totalExamples = 0;
        Writer writerTraining = null;
        Writer writerEvaluation = null;
        try {
            System.out.println("labeled corpus path: " + corpusDir.getPath());
            System.out.println("training data path: " + trainingOutputPath.getPath());
            if (evalOutputPath != null)
                System.out.println("evaluation data path: " + evalOutputPath.getPath());

            // we convert first the tei files into the usual CRF label format
            // we process all tei files in the output directory
            File[] refFiles = corpusDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".xml");
                }
            });

            // the file for writing the training data
            writerTraining = new OutputStreamWriter(new FileOutputStream(trainingOutputPath), "UTF8");

            // the file for writing the evaluation data
            if (evalOutputPath != null)
                writerEvaluation = new OutputStreamWriter(new FileOutputStream(evalOutputPath), "UTF8");

            // the active writer
            Writer writer = null;

            // this ratio this the minimum proportion of token with non default label, it is used to 
            // decide to keep or not a paragraph without any entities in the training data
            double ratioNegativeSample = 0.01;

            if (refFiles != null) {
                System.out.println("\n" + refFiles.length + " XML training files\n");

                // get a factory for SAX parser
                SAXParserFactory spf = SAXParserFactory.newInstance();

                String name;
                for (int n = 0; n < refFiles.length; n++) {
                    File thefile = refFiles[n];
                    name = thefile.getName();
                    if (name.equals("all.clean.tei.xml"))
                        continue;
                    //System.out.println(name);
                    //if (n > 100)
                    //    break;
                    SoftwareAnnotationCollectionSaxHandler handler = new SoftwareAnnotationCollectionSaxHandler();

                    //get a new instance of parser
                    SAXParser p = spf.newSAXParser();
                    p.parse(thefile, handler);

                    List<Pair<String, String>> labeled = handler.getLabeledResult();
                    labeled = subSample(labeled, ratioNegativeSample);

                    // we need to add now the features to the labeled tokens
                    List<Pair<String, String>> bufferLabeled = null;
                    int pos = 0;

                    // segmentation into training/evaluation is done file by file
                    if (splitRandom) {
                        if (Math.random() <= splitRatio)
                            writer = writerTraining;
                        else
                            writer = writerEvaluation;
                    } else {
                        if ((double) n / refFiles.length <= splitRatio)
                            writer = writerTraining;
                        else
                            writer = writerEvaluation;
                    }

                    // let's iterate by defined CRF input (separated by new line)
                    while (pos < labeled.size()) {
                        bufferLabeled = new ArrayList<>();
                        while (pos < labeled.size()) {
                            if (labeled.get(pos).getA().equals("\n")) {
                                pos++;
                                break;
                            }
                            bufferLabeled.add(labeled.get(pos));
                            pos++;
                        }

                        if (bufferLabeled.size() == 0)
                            continue;

                        List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNamesVectorLabeled(bufferLabeled);
                        List<OffsetPosition> urlPositions = softwareLexicon.tokenPositionsUrlVectorLabeled(bufferLabeled);

                        addFeatures(bufferLabeled, writer, softwareTokenPositions, urlPositions);
                        writer.write("\n");
                    }
                    writer.write("\n");
                }
            }

            // uncomment bellow in case of PDF files having annotated entities in the annotation layout 
            // (some adjustments would be necessary for translating annotation target into label type)
            /*refFiles = corpusDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".pdf");
                }
            });

            if (refFiles != null) {
                EngineParsers parsers = new EngineParsers();
                System.out.println(refFiles.length + " PDF files");

                String name;
                for (int n = 0; n < refFiles.length; n++) {
                    File thefile = refFiles[n];
                    name = thefile.getName();
                    System.out.println(name);

                    // parse the PDF
                    GrobidAnalysisConfig config =
                            new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder().build();
                    DocumentSource documentSource =
                            DocumentSource.fromPdf(thefile, config.getStartPage(), config.getEndPage());
                    Document doc = parsers.getSegmentationParser().processing(documentSource, config);

                    List<LayoutToken> tokenizations = doc.getTokenizations();

                    // get the annotations
                    List<PDFAnnotation> annotations = doc.getPDFAnnotations();

                    // we can create the labeled data block per block
                    int indexAnnotation = 0;
                    List<Block> blocks = doc.getBlocks();

                    // segmentation into training/evaluation is done file by file
                    // it could be done block by block y moving the piece of code bellow
                    // under the next loop on blocks bellow
                    if (splitRandom) {
                        if (Math.random() <= splitRatio)
                            writer = writerTraining;
                        else
                            writer = writerEvaluation;
                    } else {
                        if ((double) n / refFiles.length <= splitRatio)
                            writer = writerTraining;
                        else
                            writer = writerEvaluation;
                    }

                    for (Block block : blocks) {
                        List<Pair<String, String>> labeled = new ArrayList<Pair<String, String>>();
                        String previousLabel = "";
                        int startBlockToken = block.getStartToken();
                        int endBlockToken = block.getEndToken();

                        for (int p = startBlockToken; p < endBlockToken; p++) {
                            LayoutToken token = tokenizations.get(p);
                            //for(LayoutToken token : tokenizations) {
                            if ((token.getText() != null) &&
                                    (token.getText().trim().length() > 0) &&
                                    (!token.getText().equals("\t")) &&
                                    (!token.getText().equals("\n")) &&
                                    (!token.getText().equals("\r"))) {
                                String theLabel = "<other>";
                                for (int i = indexAnnotation; i < annotations.size(); i++) {
                                    PDFAnnotation currentAnnotation = annotations.get(i);
                                    // check if we are at least on the same page
                                    if (currentAnnotation.getPageNumber() < token.getPage())
                                        continue;
                                    else if (currentAnnotation.getPageNumber() > token.getPage())
                                        break;

                                    // check if we have an software entity
                                    // TBD: adapt for getting the annotation type of the software mention model
                                    if ((currentAnnotation.getType() == PDFAnnotation.Type.URI) &&
                                            (currentAnnotation.getDestination() != null) ) {

                                        //System.out.println(currentAnnotation.toString() + "\n");

                                        if (currentAnnotation.cover(token)) {
                                            System.out.println(currentAnnotation.toString() + " covers " + token.toString());
                                            // the annotation covers the token position
                                            // we have an software entity at this token position
                                            if (previousLabel.endsWith("<software>")) {
                                                theLabel = "<software>";
                                            } else {
                                                // we filter out entity starting with (
                                                if (!token.getText().equals("("))
                                                    theLabel = "I-<software>";
                                            }
                                            break;
                                        }
                                    }
                                }
                                Pair<String, String> thePair =
                                        new Pair<String, String>(token.getText(), theLabel);

                                // we filter out entity ending with a punctuation mark
                                if (theLabel.equals("<other>") && previousLabel.equals("<software>")) {
                                    // check the previous token
                                    Pair<String, String> theLastPair = labeled.get(labeled.size() - 1);
                                    String theLastToken = theLastPair.getA();
                                    if (theLastToken.equals(";") ||
                                            theLastToken.equals(".") ||
                                            theLastToken.equals(",")) {
                                        theLastPair = new Pair(theLastToken, "<other>");
                                        labeled.set(labeled.size() - 1, theLastPair);
                                    }
                                }

                                // add the current token
                                labeled.add(thePair);
                                previousLabel = theLabel;
                            }
                        }
                        // add features
                        List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNamesVectorLabeled(labeled);

                        addFeatures(labeled, writer, softwareTokenPositions);
                    }
                    writer.write("\n");
                }
            }*/
        } catch (Exception e) {
            throw new GrobidException("An exception occured while training GROBID.", e);
        } finally {
            try {
                if (writerTraining != null)
                    writerTraining.close();
                if (writerEvaluation != null)
                    writerEvaluation.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return totalExamples;
    }

    /**
     *  Ensure a ratio between positive and negative examples and shuffle
     */
    private List<Pair<String, String>> subSample(List<Pair<String, String>> labeled, double targetRatio) {
        int nbPositionTokens = 0;
        int nbNegativeTokens = 0;

        List<Pair<String, String>> reSampled = new ArrayList<Pair<String, String>>();
        List<Pair<String, String>> newSampled = new ArrayList<Pair<String, String>>();
        
        boolean hasLabels = false;
        for (Pair<String, String> tagPair : labeled) {
            if (tagPair.getB() == null) {
                // new sequence
                if (hasLabels) {
                    reSampled.addAll(newSampled);
                    reSampled.add(tagPair);
                } 
                newSampled = new ArrayList<Pair<String, String>>();
                hasLabels = false;
            } else {
                newSampled.add(tagPair);
                if (!tagPair.getB().equals("<other>") && !tagPair.getB().equals("other") && !tagPair.getB().equals("O")) 
                    hasLabels = true;
            }
        }
        return reSampled;
    }

    /**
     * Generate CRFData for a pdf file and a list of its annotations
     * The result is a Pair with :
     * A: crf text
     * B: tokenizations list
     */
    /*public Pair<String, List<LayoutToken>> getCRFData(File pdfFile, List<PDFAnnotation> annotations) {

        Writer crfWriter = null;
        List<LayoutToken> tokenizations = null;

        try {

            EngineParsers parsers = new EngineParsers();

            // parse the PDF
            GrobidAnalysisConfig config =
                    new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder().build();
            DocumentSource documentSource =
                    DocumentSource.fromPdf(pdfFile, config.getStartPage(), config.getEndPage());
            Document doc = parsers.getSegmentationParser().processing(documentSource, config);

            tokenizations = doc.getTokenizations();

            // we can create the labeled data block per block
            int indexAnnotation = 0;
            List<Block> blocks = doc.getBlocks();

            crfWriter = new StringWriter();

            for (Block block : blocks) {
                List<Pair<String, String>> labeled = new ArrayList<Pair<String, String>>();
                String previousLabel = "";
                int startBlockToken = block.getStartToken();
                int endBlockToken = block.getEndToken();

                for (int p = startBlockToken; p < endBlockToken; p++) {
                    LayoutToken token = tokenizations.get(p);

                    //for(LayoutToken token : tokenizations) {
                    if ((token.getText() != null) &&
                            (token.getText().trim().length() > 0) &&
                            (!token.getText().equals("\t")) &&
                            (!token.getText().equals("\n")) &&
                            (!token.getText().equals("\r"))) {
                        String theLabel = "<other>";
                        for (int i = indexAnnotation; i < annotations.size(); i++) {
                            PDFAnnotation currentAnnotation = annotations.get(i);
                            // check if we are at least on the same page
                            if (currentAnnotation.getPageNumber() < token.getPage())
                                continue;
                            else if (currentAnnotation.getPageNumber() > token.getPage())
                                continue;

                            if (currentAnnotation.cover(token)) {
                                System.out.println(currentAnnotation.toString() + " covers " + token.toString());
                                // the annotation covers the token position
                                // we have an software entity at this token position
                                if (previousLabel.endsWith("<software>")) {
                                    theLabel = "<software>";
                                } else {
                                    // we filter out entity starting with (
                                    if (!token.getText().equals("("))
                                        theLabel = "I-<software>";
                                }
                                break;
                            }
                        }
                        Pair<String, String> thePair =
                                new Pair<String, String>(token.getText(), theLabel);

                        // we filter out entity ending with a punctuation mark
                        if (theLabel.equals("<other>") && previousLabel.equals("<software>")) {
                            // check the previous token
                            Pair<String, String> theLastPair = labeled.get(labeled.size() - 1);
                            String theLastToken = theLastPair.getA();
                            if (theLastToken.equals(";") ||
                                    theLastToken.equals(".") ||
                                    theLastToken.equals(",")) {
                                theLastPair = new Pair(theLastToken, "<other>");
                                labeled.set(labeled.size() - 1, theLastPair);
                            }
                        }

                        // add the current token
                        labeled.add(thePair);
                        previousLabel = theLabel;
                    }
                }
                // add features
                List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNamesVectorLabeled(labeled);
                List<OffsetPosition> urlPositions = softwareLexicon.tokenPositionsUrlVectorLabeled(labeled);

                addFeatures(labeled, crfWriter, softwareTokenPositions, urlPositions);
            }
            crfWriter.write("\n");
        } catch (Exception e) {
            throw new GrobidException("An exception occured while training Grobid.", e);
        } finally {
            try {
                if (crfWriter != null)
                    crfWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (crfWriter != null && tokenizations != null)
            return new Pair<String, List<LayoutToken>>(crfWriter.toString(), tokenizations);
        else
            return null;
    }*/

    @SuppressWarnings({"UnusedParameters"})
    protected void addFeatures(List<Pair<String, String>> texts,
                             Writer writer,
                             List<OffsetPosition> softwareTokenPositions,
                             List<OffsetPosition> urlPositions) {
        int totalLine = texts.size();
        int posit = 0;
        int positUrl = 0;
        int currentSoftwareIndex = 0;
        List<OffsetPosition> localPositions = softwareTokenPositions;
        boolean isSoftwarePattern = false;
        boolean isUrl = false;
        try {
            for (Pair<String, String> lineP : texts) {
                String token = lineP.getA();
                if (token.trim().equals("@newline")) {
                    writer.write("\n");
                    writer.flush();
                }

                if (token.trim().length() == 0) {
                    positUrl++;
                    continue;
                }

                String label = lineP.getB();

                // do we have an software at position posit?
                isSoftwarePattern = false;
                if (localPositions != null) {
                    for(OffsetPosition thePosition : localPositions) {
                        if (posit >= thePosition.start && posit <= thePosition.end) {     
                            isSoftwarePattern = true;
                            break;
                        } 
                    }
                }

                /*if ((localPositions != null) && (localPositions.size() > 0)) {
                    for (int mm = currentSoftwareIndex; mm < localPositions.size(); mm++) {
                        if ((posit >= localPositions.get(mm).start) &&
                                (posit <= localPositions.get(mm).end)) {
                            isSoftwarePattern = true;
                            currentSoftwareIndex = mm;
                            break;
                        } else if (posit < localPositions.get(mm).start) {
                            isSoftwarePattern = false;
                            break;
                        } else if (posit > localPositions.get(mm).end) {
                            continue;
                        }
                    }
                }*/

                isUrl = false;
                if (urlPositions != null) {
                    for(OffsetPosition thePosition : urlPositions) {
                        if (positUrl >= thePosition.start && positUrl <= thePosition.end) {     
                            isUrl = true;
                            break;
                        } 
                    }
                }

                FeaturesVectorSoftware featuresVector =
                        FeaturesVectorSoftware.addFeaturesSoftware(token, label, 
                            softwareLexicon.inSoftwareDictionary(token), isSoftwarePattern, isUrl);
                if (featuresVector.label == null)
                    continue;
                writer.write(featuresVector.printVector());
                writer.write("\n");
                writer.flush();
                posit++;
                positUrl++;
                isSoftwarePattern = false;
            }
        } catch (Exception e) {
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
    }

    public static String serialize(org.w3c.dom.Document doc, Node node) {
        DOMSource domSource = null;
        String xml = null;
        try {
            if (node == null) {
                domSource = new DOMSource(doc);
            } else {
                domSource = new DOMSource(node);
            }
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            if (node != null)
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(domSource, result);
            xml = writer.toString();
        } catch(TransformerException ex) {
            ex.printStackTrace();
        }
        return xml;
    }

    /**
     * Using a set of negative examples, select those which contradicts a given recognition model.
     * Contradict means that the model predicts incorrectly a software mention and that this 
     * particular negative example is particularly relevant to correct this model. 
     *
     * Given the max parameter, if the max if not reached, we fill the remaning with random samples. 
     */
    public int selectNegativeExamples(File negativeCorpusFile, double max, File outputXMLFile) {
        int totalExamples = 0;
        Writer writer = null;
        try {
            System.out.println("Negative corpus path: " + negativeCorpusFile.getPath());
            System.out.println("selection corpus path: " + outputXMLFile.getPath());

            // the file for writing the training data
            writer = new OutputStreamWriter(new FileOutputStream(outputXMLFile), "UTF8");

            SoftwareParser parser = SoftwareParser.getInstance(this.conf);

            if (!negativeCorpusFile.exists()) {
                System.out.println("The XML TEI negative corpus does not exist: " + negativeCorpusFile.getPath());
            } else {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                String tei = FileUtils.readFileToString(negativeCorpusFile, UTF_8);
                org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(tei)));

                int totalAdded = 0;

                // list of index of nodes to remove
                List<Integer> toAdd = new ArrayList<Integer>();

                NodeList pList = document.getElementsByTagName("p");

                try (ProgressBar pb = new ProgressBar("active negative sampling", pList.getLength())) {
                    for (int i = 0; i < pList.getLength(); i++) {
                        Element paragraphElement = (Element) pList.item(i);
                        String text = XMLUtilities.getText(paragraphElement);
                        if (text == null || text.trim().length() == 0) {
                            pb.step();
                            continue;
                        }

                        // run the mention recognizer and check if we have annotations
                        List<SoftwareEntity> entities = parser.processText(text, false);
                        if (entities != null && entities.size() > 0) {
                            toAdd.add(new Integer(i));
                            totalAdded++;
                        }
                        pb.step();
                    }
                }

                System.out.println("Number of examples based on active sampling: " + totalAdded);

                List<Integer> toRemove = new ArrayList<Integer>();
                for (int i = 0; i < pList.getLength(); i++) {
                    Element paragraphElement = (Element) pList.item(i);
                    if (totalAdded < max) {
                        toAdd.add(new Integer(i));
                        totalAdded++;
                    } else if (!toAdd.contains(i)) {
                        toRemove.add(new Integer(i));
                    }
                }

                totalExamples = totalAdded;

                for(int j=toRemove.size()-1; j>0; j--) {
                    // remove the specific node
                    Node element = pList.item(toRemove.get(j));
                    if (element == null) {
                        System.out.println("Warning: null element at " + toRemove.get(j));
                        continue;
                    }
                    if (element.getParentNode() != null)
                        element.getParentNode().removeChild(element);
                }

                writer.write(serialize(document, null));
            }
        } catch (Exception e) {
            throw new GrobidException("An exception occured while selecting negative examples.", e);
        } finally {
            try {
                if (writer != null)
                    writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return totalExamples;
    }


    /**
     * Select a given number of negative examples among a given (very large) list, in a random
     * manner. 
     * @param max   maximum of negative examples to select
     */
    public int randomNegativeExamples(File negativeCorpusFile, double max, File outputXMLFile) {
        int totalExamples = 0;
        Writer writer = null;
        try {
            System.out.println("Negative corpus path: " + negativeCorpusFile.getPath());
            System.out.println("selection corpus path: " + outputXMLFile.getPath());

            // the file for writing the training data
            writer = new OutputStreamWriter(new FileOutputStream(outputXMLFile), "UTF8");

            SoftwareParser parser = SoftwareParser.getInstance(this.conf);

            if (!negativeCorpusFile.exists()) {
                System.out.println("The XML TEI negative corpus does not exist: " + negativeCorpusFile.getPath());
            } else {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                String tei = FileUtils.readFileToString(negativeCorpusFile, UTF_8);
                org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(tei)));

                NodeList pList = document.getElementsByTagName("p");
                int totalMaxExamples = pList.getLength();

                int rate = 0;
                if (max < totalMaxExamples) {
                    rate = (int)(totalMaxExamples / max) - 1;
                } /*else {
                    max = totalMaxExamples;
                }*/
                // list of index of nodes to remove
                List<Integer> toRemove = new ArrayList<Integer>();

                int rank = 0;
                int totalAdded = 0;
                try (ProgressBar pb = new ProgressBar("random negative sampling", pList.getLength())) {
                    for (int i = 0; i < pList.getLength(); i++) {
                        if (totalAdded >= max) {
                            toRemove.add(new Integer(i));
                            pb.step();
                            continue;
                        }

                        // we remove when no text auf jeden falle
                        Element paragraphElement = (Element) pList.item(i);
                        String text = XMLUtilities.getText(paragraphElement);
                        if (text == null || text.trim().length() == 0) {
                            toRemove.add(new Integer(i));
                            pb.step();
                            continue;
                        }

                        // if we want to avoid selecting the same paragraphs as the model-driven selection
                        // run the mention recognizer and check if we have annotations
                        /*List<SoftwareEntity> entities = parser.processText(text, false);
                        if (entities != null && entities.size() > 0) {
                            toRemove.add(new Integer(i));
                            pb.step();
                            continue;
                        }*/

                        if (rank >= rate) {
                            rank = 0;
                            totalAdded++;
                        } else {
                            toRemove.add(new Integer(i));
                        }
                        rank++;
                        pb.step();
                    }
                }

                totalExamples = pList.getLength() - toRemove.size();

                for(int j=toRemove.size()-1; j>0; j--) {
                    // remove the specific node
                    Node element = pList.item(toRemove.get(j));
                    if (element == null) {
                        System.out.println("Warning: null element at " + toRemove.get(j));
                        continue;
                    }
                    if (element.getParentNode() != null)
                        element.getParentNode().removeChild(element);
                }

                writer.write(serialize(document, null));
            }
        } catch (Exception e) {
            throw new GrobidException("An exception occured while selecting negative examples.", e);
        } finally {
            try {
                if (writer != null)
                    writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return totalExamples;
    }

    /**
     * Standard evaluation via the the usual Grobid evaluation framework.
     */
    @Override
    public String evaluate() {
        return evaluate(false);
    }

    @Override
    public String evaluate(boolean includeRawResults) {
        File evalDataF = GrobidProperties.getInstance().getEvalCorpusPath(
                new File(new File("resources").getAbsolutePath()), model);

        File tmpEvalPath = getTempEvaluationDataPath();
        createCRFPPData(evalDataF, tmpEvalPath);

        return EvaluationUtilities.evaluateStandard(tmpEvalPath.getAbsolutePath(), getTagger()).toString(includeRawResults);
    }

    @Override
    public String evaluate(GenericTagger tagger, boolean includeRawResults) {
        File evalDataF = GrobidProperties.getInstance().getEvalCorpusPath(
                new File(new File("resources").getAbsolutePath()), model);

        File tmpEvalPath = getTempEvaluationDataPath();
        createCRFPPData(evalDataF, tmpEvalPath);

        return EvaluationUtilities.evaluateStandard(tmpEvalPath.getAbsolutePath(), tagger).toString(includeRawResults);
    }

    @Override
    public String splitTrainEvaluate(Double split) {
        System.out.println("Paths :\n" + getCorpusPath() + "\n" + GrobidProperties.getModelPath(model).getAbsolutePath() + "\n" + getTempTrainingDataPath().getAbsolutePath() + "\n" + getTempEvaluationDataPath().getAbsolutePath());// + " \nrand " + random);

        File trainDataPath = getTempTrainingDataPath();
        File evalDataPath = getTempEvaluationDataPath();

        final File dataPath = trainDataPath;
        createCRFPPData(getCorpusPath(), dataPath, evalDataPath, split);
        GenericTrainer trainer = TrainerFactory.getTrainer();

        if (epsilon != 0.0)
            trainer.setEpsilon(epsilon);
        if (window != 0)
            trainer.setWindow(window);
        if (nbMaxIterations != 0)
            trainer.setNbMaxIterations(nbMaxIterations);

        final File tempModelPath = new File(GrobidProperties.getModelPath(model).getAbsolutePath() + NEW_MODEL_EXT);
        final File oldModelPath = GrobidProperties.getModelPath(model);

        trainer.train(getTemplatePath(), dataPath, tempModelPath, GrobidProperties.getNBThreads(), model);

        // if we are here, that means that training succeeded
        renameModels(oldModelPath, tempModelPath);

        return EvaluationUtilities.evaluateStandard(evalDataPath.getAbsolutePath(), getTagger()).toString();
    }

    protected final File getCorpusPath() {
        return new File(conf.getCorpusPath());
    }

    protected final File getTemplatePath() {
        return new File(conf.getTemplatePath());
    }

    /**
     * Command line execution.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        SoftwareConfiguration conf = null;
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            conf = mapper.readValue(new File("resources/config/config.yml"), SoftwareConfiguration.class);
            String pGrobidHome = conf.getGrobidHome();

            //String pGrobidHome = SoftwareProperties.get("grobid.home");

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            System.out.println(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());

            if (conf != null &&
                conf.getEngine() != null && 
                conf.getEngine().equals("delft"))
                GrobidProperties.setPropertyValue(GrobidPropertyKeys.PROP_GROBID_CRF_ENGINE + ".software", "delft");
            LibraryLoader.load();

        } catch (final Exception exp) {
            System.err.println("GROBID software initialisation failed: " + exp);
            exp.printStackTrace();
        }

        SoftwareTrainer trainer = new SoftwareTrainer();
        trainer.setSoftwareConf(conf);
        AbstractTrainer.runTraining(trainer);
        System.out.println(AbstractTrainer.runEvaluation(trainer));
        System.exit(0);
    }
}
