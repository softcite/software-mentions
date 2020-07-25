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
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.utilities.Pair;
import org.grobid.core.utilities.SoftwareConfiguration;
import org.grobid.trainer.evaluation.*;
import org.grobid.core.engines.SoftwareDisambiguator;
import org.grobid.core.engines.SoftwareParser;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.data.SoftwareComponent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.chasen.crfpp.Tagger;
import org.grobid.core.engines.tagging.GenericTagger;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Evaluation of the software entity recognition model with additional 
 *
 * @author Patrice
 */
public class SoftwareExtendedEval extends SoftwareTrainer {

    /*private SoftwareLexicon softwareLexicon = null;
    private SoftwareConfiguration conf = null;*/
    private SoftwareDisambiguator disambiguator = null;

    public SoftwareExtendedEval() {
        super(0.00001, 20, 0);
        softwareLexicon = SoftwareLexicon.getInstance();
    }

    public SoftwareExtendedEval(double epsilon, int window, int nbMaxIterations) {
        super(epsilon, window, 2000);
        softwareLexicon = SoftwareLexicon.getInstance();
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

        return this.evaluateStandard(tmpEvalPath.getAbsolutePath(), getTagger()).toString(includeRawResults);
    }

    @Override
    public String evaluate(GenericTagger tagger, boolean includeRawResults) {
        File evalDataF = GrobidProperties.getInstance().getEvalCorpusPath(
                new File(new File("resources").getAbsolutePath()), model);

        File tmpEvalPath = getTempEvaluationDataPath();
        createCRFPPData(evalDataF, tmpEvalPath);

        return this.evaluateStandard(tmpEvalPath.getAbsolutePath(), tagger).toString(includeRawResults);
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

        return this.evaluateStandard(evalDataPath.getAbsolutePath(), getTagger()).toString();
    }

    /**
     * CRF training data here are produced from the unique training TEI file containing labelled paragraphs only
     */
    @Override
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

            //File thefile = new File(corpusDir.getPath() + "/all.clean.tei.xml");
            File thefile = new File(corpusDir.getPath() + "/all_clean_post_processed.tei.xml");
            //all_clean_post_processed_with_no_mention.tei.xml

            if (!thefile.exists()) {
                System.out.println("The XML TEI corpus training document does not exist: " + corpusDir.getPath() + "/all_clean_post_processed.tei.xml");
            } else {
                //get a new instance of parser
                SAXParser p = spf.newSAXParser();
                p.parse(thefile, handler);

                List<List<Pair<String, String>>> allLabeled = handler.getLabeledResult();
                //labeled = subSample(labeled, ratioNegativeSample);

                int n = 0;
                for(List<Pair<String, String>> labeled : allLabeled) {

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

    public ModelStats evaluateStandard(String path, final GenericTagger tagger) {
        return this.evaluateStandard(path, tagger::label);
    }

    public ModelStats evaluateStandard(String path, Function<List<String>, String> taggerFunction) {
        String theResult = null;
        try {
            final BufferedReader bufReader = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));

            String line = null;
            List<String> instance = new ArrayList<>();
            while ((line = bufReader.readLine()) != null) {
                instance.add(line);
            }
            long time = System.currentTimeMillis();
            theResult = taggerFunction.apply(instance);
            bufReader.close();
            System.out.println("Labeling took: " + (System.currentTimeMillis() - time) + " ms");

            SoftwareParser softwareParser = SoftwareParser.getInstance(this.conf);
            StringBuilder textBuilder = new StringBuilder();
            List<LayoutToken> tokens = new ArrayList<>();

            //System.out.println(theResult);

            int ind = theResult.indexOf("\n\n");
            int currentInd = 0;
            while(ind != -1) {
                String localInstance = theResult.substring(currentInd, ind);
//System.out.println(localInstance);
                String[] localInstanceLines = localInstance.split("\n");
System.out.println("nb line localInstance:" + localInstanceLines.length); 
                for(int i=0; i <localInstanceLines.length; i++) {
                    String theLine = localInstanceLines[i];
                    if (theLine.trim().length() == 0) {
                         textBuilder.append("\n");
                         tokens.add(new LayoutToken("\n"));
                     }
                    int ind2 = theLine.indexOf("\t");
                    if (ind2 == -1)
                        ind2 = theLine.indexOf(" ");
                    if (ind2 == -1)
                        continue;
                    textBuilder.append(theLine.substring(0,ind2));
                    textBuilder.append(" ");
                    tokens.add(new LayoutToken(theLine.substring(0,ind2)));
//System.out.println("adding: " + theLine.substring(0,ind2));
                    tokens.add(new LayoutToken(" "));
                }

                String text = textBuilder.toString();

                // filter out software mentions based on entity disambiguation
//System.out.println("text: " + text);

                List<SoftwareComponent> components = softwareParser.extractSoftwareComponents(text, localInstance, tokens);

                // we group the identified components by full entities
                List<SoftwareEntity> entities = softwareParser.groupByEntities(components);

                // disambiguation
                if (disambiguator == null)
                    disambiguator = SoftwareDisambiguator.getInstance(this.conf);
                entities = disambiguator.disambiguate(entities, tokens);

                // review labelling based on disambiguated entities
                /*for(SoftwareEntity entity : entities) {
                    int offset_start = entity.getOffsetStart;
                    int offset_end = entity.getOffsetStart;
                }*/
                
                currentInd = ind+2;
                ind = theResult.indexOf("\n\n", ind+1);
                textBuilder = new StringBuilder();
                tokens = new ArrayList<>();
            }

        } catch (Exception e) {
            throw new GrobidException("An exception occurred while evaluating Grobid.", e);
        }

        return EvaluationUtilities.computeStats(theResult);
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
        } catch (final Exception exp) {
            System.err.println("GROBID software initialisation failed: " + exp);
            exp.printStackTrace();
        }

        SoftwareTrainer trainer = new SoftwareExtendedEval();
        trainer.setSoftwareConf(conf);
        AbstractTrainer.runTraining(trainer);
        System.out.println(AbstractTrainer.runEvaluation(trainer));
        System.exit(0);
    }
}