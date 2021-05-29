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
import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.lexicon.FastMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.*;

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
    private boolean docLevel = false;
    private boolean disambiguate = false;

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
        GenericTrainer trainer = TrainerFactory.getTrainer(model);

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
            System.out.println("labeled data path: " + trainingOutputPath.getPath());
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

            //final String corpus_file_name = "all_clean_post_processed.tei.xml";
            //final String corpus_file_name = "softcite_corpus.tei.xml";
            //final String corpus_file_name = "softcite_corpus_pmc.tei.xml";
            //final String corpus_file_name = "softcite_corpus_econ.tei.xml";

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
                            if (!docLevel)
                                writer.write("\n");
                        }
                        writer.write("\n");
                        n++;
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
            StringBuilder resultBuilder = new StringBuilder();
            bufReader.close();
            System.out.println("Labeling took: " + (System.currentTimeMillis() - time) + " ms");

            SoftwareParser softwareParser = SoftwareParser.getInstance(this.conf);
            StringBuilder textBuilder = new StringBuilder();
            List<LayoutToken> tokens = new ArrayList<>();

            //System.out.println(theResult);

            int ind = theResult.indexOf("\n\n");
            int currentInd = 0;
            int pos = 0;
            while(ind != -1) {
                String localInstance = theResult.substring(currentInd, ind);
//System.out.println(localInstance);
                String[] localInstanceLines = localInstance.split("\n");
//System.out.println("nb line localInstance:" + localInstanceLines.length); 
                pos = 0;
                for(int i=0; i <localInstanceLines.length; i++) {
                    String theLine = localInstanceLines[i];
                    if (theLine.trim().length() == 0) {
                         textBuilder.append("\n");
                         LayoutToken newToken = new LayoutToken("\n");
                         newToken.setOffset(pos);
                         tokens.add(newToken);
                         pos++;
                    }
                    int ind2 = theLine.indexOf("\t");
                    if (ind2 == -1)
                        ind2 = theLine.indexOf(" ");
                    if (ind2 == -1)
                        continue;
                    LayoutToken newToken = new LayoutToken(theLine.substring(0,ind2));
                    newToken.setOffset(pos);
                    pos += newToken.getText().length();
                    textBuilder.append(theLine.substring(0,ind2));
                    textBuilder.append(" ");
                    tokens.add(newToken);
//System.out.println("adding: " + theLine.substring(0,ind2));
                    newToken = new LayoutToken(" ");
                    newToken.setOffset(pos);
                    tokens.add(newToken);
                    pos++;
                }

                String text = textBuilder.toString();
                textBuilder = new StringBuilder();

                // filter out software mentions based on entity disambiguation
//System.out.println("text: " + text);
//System.out.println("nb layout tokens: " + tokens.size());

                List<SoftwareComponent> components = softwareParser.extractSoftwareComponents(text, localInstance, tokens);

                // we group the identified components by full entities
                List<SoftwareEntity> entities = softwareParser.groupByEntities(components);

                if (this.disambiguate) {
                    // disambiguation evaluation
                    if (disambiguator == null)
                        disambiguator = SoftwareDisambiguator.getInstance(this.conf);
                    entities = disambiguator.disambiguate(entities, tokens); 
                }

                if (this.docLevel) {
                    // doc level evaluation
                    // we prepare a matcher for all the identified software names 
                    FastMatcher termPattern = softwareParser.prepareTermPattern(entities);
                    // we prepare the frequencies for each software name in the whole document
                    Map<String, Integer> frequencies = softwareParser.prepareFrequencies(entities, tokens);
                    // we prepare a map for mapping a software name with its positions of annotation in the document and its IDF
                    Map<String, Double> termProfiles = softwareParser.prepareTermProfiles(entities);
                    // and call the propagation method
                    List<OffsetPosition> placeTaken = softwareParser.preparePlaceTaken(entities);
                    entities = softwareParser.propagateLayoutTokenSequence(tokens, entities, termProfiles, termPattern, placeTaken, frequencies, false);
                    Collections.sort(entities);
                }          

                int currentLineIndex = 0;
                int currentLayoutTokenIndex = 0;
                pos = 0;
                int posLine = 0;
                // review labelling based on disambiguated entities
                for(SoftwareEntity entity : entities) {
                    if (entity.isFiltered()) {
                        List<SoftwareComponent> localComponents = new ArrayList<>();
                        List<String> componentTypes = new ArrayList<>();
                        SoftwareComponent theComponent = entity.getSoftwareName();
                        Double disamb_score = null;
                        if (theComponent != null) {
                            localComponents.add(theComponent);
                            componentTypes.add("software");
                            disamb_score = theComponent.getDisambiguationScore();
                            /*if (disamb_score != null) {
                                System.out.println(disamb_score);
                            }*/
                        }
                        theComponent = entity.getVersion();
                        if (theComponent != null) {
                            localComponents.add(theComponent);
                            componentTypes.add("version");
                        }
                        theComponent = entity.getCreator();
                        if (theComponent != null) {
                            localComponents.add(theComponent);
                            componentTypes.add("creator");
                        }
                        theComponent = entity.getSoftwareURL();
                        if (theComponent != null) {
                            localComponents.add(theComponent);
                            componentTypes.add("url");
                        } 

                        if (disamb_score != null && disamb_score.doubleValue() < 0.4) {
                            continue;
                        }

                        for(SoftwareComponent component : localComponents) {
                            // it should always be the case
                            int offset_start = component.getOffsetStart();
                            int offset_end = component.getOffsetEnd();
//System.out.println("offsets: " + offset_start + " / " + offset_end + " | " + text.length());
                            String segment = text.substring(offset_start, offset_end);
//System.out.println("filtered: " + segment);

                            List<String> segmentTokens = SoftwareAnalyzer.getInstance().tokenize(segment);

                            // align with labeled string
                            for(int l=currentLineIndex; l<localInstanceLines.length; l++) {
                                String theLine = localInstanceLines[l];
                                int ind2 = theLine.indexOf("\t");
                                if (ind2 == -1)
                                    ind2 = theLine.indexOf(" ");
                                if (ind2 == -1)
                                    continue;
                                String tokenStr = theLine.substring(0,ind2);
                                posLine += tokenStr.length();

                                /*if (posLine < offset_start)
                                    continue;*/

                                String currentToken = "";
                                while(currentToken.equals(" ") || pos < offset_start || pos < posLine) {
                                    currentToken = tokens.get(currentLayoutTokenIndex).getText();
                                    currentLayoutTokenIndex++;
                                    pos += currentToken.length();
                                    if (pos >= posLine && currentToken.equals(tokenStr)) {
                                        posLine = pos;
                                        break;
                                    }
                                }
                                if (pos > offset_end)
                                    break;
                                if (segmentTokens.contains(tokenStr)) {
                                    int ind3 = theLine.lastIndexOf("\t");
                                    if (ind3 == -1)
                                        ind3 = theLine.indexOf(" ");
                                    if (ind3 != -1) {
//System.out.println(theLine);
                                        String theNewLine = theLine.substring(0, ind3);
                                        theNewLine += "\t<other>";
//System.out.println(theNewLine);
                                        localInstance = localInstance.replace(theLine, theNewLine);

                                    }
                                    currentLineIndex = l;
                                    posLine = pos;
                                }
                            }
                        }       
                    }
                } 

                currentLineIndex = 0;
                currentLayoutTokenIndex = 0;
                pos = 0;
                posLine = 0;
                // review labelling based on doc level propagation 
                for(SoftwareEntity entity : entities) {
                    if (entity.isPropagated()) {
                        SoftwareComponent component = entity.getSoftwareName();
                        if (component != null) {
                            int offset_start = component.getOffsetStart();
                            int offset_end = component.getOffsetEnd();
                            String segment = text.substring(offset_start, offset_end);
                            List<String> segmentTokens = SoftwareAnalyzer.getInstance().tokenize(segment);

                            boolean start = true;
                            // align with labeled string
                            for(int l=currentLineIndex; l<localInstanceLines.length; l++) {
                                String theLine = localInstanceLines[l];
                                int ind2 = theLine.indexOf("\t");
                                if (ind2 == -1)
                                    ind2 = theLine.indexOf(" ");
                                if (ind2 == -1)
                                    continue;
                                String tokenStr = theLine.substring(0,ind2);
                                posLine += tokenStr.length();

                                /*if (posLine < offset_start)
                                    continue;*/

                                String currentToken = "";
                                while(currentToken.equals(" ") || pos < offset_start || pos < posLine) {
                                    currentToken = tokens.get(currentLayoutTokenIndex).getText();
                                    currentLayoutTokenIndex++;
                                    pos += currentToken.length();
                                    if (pos >= posLine && currentToken.equals(tokenStr)) {
                                        posLine = pos;
                                        break;
                                    }
                                }
                                if (pos > offset_end)
                                    break;
                                if (segmentTokens.contains(tokenStr)) {
                                    int ind3 = theLine.lastIndexOf("\t");
                                    if (ind3 == -1)
                                        ind3 = theLine.indexOf(" ");
                                    if (ind3 != -1) {

                                        String originalLabel = theLine.substring(ind3, theLine.length());
                                        if (originalLabel.equals("other")) {
                                            String theNewLine = theLine.substring(0, ind3);
                                            if (start) {
                                                theNewLine += "\tI-<software>";
                                                start = false;
                                            }
                                            else    
                                                theNewLine += "\t<software>";
                                            localInstance = localInstance.replace(theLine, theNewLine);
                                        }
                                    }
                                    currentLineIndex = l;
                                    posLine = pos;
                                }
                            }
                        }
                    }
                }

                resultBuilder.append(localInstance);
                resultBuilder.append("\n\n");

                currentInd = ind+2;
                ind = theResult.indexOf("\n\n", ind+1);
                textBuilder = new StringBuilder();
                tokens = new ArrayList<>();
            }
            theResult = resultBuilder.toString();

        } catch (Exception e) {
            throw new GrobidException("An exception occurred while evaluating Grobid.", e);
        }

        return EvaluationUtilities.computeStats(theResult);
    }

    public void setDocLevel(boolean docLevel) {
        this.docLevel = docLevel;
    }

    public boolean isDocLevel() {
        return this.docLevel;
    }

    public void setDisambiguate(boolean disamb) {
        this.disambiguate = disamb;
    }

    public boolean isDisambiguate() {
        return this.disambiguate;
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