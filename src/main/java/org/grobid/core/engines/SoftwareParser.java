package org.grobid.core.engines;

import nu.xom.Attribute;
import nu.xom.Element;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.grobid.core.GrobidModels;
import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.data.*;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.document.TEIFormatter;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.SoftwareTaggingLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.engines.tagging.GrobidCRFEngine;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.features.FeatureFactory;
import org.grobid.core.features.FeaturesVectorSoftware;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.layout.PDFAnnotation;
import org.grobid.core.lexicon.FastMatcher;
import org.grobid.core.lexicon.Lexicon;
import org.grobid.core.lexicon.SoftwareLexicon;
import org.grobid.core.sax.TextChunkSaxHandler;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.*;
import org.grobid.core.utilities.counters.impl.CntManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;

/**
 * Software mentions extraction.
 *
 * @author Patrice
 */
public class SoftwareParser extends AbstractParser {
    private static final Logger logger = LoggerFactory.getLogger(SoftwareParser.class);

    private static volatile SoftwareParser instance;

    private SoftwareLexicon softwareLexicon = null;
    private EngineParsers parsers;
    private SoftwareDisambiguator disambiguator;
    private SoftwareConfiguration softwareConfiguration;
    private SoftwareTypeParser softwareTypeParser;

    public static SoftwareParser getInstance(SoftwareConfiguration configuration) {
        if (instance == null) {
            getNewInstance(configuration);
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance(SoftwareConfiguration configuration) {
        instance = new SoftwareParser(configuration);
    }


    private SoftwareParser(SoftwareConfiguration configuration) {
        super(GrobidModels.SOFTWARE, CntManagerFactory.getCntManager(),
            GrobidCRFEngine.valueOf(configuration.getModel("software").engine.toUpperCase()),
            configuration.getModel("software").delft.architecture);

        softwareLexicon = SoftwareLexicon.getInstance();
        parsers = new EngineParsers();
        disambiguator = SoftwareDisambiguator.getInstance(configuration);
        softwareConfiguration = configuration;
        softwareTypeParser = SoftwareTypeParser.getInstance(configuration);
    }

    public List<List<SoftwareEntity>> processTexts(List<List<LayoutToken>> tokens, boolean disambiguate) throws Exception {
        if (CollectionUtils.isEmpty(tokens)) {
            return new ArrayList<>();
        }

        List<String> ressList = new ArrayList<>();

        for (List<LayoutToken> tokensSentence : tokens) {
            // to store software name positions (names coming from the optional dictionary)
            List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(tokensSentence);
            List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(tokensSentence);
            String ress = addFeatures(tokensSentence, softwareTokenPositions, urlPositions);
            ressList.add(ress + "\n");
        }
        String res;
        try {
            res = label(ressList);
        } catch (Exception e) {
            throw new GrobidException("Sequence labeling for software parsing failed.", e);
        }
        String[] split = res.split("\n\n");
        List<String> resList = Arrays.asList(split);


        List<SoftwareComponent> allComponents = new ArrayList<>();
        List<List<SoftwareEntity>> allEntities = new ArrayList<>();

        int sentenceStartToken = 0;

        for (int i = 0; i < tokens.size(); i++) {

            List<SoftwareComponent> components = extractSoftwareComponents(LayoutTokensUtil.toText(tokens.get(i)), resList.get(i), tokens.get(i));
            // we group the identified components by full entities
            List<SoftwareEntity> entities = groupByEntities(components);
            // disambiguation

            if (disambiguate) {
                entities = disambiguator.disambiguate(entities, tokens.get(i));
                // apply existing filtering
                List<Integer> indexToBeFiltered = new ArrayList<>();
                int k = 0;
                for (SoftwareEntity entity : entities) {
                    if (entity.isFiltered()) {
                        indexToBeFiltered.add(Integer.valueOf(k));
                    }
                    k++;
                }

                if (indexToBeFiltered.size() > 0) {
                    for (int j = indexToBeFiltered.size() - 1; j >= 0; j--) {
                        entities.remove(indexToBeFiltered.get(j).intValue());
                    }
                }
            }


            // propagate
            // we prepare a matcher for all the identified software names
            FastMatcher termPattern = prepareTermPattern(entities);
            // we prepare the frequencies for each software name in the whole document
            Map<String, Integer> frequencies = prepareFrequencies(entities, tokens.get(i));
            // we prepare a map for mapping a software name with its positions of annotation in the document and its IDF
            Map<String, Double> termProfiles = prepareTermProfiles(entities);
            // we prepare a list of existing positions to avoid overlap
            List<OffsetPosition> placeTaken = preparePlaceTaken(entities);
            // and call the propagation method
            entities = propagateLayoutTokenSequence(tokens.get(i), entities, termProfiles, termPattern, placeTaken, frequencies, false, false, false);
            Collections.sort(entities);

            // refine software types, if there is anything to refine
            if (entities.size() > 0) {
                try {
                    List<SoftwareType> entityTypes = softwareTypeParser.processFeatureInput(LayoutTokensUtil.toText(tokens.get(i)), resList.get(i), tokens.get(i));
                    /*for(SoftwareType entityType : entityTypes) {
                        System.out.println("\n" + entityType.toString());
                    }*/
                    if (entityTypes != null && entityTypes.size() > 0) {
                        entities = refineTypes(entities, entityTypes);

                        // additional sort in case new entites were introduced
                        Collections.sort(entities);
                    }

                } catch (Exception e) {
                    throw new GrobidException("Sequence labeling for software type parsing failed.", e);
                }
            }

            // attach a local text context to the entities
            entities = addContext(entities, LayoutTokensUtil.toText(tokens.get(i)), tokens.get(i), false, false, false);

            // finally classify the context for predicting the role of the software mention
            entities = SoftwareContextClassifier.getInstance(softwareConfiguration).classifyDocumentContexts(entities);

            allEntities.add(entities);
            allComponents.addAll(components);
        }

        return allEntities;
    }

    /**
     * Extract all Software mentions from a simple piece of text.
     */
    public List<SoftwareEntity> processText(String text, boolean disambiguate) throws Exception {
        if (isBlank(text)) {
            return null;
        }
        text = UnicodeUtil.normaliseText(text);
        text = text.replace("\n", " ");
        text = text.replace("\t", " ");
        List<LayoutToken> tokens = SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(text);
        if (CollectionUtils.isEmpty(tokens)) {
            return null;
        } else if (tokens.size() > 512 && softwareConfiguration.getModel("software").engine.equals("delft")) {
//        } else if (tokens.size() > 512) {
            String tempText = LayoutTokensUtil.toText(tokens);
            List<OffsetPosition> offsetPositions = SentenceUtilities.getInstance().runSentenceDetection(tempText);
            List<List<LayoutToken>> outputTokens = offsetPositions.stream()
                .map(op -> tempText.substring(op.start, op.end))
                .map(s -> SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(s))
                .collect(Collectors.toList());
            List<List<SoftwareEntity>> tempEntities = processTexts(outputTokens, disambiguate);

            List<SoftwareEntity> allEntities = new ArrayList<>();
            // Adjust the start and end position for each entity based on the original tokens

            for (int i = 0; i < offsetPositions.size(); i++) {
                List<SoftwareEntity> entities = tempEntities.get(i);
                int sentenceStartToken = offsetPositions.get(i).start;

                for (SoftwareEntity entity : entities) {
                    entity.getSoftwareName().setOffsetStart(entity.getSoftwareName().getOffsetStart() + sentenceStartToken);
                    entity.getSoftwareName().setOffsetEnd(entity.getSoftwareName().getOffsetEnd() + sentenceStartToken);

                    if (entity.getVersion() != null) {
                        entity.getVersion().setOffsetStart(entity.getVersion().getOffsetStart() + sentenceStartToken);
                        entity.getVersion().setOffsetEnd(entity.getVersion().getOffsetEnd() + sentenceStartToken);
                    }

                    if (entity.getCreator() != null) {
                        entity.getCreator().setOffsetStart(entity.getCreator().getOffsetStart() + sentenceStartToken);
                        entity.getCreator().setOffsetEnd(entity.getCreator().getOffsetEnd() + sentenceStartToken);
                    }

                    if (entity.getSoftwareURL() != null) {
                        entity.getSoftwareURL().setOffsetStart(entity.getSoftwareURL().getOffsetStart() + sentenceStartToken);
                        entity.getSoftwareURL().setOffsetEnd(entity.getSoftwareURL().getOffsetEnd() + sentenceStartToken);
                    }
                    if (entity.getBibRefs() != null) {
                        int sentence2StartToken = sentenceStartToken;
                        entity.getBibRefs().forEach(bibRef -> bibRef.setOffsetStart(bibRef.getOffsetStart() + sentence2StartToken));
                        entity.getBibRefs().forEach(bibRef -> bibRef.setOffsetEnd(bibRef.getOffsetEnd() + sentence2StartToken));
                    }
                }
                allEntities.addAll(entities);
            }

            return allEntities;
        }

        List<SoftwareComponent> components = new ArrayList<SoftwareComponent>();
        List<SoftwareEntity> entities = null;
        try {
            // to store software name positions (names coming from the optional dictionary)
            List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(tokens);
            List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(tokens);
            String ress = addFeatures(tokens, softwareTokenPositions, urlPositions);
            String res;
            try {
                res = label(ress);
            } catch (Exception e) {
                throw new GrobidException("Sequence labeling for software parsing failed.", e);
            }

            components = extractSoftwareComponents(text, res, tokens);

            // we group the identified components by full entities
            entities = groupByEntities(components);

            // disambiguation
            if (disambiguate) {
                entities = disambiguator.disambiguate(entities, tokens);
                // apply existing filtering
                List<Integer> indexToBeFiltered = new ArrayList<>();
                int k = 0;
                for (SoftwareEntity entity : entities) {
                    if (entity.isFiltered()) {
                        indexToBeFiltered.add(Integer.valueOf(k));
                    }
                    k++;
                }

                if (indexToBeFiltered.size() > 0) {
                    for (int j = indexToBeFiltered.size() - 1; j >= 0; j--) {
                        entities.remove(indexToBeFiltered.get(j).intValue());
                    }
                }
            }

            // propagate
            // we prepare a matcher for all the identified software names
            FastMatcher termPattern = prepareTermPattern(entities);
            // we prepare the frequencies for each software name in the whole document
            Map<String, Integer> frequencies = prepareFrequencies(entities, tokens);
            // we prepare a map for mapping a software name with its positions of annotation in the document and its IDF
            Map<String, Double> termProfiles = prepareTermProfiles(entities);
            // we prepare a list of existing positions to avoid overlap
            List<OffsetPosition> placeTaken = preparePlaceTaken(entities);
            // and call the propagation method
            entities = propagateLayoutTokenSequence(tokens, entities, termProfiles, termPattern, placeTaken, frequencies, false, false, false);
            Collections.sort(entities);

            // refine software types, if there is anything to refine
            if (entities.size() > 0) {
                try {
                    List<SoftwareType> entityTypes = softwareTypeParser.processFeatureInput(text, ress, tokens);
                    /*for(SoftwareType entityType : entityTypes) {
                        System.out.println("\n" + entityType.toString());
                    }*/
                    if (entityTypes != null && entityTypes.size() > 0) {
                        entities = refineTypes(entities, entityTypes);

                        // additional sort in case new entites were introduced
                        Collections.sort(entities);
                    }

                } catch (Exception e) {
                    throw new GrobidException("Sequence labeling for software type parsing failed.", e);
                }
            }

            // attach a local text context to the entities
            entities = addContext(entities, text, tokens, false, false, false);

            // finally classify the context for predicting the role of the software mention
            entities = SoftwareContextClassifier.getInstance(softwareConfiguration).classifyDocumentContexts(entities);

        } catch (Exception e) {
            throw new GrobidException("An exception occured while running Grobid.", e);
        }

        return entities;
    }

    /**
     * Extract all Software mentions from a pdf file
     */
    public Pair<List<SoftwareEntity>, Document> processPDF(File file,
                                                           boolean disambiguate,
                                                           boolean addParagraphContext) throws IOException {
        List<SoftwareEntity> entities = new ArrayList<SoftwareEntity>();
        Document doc = null;
        try {
            GrobidAnalysisConfig config =
                GrobidAnalysisConfig.builder()
                    .consolidateHeader(0)
                    .consolidateCitations(0)
                    .build();

            DocumentSource documentSource =
                DocumentSource.fromPdf(file, config.getStartPage(), config.getEndPage());
            doc = parsers.getSegmentationParser().processing(documentSource, config);

            // process bibliographical reference section first
            List<BibDataSet> resCitations = parsers.getCitationParser().
                processingReferenceSection(doc, parsers.getReferenceSegmenterParser(), config.getConsolidateCitations());

            doc.setBibDataSets(resCitations);

            // here we process the relevant textual content of the document

            // for refining the process based on structures, we need to filter
            // segment of interest (e.g. header, body, annex) and possibly apply
            // the corresponding model to further filter by structure types

            List<List<LayoutToken>> selectedLayoutTokenSequences = new ArrayList<>();

            // from the header, we are interested in title, abstract and keywords
            BiblioItem resHeader = null;
            SortedSet<DocumentPiece> documentParts = doc.getDocumentPart(SegmentationLabels.HEADER);
            if (documentParts != null) {
                try {
                    Pair<String, List<LayoutToken>> headerFeatured = parsers.getHeaderParser().getSectionHeaderFeatured(doc, documentParts);
                    String header = headerFeatured.getLeft();
                    List<LayoutToken> headerTokenization = headerFeatured.getRight();
                    String labeledResult = null;
                    if ((header != null) && (header.trim().length() > 0)) {
                        labeledResult = parsers.getHeaderParser().label(header);
                        resHeader = new BiblioItem();
                        resHeader.generalResultMappingHeader(labeledResult, headerTokenization);

                        // title
                        List<LayoutToken> titleTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_TITLE);
                        if (titleTokens != null) {
                            selectedLayoutTokenSequences.add(titleTokens);
                        }

                        // abstract
                        List<LayoutToken> abstractTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_ABSTRACT);
                        if (abstractTokens != null) {
                            selectedLayoutTokenSequences.add(abstractTokens);
                        }

                        // keywords
                        List<LayoutToken> keywordTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_KEYWORD);
                        if (keywordTokens != null) {
                            selectedLayoutTokenSequences.add(keywordTokens);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Fail to parse header area for file " + file.getPath(), e);
                    resHeader = null;
                }
            }

            // process selected structures in the body,
            documentParts = doc.getDocumentPart(SegmentationLabels.BODY);
            List<TaggingTokenCluster> bodyClusters = null;
            if (documentParts != null) {
                // full text processing
                Pair<String, LayoutTokenization> featSeg = parsers.getFullTextParser().getBodyTextFeatured(doc, documentParts);
                if (featSeg != null) {
                    // if featSeg is null, it usually means that no body segment is found in the
                    // document segmentation
                    String bodytext = featSeg.getLeft();

                    LayoutTokenization tokenizationBody = featSeg.getRight();
                    String rese = null;
                    if ((bodytext != null) && (bodytext.trim().length() > 0)) {
                        rese = parsers.getFullTextParser().label(bodytext);
                    } else {
                        logger.debug("Fulltext model: The input to the sequence labelling processing is empty");
                    }

                    TaggingTokenClusteror clusteror = new TaggingTokenClusteror(GrobidModels.FULLTEXT, rese,
                        tokenizationBody.getTokenization(), true);
                    bodyClusters = clusteror.cluster();
                    List<LayoutToken> curParagraphTokens = null;
                    TaggingLabel lastClusterLabel = null;
                    for (TaggingTokenCluster cluster : bodyClusters) {
                        if (cluster == null) {
                            continue;
                        }

                        TaggingLabel clusterLabel = cluster.getTaggingLabel();

                        List<LayoutToken> localTokenization = cluster.concatTokens();
                        if ((localTokenization == null) || (localTokenization.size() == 0))
                            continue;

                        if (TEIFormatter.MARKER_LABELS.contains(clusterLabel)) {
                            if (curParagraphTokens == null)
                                curParagraphTokens = new ArrayList<>();
                            curParagraphTokens.addAll(localTokenization);
                        } else if (clusterLabel.equals(TaggingLabels.PARAGRAPH) || clusterLabel.equals(TaggingLabels.ITEM)) {
                            //|| clusterLabel.equals(TaggingLabels.SECTION) {
                            if (lastClusterLabel == null || curParagraphTokens == null || isNewParagraph(lastClusterLabel)) {
                                if (curParagraphTokens != null)
                                    selectedLayoutTokenSequences.add(curParagraphTokens);
                                curParagraphTokens = new ArrayList<>();
                            }
                            curParagraphTokens.addAll(localTokenization);

                            //selectedLayoutTokenSequences.add(localTokenization);
                        } else if (clusterLabel.equals(TaggingLabels.TABLE)) {
                            //processLayoutTokenSequenceTableFigure(localTokenization, entities);
                        } else if (clusterLabel.equals(TaggingLabels.FIGURE)) {
                            //processLayoutTokenSequenceTableFigure(localTokenization, entities);
                        }

                        lastClusterLabel = clusterLabel;
                    }
                    // last paragraph
                    if (curParagraphTokens != null)
                        selectedLayoutTokenSequences.add(curParagraphTokens);
                }
            }

            // we don't process references (although reference titles could be relevant)
            // acknowledgement?

            // we can process annexes
            documentParts = doc.getDocumentPart(SegmentationLabels.ANNEX);
            if (documentParts != null) {
                List<LayoutToken> annexTokens = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                if (annexTokens != null) {
                    selectedLayoutTokenSequences.add(annexTokens);
                }
            }

            // footnotes are also relevant
            documentParts = doc.getDocumentPart(SegmentationLabels.FOOTNOTE);
            if (documentParts != null) {
                List<LayoutToken> footnoteTokens = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                if (footnoteTokens != null) {
                    selectedLayoutTokenSequences.add(footnoteTokens);
                }
            }

            // explicit availability statements
            List<LayoutToken> availabilityTokens = null;
            documentParts = doc.getDocumentPart(SegmentationLabels.AVAILABILITY);
            if (documentParts != null) {
                availabilityTokens = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                if (availabilityTokens != null) {
                    selectedLayoutTokenSequences.add(availabilityTokens);
                }
            }

            // actual processing of the selected sequences which have been delayed to be processed in groups and
            // take advantage of deep learning batch
            processLayoutTokenSequenceMultiple(selectedLayoutTokenSequences, entities, disambiguate, addParagraphContext, true, false, doc.getPDFAnnotations());

            // propagate the disambiguated entities to the non-disambiguated entities corresponding to the same software name
            for (SoftwareEntity entity1 : entities) {
                if (entity1.getSoftwareName() != null && entity1.getSoftwareName().getWikidataId() != null) {
                    for (SoftwareEntity entity2 : entities) {
                        if (entity2.getSoftwareName() != null && entity2.getSoftwareName().getWikidataId() != null) {
                            // if the entity is already disambiguated, nothing possible
                            continue;
                        }
                        if (entity2.getSoftwareName() != null &&
                            entity2.getSoftwareName().getRawForm().equals(entity1.getSoftwareName().getRawForm())) {
                            entity1.getSoftwareName().copyKnowledgeInformationTo(entity2.getSoftwareName());
                            entity2.getSoftwareName().setLang(entity1.getSoftwareName().getLang());
                        }
                    }
                }
            }

            // use identified software names to possibly normalize hyphenized software names
            // e.g. for MOD-ELLER, normalize to MODELLER because MODELLER is found elsewhere in the document
            // In addition, identify suspicious software names ending with hyphen and check if they
            // are not part of a larger "hyphenized" entity
            if (entities.size() > 0) {
                List<String> allRawForms = new ArrayList<String>();
                List<String> allHyphenedForm = new ArrayList<String>();
                for (SoftwareEntity entity : entities) {
                    SoftwareComponent softwareComponent = entity.getSoftwareName();
                    String localRawForm = softwareComponent.getRawForm();
                    if (localRawForm.indexOf("-") == -1 && !allRawForms.contains(localRawForm))
                        allRawForms.add(localRawForm);
                    if (localRawForm.indexOf("-") != -1 && !localRawForm.endsWith("-") && !allHyphenedForm.contains(localRawForm))
                        allHyphenedForm.add(localRawForm);
                }
                for (SoftwareEntity entity : entities) {
                    SoftwareComponent softwareComponent = entity.getSoftwareName();
                    String localRawForm = softwareComponent.getRawForm();
                    if (localRawForm.indexOf("-") != -1 && !localRawForm.endsWith("-")) {
                        localRawForm = localRawForm.replaceAll("-( |\\n)*", "");
                        localRawForm = localRawForm.replace("-", "");
                        if (allRawForms.contains(localRawForm)) {
                            softwareComponent.setNormalizedForm(localRawForm);
                        }
                    }
                }
                List<Integer> toBeFiltered = new ArrayList<>();
                for (int i = 0; i < entities.size(); i++) {
                    SoftwareEntity entity = entities.get(i);
                    SoftwareComponent softwareComponent = entity.getSoftwareName();
                    String localRawForm = softwareComponent.getRawForm();
                    if (localRawForm.endsWith("-")) {
                        for (String hyphenForm : allHyphenedForm) {
                            if (hyphenForm.indexOf(localRawForm) != -1) {
                                toBeFiltered.add(i);
                            }
                        }
                    }
                }

                for (int j = entities.size() - 1; j >= 0; j--) {
                    if (toBeFiltered.contains(j))
                        entities.remove(j);
                }
            }

            // second pass for document level consistency: the goal is to propagate the identified entities in the part of the
            // document where the same term appears without labeling. For controlling the propagation we use a tf-idf measure
            // of the term. As possible improvement, a specific classifier could be used.

            // we prepare a matcher for all the identified software names
            FastMatcher termPattern = prepareTermPattern(entities);
            // we prepare the frequencies for each software name in the whole document
            Map<String, Integer> frequencies = prepareFrequencies(entities, doc.getTokenizations());
            // we prepare a map for mapping a software name with its positions of annotation in the document and its IDF
            Map<String, Double> termProfiles = prepareTermProfiles(entities);
            List<OffsetPosition> placeTaken = preparePlaceTaken(entities);

            // second pass, header
            if (resHeader != null) {
                // title
                List<LayoutToken> titleTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_TITLE);
                if (titleTokens != null) {
                    propagateLayoutTokenSequence(titleTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext, true, false);
                }

                // abstract
                List<LayoutToken> abstractTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_ABSTRACT);
                if (abstractTokens != null) {
                    propagateLayoutTokenSequence(abstractTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext, true, false);
                }

                // keywords
                List<LayoutToken> keywordTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_KEYWORD);
                if (keywordTokens != null) {
                    propagateLayoutTokenSequence(keywordTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext, true, false);
                }
            }

            // second pass, body
            if (bodyClusters != null) {
                List<LayoutToken> curParagraphTokens = null;
                TaggingLabel lastClusterLabel = null;
                for (TaggingTokenCluster cluster : bodyClusters) {
                    if (cluster == null) {
                        continue;
                    }

                    TaggingLabel clusterLabel = cluster.getTaggingLabel();

                    List<LayoutToken> localTokenization = cluster.concatTokens();
                    if ((localTokenization == null) || (localTokenization.size() == 0))
                        continue;

                    if (TEIFormatter.MARKER_LABELS.contains(clusterLabel)) {
                        if (curParagraphTokens == null)
                            curParagraphTokens = new ArrayList<>();
                        curParagraphTokens.addAll(localTokenization);
                    } else if (clusterLabel.equals(TaggingLabels.PARAGRAPH) || clusterLabel.equals(TaggingLabels.ITEM)) {
                        if (lastClusterLabel == null || curParagraphTokens == null || isNewParagraph(lastClusterLabel)) {
                            if (curParagraphTokens != null)
                                propagateLayoutTokenSequence(curParagraphTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext, true, false);
                            curParagraphTokens = new ArrayList<>();
                        }
                        curParagraphTokens.addAll(localTokenization);
                    }

                    lastClusterLabel = clusterLabel;
                }

                if (curParagraphTokens != null)
                    propagateLayoutTokenSequence(curParagraphTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext, true, false);
            }

            // second pass, annex - if relevant, uncomment
            /*documentParts = doc.getDocumentPart(SegmentationLabels.ANNEX);
            if (documentParts != null) {
                List<LayoutToken> tokenizationParts = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                propagateLayoutTokenSequence(tokenizationParts, entities, termProfiles, termPattern, placeTaken, frequencies, true, false);
            }*/

            // second pass, footnotes (if relevant, uncomment)
            /*documentParts = doc.getDocumentPart(SegmentationLabel.FOOTNOTE);
            if (documentParts != null) {
                List<LayoutToken> tokenizationParts = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                propagateLayoutTokenSequence(tokenizationParts, entities, termProfiles, termPattern, placeTaken, frequencies, true, false);
            }*/

            // finally we attach and match bibliographical reference callout
            TEIFormatter formatter = new TEIFormatter(doc, parsers.getFullTextParser());
            // second pass, body
            if ((bodyClusters != null) && (resCitations != null) && (resCitations.size() > 0)) {
                List<BiblioComponent> bibRefComponents = new ArrayList<BiblioComponent>();
                for (TaggingTokenCluster cluster : bodyClusters) {
                    if (cluster == null) {
                        continue;
                    }

                    TaggingLabel clusterLabel = cluster.getTaggingLabel();

                    List<LayoutToken> localTokenization = cluster.concatTokens();
                    if ((localTokenization == null) || (localTokenization.size() == 0))
                        continue;

                    if (clusterLabel.equals(TaggingLabels.CITATION_MARKER)) {
                        List<LayoutToken> refTokens = TextUtilities.dehyphenize(localTokenization);
                        String chunkRefString = LayoutTokensUtil.toText(refTokens);

                        List<nu.xom.Node> refNodes = formatter.markReferencesTEILuceneBased(refTokens,
                            doc.getReferenceMarkerMatcher(),
                            true, // generate coordinates
                            false); // do not mark unsolved callout as ref

                        if (refNodes != null) {
                            for (nu.xom.Node refNode : refNodes) {
                                if (refNode instanceof Element) {
                                    // get the bib ref key
                                    String refKey = ((Element) refNode).getAttributeValue("target");

                                    if (refKey == null)
                                        continue;

                                    int refKeyVal = -1;
                                    if (refKey.startsWith("#b")) {
                                        refKey = refKey.substring(2, refKey.length());
                                        try {
                                            refKeyVal = Integer.parseInt(refKey);
                                        } catch (Exception e) {
                                            logger.warn("Invalid ref identifier: " + refKey);
                                        }
                                    }
                                    if (refKeyVal == -1)
                                        continue;

                                    // get the bibref object
                                    BibDataSet resBib = resCitations.get(refKeyVal);
                                    if (resBib != null) {
                                        BiblioComponent biblioComponent = new BiblioComponent(resBib.getResBib(), refKeyVal);
                                        biblioComponent.setRawForm(refNode.getValue());
                                        biblioComponent.setOffsetStart(refTokens.get(0).getOffset());
                                        biblioComponent.setOffsetEnd(refTokens.get(refTokens.size() - 1).getOffset() +
                                            refTokens.get(refTokens.size() - 1).getText().length());
                                        List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(refTokens);
                                        biblioComponent.setBoundingBoxes(boundingBoxes);
                                        bibRefComponents.add(biblioComponent);
                                    }
                                }
                            }
                        }
                    }
                }

                if (bibRefComponents.size() > 0) {
                    // avoid having version number where we identified bibliographical reference
                    entities = filterByRefCallout(entities, bibRefComponents);
                    // attach references to software entities
                    entities = attachRefBib(entities, bibRefComponents);
                }

                // consolidate the attached ref bib (we don't consolidate all bibliographical references
                // to avoid useless costly computation)
                List<BibDataSet> citationsToConsolidate = new ArrayList<BibDataSet>();
                List<Integer> consolidated = new ArrayList<Integer>();
                for (SoftwareEntity entity : entities) {
                    if (entity.getBibRefs() != null && entity.getBibRefs().size() > 0) {
                        List<BiblioComponent> bibRefs = entity.getBibRefs();
                        for (BiblioComponent bibRef : bibRefs) {
                            Integer refKeyVal = Integer.valueOf(bibRef.getRefKey());
                            if (!consolidated.contains(refKeyVal)) {
                                citationsToConsolidate.add(resCitations.get(refKeyVal));
                                consolidated.add(refKeyVal);
                            }
                        }
                    }
                }

                try {
                    Consolidation consolidator = Consolidation.getInstance();
                    Map<Integer, BiblioItem> resConsolidation = consolidator.consolidate(citationsToConsolidate);
                    for (int i = 0; i < citationsToConsolidate.size(); i++) {
                        BiblioItem resCitation = citationsToConsolidate.get(i).getResBib();
                        BiblioItem bibo = resConsolidation.get(i);
                        if (bibo != null) {
                            BiblioItem.correct(resCitation, bibo);
                        }
                    }
                } catch (Exception e) {
                    throw new GrobidException(
                        "An exception occured while running consolidation on bibliographical references.", e);
                }

                // propagate the bib. ref. to the entities corresponding to the same software name without bib. ref.
                for (SoftwareEntity entity1 : entities) {
                    if (entity1.getBibRefs() != null && entity1.getBibRefs().size() > 0) {
                        for (SoftwareEntity entity2 : entities) {
                            if (entity2.getBibRefs() != null) {
                                continue;
                            }
                            if (entity2.getSoftwareName() != null &&
                                entity2.getSoftwareName().getRawForm().equals(entity1.getSoftwareName().getRawForm())) {
                                List<BiblioComponent> newBibRefs = new ArrayList<>();
                                for (BiblioComponent bibComponent : entity1.getBibRefs()) {
                                    newBibRefs.add(new BiblioComponent(bibComponent));
                                }
                                entity2.setBibRefs(newBibRefs);
                            }
                        }
                    }
                }
            }

            logger.info(entities.size() + " total software entities");
            // propagate the non-disambiguated entities attributes to the new propagated entities corresponding
            // to the same software name
            for (SoftwareEntity entity1 : entities) {
                if (entity1.getSoftwareName() != null) {
                    for (SoftwareEntity entity2 : entities) {
                        if (entity2.getSoftwareName() != null &&
                            entity2.getSoftwareName().getNormalizedForm().equals(entity1.getSoftwareName().getNormalizedForm())) {
                            SoftwareEntity.mergeWithCopy(entity1, entity2);
                            if (entity1.getSoftwareName().getWikidataId() != null && entity2.getSoftwareName().getWikidataId() == null) {
                                entity1.getSoftwareName().copyKnowledgeInformationTo(entity2.getSoftwareName());
                                entity2.getSoftwareName().setLang(entity1.getSoftwareName().getLang());
                            } else if (entity2.getSoftwareName().getWikidataId() != null && entity1.getSoftwareName().getWikidataId() == null) {
                                entity2.getSoftwareName().copyKnowledgeInformationTo(entity1.getSoftwareName());
                                entity1.getSoftwareName().setLang(entity2.getSoftwareName().getLang());
                            }
                        }
                    }
                }
            }

            Collections.sort(entities);

            // mark software present in Data Availability section(s)
            if (availabilityTokens != null && availabilityTokens.size() > 0)
                entities = markDAS(entities, availabilityTokens);

            entities = SoftwareContextClassifier.getInstance(softwareConfiguration).classifyDocumentContexts(entities);

        } catch (Exception e) {
            e.printStackTrace();
            throw new GrobidException("Cannot process pdf file: " + file.getPath());
        }

        return Pair.of(entities, doc);
    }

    /**
     * Process with the software model a single arbitrary sequence of LayoutToken objects
     */
    private List<SoftwareEntity> processLayoutTokenSequence(
        List<LayoutToken> layoutTokens,
            List<SoftwareEntity> entities,
            boolean disambiguate,
            boolean addParagraphContext,
            boolean fromPDF,
            boolean fromXML,
            List<PDFAnnotation> pdfAnnotations
    ) {
        List<LayoutTokenization> layoutTokenizations = new ArrayList<LayoutTokenization>();
        layoutTokenizations.add(new LayoutTokenization(layoutTokens));
        return processLayoutTokenSequences(layoutTokenizations, entities, disambiguate, addParagraphContext, fromPDF, fromXML, pdfAnnotations);
    }

    /**
     * Process with the software model a single arbitrary sequence of LayoutToken objects
     */
    private List<SoftwareEntity> processLayoutTokenSequenceMultiple(
        List<List<LayoutToken>> layoutTokenList,
                                                                    List<SoftwareEntity> entities,
                                                                    boolean disambiguate,
                                                                    boolean addParagraphContext,
                                                                    boolean fromPDF,
                                                                    boolean fromXML) {

        return processLayoutTokenSequenceMultiple(
            layoutTokenList,
            entities,
            disambiguate,
            addParagraphContext,
            fromPDF,
            fromXML,
            null
        );
    }

    private List<SoftwareEntity> processLayoutTokenSequenceMultiple(
        List<List<LayoutToken>> layoutTokenList,
        List<SoftwareEntity> entities,
        boolean disambiguate,
        boolean addParagraphContext,
        boolean fromPDF,
        boolean fromXML,
        List<PDFAnnotation> pdfAnnotations
    ) {
        List<LayoutTokenization> layoutTokenizations = new ArrayList<LayoutTokenization>();
        for (List<LayoutToken> layoutTokens : layoutTokenList)
            layoutTokenizations.add(new LayoutTokenization(layoutTokens));
        return processLayoutTokenSequences(layoutTokenizations, entities, disambiguate, addParagraphContext, fromPDF, fromXML, pdfAnnotations);
    }

    /**
     * Process with the software model a set of arbitrary sequence of LayoutTokenization
     */
    private List<SoftwareEntity> processLayoutTokenSequences(
        List<LayoutTokenization> layoutTokenizations,
        List<SoftwareEntity> entities,
        boolean disambiguate,
        boolean addParagraphContext,
        boolean fromPDF,
        boolean fromXML,
        List<PDFAnnotation> pdfAnnotations
    ) {
        StringBuilder allRess = new StringBuilder();
        for (LayoutTokenization layoutTokenization : layoutTokenizations) {
            List<LayoutToken> layoutTokens = layoutTokenization.getTokenization();
            layoutTokens = SoftwareAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);

            if ((layoutTokens == null) || (layoutTokens.size() == 0))
                continue;

            // positions for lexical match
            List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(layoutTokens);
            List<OffsetPosition> urlTokensPositions = Lexicon.tokenPositionUrlPatternWithPdfAnnotations(layoutTokens, pdfAnnotations).stream()
                .map(Pair::getLeft)
                .collect(Collectors.toList());

            urlTokensPositions.stream().forEach(o -> o.end += 1);

            // string representation of the feature matrix for sequence labeling lib
            String ress = addFeatures(layoutTokens, softwareTokenPositions, urlTokensPositions);
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
        for (LayoutTokenization layoutTokenization : layoutTokenizations) {
            List<LayoutToken> layoutTokens = layoutTokenization.getTokenization();
            layoutTokens = SoftwareAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);

            if ((layoutTokens == null) || (layoutTokens.size() == 0))
                continue;

            // text of the selected segment
            String text = LayoutTokensUtil.toText(layoutTokens);

            if (l >= resBlocks.length)
                break;

            String localRes = resBlocks[l];

            if (localRes == null || localRes.length() == 0)
                continue;

            List<SoftwareComponent> components = extractSoftwareComponents(text, localRes, layoutTokens);
            l++;

            List<SoftwareEntity> localEntities = groupByEntities(components);

            // disambiguation
            if (disambiguate) {
                localEntities = disambiguator.disambiguate(localEntities, layoutTokens);

                // apply existing filtering
                List<Integer> indexToBeFiltered = new ArrayList<>();
                int k = 0;
                for (SoftwareEntity entity : localEntities) {
                    if (entity.isFiltered()) {
                        indexToBeFiltered.add(Integer.valueOf(k));
                    }
                    k++;
                }

                if (indexToBeFiltered.size() > 0) {
                    for (int j = indexToBeFiltered.size() - 1; j >= 0; j--) {
                        localEntities.remove(indexToBeFiltered.get(j).intValue());
                    }
                }
            }

            // note using dehyphenized text looks nicer, but break entity-level offsets
            // we would need to re-align offsets in a post-processing if we go with
            // dehyphenized text in the context
            //text = LayoutTokensUtil.normalizeDehyphenizeText(layoutTokens);

            // finally refine software types, if there is anything to refine
            if (localEntities.size() > 0) {
                try {
                    List<SoftwareType> entityTypes = softwareTypeParser.processFeatureInput(text, localRes, layoutTokens);
                    if (entityTypes != null && entityTypes.size() > 0) {
                        localEntities = refineTypes(localEntities, entityTypes);
                        Collections.sort(localEntities);
                    }
                } catch (Exception e) {
                    throw new GrobidException("Sequence labeling for software type parsing failed.", e);
                }
            }

            localEntities = addContext(localEntities, text, layoutTokens, fromPDF, fromXML, addParagraphContext);

            entities.addAll(localEntities);
        }

        return entities;
    }

    /**
     * Process with the software model a set of arbitrary sequence of LayoutTokenization
     * from tables and figures, where the content is not structured (yet)
     */
    private List<SoftwareEntity> processLayoutTokenSequenceTableFigure(
        List<LayoutToken> layoutTokens,
        List<SoftwareEntity> entities,
        boolean disambiguate,
        boolean addParagraphContext,
        List<PDFAnnotation> pdfAnnotations
    ) {
        layoutTokens = SoftwareAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);

        int pos = 0;
        List<LayoutToken> localLayoutTokens = null;
        while (pos < layoutTokens.size()) {
            while ((pos < layoutTokens.size()) && !layoutTokens.get(pos).getText().equals("\n")) {
                if (localLayoutTokens == null)
                    localLayoutTokens = new ArrayList<LayoutToken>();
                localLayoutTokens.add(layoutTokens.get(pos));
                pos++;
            }

            if ((localLayoutTokens == null) || (localLayoutTokens.size() == 0)) {
                pos++;
                continue;
            }

            // text of the selected segment
            String text = LayoutTokensUtil.toText(localLayoutTokens);

            // positions for lexical match
            List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(localLayoutTokens);
            List<OffsetPosition> urlPositions = Lexicon.tokenPositionUrlPatternWithPdfAnnotations(layoutTokens, pdfAnnotations).stream()
                .map(Pair::getLeft)
                .collect(Collectors.toList());

            // string representation of the feature matrix for sequence labeling lib
            String ress = addFeatures(localLayoutTokens, softwareTokenPositions, urlPositions);

            // labeled result from sequence labeling lib
            String res = label(ress);
            //System.out.println(res);
            List<SoftwareComponent> components = extractSoftwareComponents(text, res, localLayoutTokens);

            // we group the identified components by full entities
            List<SoftwareEntity> localEntities = groupByEntities(components);

            // disambiguation
            if (disambiguate) {
                localEntities = disambiguator.disambiguate(localEntities, localLayoutTokens);

                // apply existing filtering
                List<Integer> indexToBeFiltered = new ArrayList<>();
                int k = 0;
                for (SoftwareEntity entity : localEntities) {
                    if (entity.isFiltered()) {
                        indexToBeFiltered.add(Integer.valueOf(k));
                    }
                    k++;
                }

                if (indexToBeFiltered.size() > 0) {
                    for (int j = indexToBeFiltered.size() - 1; j >= 0; j--) {
                        localEntities.remove(indexToBeFiltered.get(j).intValue());
                    }
                }
            }

            entities.addAll(localEntities);

            localLayoutTokens = null;
            pos++;
        }

        return entities;
    }

    public List<SoftwareEntity> propagateLayoutTokenSequence(List<LayoutToken> layoutTokens,
                                                             List<SoftwareEntity> entities,
                                                             Map<String, Double> termProfiles,
                                                             FastMatcher termPattern,
                                                             List<OffsetPosition> placeTaken,
                                                             Map<String, Integer> frequencies,
                                                             boolean addParagraphContext,
                                                             boolean fromPDF,
                                                             boolean fromXML) {
        // possible offset of the sequence in the complete document tokenization
        int offsetShift = 0;
        if (layoutTokens != null && layoutTokens.size() > 0 && layoutTokens.get(0).getOffset() != 0) {
            offsetShift = layoutTokens.get(0).getOffset();
        }

        List<OffsetPosition> results = termPattern.matchLayoutToken(layoutTokens, true, true);
        // above: do not ignore delimiters and case sensitive matching

        if ((results == null) || (results.size() == 0)) {
            return entities;
        }

        List<SoftwareEntity> localEntities = new ArrayList<>();
        for (OffsetPosition position : results) {
            // the match positions are expressed relative to the local layoutTokens index, while the offset at
            // token level are expressed relative to the complete doc positions in characters
            List<LayoutToken> matchedTokens = layoutTokens.subList(position.start, position.end + 1);

            // we recompute matched position using local tokens (safer than using doc level offsets)
            int matchedPositionStart = 0;
            for (int i = 0; i < position.start; i++) {
                LayoutToken theToken = layoutTokens.get(i);
                if (theToken.getText() == null)
                    continue;
                matchedPositionStart += theToken.getText().length();
            }

            String term = LayoutTokensUtil.toText(matchedTokens);
            if (term.length() == 1 && !"R".equals(term)) {
                // if the term is just one character, better skip it (except for "R" alone,
                // but we will need to consider if it creates too many false matches)
                continue;
            }
            OffsetPosition matchedPosition = new OffsetPosition(matchedPositionStart, matchedPositionStart + term.length());

            // this positions is expressed at document-level, to check if we have not matched something already recognized
            OffsetPosition rawMatchedPosition = new OffsetPosition(
                matchedTokens.get(0).getOffset(),
                matchedTokens.get(matchedTokens.size() - 1).getOffset() + matchedTokens.get(matchedTokens.size() - 1).getText().length()
            );

            int termFrequency = 1;
            if (frequencies != null && frequencies.get(term) != null)
                termFrequency = frequencies.get(term);

            // check the tf-idf of the term
            double tfidf = -1.0;

            // is the match already present in the entity list?
            if (overlapsPosition(placeTaken, rawMatchedPosition, offsetShift)) {
                continue;
            }
            if (termProfiles.get(term) != null) {
                tfidf = termFrequency * termProfiles.get(term);
            }

            // ideally we should make a small classifier here with entity frequency, tfidf, disambiguation success and
            // and/or log-likelyhood/dice coefficient as features - but for the time being we introduce a simple rule
            // with an experimentally defined threshold:
            if ((tfidf <= 0) || (tfidf > 0.001)) {
                // add new entity mention
                SoftwareComponent name = new SoftwareComponent();
                name.setRawForm(term);
                // these offsets are relative now to the local layoutTokens sequence
                name.setOffsetStart(matchedPosition.start);
                name.setOffsetEnd(matchedPosition.end);
                name.setLabel(SoftwareTaggingLabels.SOFTWARE);
                name.setTokens(matchedTokens);

                List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(matchedTokens);
                name.setBoundingBoxes(boundingBoxes);

                SoftwareEntity entity = new SoftwareEntity();
                entity.setSoftwareName(name);
                entity.setType(SoftwareLexicon.Software_Type.SOFTWARE);
                entity.setPropagated(true);
                localEntities.add(entity);
                entities.add(entity);

                placeTaken.add(new OffsetPosition(matchedPosition.start + offsetShift, matchedPosition.end + offsetShift));
            }
        }

        // add context to the new entities
        addContext(localEntities, null, layoutTokens, fromPDF, fromXML, addParagraphContext);

        return entities;
    }

    public List<SoftwareEntity> markDAS(List<SoftwareEntity> entities, List<LayoutToken> availabilityTokens) {
        if (entities == null || entities.size() == 0)
            return entities;
        for (SoftwareEntity entity : entities) {
            if (entity.isInDataAvailabilitySection())
                continue;
            if (entity.getContext() == null)
                continue;
            int context_offset_start = entity.getGlobalContextOffset();
            int context_offset_end = context_offset_start + entity.getContext().length();
            for (LayoutToken token : availabilityTokens) {
                if (context_offset_start <= token.getOffset() && token.getOffset() < context_offset_end) {
                    entity.setInDataAvailabilitySection(true);
                    break;
                }
            }
        }
        return entities;
    }

    public static boolean isNewParagraph(TaggingLabel lastClusterLabel) {
        return (!TEIFormatter.MARKER_LABELS.contains(lastClusterLabel) && lastClusterLabel != TaggingLabels.FIGURE
            && lastClusterLabel != TaggingLabels.TABLE);
    }

    private boolean overlapsPosition(final List<OffsetPosition> list, final OffsetPosition position,
                                     int offsetShift) {
        // note: in principle, positions related to absolute document offsets from the layout tokens, so offsetShift
        // does not be used. However, to be conservative, we also consider it.
        for (OffsetPosition pos : list) {
            if (pos.start == position.start || pos.start - offsetShift == position.start)
                return true;
            if (pos.end == position.end || pos.end - offsetShift == position.end)
                return true;
            if (position.start <= pos.start && pos.start <= position.end)
                return true;
            if (position.start <= pos.start - offsetShift && pos.start - offsetShift <= position.end)
                return true;
            if (pos.start - offsetShift <= position.start && position.start <= pos.end - offsetShift)
                return true;
            if (pos.start - offsetShift <= position.start && position.start <= pos.end - offsetShift)
                return true;
            if (pos.start <= position.start && position.start < pos.end)
                return true;
            if (position.start < pos.end && pos.end <= position.end)
                return true;
        }
        return false;
    }

    /**
     * Identify components corresponding to the same software entities
     */
    public List<SoftwareEntity> groupByEntities(List<SoftwareComponent> components) {

        // we anchor the process to the software names and aggregate other closest components
        // to form full entities
        List<SoftwareEntity> entities = new ArrayList<SoftwareEntity>();
        SoftwareEntity currentEntity = null;
        // first pass for creating entities based on software names
        for (SoftwareComponent component : components) {
            if (component.getLabel().equals(SoftwareTaggingLabels.SOFTWARE)) {
                currentEntity = new SoftwareEntity();
                currentEntity.setSoftwareName(component);
                currentEntity.setType(SoftwareLexicon.Software_Type.SOFTWARE);
                entities.add(currentEntity);
            }
        }

        // second pass for aggregating other components
        int n = 0; // index in entities
        SoftwareEntity previousEntity = null;
        currentEntity = null;
        if (entities.size() == 0)
            return entities;
        if (entities.size() > 1) {
            previousEntity = entities.get(0);
            currentEntity = entities.get(1);
            n = 1;
        } else {
            previousEntity = entities.get(0);
        }

        for (SoftwareComponent component : components) {
            if (component.getLabel().equals(SoftwareTaggingLabels.SOFTWARE))
                continue;

            while ((currentEntity != null) &&
                (component.getOffsetStart() >= currentEntity.getSoftwareName().getOffsetEnd())) {
                previousEntity = currentEntity;
                if (n < entities.size())
                    currentEntity = entities.get(n);
                n += 1;
                if (n >= entities.size())
                    break;
            }
            if (currentEntity == null) {
                if (previousEntity.freeField(component.getLabel()) || previousEntity.betterField(component)) {
                    previousEntity.setComponent(component);
                }
            } else if (component.getOffsetEnd() < previousEntity.getSoftwareName().getOffsetStart()) {
                if (previousEntity.freeField(component.getLabel()) || previousEntity.betterField(component)) {
                    previousEntity.setComponent(component);
                }
            } else if (component.getOffsetEnd() < currentEntity.getSoftwareName().getOffsetStart()) {
                // we are in the middle of the two entities, we use proximity to attach the component
                // to an entity, with a strong bonus to the entity on the left
                // using sentence boundary could be helpful too in this situation
                int dist1 = currentEntity.getSoftwareName().getOffsetStart() - component.getOffsetEnd();
                int dist2 = component.getOffsetStart() - previousEntity.getSoftwareName().getOffsetEnd();
                if (dist2 <= dist1 * 2) {
                    if (previousEntity.freeField(component.getLabel()))
                        previousEntity.setComponent(component);
                } else
                    currentEntity.setComponent(component);
            } else if (component.getOffsetEnd() >= currentEntity.getSoftwareName().getOffsetEnd()) {
                currentEntity.setComponent(component);
            }
        }
        return entities;
    }


    /**
     * Try to attach relevant bib ref component to software entities.
     * Default max interval between ref and "mention" boundary is 5 characters, but it can be modified if needed.
     */
    public List<SoftwareEntity> attachRefBib
    (List<SoftwareEntity> entities, List<BiblioComponent> refBibComponents) {
        return attachRefBib(entities, refBibComponents, 5);
    }

    public List<SoftwareEntity> attachRefBib
        (List<SoftwareEntity> entities, List<BiblioComponent> refBibComponents, int intervalMax) {

        // we anchor the process to the software names and aggregate other closest components on the right
        // if we cross a bib ref component we attach it, if a bib ref component is just after the last
        // component of the entity group, we attach it
        for (SoftwareEntity entity : entities) {
            // positions are relative to the context if present, so they have to be shifted in this case
            // to be comparable with reference marker offsets
            int shiftOffset = 0;
            if (entity.getGlobalContextOffset() != -1) {
                shiftOffset = entity.getGlobalContextOffset();
            }

            // find the name component
            SoftwareComponent nameComponent = entity.getSoftwareName();
            int pos = nameComponent.getOffsetEnd() + shiftOffset;

            // find end boundary
            int endPos = pos;
            List<SoftwareComponent> theComps = new ArrayList<SoftwareComponent>();
            SoftwareComponent comp = entity.getVersion();
            if (comp != null)
                theComps.add(comp);
            /*comp = entity.getVersionDate();
            if (comp != null) 
                theComps.add(comp);*/
            comp = entity.getCreator();
            if (comp != null)
                theComps.add(comp);
            comp = entity.getSoftwareURL();
            if (comp != null)
                theComps.add(comp);

            for (SoftwareComponent theComp : theComps) {
                if (theComp.getOffsets() == null)
                    continue;
                int localPos = theComp.getOffsetEnd() + shiftOffset;
                if (localPos > endPos)
                    endPos = localPos;
            }

            // find included or just next bib ref callout
            for (BiblioComponent refBib : refBibComponents) {
                if ((refBib.getOffsetStart() >= pos) &&
                    (refBib.getOffsetStart() <= endPos + intervalMax)) {
                    entity.addBibRef(refBib);
                    endPos = refBib.getOffsetEnd();
                }
            }
        }

        return entities;
    }


    /**
     * Avoid having a version number where we identified a reference callout
     */
    public List<SoftwareEntity> filterByRefCallout
    (List<SoftwareEntity> entities, List<BiblioComponent> refBibComponents) {
        for (BiblioComponent refBib : refBibComponents) {
            for (SoftwareEntity entity : entities) {
                if (entity.getVersion() == null)
                    continue;
                SoftwareComponent version = entity.getVersion();
                if ((refBib.getOffsetStart() >= version.getOffsetStart()) &&
                    (refBib.getOffsetEnd() <= version.getOffsetEnd())) {
                    entity.setVersion(null);
                }
            }
        }
        return entities;
    }


    /**
     *
     */
    public int batchProcess(String inputDirectory,
                            String outputDirectory,
                            boolean isRecursive) throws IOException {
        // TBD
        return 0;
    }

    /**
     * Process the content of the specified input file and format the result as training data.
     * <p>
     * Input file can be (i)) PDF (.pdf) and it is assumed that we have a scientific article which will
     * be processed by GROBID full text first, (ii) some text (.txt extension).
     *
     * Note that we could consider a third input type which would be a TEI file resuling from the
     * conversion of a publisher's native XML file following Pub2TEI transformatiom/standardization.
     *
     * @param inputFile input file
     * @param pathTEI   path to TEI with annotated training data
     * @param id        id
     */
    public void createTraining(String inputFile,
                               String pathTEI,
                               int id) throws Exception {
        File file = new File(inputFile);
        if (!file.exists()) {
            throw new GrobidException("Cannot create training data because input file can not be accessed: " + inputFile);
        }

        Element root = getTEIHeader("_" + id);
        if (inputFile.endsWith(".txt") || inputFile.endsWith(".TXT")) {
            root = createTrainingText(file, root);
        } else if (inputFile.endsWith(".pdf") || inputFile.endsWith(".PDF")) {
            root = createTrainingPDF(file, root);
        }

        if (root != null) {
            //System.out.println(XmlBuilderUtils.toXml(root));
            try {
                FileUtils.writeStringToFile(new File(pathTEI), XmlBuilderUtils.toPrettyXml(root));
            } catch (IOException e) {
                throw new GrobidException("Cannot create training data because output file can not be accessed: " + pathTEI);
            }
        }
    }

    /**
     * Generate training data with the current model using new files located in a given directory.
     * the generated training data can then be corrected manually to be used for updating the
     * software sequence labeling model.
     */
    @SuppressWarnings({"UnusedParameters"})
    public int createTrainingBatch(String inputDirectory,
                                   String outputDirectory,
                                   int ind) throws IOException {
        try {
            if (inputDirectory == null || inputDirectory.length() == 0) {
                throw new GrobidException("Cannot create training data because input directory is invalid: " + inputDirectory);
            }

            File path = new File(inputDirectory);
            if (!path.exists()) {
                throw new GrobidException("Cannot create training data because input directory can not be accessed: " + inputDirectory);
            }

            if (outputDirectory == null || outputDirectory.length() == 0) {
                throw new GrobidException("Cannot create training data because output directory is invalid: " + outputDirectory);
            }

            File pathOut = new File(outputDirectory);
            if (!pathOut.exists()) {
                throw new GrobidException("Cannot create training data because ouput directory can not be accessed: " + outputDirectory);
            }

            // we process all pdf files in the directory
            File[] refFiles = path.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    System.out.println(name);
                    return name.endsWith(".pdf") || name.endsWith(".PDF") ||
                        name.endsWith(".txt") || name.endsWith(".TXT");// ||
//                            name.endsWith(".xml") || name.endsWith(".tei") ||
                    //                           name.endsWith(".XML") || name.endsWith(".TEI");
                }
            });

            if (refFiles == null)
                return 0;

            System.out.println(refFiles.length + " files to be processed.");

            int n = 0;
            if (ind == -1) {
                // for undefined identifier (value at -1), we initialize it to 0
                n = 1;
            }
            for (final File file : refFiles) {
                try {
                    String pathTEI = outputDirectory + "/" + file.getName().substring(0, file.getName().length() - 4) + ".training.tei.xml";
                    createTraining(file.getAbsolutePath(), pathTEI, n);
                } catch (final Exception exp) {
                    logger.error("An error occured while processing the following pdf: "
                        + file.getPath() + ": " + exp);
                }
                if (ind != -1)
                    n++;
            }

            return refFiles.length;
        } catch (final Exception exp) {
            throw new GrobidException("An exception occured while running Grobid batch.", exp);
        }
    }

    /**
     * Generate training data from a text file
     */
    private Element createTrainingText(File file, Element root) throws IOException {
        String text = FileUtils.readFileToString(file, "UTF-8");

        Element textNode = teiElement("text");
        // for the moment we suppose we have english only...
        textNode.addAttribute(new Attribute("xml:lang", "http://www.w3.org/XML/1998/namespace", "en"));

        // we process the text paragraph by paragraph
        String lines[] = text.split("\n");
        StringBuilder paragraph = new StringBuilder();
        List<SoftwareComponent> components = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() != 0) {
                paragraph.append(line).append("\n");
            }
            if (((line.length() == 0) || (i == lines.length - 1)) && (paragraph.length() > 0)) {
                // we have a new paragraph
                text = paragraph.toString().replace("\n", " ").replace("\r", " ").replace("\t", " ");
                List<LayoutToken> tokens = SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(text);

                if (tokens.size() == 0)
                    continue;

                // to store unit term positions
                List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(tokens);
                List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(tokens);
                String ress = addFeatures(tokens, softwareTokenPositions, urlPositions);
                String res = null;
                try {
                    res = label(ress);
                } catch (Exception e) {
                    throw new GrobidException("Sequence labeling for software mention parsing failed.", e);
                }
                components = extractSoftwareComponents(text, res, tokens);

                textNode.appendChild(trainingExtraction(components, text, tokens));
                paragraph = new StringBuilder();
            }
        }
        root.appendChild(textNode);

        return root;
    }

    /**
     * Generate training data from a PDf file
     */
    private Element createTrainingPDF(File file, Element root) throws IOException {
        // first we apply GROBID fulltext model on the PDF to get the full text TEI
        String teiXML = null;
        try {
            teiXML = GrobidFactory.getInstance().createEngine().fullTextToTEI(file, GrobidAnalysisConfig.defaultInstance());
        } catch (Exception e) {
            e.printStackTrace();
            throw new GrobidException("Cannot create training data because GROBID full text model failed on the PDF: " + file.getPath());
        }
        if (teiXML == null) {
            return null;
        }

        FileUtils.writeStringToFile(new File(file.getPath() + ".tei.xml"), teiXML);

        // we parse this TEI string similarly as for createTrainingXML

        List<SoftwareComponent> components = null;

        Element textNode = teiElement("text");
        // for the moment we suppose we have english only...
        textNode.addAttribute(new Attribute("xml:lang", "http://www.w3.org/XML/1998/namespace", "en"));

        try {
            // get a factory for SAX parser
            SAXParserFactory spf = SAXParserFactory.newInstance();

            TextChunkSaxHandler handler = new TextChunkSaxHandler();

            //get a new instance of parser
            SAXParser p = spf.newSAXParser();
            p.parse(new InputSource(new StringReader(teiXML)), handler);

            List<String> chunks = handler.getChunks();
            for (String text : chunks) {
                text = text.toString().replace("\n", " ").replace("\r", " ").replace("\t", " ");
                // the last one is a special "large" space missed by the regex "\\p{Space}+" used on the SAX parser
                if (text.trim().length() == 0)
                    continue;
                List<LayoutToken> tokenizations = SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(text);

                if (tokenizations.size() == 0)
                    continue;

                // to store unit term positions
                List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(tokenizations);
                List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(tokenizations);
                String ress = addFeatures(tokenizations, softwareTokenPositions, urlPositions);
                String res = null;
                try {
                    res = label(ress);
                } catch (Exception e) {
                    throw new GrobidException("Sequence labeling for software parsing failed.", e);
                }
                components = extractSoftwareComponents(text, res, tokenizations);

                textNode.appendChild(trainingExtraction(components, text, tokenizations));
            }
            root.appendChild(textNode);
        } catch (Exception e) {
            e.printStackTrace();
            throw new GrobidException("Cannot create training data because input PDF/XML file can not be parsed: " +
                file.getPath());
        }

        return root;
    }

    public List<OffsetPosition> preparePlaceTaken(List<SoftwareEntity> entities) {
        List<OffsetPosition> localPositions = new ArrayList<>();
        for (SoftwareEntity entity : entities) {
            SoftwareComponent nameComponent = entity.getSoftwareName();
            if (nameComponent == null)
                continue;
            List<LayoutToken> localTokens = nameComponent.getTokens();
            localPositions.add(new OffsetPosition(localTokens.get(0).getOffset(),
                localTokens.get(localTokens.size() - 1).getOffset() + localTokens.get(localTokens.size() - 1).getText().length() - 1));

            // we need to add the other component to avoid overlap
            SoftwareComponent versionComponent = entity.getVersion();
            if (versionComponent != null) {
                localTokens = versionComponent.getTokens();
                if (localTokens.size() > 0) {
                    localPositions.add(new OffsetPosition(localTokens.get(0).getOffset(),
                        localTokens.get(localTokens.size() - 1).getOffset() + localTokens.get(localTokens.size() - 1).getText().length() - 1));
                }
            }
            SoftwareComponent publisherComponent = entity.getCreator();
            if (publisherComponent != null) {
                localTokens = publisherComponent.getTokens();
                if (localTokens.size() > 0) {
                    localPositions.add(new OffsetPosition(localTokens.get(0).getOffset(),
                        localTokens.get(localTokens.size() - 1).getOffset() + localTokens.get(localTokens.size() - 1).getText().length() - 1));
                }
            }
            SoftwareComponent urlComponent = entity.getSoftwareURL();
            if (urlComponent != null) {
                localTokens = urlComponent.getTokens();
                if (localTokens.size() > 0) {
                    localPositions.add(new OffsetPosition(localTokens.get(0).getOffset(),
                        localTokens.get(localTokens.size() - 1).getOffset() + localTokens.get(localTokens.size() - 1).getText().length() - 1));
                }
            }
        }
        return localPositions;
    }

    public Map<String, Double> prepareTermProfiles(List<SoftwareEntity> entities) {
        Map<String, Double> result = new TreeMap<String, Double>();

        for (SoftwareEntity entity : entities) {
            SoftwareComponent nameComponent = entity.getSoftwareName();
            if (nameComponent == null)
                continue;
            String term = nameComponent.getRawForm();
            term = term.replace("\n", " ");
            term = term.replaceAll("( )+", " ");

            Double profile = result.get(term);
            if (profile == null) {
                profile = SoftwareLexicon.getInstance().getTermIDF(term);
                result.put(term, profile);
            }

            if (!term.equals(nameComponent.getNormalizedForm())) {
                profile = result.get(nameComponent.getNormalizedForm());
                if (profile == null) {
                    profile = SoftwareLexicon.getInstance().getTermIDF(nameComponent.getNormalizedForm());
                    result.put(nameComponent.getNormalizedForm(), profile);
                }
            }
        }

        return result;
    }

    public FastMatcher prepareTermPattern(List<SoftwareEntity> entities) {
        FastMatcher termPattern = new FastMatcher();
        List<String> added = new ArrayList<>();
        for (SoftwareEntity entity : entities) {
            SoftwareComponent nameComponent = entity.getSoftwareName();
            if (nameComponent == null)
                continue;

            // we don't propagate implicit software names
            if (entity.getType() == SoftwareLexicon.Software_Type.IMPLICIT)
                continue;

            String term = nameComponent.getRawForm();
            term = term.replace("\n", " ");
            term = term.replaceAll("( )+", " ");

            if (term.trim().length() == 0)
                continue;

            // for safety, we don't propagate something that looks like a stopword with simply an Uppercase first letter
            if (FeatureFactory.getInstance().test_first_capital(term) &&
                !FeatureFactory.getInstance().test_all_capital(term) &&
                SoftwareLexicon.getInstance().isEnglishStopword(term.toLowerCase())) {
                continue;
            }

            if (!added.contains(term)) {
                termPattern.loadTerm(term, SoftwareAnalyzer.getInstance(), false);
                added.add(term);
            }

            if (!term.equals(nameComponent.getNormalizedForm())) {
                if (!added.contains(nameComponent.getNormalizedForm())) {
                    termPattern.loadTerm(nameComponent.getNormalizedForm(), SoftwareAnalyzer.getInstance(), false);
                    added.add(nameComponent.getNormalizedForm());
                }
            }
        }
        return termPattern;
    }

    public Map<String, Integer> prepareFrequencies(List<SoftwareEntity> entities, List<LayoutToken> tokens) {
        Map<String, Integer> frequencies = new TreeMap<String, Integer>();
        for (SoftwareEntity entity : entities) {
            SoftwareComponent nameComponent = entity.getSoftwareName();
            if (nameComponent == null)
                continue;
            String term = nameComponent.getRawForm();
            if (frequencies.get(term) == null) {
                FastMatcher localTermPattern = new FastMatcher();
                localTermPattern.loadTerm(term, SoftwareAnalyzer.getInstance());
                List<OffsetPosition> results = localTermPattern.matchLayoutToken(tokens, true, true);
                // ignore delimiters, but case sensitive matching
                int freq = 0;
                if (results != null) {
                    freq = results.size();
                }
                frequencies.put(term, Integer.valueOf(freq));
            }
        }
        return frequencies;
    }

    @SuppressWarnings({"UnusedParameters"})
    public String addFeatures(List<LayoutToken> tokens,
                              List<OffsetPosition> softwareTokenPositions,
                              List<OffsetPosition> urlPositions) {
        int totalLine = tokens.size();
        int posit = 0;
        int currentSoftwareIndex = 0;
        List<OffsetPosition> localPositions = softwareTokenPositions;
        boolean isSoftwarePattern = false;
        boolean isUrl = false;
        StringBuilder result = new StringBuilder();
        try {
            for (LayoutToken token : tokens) {
                if (token.getText().trim().equals("@newline")) {
                    result.append("\n");
                    posit++;
                    continue;
                }

                String text = token.getText();
                if (text.equals(" ") || text.equals("\n")) {
                    posit++;
                    continue;
                }

                // parano normalisation
                text = UnicodeUtil.normaliseTextAndRemoveSpaces(text);
                if (text.trim().length() == 0) {
                    posit++;
                    continue;
                }

                // do we have a software-match token at position posit?
                if ((localPositions != null) && (localPositions.size() > 0)) {
                    for (int mm = currentSoftwareIndex; mm < localPositions.size(); mm++) {
                        if ((posit >= localPositions.get(mm).start) && (posit <= localPositions.get(mm).end)) {
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

                isUrl = false;
                if (urlPositions != null) {
                    for (OffsetPosition thePosition : urlPositions) {
                        if (posit >= thePosition.start && posit <= thePosition.end) {
                            isUrl = true;
                            break;
                        }
                    }
                }

                FeaturesVectorSoftware featuresVector =
                    FeaturesVectorSoftware.addFeaturesSoftware(text, null,
                        softwareLexicon.inSoftwareDictionary(text), isSoftwarePattern, isUrl);
                result.append(featuresVector.printVector());
                result.append("\n");
                posit++;
                isSoftwarePattern = false;
            }
        } catch (Exception e) {
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
        return result.toString();
    }

    /**
     * Add to the entities their local text contexts: the sentence where the software name occurs.
     */
    public List<SoftwareEntity> addContext(List<SoftwareEntity> entities,
                                           String text,
                                           List<LayoutToken> tokens,
                                           boolean fromPDF,
                                           boolean fromXML,
                                           boolean addParagraphContext) {
        // adjust offsets if tokenization does not start at 0
        int offsetShift = 0;
        if (tokens != null && tokens.size() > 0 && tokens.get(0).getOffset() != 0) {
            offsetShift = tokens.get(0).getOffset();
        }

        //System.out.println("offsetShift: " + offsetShift);

        // we start by segmenting the tokenized text into sentences

        List<OffsetPosition> forbidden = new ArrayList<OffsetPosition>();
        // fill the position where entities occur to avoid segmenting in the middle of a software string, same for
        // the reference marker positions too
        for (SoftwareEntity entity : entities) {
            SoftwareComponent softwareName = entity.getSoftwareName();
            SoftwareComponent version = entity.getVersion();
            SoftwareComponent creator = entity.getCreator();
            SoftwareComponent softwareURL = entity.getSoftwareURL();
            List<BiblioComponent> refMarkers = entity.getBibRefs();

            // offsets here are relative to the local provided tokens
            if (softwareName != null) {
                forbidden.add(new OffsetPosition(softwareName.getOffsetStart(), softwareName.getOffsetEnd()));
            }
            if (version != null) {
                forbidden.add(new OffsetPosition(version.getOffsetStart(), version.getOffsetEnd()));
            }
            if (creator != null) {
                forbidden.add(new OffsetPosition(creator.getOffsetStart(), creator.getOffsetEnd()));
            }
            if (softwareURL != null) {
                forbidden.add(new OffsetPosition(softwareURL.getOffsetStart(), softwareURL.getOffsetEnd()));
            }
            if (refMarkers != null) {
                for (BiblioComponent biblioComponent : refMarkers) {
                    forbidden.add(new OffsetPosition(biblioComponent.getOffsetStart() - offsetShift, biblioComponent.getOffsetEnd() - offsetShift));
                }
            }
        }

        if (text == null) {
            text = LayoutTokensUtil.toText(tokens);
        }
        // note: sentence position offsets are relative to the local provided tokens
        List<OffsetPosition> sentencePositions = SentenceUtilities.getInstance().runSentenceDetection(text, forbidden, tokens, null);
        if (sentencePositions == null) {
            sentencePositions = new ArrayList<>();
            sentencePositions.add(new OffsetPosition(0, text.length()));
        }

        for (SoftwareEntity entity : entities) {
            SoftwareComponent softwareName = entity.getSoftwareName();
            if (softwareName == null)
                continue;

            // offsets here are relative to the local provided tokens
            int startEntity = softwareName.getOffsetStart();
            int endEntity = softwareName.getOffsetEnd();

            // the following should never happen, but for safety - TBD: this kind of ill-formed entity should be discarded ?
            if (startEntity < 0 || endEntity < 0)
                continue;

            // get the sentence corresponding to these positions
            for (OffsetPosition sentencePosition : sentencePositions) {
                int startSentence = sentencePosition.start;
                int endSentence = sentencePosition.end;

                //System.out.println("startSentence: " + startSentence + ", endSentence: " + endSentence);

                if (startSentence <= startEntity && endEntity <= endSentence) {
                    // set the context as the identified sentence
                    entity.setContext(text.substring(startSentence, endSentence));

                    //System.out.println("context: " + entity.getContext());

                    if (fromPDF || fromXML) {
                        // we relate the entity offset to the context text
                        // update the offsets of the entity components relatively to the context
                        softwareName.setOffsetStart(startEntity - startSentence);
                        softwareName.setOffsetEnd(endEntity - startSentence);

                        SoftwareComponent version = entity.getVersion();
                        if (version != null) {
                            version.setOffsetStart(version.getOffsetStart() - startSentence);
                            version.setOffsetEnd(version.getOffsetEnd() - startSentence);
                        }

                        SoftwareComponent creator = entity.getCreator();
                        if (creator != null) {
                            creator.setOffsetStart(creator.getOffsetStart() - startSentence);
                            creator.setOffsetEnd(creator.getOffsetEnd() - startSentence);
                        }

                        SoftwareComponent softwareURL = entity.getSoftwareURL();
                        if (softwareURL != null) {
                            softwareURL.setOffsetStart(softwareURL.getOffsetStart() - startSentence);
                            softwareURL.setOffsetEnd(softwareURL.getOffsetEnd() - startSentence);
                        }

                        /*
                        // normally no bib ref attached to a software mention at this stage
                        List<BiblioComponent> localBibRefs = entity.getBibRefs();
                        if (localBibRefs != null) {
                            for(BiblioComponent localBibRef : localBibRefs) {
                                localBibRef.setOffsetStart(localBibRef.getOffsetStart() - startSentence - offsetShift);
                                localBibRef.setOffsetEnd(localBibRef.getOffsetEnd() - startSentence - offsetShift);
                            }
                        }
                        */

                        entity.setGlobalContextOffset(startSentence + offsetShift);

                        if (addParagraphContext) {
                            entity.setParagraphContextOffset(startSentence);
                            entity.setParagraph(text);
                        }
                    }
                    break;
                }
            }
        }

        // normalize context string and offsets
        // we want to avoid end-of-line and multiple spaces in the context string,
        // which involves normalizing the context string and adjust the entity offset according to the string changes
        if (fromPDF) {
            for (SoftwareEntity entity : entities) {
                SoftwareComponent softwareName = entity.getSoftwareName();
                if (softwareName == null)
                    continue;

                String context = entity.getContext();
                if (context == null)
                    continue;
                String new_context = context.replace("\n", " ").replace("  ", " ");
                int shift = context.length() - new_context.length();
                if (shift == 0)
                    continue;

                int localStart = softwareName.getOffsetStart();
                int localEnd = softwareName.getOffsetEnd();

                int newStartEntity = new_context.indexOf(softwareName.getRawForm(), localStart - shift);
                int newEndEntity = newStartEntity + softwareName.getRawForm().length();

                if (newStartEntity != -1) {
                    softwareName.setOffsetStart(newStartEntity);
                    softwareName.setOffsetEnd(newEndEntity);
                }

                SoftwareComponent version = entity.getVersion();
                if (version != null) {
                    localStart = version.getOffsetStart();

                    newStartEntity = new_context.indexOf(version.getRawForm(), localStart - shift);
                    newEndEntity = newStartEntity + version.getRawForm().length();

                    if (newStartEntity != -1) {
                        version.setOffsetStart(newStartEntity);
                        version.setOffsetEnd(newEndEntity);
                    }
                }

                SoftwareComponent creator = entity.getCreator();
                if (creator != null) {
                    localStart = creator.getOffsetStart();

                    newStartEntity = new_context.indexOf(creator.getRawForm(), localStart - shift);
                    newEndEntity = newStartEntity + creator.getRawForm().length();

                    if (newStartEntity != -1) {
                        creator.setOffsetStart(newStartEntity);
                        creator.setOffsetEnd(newEndEntity);
                    }
                }

                SoftwareComponent softwareURL = entity.getSoftwareURL();
                if (softwareURL != null) {
                    localStart = softwareURL.getOffsetStart();

                    newStartEntity = new_context.indexOf(softwareURL.getRawForm(), localStart - shift);
                    newEndEntity = newStartEntity + softwareURL.getRawForm().length();

                    if (newStartEntity != -1) {
                        softwareURL.setOffsetStart(newStartEntity);
                        softwareURL.setOffsetEnd(newEndEntity);
                    }
                }

                entity.setContext(new_context);
            }
        }
        return entities;
    }

    /**
     * Extract identified software components from a sequence labelled text.
     */
    public List<SoftwareComponent> extractSoftwareComponents(String text,
                                                             String result,
                                                             List<LayoutToken> tokenizations) {
        List<SoftwareComponent> components = new ArrayList<>();

        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(GrobidModels.SOFTWARE, result, tokenizations);
        List<TaggingTokenCluster> clusters = clusteror.cluster();

        SoftwareComponent currentComponent = null;
        //SoftwareLexicon.Software_Type openEntity = null;
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
                    for (TaggingLabel label : localLabels) {
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

            if ((pos < text.length() - 1) && (text.charAt(pos) == ' '))
                pos += 1;
            if ((pos < text.length() - 1) && (text.charAt(pos) == '\n'))
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

            if ((endPos > 0) && (text.length() >= endPos) && (text.charAt(endPos - 1) == '\n'))
                endPos--;
            if ((endPos > 0) && (text.length() >= endPos) && (text.charAt(endPos - 1) == ' '))
                endPos--;

            if (overlapRefMarker) {
                pos = endPos;
                continue;
            }

            if (!clusterLabel.equals(SoftwareTaggingLabels.OTHER)) {

                // conservative check, minimal well-formedness of the content for software name
                if (clusterLabel.equals(SoftwareTaggingLabels.SOFTWARE)) {
                    if (SoftwareAnalyzer.DELIMITERS.indexOf(clusterContent) != -1 ||
                        SoftwareLexicon.getInstance().isEnglishStopword(clusterContent) ||
                        FeatureFactory.getInstance().test_number(clusterContent)) {
                        pos = endPos;
                        continue;
                    }

                    // software name blacklist check
                    if (SoftwareLexicon.getInstance().isInSoftwareNameBlacklist(clusterContent)) {
                        pos = endPos;
                        continue;
                    }
                }

                // conservative check, minimal well-formedness of the content for URL
                if (clusterLabel.equals(SoftwareTaggingLabels.SOFTWARE_URL)) {
                    if (SoftwareAnalyzer.DELIMITERS.indexOf(clusterContent) != -1 ||
                        SoftwareLexicon.getInstance().isEnglishStopword(clusterContent) ||
                        FeatureFactory.getInstance().test_number(clusterContent) ||
                        clusterContent.replace("\n", "").equals("//")) {
                        // note: the last conditional test is a rare error by SciBERT model
                        pos = endPos;
                        continue;
                    }
                }

                // conservative check, minimal well-formedness of the content for version
                if (clusterLabel.equals(SoftwareTaggingLabels.VERSION)) {
                    if (SoftwareAnalyzer.DELIMITERS.indexOf(clusterContent) != -1) {
                        pos = endPos;
                        continue;
                    }
                }

                // conservative check, minimal well-formedness of the content for publisher name
                if (clusterLabel.equals(SoftwareTaggingLabels.CREATOR)) {
                    if (SoftwareAnalyzer.DELIMITERS.indexOf(clusterContent) != -1 ||
                        SoftwareLexicon.getInstance().isEnglishStopword(clusterContent) ||
                        FeatureFactory.getInstance().test_number(clusterContent) ||
                        (clusterContent.startsWith("-") && clusterContent.indexOf("\n") != -1)) {
                        pos = endPos;
                        continue;
                    }
                }

                currentComponent = new SoftwareComponent();

                currentComponent.setRawForm(clusterContent);

                currentComponent.setOffsetStart(pos);
                currentComponent.setOffsetEnd(endPos);

                currentComponent.setLabel(clusterLabel);
                currentComponent.setTokens(theTokens);

                List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(cluster.concatTokens());
                currentComponent.setBoundingBoxes(boundingBoxes);

                components.add(currentComponent);
                currentComponent = null;
            }

            pos = endPos;
            //pos += clusterContent.length();
        }

        // post process URL to have well formed URL
        components = postProcessURLComponents(components, tokenizations);

        Collections.sort(components);

        return components;
    }

    /**
     * Post-processing for labeled URL component to obtain a well-formed URL.
     * We extend the recognized URL on the left and the right and add parts corresponding to a URL
     * but not incorrectly labeled (e.g. missed "http" prefix or everlooked trailing ".html").
     **/
    public List<SoftwareComponent> postProcessURLComponents
    (List<SoftwareComponent> components, List<LayoutToken> tokenizations) {
        List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(tokenizations);
        if (urlPositions == null || urlPositions.size() == 0)
            return components;

        /*for(OffsetPosition position : urlPositions) {
            String localUrl = LayoutTokensUtil.toText(tokenizations.subList(position.start, position.end+1));
            System.out.println(localUrl);
        }*/

        // in case component labeled tokens overlap with URL positions, we extend the component to URL position
        for (SoftwareComponent component : components) {
            // only consider URL components
            if (!component.getLabel().equals(SoftwareTaggingLabels.SOFTWARE_URL))
                continue;

            // character offsets
            int start = component.getOffsetStart();
            int end = component.getOffsetEnd();

            if (component.getTokens() == null || component.getTokens().size() == 0)
                continue;

            // layout tokens
            LayoutToken startToken = component.getTokens().get(0);
            LayoutToken endToken = component.getTokens().get(component.getTokens().size() - 1);

            // index of Layouttoken in the token list
            int startTokenOffset = tokenizations.indexOf(startToken);
            int endTokenOffset = tokenizations.indexOf(endToken);

            if (startTokenOffset == -1 || endTokenOffset == -1)
                continue;

            //System.out.println(">>" + LayoutTokensUtil.toText(tokenizations.subList(startTokenOffset, endTokenOffset+1)) + "<<");

            // check overlap with URL positions
            for (OffsetPosition position : urlPositions) {
                // parano check
                if (position.start >= tokenizations.size() || position.end >= tokenizations.size())
                    continue;

                if (position.start <= startTokenOffset && endTokenOffset <= position.end) {
                    // extend component
                    component.setOffsetStart(tokenizations.get(position.start).getOffset());

                    component.setTokens(tokenizations.subList(position.start, position.end + 1));

                    component.setRawForm(LayoutTokensUtil.toText(tokenizations.subList(position.start, position.end + 1)));
                    component.setOffsetEnd(component.getOffsetStart() + component.getRawForm().length());

                    List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(tokenizations.subList(position.start, position.end + 1));
                    component.setBoundingBoxes(boundingBoxes);
                }
            }
        }

        // filter out possible redundant components for the same URL positions
        List<Integer> toBeRemoved = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            SoftwareComponent component = components.get(i);

            // only consider URL components
            if (!component.getLabel().equals(SoftwareTaggingLabels.SOFTWARE_URL))
                continue;

            if (toBeRemoved.contains(i))
                continue;

            int start = component.getOffsetStart();
            int end = component.getOffsetEnd();

            for (int j = 0; j < components.size(); j++) {
                if (i == j)
                    continue;

                if (toBeRemoved.contains(j))
                    continue;

                SoftwareComponent component2 = components.get(j);
                // only consider URL components
                if (!component2.getLabel().equals(SoftwareTaggingLabels.SOFTWARE_URL))
                    continue;

                int localStart = component2.getOffsetStart();
                int localEnd = component2.getOffsetEnd();

                // check overlap
                if (end < localStart)
                    break;
                if (localEnd < start)
                    continue;
                if ((start > localStart && start < localEnd) ||
                    (localStart < end && end < localEnd)) {
                    // we have an overlap, we keep the longest match
                    if (end - start < localEnd - localStart)
                        toBeRemoved.add(i);
                    else
                        toBeRemoved.add(j);
                    continue;
                }
            }
        }

        if (toBeRemoved.size() == 0)
            return components;

        List<SoftwareComponent> newComponents = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            if (!toBeRemoved.contains(i))
                newComponents.add(components.get(i));
        }

        return newComponents;
    }

    /**
     *  Add XML annotations corresponding to components in a piece of text, to be included in
     *  generated training data.
     */
    public Element trainingExtraction(List<SoftwareComponent> components, String
        text, List<LayoutToken> tokenizations) {
        Element p = teiElement("p");

        int pos = 0;
        if ((components == null) || (components.size() == 0))
            p.appendChild(text);
        else {
            for (SoftwareComponent component : components) {
                Element componentElement = teiElement("rs");

                //if (component.getLabel() != OTHER)
                {
                    String localLabel = component.getLabel().getLabel().replace("<", "");
                    localLabel = localLabel.replace(">", "");

                    componentElement.addAttribute(new Attribute("type", localLabel));

                    int startE = component.getOffsetStart();
                    int endE = component.getOffsetEnd();

                    p.appendChild(text.substring(pos, startE));
                    componentElement.appendChild(text.substring(startE, endE));
                    pos = endE;
                }
                p.appendChild(componentElement);
            }
            p.appendChild(text.substring(pos, text.length()));
        }

        return p;
    }

    /**
     *  Create a standard TEI header to be included in the TEI training files.
     */
    static public nu.xom.Element getTEIHeader(String id) {
        Element tei = teiElement("tei");
        Element teiHeader = teiElement("teiHeader");

        if (id != null) {
            Element fileDesc = teiElement("fileDesc");
            fileDesc.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", id));
            teiHeader.appendChild(fileDesc);
        }

        Element encodingDesc = teiElement("encodingDesc");

        Element appInfo = teiElement("appInfo");

        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);
        String dateISOString = df.format(new java.util.Date());

        Element application = teiElement("application");
        application.addAttribute(new Attribute("version", GrobidProperties.getVersion()));
        application.addAttribute(new Attribute("ident", "GROBID"));
        application.addAttribute(new Attribute("when", dateISOString));

        Element ref = teiElement("ref");
        ref.addAttribute(new Attribute("target", "https://github.com/kermitt2/grobid"));
        ref.appendChild("A machine learning software for extracting information from scholarly documents");

        application.appendChild(ref);
        appInfo.appendChild(application);
        encodingDesc.appendChild(appInfo);
        teiHeader.appendChild(encodingDesc);
        tei.appendChild(teiHeader);

        return tei;
    }

    /**
     *  Create a simplified TEI header to be included in a TEI corpus file.
     */
    static public Element getTEIHeaderSimple(String id, BiblioItem biblio) {
        return getTEIHeaderSimple(id, biblio, null);
    }

    static public Element getTEIHeaderSimple(String id, BiblioItem biblio, String catCuration) {
        Element tei = teiElement("TEI");
        Element teiHeader = teiElement("teiHeader");

        if (id != null) {
            Element fileDesc = teiElement("fileDesc");
            fileDesc.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", id));

            Element titleStatement = teiElement("titleStmt");
            Element title = teiElement("title");
            title.appendChild(biblio.getTitle());
            titleStatement.appendChild(title);
            fileDesc.appendChild(titleStatement);
            Element sourceDesc = teiElement("sourceDesc");
            Element bibl = teiElement("bibl");
            if (biblio.getDOI() != null) {
                Element idno = teiElement("idno");
                idno.addAttribute(new Attribute("type", null, "DOI"));
                idno.appendChild(biblio.getDOI());
                bibl.appendChild(idno);
            } else if (id.startsWith("10.")) {
                Element idno = teiElement("idno");
                idno.addAttribute(new Attribute("type", null, "DOI"));
                idno.appendChild(id);
                bibl.appendChild(idno);
            }
            if (biblio.getPMCID() != null) {
                Element idno = teiElement("idno");
                idno.addAttribute(new Attribute("type", null, "PMC"));
                idno.appendChild(biblio.getPMCID());
                bibl.appendChild(idno);
            } else if (id.startsWith("PMC")) {
                Element idno = teiElement("idno");
                idno.addAttribute(new Attribute("type", null, "PMC"));
                idno.appendChild(id);
                bibl.appendChild(idno);
            }
            if (biblio.getPMID() != null) {
                Element idno = teiElement("idno");
                idno.addAttribute(new Attribute("type", null, "PMID"));
                idno.appendChild(biblio.getPMID());
                bibl.appendChild(idno);
            }
            sourceDesc.appendChild(bibl);
            fileDesc.appendChild(sourceDesc);
            teiHeader.appendChild(fileDesc);
        }

        Element encodingDesc = teiElement("encodingDesc");
        teiHeader.appendChild(encodingDesc);

        if (catCuration != null) {
            Element profileDesc = teiElement("profileDesc");

            Element textClass = teiElement("textClass");
            Element catRef = teiElement("catRef");

            catRef.addAttribute(new Attribute("target", null, "#" + catCuration));

            textClass.appendChild(catRef);
            profileDesc.appendChild(textClass);

            teiHeader.appendChild(profileDesc);
        }

        tei.appendChild(teiHeader);

        return tei;
    }

    /**
     *  Create training data from PDF with annotation layers corresponding to the entities.
     */
    public int boostrapTrainingPDF(String inputDirectory,
                                   String outputDirectory,
                                   int ind) {
        return 0;
    }

    /**
     * Extract all software mentions from a publisher XML file
     */
    public Pair<List<SoftwareEntity>, List<BibDataSet>> processXML(File file,
                                                                   boolean disambiguate,
                                                                   boolean addParagraphContext) throws IOException {
        Pair<List<SoftwareEntity>, List<BibDataSet>> resultExtraction = null;
        try {
            String tei = processXML(file);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            //tei = avoidDomParserAttributeBug(tei);

            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(tei)));
            //document.getDocumentElement().normalize();

            resultExtraction = processTEIDocument(document, disambiguate, addParagraphContext);

            //tei = restoreDomParserAttributeBug(tei);

        } catch (final Exception exp) {
            logger.error("An error occured while processing the following XML file: "
                + file.getPath(), exp);
        }

        return resultExtraction;
    }


    /**
     * Extract all software mentions from a publisher XML file
     */
    public Pair<List<SoftwareEntity>, List<BibDataSet>> processTEI(File file,
                                                                   boolean disambiguate,
                                                                   boolean addParagraphContext) throws IOException {
        Pair<List<SoftwareEntity>, List<BibDataSet>> resultExtraction = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document document = builder.parse(file);
            //document.getDocumentElement().normalize();
            resultExtraction = processTEIDocument(document, disambiguate, addParagraphContext);
            //tei = restoreDomParserAttributeBug(tei);

        } catch (final Exception exp) {
            logger.error("An error occured while processing the following XML file: "
                + file.getPath(), exp);
        }

        return resultExtraction;
    }

    /**
     * Tranform an XML document (for example JATS) to a TEI document.
     * Transformation of the XML/JATS/NLM/etc. document is realised thanks to Pub2TEI
     * (https://github.com/kermitt2/pub2tei)
     *
     * @return TEI string
     */
    public String processXML(File file) throws Exception {
        /*File file = new File(filePath);
        if (!file.exists())
            return null;*/
        String fileName = file.getName();
        String tei = null;
        String newFilePath = null;
        try {
            String tmpFilePath = this.softwareConfiguration.getTmpPath();
            newFilePath = ArticleUtilities.applyPub2TEI(file.getAbsolutePath(),
                tmpFilePath + "/" + fileName.replace(".xml", ".tei.xml"),
                this.softwareConfiguration.getPub2TEIPath());
            //System.out.println(newFilePath);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            tei = FileUtils.readFileToString(new File(newFilePath), UTF_8);

        } catch (final Exception exp) {
            logger.error("An error occured while processing the following XML file: " + file.getAbsolutePath(), exp);
        } finally {
            if (newFilePath != null) {
                File newFile = new File(newFilePath);
                IOUtilities.removeTempFile(newFile);
            }
        }
        return tei;
    }


    /**
     * Extract all software mentions from a publisher XML file
     */
    public Pair<List<SoftwareEntity>, List<BibDataSet>> processTEIDocument(org.w3c.dom.Document doc,
                                                                           boolean disambiguate,
                                                                           boolean addParagraphContext) {
        List<SoftwareEntity> entities = new ArrayList<>();

        List<List<LayoutToken>> selectedLayoutTokenSequencesRaw = new ArrayList<>();
        List<List<LayoutToken>> selectedOriginalLayoutTokenSequences = new ArrayList<>();
        List<LayoutToken> docLayoutTokens = new ArrayList<>();

        List<Map<String, Pair<OffsetPosition, String>>> selectedRefInfos = new ArrayList<>();

        org.w3c.dom.NodeList paragraphList = doc.getElementsByTagName("p");
        int globalPos = 0;
        for (int i = 0; i < paragraphList.getLength(); i++) {
            org.w3c.dom.Element paragraphElement = (org.w3c.dom.Element) paragraphList.item(i);

            // check that the father is not <abstract> and not <figDesc>
            /*org.w3c.dom.Node fatherNode = paragraphElement.getParentNode();
            if (fatherNode != null) {
                if ("availability".equals(fatherNode.getNodeName()))
                    continue;
            }*/

            Pair<String, Map<String, Pair<OffsetPosition, String>>> contentTextAndRef =
                XMLUtilities.getTextNoRefMarkersAndMarkerPositions(paragraphElement, globalPos);
            String contentText = UnicodeUtil.normaliseText(contentTextAndRef.getLeft());
            Map<String, Pair<OffsetPosition, String>> refInfos = contentTextAndRef.getRight();

            if (StringUtils.isNotBlank(contentText)) {
                List<LayoutToken> paragraphTokens =
                    SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(contentText);
                String orginalText = UnicodeUtil.normaliseText(paragraphElement.getTextContent());
                List<LayoutToken> originalParagraphTokens =
                    SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(orginalText);
                if (CollectionUtils.isNotEmpty(paragraphTokens)) {
                    // shift the paragraph tokens to the global position
                    for (LayoutToken paragraphToken : paragraphTokens) {
                        paragraphToken.setOffset(paragraphToken.getOffset() + globalPos);
                    }
                    for (LayoutToken originalParagraphToken : originalParagraphTokens) {
                        originalParagraphToken.setOffset(originalParagraphToken.getOffset() + globalPos);
                    }

                    selectedLayoutTokenSequencesRaw.add(paragraphTokens);
                    docLayoutTokens.addAll(originalParagraphTokens);

                    selectedRefInfos.add(refInfos);
                    selectedOriginalLayoutTokenSequences.add(originalParagraphTokens);
                }

                globalPos += contentText.length();
            }
        }

        logger.warn("Number of sequences too large for the BERT model: "
            + selectedLayoutTokenSequencesRaw.stream().filter(s -> s.size() > 512).count());

        // Run the sentence segmenter for sequences that are larger than 512 tokens, what the BERT model can handle.
        List<List<LayoutToken>> selectedLayoutTokenSequences = selectedLayoutTokenSequencesRaw.stream()
            .flatMap(tokens -> {
                    if (tokens.size() > 512) {
                        String tempText = LayoutTokensUtil.toText(tokens);
                        List<OffsetPosition> offsetPositions = SentenceUtilities.getInstance().runSentenceDetection(tempText);
                        List<List<LayoutToken>> splitTokens =offsetPositions.stream()
                            .map(op -> tempText.substring(op.start, op.end))
                            .map(s -> SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(s))
                            .collect(Collectors.toList());
                        return splitTokens.stream();
                    }
                    return Stream.of(tokens);
                }
            ).collect(Collectors.toList());

        logger.warn("Number of sequences too large for the BERT model: "
            + selectedLayoutTokenSequences.stream().filter(s -> s.size() > 512).count());


        processLayoutTokenSequenceMultiple(selectedLayoutTokenSequences, entities, disambiguate, addParagraphContext, false, true);
        selectedLayoutTokenSequences = selectedOriginalLayoutTokenSequences;

        // filter out components outside context, restore original tokenization
        int sequenceIndex = 0;
        for (SoftwareEntity entity : entities) {
            if (entity.getSoftwareName() != null) {
                String context = entity.getContext();
                if (context == null)
                    continue;
                String oldContext = new String(entity.getContext());

                int paragraphContextOffset = entity.getParagraphContextOffset();
                int globalContextOffset = entity.getGlobalContextOffset();
                SoftwareComponent softwareName = entity.getSoftwareName();

                //System.out.println(softwareName.getRawForm() + " / " + softwareName.getOffsetStart() + "-" + softwareName.getOffsetEnd() +
                //    " / global offset: " + globalContextOffset + " / context: " + context + " / final context pos: " + (globalContextOffset+context.length()) );

                for (int i = sequenceIndex; i < selectedLayoutTokenSequences.size(); i++) {
                    int posStartSequence = -1;
                    List<LayoutToken> selectedLayoutTokenSequence = selectedLayoutTokenSequences.get(i);
                    if (selectedLayoutTokenSequence != null && selectedLayoutTokenSequence.size() > 0) {
                        posStartSequence = selectedLayoutTokenSequence.get(0).getOffset();
                        String localText = LayoutTokensUtil.toText(selectedLayoutTokenSequence);
                        if (posStartSequence <= globalContextOffset && globalContextOffset < posStartSequence + localText.length()) {
                            // the context is within this sequence
                            int maxBound = Math.min((globalContextOffset - posStartSequence) + context.length() + 1, localText.length());
                            String newContext = localText.substring(globalContextOffset - posStartSequence, maxBound);
                            entity.setContext(newContext);
                            break;
                        }
                    }
                }

                context = entity.getContext();
                if (context == null || context.trim().length() == 0) {
                    // this should never happen, but just in case...
                    entity.setContext(oldContext);
                    context = oldContext;
                }

                SoftwareComponent version = entity.getVersion();
                if (version != null && (version.getOffsetStart() < 0 || version.getOffsetEnd() < 0)) {
                    entity.setVersion(null);
                }
                if (version != null && (version.getOffsetStart() > context.length() || version.getOffsetEnd() > context.length())) {
                    entity.setVersion(null);
                }
                SoftwareComponent creator = entity.getCreator();
                if (creator != null && (creator.getOffsetStart() < 0 || creator.getOffsetEnd() < 0)) {
                    entity.setCreator(null);
                }
                if (creator != null && (creator.getOffsetStart() > context.length() || creator.getOffsetEnd() > context.length())) {
                    entity.setCreator(null);
                }
                SoftwareComponent url = entity.getSoftwareURL();
                if (url != null && (url.getOffsetStart() < 0 || url.getOffsetEnd() < 0)) {
                    entity.setSoftwareURL(null);
                }
                if (url != null && (url.getOffsetStart() > context.length() || url.getOffsetEnd() > context.length())) {
                    entity.setSoftwareURL(null);
                }
                SoftwareComponent language = entity.getLanguage();
                if (language != null && (language.getOffsetStart() < 0 || language.getOffsetEnd() < 0)) {
                    entity.setLanguage(null);
                }
                if (language != null && (language.getOffsetStart() > context.length() || language.getOffsetEnd() > context.length())) {
                    entity.setLanguage(null);
                }
            }
        }

        // propagate the disambiguated entities to the non-disambiguated entities corresponding to the same software name
        for (SoftwareEntity entity1 : entities) {
            if (entity1.getSoftwareName() != null && entity1.getSoftwareName().getWikidataId() != null) {
                for (SoftwareEntity entity2 : entities) {
                    if (entity2.getSoftwareName() != null && entity2.getSoftwareName().getWikidataId() != null) {
                        // if the entity is already disambiguated, nothing possible
                        continue;
                    }
                    if (entity2.getSoftwareName() != null &&
                        entity2.getSoftwareName().getRawForm().equals(entity1.getSoftwareName().getRawForm())) {
                        entity1.getSoftwareName().copyKnowledgeInformationTo(entity2.getSoftwareName());
                        entity2.getSoftwareName().setLang(entity1.getSoftwareName().getLang());
                    }
                }
            }
        }

        // second pass for document level consistency
        // we prepare a matcher for all the identified software names
        FastMatcher termPattern = prepareTermPattern(entities);
        // we prepare the frequencies for each software name in the whole document
        Map<String, Integer> frequencies = prepareFrequencies(entities, docLayoutTokens);
        // we prepare a map for mapping a software name with its positions of annotation in the document and its IDF
        Map<String, Double> termProfiles = prepareTermProfiles(entities);
        List<OffsetPosition> placeTaken = preparePlaceTaken(entities);

        globalPos = 0;
        for (int i = 0; i < paragraphList.getLength(); i++) {
            org.w3c.dom.Element paragraphElement = (org.w3c.dom.Element) paragraphList.item(i);

            // check that the father is not <abstract> and not <figDesc>
            /*org.w3c.dom.Node fatherNode = paragraphElement.getParentNode();
            if (fatherNode != null) {
                if ("availability".equals(fatherNode.getNodeName()))
                    continue;
            }*/

            String contentText = UnicodeUtil.normaliseText(paragraphElement.getTextContent());
            if (contentText != null && contentText.length() > 0) {
                List<LayoutToken> paragraphTokens =
                    SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(contentText);

                if (paragraphTokens != null && paragraphTokens.size() > 0) {
                    for (LayoutToken paragraphToken : paragraphTokens) {
                        paragraphToken.setOffset(paragraphToken.getOffset() + globalPos);
                    }
                    propagateLayoutTokenSequence(paragraphTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext, false, true);
                }
                globalPos += contentText.length();
            }
        }

        // propagate the non-disambiguated entities attributes to the new propagated entities corresponding
        // to the same software name
        for (SoftwareEntity entity1 : entities) {
            if (entity1.getSoftwareName() != null) {
                for (SoftwareEntity entity2 : entities) {
                    if (entity2.getSoftwareName() != null &&
                        entity2.getSoftwareName().getNormalizedForm().equals(entity1.getSoftwareName().getNormalizedForm())) {
                        SoftwareEntity.mergeWithCopy(entity1, entity2);
                        if (entity1.getSoftwareName().getWikidataId() != null && entity2.getSoftwareName().getWikidataId() == null) {
                            entity1.getSoftwareName().copyKnowledgeInformationTo(entity2.getSoftwareName());
                            entity2.getSoftwareName().setLang(entity1.getSoftwareName().getLang());
                        } else if (entity2.getSoftwareName().getWikidataId() != null && entity1.getSoftwareName().getWikidataId() == null) {
                            entity2.getSoftwareName().copyKnowledgeInformationTo(entity1.getSoftwareName());
                            entity1.getSoftwareName().setLang(entity2.getSoftwareName().getLang());
                        }
                    }
                }
            }
        }

        //Collections.sort(entities);

        // local bibliographical references to spot in the XML mark-up, to attach and propagate
        List<BibDataSet> resCitations = new ArrayList<>();
        org.w3c.dom.NodeList bibList = doc.getElementsByTagName("biblStruct");
        for (int i = 0; i < bibList.getLength(); i++) {
            org.w3c.dom.Element biblStructElement = (org.w3c.dom.Element) bibList.item(i);

            // filter <biblStruct> not having as father <listBibl>
            /*org.w3c.dom.Node fatherNode = biblStructElement.getParentNode();
            if (fatherNode != null) {
                if (!"listBibl".equals(fatherNode.getNodeName()))
                    continue;
            }*/

            BiblioItem biblio = XMLUtilities.parseTEIBiblioItem(doc, biblStructElement);

            BibDataSet bds = new BibDataSet();
            bds.setResBib(biblio);
            bds.setRefSymbol(biblStructElement.getAttribute("xml:id"));
            resCitations.add(bds);
        }

        entities = attachReferencesXML(entities,
            selectedRefInfos,
            resCitations);

        // consolidate the attached ref bib (we don't consolidate all bibliographical references
        // to avoid useless costly computation)
        List<BibDataSet> citationsToConsolidate = new ArrayList<BibDataSet>();
        List<Integer> consolidated = new ArrayList<Integer>();
        for (SoftwareEntity entity : entities) {
            if (entity.getBibRefs() != null && entity.getBibRefs().size() > 0) {
                List<BiblioComponent> bibRefs = entity.getBibRefs();
                for (BiblioComponent bibRef : bibRefs) {
                    Integer refKeyVal = Integer.valueOf(bibRef.getRefKey());
                    if (!consolidated.contains(refKeyVal)) {
                        citationsToConsolidate.add(resCitations.get(refKeyVal));
                        consolidated.add(refKeyVal);
                    }
                }
            }
        }

        try {
            Consolidation consolidator = Consolidation.getInstance();
            Map<Integer, BiblioItem> resConsolidation = consolidator.consolidate(citationsToConsolidate);
            for (int i = 0; i < citationsToConsolidate.size(); i++) {
                BiblioItem resCitation = citationsToConsolidate.get(i).getResBib();
                BiblioItem bibo = resConsolidation.get(i);
                if (bibo != null) {
                    BiblioItem.correct(resCitation, bibo);
                }
            }
        } catch (Exception e) {
            throw new GrobidException(
                "An exception occured while running consolidation on bibliographical references.", e);
        }

        // propagate the bib. ref. to the entities corresponding to the same software name without bib. ref.
        if (entities != null && entities.size() > 0) {
            for (SoftwareEntity entity1 : entities) {
                if (entity1.getBibRefs() != null && entity1.getBibRefs().size() > 0) {
                    for (SoftwareEntity entity2 : entities) {
                        if (entity2.getBibRefs() != null) {
                            continue;
                        }
                        if (entity2.getSoftwareName() != null &&
                            entity2.getSoftwareName().getRawForm().equals(entity1.getSoftwareName().getRawForm())) {
                            List<BiblioComponent> newBibRefs = new ArrayList<>();
                            for (BiblioComponent bibComponent : entity1.getBibRefs()) {
                                newBibRefs.add(new BiblioComponent(bibComponent));
                            }
                            entity2.setBibRefs(newBibRefs);
                        }
                    }
                }
            }
        }

        Collections.sort(entities);

        // finally classify the context for predicting the role of the software mention
        entities = SoftwareContextClassifier.getInstance(softwareConfiguration).classifyDocumentContexts(entities);

        return Pair.of(entities, resCitations);
    }


    private List<SoftwareEntity> refineTypes(List<SoftwareEntity> entities, List<SoftwareType> entityTypes) {
        // check overlap and possibly associate types
        for (SoftwareEntity entity : entities) {
            // get software name component
            SoftwareComponent softwareName = entity.getSoftwareName();
            OffsetPosition positionEntity = softwareName.getOffsets();

            // note: elements in entityTypes are removed as they are "consumed", via iterator.remove()
            for (Iterator iter = entityTypes.iterator(); iter.hasNext(); ) {
                SoftwareType entityType = (SoftwareType) iter.next();
                OffsetPosition localTypePosition = entityType.getOffsets();

                // check overlap between software entity and software type span
                if (
                    (localTypePosition.start <= positionEntity.start && localTypePosition.end > positionEntity.start) ||
                        (localTypePosition.start >= positionEntity.start && localTypePosition.start < positionEntity.end)) {
                    // overlap case
                    if (entityType.getType() == SoftwareLexicon.Software_Type.LANGUAGE &&
                        (localTypePosition.start != positionEntity.start || localTypePosition.end != positionEntity.end)) {
                        // we remove the language raw string from the software name if leading or trailing overlap
                        String softwareNameRawForm = softwareName.getRawForm();
                        String entityTypeRawForm = entityType.getRawForm();
                        if (softwareNameRawForm.startsWith(entityTypeRawForm) || softwareNameRawForm.endsWith(entityTypeRawForm)) {

                            if (softwareNameRawForm.startsWith(entityTypeRawForm)) {
                                softwareName.setRawForm(softwareNameRawForm.substring(entityTypeRawForm.length(), softwareNameRawForm.length()));
                                softwareName.setOffsetStart(softwareName.getOffsetStart() + entityTypeRawForm.length());
                                softwareName.setTokens(
                                    softwareName.getTokens().subList(entityType.getTokens().size(), softwareName.getTokens().size()));

                                while (softwareName.getRawForm().startsWith(" ")) {
                                    softwareName.setRawForm(softwareName.getRawForm().substring(1, softwareName.getRawForm().length()));
                                    softwareName.setOffsetStart(softwareName.getOffsetStart() + 1);
                                    softwareName.setTokens(softwareName.getTokens().subList(1, softwareName.getTokens().size()));
                                }

                                List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(softwareName.getTokens());
                                softwareName.setBoundingBoxes(boundingBoxes);
                            } else {
                                softwareName.setRawForm(softwareNameRawForm.substring(0, softwareNameRawForm.length() - entityTypeRawForm.length()));
                                softwareName.setOffsetEnd(softwareName.getOffsetEnd() - entityTypeRawForm.length());
                                softwareName.setTokens(
                                    softwareName.getTokens().subList(0, softwareName.getTokens().size() - entityType.getTokens().size()));

                                while (softwareName.getRawForm().endsWith(" ")) {
                                    softwareName.setRawForm(softwareName.getRawForm().substring(0, softwareName.getRawForm().length() - 1));
                                    softwareName.setOffsetStart(softwareName.getOffsetEnd() - 1);
                                    softwareName.setTokens(softwareName.getTokens().subList(0, softwareName.getTokens().size() - 1));
                                }

                                List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(softwareName.getTokens());
                                softwareName.setBoundingBoxes(boundingBoxes);
                            }

                            // we had a language component to the software entity
                            SoftwareComponent languageComponent = createSoftwareComponentFromLanguage(entityType, SoftwareTaggingLabels.LANGUAGE);
                            entity.setLanguage(languageComponent);

                            // consume the entityType
                            iter.remove();
                        }
                    } else if (entityType.getType() != SoftwareLexicon.Software_Type.LANGUAGE &&
                        (entity.getType() == null ||
                            entity.getType() == SoftwareLexicon.Software_Type.UNKNOWN ||
                            entity.getType() == SoftwareLexicon.Software_Type.SOFTWARE)
                    ) {
                        entity.setType(entityType.getType());

                        // consume the entityType
                        iter.remove();
                    }
                }
            }
        }

        // add remaining implicit software names
        if (entityTypes.size() > 0) {
            for (Iterator iter = entityTypes.iterator(); iter.hasNext(); ) {
                SoftwareType entityType = (SoftwareType) iter.next();
                OffsetPosition localTypePosition = entityType.getOffsets();

                if (entityType.getType() == SoftwareLexicon.Software_Type.IMPLICIT) {
                    // if at the end of the process we still have a implicit software name not consumed, we can
                    // add it as simple implicit software name
                    SoftwareComponent implicitComponent = createSoftwareComponentFromLanguage(entityType, SoftwareTaggingLabels.IMPLICIT);
                    SoftwareEntity entity = new SoftwareEntity();
                    entity.setSoftwareName(implicitComponent);
                    entity.setType(SoftwareLexicon.Software_Type.IMPLICIT);
                    entities.add(entity);

                    // consume the entityType
                    iter.remove();
                }
            }
        }

        // model conflict language / software for the same position: so which one to choose? it is reasonable to
        // consider it as a software environment corresponding to the identified programming language
        if (entityTypes.size() > 0) {
            for (Iterator iter = entityTypes.iterator(); iter.hasNext(); ) {
                SoftwareType entityType = (SoftwareType) iter.next();

                if (entityType.getType() == SoftwareLexicon.Software_Type.LANGUAGE) {
                    OffsetPosition localTypePosition = entityType.getOffsets();
                    for (SoftwareEntity softwareEntity : entities) {
                        SoftwareComponent softwareName = softwareEntity.getSoftwareName();
                        OffsetPosition positionEntity = softwareName.getOffsets();

                        if (positionEntity.start == localTypePosition.start && positionEntity.end == localTypePosition.end) {
                            softwareEntity.setType(SoftwareLexicon.Software_Type.ENVIRONMENT);

                            // consume the entityType
                            iter.remove();
                        }
                    }
                }
            }
        }

        Collections.sort(entities);

        // try to attach remaining language name(s) to software entity
        if (entityTypes.size() > 0) {
            for (Iterator iter = entityTypes.iterator(); iter.hasNext(); ) {
                SoftwareType entityType = (SoftwareType) iter.next();
                OffsetPosition localTypePosition = entityType.getOffsets();

                if (entityType.getType() == SoftwareLexicon.Software_Type.LANGUAGE) {
                    // if at the end of the process we still have language name not consumed, we can try to attach it to
                    // the closest software name

                    // closest software name on the left (if any)
                    SoftwareEntity entityLeft = null;
                    for (SoftwareEntity softwareEntity : entities) {
                        SoftwareComponent softwareName = softwareEntity.getSoftwareName();
                        OffsetPosition positionEntity = softwareName.getOffsets();

                        if (positionEntity.end < localTypePosition.start) {
                            entityLeft = softwareEntity;
                        } else {
                            break;
                        }
                    }

                    // closest software name on the right (if any)
                    SoftwareEntity entityRight = null;
                    for (int k = entities.size() - 1; k >= 0; k--) {
                        SoftwareEntity softwareEntity = entities.get(k);
                        SoftwareComponent softwareName = softwareEntity.getSoftwareName();
                        OffsetPosition positionEntity = softwareName.getOffsets();

                        if (positionEntity.start > localTypePosition.end) {
                            entityLeft = softwareEntity;
                        } else {
                            break;
                        }
                    }

                    // proximity constraint + bonus to the left in case of middle position
                    if (entityLeft == null && entityRight != null) {
                        // check distance
                        if (entityRight.getSoftwareName().getOffsetStart() - localTypePosition.end <= 20) {
                            // attachment
                            SoftwareComponent languageComponent = createSoftwareComponentFromLanguage(entityType, SoftwareTaggingLabels.LANGUAGE);
                            entityRight.setLanguage(languageComponent);
                        }
                    } else if (entityRight == null && entityLeft != null) {
                        // check distance
                        if (localTypePosition.start - entityLeft.getSoftwareName().getOffsetEnd() <= 20) {
                            // attachment
                            SoftwareComponent languageComponent = createSoftwareComponentFromLanguage(entityType, SoftwareTaggingLabels.LANGUAGE);
                            entityLeft.setLanguage(languageComponent);
                        }
                    } else if (entityRight != null & entityLeft != null) {
                        // programming language component is in the middle of the two software entities, attach to the closest
                        // with distance constraint + bonus to left entity
                        int distLeft = localTypePosition.start - entityLeft.getSoftwareName().getOffsetEnd();
                        int distRight = entityRight.getSoftwareName().getOffsetStart() - localTypePosition.end;

                        if (distLeft > 20 && distRight <= 20) {
                            SoftwareComponent languageComponent = createSoftwareComponentFromLanguage(entityType, SoftwareTaggingLabels.LANGUAGE);
                            entityRight.setLanguage(languageComponent);
                        } else if (distLeft <= 20 && distRight > 20) {
                            SoftwareComponent languageComponent = createSoftwareComponentFromLanguage(entityType, SoftwareTaggingLabels.LANGUAGE);
                            entityLeft.setLanguage(languageComponent);
                        } else if (distRight <= 20 && distLeft <= 20) {
                            if (distRight * 2 < distLeft) {
                                SoftwareComponent languageComponent = createSoftwareComponentFromLanguage(entityType, SoftwareTaggingLabels.LANGUAGE);
                                entityRight.setLanguage(languageComponent);
                            } else {
                                SoftwareComponent languageComponent = createSoftwareComponentFromLanguage(entityType, SoftwareTaggingLabels.LANGUAGE);
                                entityLeft.setLanguage(languageComponent);
                            }
                        }
                    }

                    // consume the entityType
                    iter.remove();
                }
            }
        }

        return entities;
    }

    public SoftwareComponent createSoftwareComponentFromLanguage(SoftwareType entityType, TaggingLabel label) {
        SoftwareComponent languageComponent = new SoftwareComponent();
        languageComponent.setRawForm(entityType.getRawForm());
        languageComponent.setOffsetStart(entityType.getOffsetStart());
        languageComponent.setOffsetEnd(entityType.getOffsetEnd());
        languageComponent.setLabel(label);
        languageComponent.setTokens(entityType.getTokens());
        List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(entityType.getTokens());
        languageComponent.setBoundingBoxes(boundingBoxes);

        // look-up Wikidata information
        org.grobid.core.utilities.Pair<String, String> wikiInfo = softwareLexicon.getProgrammingLanguageWikiInfo(entityType.getRawForm());
        if (wikiInfo != null && wikiInfo.getB() != null && wikiInfo.getB().length() > 0) {
            languageComponent.setWikidataId(wikiInfo.getB());
        }
        return languageComponent;
    }

    private List<SoftwareEntity> attachReferencesXML(List<SoftwareEntity> entities,
                                                     List<Map<String, Pair<OffsetPosition, String>>> selectedRefInfos,
                                                     List<BibDataSet> resCitations) {
        if (selectedRefInfos == null || selectedRefInfos.size() == 0)
            return entities;

        // for the biblio ref. attached to this software entity
        List<BiblioComponent> bibRefComponents = new ArrayList<BiblioComponent>();

        int refInfoIndex = 0;
        List<String> contextDone = new ArrayList<>();
        for (SoftwareEntity entity : entities) {
            if (entity.getSoftwareName() != null) {
                String context = entity.getContext();

                if (context == null || contextDone.contains(context))
                    continue;

                int contextOffset = entity.getGlobalContextOffset();

                // do we have ref markers in this context window?
                for (int i = refInfoIndex; i < selectedRefInfos.size(); i++) {
                    Map<String, Pair<OffsetPosition, String>> selectedRefInfo = selectedRefInfos.get(i);
                    for (Map.Entry<String, Pair<OffsetPosition, String>> entry : selectedRefInfo.entrySet()) {
                        String bibString = entry.getKey();
                        Pair<OffsetPosition, String> bibValue = entry.getValue();

                        OffsetPosition refMarkerPosition = bibValue.getLeft();
                        String refMarkerKey = bibValue.getRight();
                        if (refMarkerPosition.start >= contextOffset && refMarkerPosition.end <= contextOffset + context.length()) {

                            // de we have components overlaping a ref marker? if yes discard these components
                            SoftwareComponent version = entity.getVersion();
                            if (version != null && version.getOffsets() != null && version.getOffsetStart() >= refMarkerPosition.start && version.getOffsetEnd() <= refMarkerPosition.end) {
                                entity.setVersion(null);
                            }
                            SoftwareComponent creator = entity.getCreator();
                            if (creator != null && creator.getOffsets() != null && creator.getOffsetStart() >= refMarkerPosition.start && creator.getOffsetEnd() <= refMarkerPosition.end) {
                                entity.setCreator(null);
                            }
                            SoftwareComponent url = entity.getSoftwareURL();
                            if (url != null && url.getOffsets() != null && url.getOffsetStart() >= refMarkerPosition.start && url.getOffsetEnd() <= refMarkerPosition.end) {
                                entity.setSoftwareURL(null);
                            }
                            SoftwareComponent language = entity.getLanguage();
                            if (language != null && language.getOffsets() != null && language.getOffsetStart() >= refMarkerPosition.start && language.getOffsetEnd() <= refMarkerPosition.end) {
                                entity.setLanguage(null);
                            }

                            // finally ref marker attachement
                            SoftwareComponent softwareName = entity.getSoftwareName();
                            // get the bibref object
                            BibDataSet resBib = null;
                            int indexRef = 0;
                            if (resCitations != null & resCitations.size() > 0) {
                                for (BibDataSet resCitation : resCitations) {
                                    if (refMarkerKey.equals(resCitation.getRefSymbol())) {
                                        resBib = resCitation;
                                        break;
                                    }
                                    indexRef++;
                                }
                                if (resBib != null) {
                                    BiblioComponent biblioComponent = new BiblioComponent(resBib.getResBib(), indexRef);
                                    biblioComponent.setRawForm(bibString);
                                    biblioComponent.setOffsetStart(refMarkerPosition.start);
                                    biblioComponent.setOffsetEnd(refMarkerPosition.end);
                                    bibRefComponents.add(biblioComponent);
                                }
                            }
                        }
                    }
                }
                contextDone.add(context);
            }
        }

        if (bibRefComponents.size() > 0) {
            // attach references to software entities
            entities = attachRefBib(entities, bibRefComponents, 10);
        }

        return entities;
    }

}
