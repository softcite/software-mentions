package org.grobid.core.engines;

import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.data.SoftwareComponent;
import org.grobid.core.data.BiblioComponent;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.data.SoftwareType;
import org.grobid.core.lexicon.SoftwareLexicon.Software_Type;
import org.grobid.core.document.TEIFormatter;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.SoftwareTaggingLabels;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.engines.tagging.GrobidCRFEngine;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.features.FeaturesVectorSoftware;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.lexicon.SoftwareLexicon;
import org.grobid.core.lexicon.Lexicon;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.*;
import org.grobid.core.utilities.counters.CntManager;
import org.grobid.core.utilities.counters.impl.CntManagerFactory;
import org.grobid.core.lexicon.FastMatcher;
import org.grobid.core.utilities.SoftwareConfiguration;
import org.grobid.core.features.FeatureFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.*;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;
import org.apache.commons.lang3.tuple.Pair;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * A simple sequence model for software typing/relation prediction in a mention context.
 *
 * @author Patrice
 */
public class SoftwareTypeParser extends AbstractParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(SoftwareTypeParser.class);

    private static volatile SoftwareTypeParser instance;

    private SoftwareLexicon softwareLexicon = null;
    private EngineParsers parsers;
    private SoftwareConfiguration softwareConfiguration;

    public static SoftwareTypeParser getInstance(SoftwareConfiguration configuration) {
        if (instance == null) {
            getNewInstance(configuration);
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance(SoftwareConfiguration configuration) {
        instance = new SoftwareTypeParser(configuration);
    }

    
    private SoftwareTypeParser(SoftwareConfiguration configuration) {
        super(SoftwareModels.SOFTWARE_TYPE, CntManagerFactory.getCntManager(), 
            GrobidCRFEngine.valueOf(configuration.getModel("software-type").engine.toUpperCase()),
            configuration.getModel("software-type").delft.architecture);

        softwareLexicon = SoftwareLexicon.getInstance();
        parsers = new EngineParsers();
        softwareConfiguration = configuration;
    }

    public List<SoftwareType> processSentence(String sentence) throws Exception {
        List<String> sentences = new ArrayList<>();
        sentences.add(sentence);
        List<List<SoftwareType>> theTypes = processSentences(sentences);
        if (theTypes.size() > 0)
            return theTypes.get(0);
        else
            return null;
    }

    public List<List<SoftwareType>> processSentences(List<String> sentences) throws Exception {
        List<List<LayoutToken>> sentenceTokens = new ArrayList<>();
        for(String sentence : sentences) {
            List<LayoutToken> tokens = SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(sentence);
            sentenceTokens.add(tokens);
        }
        return processSentencesTokenSequenceMultiple(sentenceTokens);
    }

    public List<SoftwareType> processSentenceTokenSequence(List<LayoutToken> tokens) throws Exception {
        List<List<LayoutToken>> sentenceTokens = new ArrayList<>();
        sentenceTokens.add(tokens);
        List<List<SoftwareType>> theTypes = processSentencesTokenSequenceMultiple(sentenceTokens);
        if (theTypes.size() > 0)
            return theTypes.get(0);
        else
            return null;
    }

    public List<List<SoftwareType>> processSentencesTokenSequenceMultiple(List<List<LayoutToken>> layoutTokenList) throws Exception {
        StringBuilder allRess = new StringBuilder();
        List<List<SoftwareType>> entities = new ArrayList<>();
        for(List<LayoutToken> layoutTokens : layoutTokenList) {
            layoutTokens = SoftwareAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);

            if ( (layoutTokens == null) || (layoutTokens.size() == 0) )
                continue;

            // positions for lexical match
            List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(layoutTokens);
            List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(layoutTokens);

            // string representation of the feature matrix for sequence labeling lib
            String ress = SoftwareParser.getInstance(softwareConfiguration).addFeatures(layoutTokens, softwareTokenPositions, urlPositions);
            allRess.append(ress);
            allRess.append("\n\n");   
        }

        // labeled result from sequence labelling lib
        String allResString = allRess.toString();
        if (allResString.trim().length() == 0) {
            // empty content, nothing more to do
            return entities;
        }

        String res = label(allResString);
        int l = 0;
        String[] resBlocks = res.split("\n\n");
        for(List<LayoutToken> layoutTokens : layoutTokenList) {
            layoutTokens = SoftwareAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);

            if ( (layoutTokens == null) || (layoutTokens.size() == 0) )
                continue;

            // text of the selected segment
            String text = LayoutTokensUtil.toText(layoutTokens);
            String localRes = resBlocks[l];

            if (localRes == null || localRes.length() == 0) 
                continue;

            List<SoftwareType> localTypes = extractSoftwareTypes(text, localRes, layoutTokens);
            entities.add(localTypes);
            l++;
        }

        return entities;
    }

    /**
     * Process one already prepared input with features
     **/
    public List<SoftwareType> processFeatureInput(String text, String inputFeatures, List<LayoutToken> tokens) throws Exception {
        if (inputFeatures.trim().length() == 0) {
            // empty content, nothing more to do
            return null;
        }

        String res = label(inputFeatures);
        return extractSoftwareTypes(text, res, tokens);
    }

    /**
     * Process a list of already prepared input with features
     **/
    public List<List<SoftwareType>> processFeatureInputs(List<String> inputFeatures, List<List<LayoutToken>> layoutTokenList) throws Exception {
        if (inputFeatures == null || inputFeatures.size() == 0) {
            // empty content, nothing more to do
            return null;
        }

        List<List<SoftwareType>> entities = new ArrayList<>();

        String res = label(inputFeatures);
        int l = 0;
        String[] resBlocks = res.split("\n\n");
        for(List<LayoutToken> layoutTokens : layoutTokenList) {
            String localRes = resBlocks[l];
            String localText = LayoutTokensUtil.toText(layoutTokens);
            List<SoftwareType> localTypes = extractSoftwareTypes(localText, localRes, layoutTokens);
            entities.add(localTypes);
        }

        return entities;
    }

    private List<SoftwareType> extractSoftwareTypes(String text, String localRes, List<LayoutToken> layoutTokens) {
        List<SoftwareType> softwareTypes = new ArrayList<>();
        //System.out.println(localRes);
        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(SoftwareModels.SOFTWARE_TYPE, localRes, layoutTokens);
        List<TaggingTokenCluster> clusters = clusteror.cluster();

        SoftwareType currentSoftwareType = null;
        SoftwareLexicon.Software_Type openEntity = null;
        int pos = 0; // position in term of characters for creating the offsets

        for (TaggingTokenCluster cluster : clusters) {
            if (cluster == null) {
                continue;
            }

            TaggingLabel clusterLabel = cluster.getTaggingLabel();
            List<LayoutToken> theTokens = cluster.concatTokens();

            // avoid chunks already identified as reference markers
            boolean overlapRefMarker = false;
            for (LayoutToken token : theTokens) {
                List<TaggingLabel> localLabels = token.getLabels();
                if (localLabels != null) {
                    for(TaggingLabel label : localLabels) {
                        if (TEIFormatter.MARKER_LABELS.contains(label)) {
                            // we have a clash
                            overlapRefMarker = true;
                            break;
                        }
                    }
                }

                if (overlapRefMarker)
                    break;
            }            

            String clusterContent = LayoutTokensUtil.toText(cluster.concatTokens()).trim();

            if ((pos < text.length()-1) && (text.charAt(pos) == ' '))
                pos += 1;
            if ((pos < text.length()-1) && (text.charAt(pos) == '\n'))
                pos += 1;
            
            int endPos = pos;
            boolean start = true;
            for (LayoutToken token : theTokens) {
                if (token.getText() != null) {
                    if (start && token.getText().equals(" ")) {
                        pos++;
                        endPos++;
                        continue;
                    }
                    if (start)
                        start = false;
                    endPos += token.getText().length();
                }
            }

            if ((endPos > 0) && (text.length() >= endPos) && (text.charAt(endPos-1) == '\n'))
                endPos--;
            if ((endPos > 0) && (text.length() >= endPos) && (text.charAt(endPos-1) == ' '))
                endPos--;

            if (overlapRefMarker) {
                pos = endPos;
                continue;
            }

            if (!clusterLabel.equals(SoftwareTaggingLabels.OTHER_TYPE)) {

                // conservative check
                if (clusterLabel.equals(SoftwareTaggingLabels.ENVIRONMENT)) {
                    if (SoftwareAnalyzer.DELIMITERS.indexOf(clusterContent) != -1 || 
                        SoftwareLexicon.getInstance().isEnglishStopword(clusterContent) ||
                        FeatureFactory.getInstance().test_number(clusterContent)) {
                        pos = endPos;
                        continue;
                    }
                }

                // conservative check
                if (clusterLabel.equals(SoftwareTaggingLabels.COMPONENT)) {
                    if (SoftwareAnalyzer.DELIMITERS.indexOf(clusterContent) != -1 || 
                        SoftwareLexicon.getInstance().isEnglishStopword(clusterContent) ||
                        FeatureFactory.getInstance().test_number(clusterContent) ) {
                        // note: the last conditional test is a rare error by SciBERT model
                        pos = endPos;
                        continue;
                    }
                }

                // conservative check
                if (clusterLabel.equals(SoftwareTaggingLabels.IMPLICIT)) {
                    if (SoftwareAnalyzer.DELIMITERS.indexOf(clusterContent) != -1 || 
                        SoftwareLexicon.getInstance().isEnglishStopword(clusterContent) ||
                        FeatureFactory.getInstance().test_number(clusterContent) ) {
                        pos = endPos;
                        continue;
                    }
                }

                // conservative check
                if (clusterLabel.equals(SoftwareTaggingLabels.LANGUAGE)) {
                    if (SoftwareAnalyzer.DELIMITERS.indexOf(clusterContent) != -1 || 
                        SoftwareLexicon.getInstance().isEnglishStopword(clusterContent) ||
                        FeatureFactory.getInstance().test_number(clusterContent) ) {
                        pos = endPos;
                        continue;
                    }
                }

                currentSoftwareType = new SoftwareType();

                currentSoftwareType.setRawForm(clusterContent);

                currentSoftwareType.setOffsetStart(pos);
                currentSoftwareType.setOffsetEnd(endPos);

                currentSoftwareType.setLabel(clusterLabel);
                currentSoftwareType.setTokens(theTokens);

                String rawType = clusterLabel.getLabel().toUpperCase().replace("<", "").replace(">", "");
                //System.out.println(rawType);
                try {
                    currentSoftwareType.setType(SoftwareLexicon.Software_Type.valueOf(rawType));
                } catch(Exception e) {
                    LOGGER.warn("Type label could not be converted into software type: " + rawType);
                }
                List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(cluster.concatTokens());
                currentSoftwareType.setBoundingBoxes(boundingBoxes);

                if (currentSoftwareType.getType() != null)
                    softwareTypes.add(currentSoftwareType);
                currentSoftwareType = null;
            } 
            
            pos = endPos;
        }

        Collections.sort(softwareTypes);

        return softwareTypes;
    }

}
