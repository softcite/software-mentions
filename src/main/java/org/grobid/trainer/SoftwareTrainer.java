package org.grobid.trainer;

import org.grobid.core.GrobidModels;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.engines.EngineParsers;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.features.FeaturesVectorSoftware;
import org.grobid.core.layout.Block;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.PDFAnnotation;
import org.grobid.core.lexicon.SoftwareLexicon;
import org.grobid.core.utilities.SoftwareProperties;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.utilities.Pair;
import org.grobid.trainer.evaluation.EvaluationUtilities;
import org.grobid.core.main.GrobidHomeFinder;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * Training of the software entity recognition model
 *
 * @author Patrice
 */
public class SoftwareTrainer extends AbstractTrainer {

    private SoftwareLexicon softwareLexicon = null;

    public SoftwareTrainer() {
        this(0.00001, 20, 0);
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

    /**
     * Add the selected features to the model training for software entities. For grobid-software, 
     * we can have two types of training files: XML/TEI files where text content is annotated with
     * software entities, and PDF files where the entities are annotated with an additional
     * PDf layer. The two types of training files suppose two different process in order to
     * generate the CRF training file.
     */
    public int createCRFPPData(File sourcePathLabel,
                               File outputPath) {
        return createCRFPPData(sourcePathLabel, outputPath, null, 1.0);
    }

    /**
     * Add the selected features to the model training for software entities. Split
     * automatically all available labeled data into training and evaluation data
     * according to a given split ratio.
     */
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
        int totalExamples = 0;
        Writer writerTraining = null;
        Writer writerEvaluation = null;
        try {
            System.out.println("labeled corpus path: " + corpusDir.getPath());
            System.out.println("training data path: " + trainingOutputPath.getPath());
            System.out.println("evaluation data path: " + trainingOutputPath.getPath());

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
                    //System.out.println(name);
                    //if (n > 100)
                    //    break;
                    SoftwareAnnotationSaxHandler handler = new SoftwareAnnotationSaxHandler();

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

                        addFeatures(bufferLabeled, writer, softwareTokenPositions);
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
    public Pair<String, List<LayoutToken>> getCRFData(File pdfFile, List<PDFAnnotation> annotations) {

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

                addFeatures(labeled, crfWriter, softwareTokenPositions);
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
    }

    @SuppressWarnings({"UnusedParameters"})
    private void addFeatures(List<Pair<String, String>> texts,
                             Writer writer,
                             List<OffsetPosition> softwareTokenPositions) {
        int totalLine = texts.size();
        int posit = 0;
        int currentSoftwareIndex = 0;
        List<OffsetPosition> localPositions = softwareTokenPositions;
        boolean isSoftwarePattern = false;
        try {
            for (Pair<String, String> lineP : texts) {
                String token = lineP.getA();
                if (token.trim().equals("@newline")) {
                    writer.write("\n");
                    writer.flush();
                }

                String label = lineP.getB();
                /*if (label != null) {
                    isSoftwarePattern = true;
                }*/

                // do we have an software at position posit?
                if ((localPositions != null) && (localPositions.size() > 0)) {
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
                }

                FeaturesVectorSoftware featuresVector =
                        FeaturesVectorSoftware.addFeaturesSoftware(token, label,
                                softwareLexicon.inSoftwareDictionary(token), isSoftwarePattern);
                if (featuresVector.label == null)
                    continue;
                writer.write(featuresVector.printVector());
                writer.write("\n");
                writer.flush();
                posit++;
                isSoftwarePattern = false;
            }
        } catch (Exception e) {
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
    }

    /**
     * Standard evaluation via the the usual Grobid evaluation framework.
     */
    public String evaluate() {
        File evalDataF = GrobidProperties.getInstance().getEvalCorpusPath(
                new File(new File("resources").getAbsolutePath()), model);

        File tmpEvalPath = getTempEvaluationDataPath();
        createCRFPPData(evalDataF, tmpEvalPath);

        return EvaluationUtilities.evaluateStandard(tmpEvalPath.getAbsolutePath(), getTagger());
    }

    public String splitTrainEvaluate(Double split, boolean random) {
        System.out.println("PAths :\n" + getCorpusPath() + "\n" + GrobidProperties.getModelPath(model).getAbsolutePath() + "\n" + getTempTrainingDataPath().getAbsolutePath() + "\n" + getTempEvaluationDataPath().getAbsolutePath() + " \nrand " + random);

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

        return EvaluationUtilities.evaluateStandard(evalDataPath.getAbsolutePath(), getTagger());
    }

    protected final File getCorpusPath() {
        return new File(SoftwareProperties.get("grobid.software.corpusPath"));
    }

    protected final File getTemplatePath() {
        return new File(SoftwareProperties.get("grobid.software.templatePath"));
    }

    /**
     * Command line execution.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        try {
            String pGrobidHome = SoftwareProperties.get("grobid.home");

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            System.out.println(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());
        } catch (final Exception exp) {
            System.err.println("GROBID software initialisation failed: " + exp);
            exp.printStackTrace();
        }

        Trainer trainer = new SoftwareTrainer();
        AbstractTrainer.runTraining(trainer);
        AbstractTrainer.runEvaluation(trainer);
    }
}