package org.grobid.trainer;

import org.apache.commons.io.FileUtils;

import org.grobid.core.engines.SoftwareModels;
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
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
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
import java.util.Iterator;

import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.GrobidProperties;
//import org.grobid.core.utilities.GrobidPropertyKeys;
import org.grobid.core.engines.tagging.GrobidCRFEngine;

import me.tongfei.progressbar.*;

/**
 * Training of the software entity recognition model
 *
 * @author Patrice
 */
public class SoftwareTypeTrainer extends AbstractTrainer {

    protected SoftwareLexicon softwareLexicon = null;

    protected SoftwareConfiguration conf = null;

    public SoftwareTypeTrainer() {
        super(SoftwareModels.SOFTWARE_TYPE);
        softwareLexicon = SoftwareLexicon.getInstance();
    }

    public void setSoftwareConf(SoftwareConfiguration conf) {
        this.conf = conf;
    }

    /**
     * Add the selected features to the model training for software types. 
     */
    @Override
    public int createCRFPPData(File sourcePathLabel,
                               File outputPath) {
        return createCRFPPData(sourcePathLabel, outputPath, null, 1.0);
    }

    /**
     * Add the selected features to the model training for software types. 
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
     * Training data here are produced from the unique training TEI file containing labeled sentences only
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
            handler.setTypeMode(true);
            
            // load training data in XML
            
            //final String corpus_file_name = "all_clean_post_processed-sentence.tei.xml";
            //handler.setSentenceLevel(true);

            final String corpus_file_name = "all_clean_post_processed.tei.xml";

            File thefile = new File(corpusDir.getPath() + File.separator + corpus_file_name);
            if (!thefile.exists()) {
                System.out.println("The XML TEI corpus training document does not exist: " + 
                    corpusDir.getPath() + File.separator + corpus_file_name);
            } else {
                //get a new instance of parser
                SAXParser p = spf.newSAXParser();
                p.parse(thefile, handler);

                List<List<List<Pair<String, String>>>> allLabeled = handler.getAllLabeledResult();
                List<List<Boolean>> allLabeledSoftwareFlags = handler.getAllLabeledSoftwareFlags();

                //labeled = subSample(labeled, ratioNegativeSample);

                // for the software typing, we can keep only the positive examples, because it is applied only
                // when at least one software is found
                //allLabeled = filterPositives(allLabeled);
                allLabeled = filterFlaggedPositives(allLabeled, allLabeledSoftwareFlags);

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

                            SoftwareTrainer.addFeatures(bufferLabeled, writer, softwareTokenPositions, urlPositions);
                            writer.write("\n");
                        }

                        writer.write("\n");
                        n++;
                    }
                }
            }

        } catch (Exception e) {
            throw new GrobidException("An exception occurred while training GROBID.", e);
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
     * Only keep labeled featured inputs with at least one positive label.
     * Filtered inputs are removed in place.
     **/
    private List<List<List<Pair<String, String>>>> filterPositives(List<List<List<Pair<String, String>>>> allLabeled) {
        List<Integer> documentToBeRemoved = new ArrayList<>();

        for (Iterator<List<List<Pair<String, String>>>> iter = allLabeled.iterator(); iter.hasNext(); ) {
            List<List<Pair<String, String>>> documentLabeled = iter.next();

            for (Iterator<List<Pair<String, String>>> iter2 = documentLabeled.iterator(); iter2.hasNext(); ) {
                List<Pair<String, String>> sentenceLabeled = iter2.next();

                // do we have a positive label in this sentence ? 
                boolean foundPositive = false;
                for(Pair<String, String> oneLabeledToken : sentenceLabeled) {
                    if (!oneLabeledToken.getB().equals("<other>")) {
                        foundPositive = true;
                        break;
                    }
                }

                if (!foundPositive) {
                    iter2.remove();
                }
            }

            if (documentLabeled.size() == 0) {
                iter.remove();
            }
        }

        return allLabeled;
    }

    /**
     * Only keep labeled featured inputs when they are flagged with a positive boolean.
     * Filtered inputs are removed in place.
     **/
    private List<List<List<Pair<String, String>>>> filterFlaggedPositives(List<List<List<Pair<String, String>>>> allLabeled,
        List<List<Boolean>> allLabeledSoftwareFlags) {

        //System.out.println("size allLabeled: " + allLabeled.size());
        //System.out.println("size allLabeledSoftwareFlags: " + allLabeledSoftwareFlags.size());

        int i = 0; // index of document in collection        
        for (Iterator<List<List<Pair<String, String>>>> iter = allLabeled.iterator(); iter.hasNext(); ) {
            List<List<Pair<String, String>>> documentLabeled = iter.next();

            //System.out.println("\tsize allLabeled at " + i + " : " + documentLabeled.size());
            //System.out.println("\tsize allLabeledSoftwareFlags at " + i + " : " + allLabeledSoftwareFlags.get(i).size());

            int j = 0; // index of sentence/paragraph in document
            for (Iterator<List<Pair<String, String>>> iter2 = documentLabeled.iterator(); iter2.hasNext(); ) {
                List<Pair<String, String>> sentenceLabeled = iter2.next();

                boolean foundPositive = allLabeledSoftwareFlags.get(i).get(j);

                if (!foundPositive) {
                    iter2.remove();
                }
                j++;
            }

            if (documentLabeled.size() == 0) {
                iter.remove();
            }
            i++;
        }

        return allLabeled;
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
        GenericTrainer trainer = TrainerFactory.getTrainer(model);

        if (epsilon != 0.0)
            trainer.setEpsilon(epsilon);
        if (window != 0)
            trainer.setWindow(window);
        if (nbMaxIterations != 0)
            trainer.setNbMaxIterations(nbMaxIterations);

        final File tempModelPath = new File(GrobidProperties.getModelPath(model).getAbsolutePath() + NEW_MODEL_EXT);
        final File oldModelPath = GrobidProperties.getModelPath(model);

        trainer.train(getTemplatePath(), dataPath, tempModelPath, GrobidProperties.getInstance().getWapitiNbThreads(), model);

        // if we are here, that means that training succeeded
        renameModels(oldModelPath, tempModelPath);

        return EvaluationUtilities.evaluateStandard(evalDataPath.getAbsolutePath(), getTagger()).toString();
    }

    protected final File getCorpusPath() {
        return new File(conf.getCorpusPath() + "/software/corpus/");
    }

    protected final File getTemplatePath() {
        return new File(conf.getCorpusPath() + "/software-type/crfpp-templates/software-type.template");
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
            File yamlFile = new File("resources/config/config.yml");
            yamlFile = new File(yamlFile.getAbsolutePath());
            conf = mapper.readValue(yamlFile, SoftwareConfiguration.class);
            String pGrobidHome = conf.getGrobidHome();

            //String pGrobidHome = SoftwareProperties.get("grobid.home");

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            System.out.println(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());

            if (conf != null && conf.getModel("software-type") != null)
                for (ModelParameters model : conf.getModels()) 
                    GrobidProperties.getInstance().addModel(model);
            LibraryLoader.load();

        } catch (final Exception exp) {
            System.err.println("GROBID software initialisation failed: " + exp);
            exp.printStackTrace();
        }

        SoftwareTypeTrainer trainer = new SoftwareTypeTrainer();
        trainer.setSoftwareConf(conf);
        AbstractTrainer.runTraining(trainer);
        System.out.println(AbstractTrainer.runEvaluation(trainer));
        System.exit(0);
    }

}