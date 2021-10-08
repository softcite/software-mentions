package org.grobid.core.engines;

import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.data.SoftwareComponent;
import org.grobid.core.data.BiblioComponent;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.document.TEIFormatter;
import org.grobid.core.document.xml.XmlBuilderUtils;
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
import org.grobid.core.sax.TextChunkSaxHandler;
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

import org.xml.sax.InputSource;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Node;
import nu.xom.Text;

import static org.apache.commons.lang3.StringUtils.*;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;
import org.apache.commons.lang3.tuple.Pair;
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

import static java.nio.charset.StandardCharsets.UTF_8;

import org.w3c.dom.ls.*;

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
            GrobidCRFEngine.valueOf(configuration.getModel().engine.toUpperCase()),
            configuration.getModel().delft.architecture);

        softwareLexicon = SoftwareLexicon.getInstance();
		parsers = new EngineParsers();
        disambiguator = SoftwareDisambiguator.getInstance(configuration);
        softwareConfiguration = configuration;
    }

    /**
     * Extract all Software mentions from a simple piece of text.
     */
    public List<SoftwareEntity> processText(String text, boolean disambiguate) throws Exception {
        if (isBlank(text)) {
            return null;
        }
        text = UnicodeUtil.normaliseText(text);
        List<SoftwareComponent> components = new ArrayList<SoftwareComponent>();
        List<SoftwareEntity> entities = null;
        try {
            text = text.replace("\n", " ");
            text = text.replace("\t", " ");
            List<LayoutToken> tokens = SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(text);
            if (tokens.size() == 0) {
                return null;
            }

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
                for(SoftwareEntity entity : entities) {
                    if (entity.isFiltered()) {
                        indexToBeFiltered.add(new Integer(k));
                    }
                    k++;
                }

                if (indexToBeFiltered.size() > 0) {
                    for(int j=indexToBeFiltered.size()-1; j>= 0; j--) {
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
            entities = propagateLayoutTokenSequence(tokens, entities, termProfiles, termPattern, placeTaken, frequencies, false);
            Collections.sort(entities);

            // finally attach a local text context to the entities
            entities = addContext(entities, text, tokens, false, false);
        } catch (Exception e) {
            throw new GrobidException("An exception occured while running Grobid.", e);
        }

        return entities;
    }

	/**
	 * Extract all Software mentions from a pdf file 
	 */
    public Pair<List<SoftwareEntity>,Document> processPDF(File file, 
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
            SortedSet<DocumentPiece> documentParts = doc.getDocumentPart(SegmentationLabels.HEADER);
            BiblioItem resHeader = null;
            if (documentParts != null) {
                Pair<String,List<LayoutToken>> headerFeatured = parsers.getHeaderParser().getSectionHeaderFeatured(doc, documentParts);
                String header = headerFeatured.getLeft();
                List<LayoutToken> tokenizationHeader = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                String labeledResult = null;
                if ((header != null) && (header.trim().length() > 0)) {
                    labeledResult = parsers.getHeaderParser().label(header);
                    resHeader = new BiblioItem();
                    resHeader.generalResultMapping(labeledResult, tokenizationHeader);

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
                    if ( (bodytext != null) && (bodytext.trim().length() > 0) ) {               
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
                            if (lastClusterLabel == null || curParagraphTokens == null  || isNewParagraph(lastClusterLabel)) { 
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

            // actual processing of the selected sequences which have been delayed to be processed in groups and
            // take advantage of deep learning batch
            processLayoutTokenSequenceMultiple(selectedLayoutTokenSequences, entities, disambiguate, addParagraphContext);

            // we don't process references (although reference titles could be relevant)
            // acknowledgement? 

            // we can process annexes, uncomment below
            /*documentParts = doc.getDocumentPart(SegmentationLabels.ANNEX);
            if (documentParts != null) {
                processDocumentPart(documentParts, doc, entities);
            }*/

            // footnotes are also relevant? uncomment below
            /*documentParts = doc.getDocumentPart(SegmentationLabel.FOOTNOTE);
            if (documentParts != null) {
                processDocumentPart(documentParts, doc, components);
            }*/

            // propagate the disambiguated entities to the non-disambiguated entities corresponding to the same software name
            for(SoftwareEntity entity1 : entities) {
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
            if (entities.size() > 0) {
                List<String> allRawForms = new ArrayList<String>();
                for (SoftwareEntity entity : entities) {
                    SoftwareComponent softwareComponent = entity.getSoftwareName();
                    String localRawForm = softwareComponent.getRawForm();
                    if (localRawForm.indexOf("-") == -1) {
                        allRawForms.add(localRawForm);
                    }
                }
                for (SoftwareEntity entity : entities) {
                    SoftwareComponent softwareComponent = entity.getSoftwareName();
                    String localRawForm = softwareComponent.getRawForm();
                    if (localRawForm.indexOf("-") != -1) {
                        localRawForm = localRawForm.replaceAll("-( |\\n)*", "");
                        localRawForm = localRawForm.replace("-", "");
                        if (allRawForms.contains(localRawForm)) {
                            softwareComponent.setNormalizedForm(localRawForm);
                        }
                    }
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
                    propagateLayoutTokenSequence(titleTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext);
                } 

                // abstract
                List<LayoutToken> abstractTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_ABSTRACT);
                if (abstractTokens != null) {
                    propagateLayoutTokenSequence(abstractTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext);
                } 

                // keywords
                List<LayoutToken> keywordTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_KEYWORD);
                if (keywordTokens != null) {
                    propagateLayoutTokenSequence(keywordTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext);
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
                        //|| clusterLabel.equals(TaggingLabels.SECTION) {
                        if (lastClusterLabel == null || curParagraphTokens == null  || isNewParagraph(lastClusterLabel)) { 
                            if (curParagraphTokens != null)
                                propagateLayoutTokenSequence(curParagraphTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext);
                            curParagraphTokens = new ArrayList<>();
                        }
                        curParagraphTokens.addAll(localTokenization);
                    }

                    lastClusterLabel = clusterLabel;
                }

                if (curParagraphTokens != null)
                    propagateLayoutTokenSequence(curParagraphTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext);
            }

            // second pass, annex - if relevant, uncomment
            /*documentParts = doc.getDocumentPart(SegmentationLabels.ANNEX);
            if (documentParts != null) {
                List<LayoutToken> tokenizationParts = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                propagateLayoutTokenSequence(tokenizationParts, entities, termProfiles, termPattern, placeTaken, frequencies);
            }*/

            // second pass, footnotes (if relevant, uncomment)
            /*documentParts = doc.getDocumentPart(SegmentationLabel.FOOTNOTE);
            if (documentParts != null) {
                List<LayoutToken> tokenizationParts = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                propagateLayoutTokenSequence(tokenizationParts, entities, termProfiles, termPattern, placeTaken, frequencies);
            }*/            

            // finally we attach and match bibliographical reference callout
            //List<LayoutToken> tokenizations = layoutTokenization.getTokenization();
            TEIFormatter formatter = new TEIFormatter(doc, parsers.getFullTextParser());
            // second pass, body
            if ( (bodyClusters != null) && (resCitations != null) && (resCitations.size() > 0) ) {
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
                                    String refKey = ((Element)refNode).getAttributeValue("target");
                       
                                    if (refKey == null)
                                        continue;

                                    int refKeyVal = -1;
                                    if (refKey.startsWith("#b")) {
                                        refKey = refKey.substring(2, refKey.length());
                                        try {
                                            refKeyVal = Integer.parseInt(refKey);
                                        } catch(Exception e) {
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
                                        biblioComponent.setOffsetEnd(refTokens.get(refTokens.size()-1).getOffset() + 
                                            refTokens.get(refTokens.size()-1).getText().length());
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
                for(SoftwareEntity entity : entities) {
                    if (entity.getBibRefs() != null && entity.getBibRefs().size() > 0) {
                        List<BiblioComponent> bibRefs = entity.getBibRefs();
                        for(BiblioComponent bibRef: bibRefs) {
                            Integer refKeyVal = new Integer(bibRef.getRefKey());
                            if (!consolidated.contains(refKeyVal)) {
                                citationsToConsolidate.add(resCitations.get(refKeyVal));
                                consolidated.add(refKeyVal);
                            }
                        }
                    }
                }

                try {
                    Consolidation consolidator = Consolidation.getInstance();
                    Map<Integer,BiblioItem> resConsolidation = consolidator.consolidate(citationsToConsolidate);
                    for(int i=0; i<citationsToConsolidate.size(); i++) {
                        BiblioItem resCitation = citationsToConsolidate.get(i).getResBib();
                        BiblioItem bibo = resConsolidation.get(i);
                        if (bibo != null) {
                            BiblioItem.correct(resCitation, bibo);
                        }
                    }
                } catch(Exception e) {
                    throw new GrobidException(
                    "An exception occured while running consolidation on bibliographical references.", e);
                } 

                // propagate the bib. ref. to the entities corresponding to the same software name without bib. ref.
                for(SoftwareEntity entity1 : entities) {
                    if (entity1.getBibRefs() != null && entity1.getBibRefs().size() > 0) {
                        for (SoftwareEntity entity2 : entities) {
                            if (entity2.getBibRefs() != null) {
                                continue;
                            }
                            if (entity2.getSoftwareName() != null && 
                                entity2.getSoftwareName().getRawForm().equals(entity1.getSoftwareName().getRawForm())) {
                                List<BiblioComponent> newBibRefs = new ArrayList<>();
                                for(BiblioComponent bibComponent : entity1.getBibRefs()) {
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
            for(SoftwareEntity entity1 : entities) {
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

        } catch (Exception e) {
            e.printStackTrace();
            throw new GrobidException("Cannot process pdf file: " + file.getPath());
        }

        //Collections.sort(entities);
        //return new Pair<List<SoftwareEntity>,Document>(entities, doc);
        return Pair.of(entities, doc);
    }

    /**
     * Process with the software model a segment coming from the segmentation model
     */
    private List<SoftwareEntity> processDocumentPart(SortedSet<DocumentPiece> documentParts, 
                                                  Document doc,
                                                  List<SoftwareEntity> entities,
                                                  boolean disambiguate, 
                                                  boolean addParagraphContext) {
        List<LayoutToken> tokenizationParts = doc.getTokenizationParts(documentParts, doc.getTokenizations());
        return processLayoutTokenSequence(tokenizationParts, entities, disambiguate, addParagraphContext);
    }

    /**
     * Process with the software model a single arbitrary sequence of LayoutToken objects
     */ 
    private List<SoftwareEntity> processLayoutTokenSequence(List<LayoutToken> layoutTokens, 
                                                            List<SoftwareEntity> entities,
                                                            boolean disambiguate,
                                                            boolean addParagraphContext) {
        List<LayoutTokenization> layoutTokenizations = new ArrayList<LayoutTokenization>();
        layoutTokenizations.add(new LayoutTokenization(layoutTokens));
        return processLayoutTokenSequences(layoutTokenizations, entities, disambiguate, addParagraphContext);
    }

    /**
     * Process with the software model a single arbitrary sequence of LayoutToken objects
     */ 
    private List<SoftwareEntity> processLayoutTokenSequenceMultiple(List<List<LayoutToken>> layoutTokenList, 
                                                            List<SoftwareEntity> entities,
                                                            boolean disambiguate, 
                                                            boolean addParagraphContext) {
        List<LayoutTokenization> layoutTokenizations = new ArrayList<LayoutTokenization>();
        for(List<LayoutToken> layoutTokens : layoutTokenList)
            layoutTokenizations.add(new LayoutTokenization(layoutTokens));
        return processLayoutTokenSequences(layoutTokenizations, entities, disambiguate, addParagraphContext);
    }

    /**
     * Process with the software model a set of arbitrary sequence of LayoutTokenization
     */ 
    private List<SoftwareEntity> processLayoutTokenSequences(List<LayoutTokenization> layoutTokenizations, 
                                                  List<SoftwareEntity> entities, 
                                                  boolean disambiguate,
                                                  boolean addParagraphContext) {
        StringBuilder allRess = new StringBuilder();
        for(LayoutTokenization layoutTokenization : layoutTokenizations) {
            List<LayoutToken> layoutTokens = layoutTokenization.getTokenization();
            layoutTokens = SoftwareAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);

            if ( (layoutTokens == null) || (layoutTokens.size() == 0) )
                continue;

            // positions for lexical match
            List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(layoutTokens);
            List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(layoutTokens);

            // string representation of the feature matrix for sequence labeling lib
            String ress = addFeatures(layoutTokens, softwareTokenPositions, urlPositions);
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
        for(LayoutTokenization layoutTokenization : layoutTokenizations) {
            List<LayoutToken> layoutTokens = layoutTokenization.getTokenization();
            layoutTokens = SoftwareAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);

            if ( (layoutTokens == null) || (layoutTokens.size() == 0) )
                continue;

            // text of the selected segment
            String text = LayoutTokensUtil.toText(layoutTokens);

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
                for(SoftwareEntity entity : localEntities) {
                    if (entity.isFiltered()) {
                        indexToBeFiltered.add(new Integer(k));
                    }
                    k++;
                }

                if (indexToBeFiltered.size() > 0) {
                    for(int j=indexToBeFiltered.size()-1; j>= 0; j--) {
                        localEntities.remove(indexToBeFiltered.get(j).intValue());
                    }
                }
            }
            
            // note using dehyphenized text looks nicer, but break entity-level offsets
            // we would need to re-align offsets in a post-processing if we go with 
            // dehyphenized text in the context
            //text = LayoutTokensUtil.normalizeDehyphenizeText(layoutTokens);
            
            localEntities = addContext(localEntities, text, layoutTokens, true, addParagraphContext);

            entities.addAll(localEntities);
        }

        return entities;
    }

    /**
     * Process with the software model a set of arbitrary sequence of LayoutTokenization
     * from tables and figures, where the content is not structured (yet)
     */ 
    private List<SoftwareEntity> processLayoutTokenSequenceTableFigure(List<LayoutToken> layoutTokens, 
                                                  List<SoftwareEntity> entities, 
                                                  boolean disambiguate, 
                                                  boolean addParagraphContext) {
        layoutTokens = SoftwareAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);

        int pos = 0;
        List<LayoutToken> localLayoutTokens = null;
        while(pos < layoutTokens.size()) { 
            while((pos < layoutTokens.size()) && !layoutTokens.get(pos).getText().equals("\n")) {
                if (localLayoutTokens == null)
                    localLayoutTokens = new ArrayList<LayoutToken>();
                localLayoutTokens.add(layoutTokens.get(pos));
                pos++;
            }

            if ( (localLayoutTokens == null) || (localLayoutTokens.size() == 0) ) {
                pos++;
                continue;
            }

            // text of the selected segment
            String text = LayoutTokensUtil.toText(localLayoutTokens);

            // positions for lexical match
            List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(localLayoutTokens);
            List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(localLayoutTokens);

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
                for(SoftwareEntity entity : localEntities) {
                    if (entity.isFiltered()) {
                        indexToBeFiltered.add(new Integer(k));
                    }
                    k++;
                }

                if (indexToBeFiltered.size() > 0) {
                    for(int j=indexToBeFiltered.size()-1; j>= 0; j--) {
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
                                              boolean addParagraphContext) {

        List<OffsetPosition> results = termPattern.matchLayoutToken(layoutTokens, true, true);
        // above: do not ignore delimiters and case sensitive matching
        
        if ( (results == null) || (results.size() == 0) ) {
            return entities;
        }

        List<SoftwareEntity> localEntities = new ArrayList<>();
        for(OffsetPosition position : results) {
            // the match positions are expressed relative to the local layoutTokens index, while the offset at
            // token level are expressed relative to the complete doc positions in characters
            List<LayoutToken> matchedTokens = layoutTokens.subList(position.start, position.end+1);
            
            // we recompute matched position using local tokens (safer than using doc level offsets)
            int matchedPositionStart = 0;
            for(int i=0; i < position.start; i++) {
                LayoutToken theToken = layoutTokens.get(i);
                if (theToken.getText() == null)
                    continue;
                matchedPositionStart += theToken.getText().length();
            }

            String term = LayoutTokensUtil.toText(matchedTokens);
            OffsetPosition matchedPosition = new OffsetPosition(matchedPositionStart, matchedPositionStart+term.length());

            // this positions is expressed at document-level, to check if we have not matched something already recognized
            OffsetPosition rawMatchedPosition = new OffsetPosition(
                matchedTokens.get(0).getOffset(),
                matchedTokens.get(matchedTokens.size()-1).getOffset() + matchedTokens.get(matchedTokens.size()-1).getText().length()
            );

            int termFrequency = 1;
            if (frequencies != null && frequencies.get(term) != null)
                termFrequency = frequencies.get(term);

            // check the tf-idf of the term
            double tfidf = -1.0;
            
            // is the match already present in the entity list? 
            if (overlapsPosition(placeTaken, rawMatchedPosition)) {
                continue;
            }
            if (termProfiles.get(term) != null) {
                tfidf = termFrequency * termProfiles.get(term);
            }

            // ideally we should make a small classifier here with entity frequency, tfidf, disambiguation success and 
            // and/or log-likelyhood/dice coefficient as features - but for the time being we introduce a simple rule
            // with an experimentally defined threshold:
            if ( (tfidf <= 0) || (tfidf > 0.001) ) {
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
            }
        }

        // add context to the new entities
        addContext(localEntities, null, layoutTokens, true, addParagraphContext);

        return entities;
    }

    public static boolean isNewParagraph(TaggingLabel lastClusterLabel) {
        return (!TEIFormatter.MARKER_LABELS.contains(lastClusterLabel) && lastClusterLabel != TaggingLabels.FIGURE
                && lastClusterLabel != TaggingLabels.TABLE);
    }

    private boolean overlapsPosition(final List<OffsetPosition> list, final OffsetPosition position) {
        for (OffsetPosition pos : list) {
            if (pos.start == position.start)  
                return true;
            if (pos.end == position.end)  
                return true;
            if (position.start <= pos.start &&  pos.start <= position.end)  
                return true;
            if (pos.start <= position.start && position.start <= pos.end)  
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
        for(SoftwareComponent component : components) {
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

        for(SoftwareComponent component : components) {
            if (component.getLabel().equals(SoftwareTaggingLabels.SOFTWARE))
                continue;

            while ( (currentEntity != null) && 
                 (component.getOffsetStart() >= currentEntity.getSoftwareName().getOffsetEnd()) ) {
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
                if (dist2 <= dist1*2) {
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
     * Try to attach relevant bib ref component to software entities
     */
    public List<SoftwareEntity> attachRefBib(List<SoftwareEntity> entities, List<BiblioComponent> refBibComponents) {

        // we anchor the process to the software names and aggregate other closest components on the right
        // if we cross a bib ref component we attach it, if a bib ref component is just after the last 
        // component of the entity group, we attach it 
        for(SoftwareEntity entity : entities) {
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

            for(SoftwareComponent theComp : theComps) {
                int localPos = theComp.getOffsetEnd() + shiftOffset;
                if (localPos > endPos)
                    endPos = localPos;
            }

            // find included or just next bib ref callout
            for(BiblioComponent refBib : refBibComponents) {
                if ( (refBib.getOffsetStart() >= pos) &&
                     (refBib.getOffsetStart() <= endPos+5) ) {
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
    public List<SoftwareEntity> filterByRefCallout(List<SoftwareEntity> entities, List<BiblioComponent> refBibComponents) {
        for(BiblioComponent refBib : refBibComponents) {
            for(SoftwareEntity entity : entities) {
                if (entity.getVersion() == null)
                    continue;
                SoftwareComponent version = entity.getVersion();
                if ( (refBib.getOffsetStart() >= version.getOffsetStart()) &&
                     (refBib.getOffsetEnd() <= version.getOffsetEnd()) ) {
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
                FileUtils.writeStringToFile(new File(pathTEI), XmlBuilderUtils.toXml(root));
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
            File path = new File(inputDirectory);
            if (!path.exists()) {
                throw new GrobidException("Cannot create training data because input directory can not be accessed: " + inputDirectory);
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

		FileUtils.writeStringToFile(new File(file.getPath()+".tei.xml"), teiXML);

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
        for(SoftwareEntity entity : entities) {
            SoftwareComponent nameComponent = entity.getSoftwareName();
            if (nameComponent == null)
                continue;
            List<LayoutToken> localTokens = nameComponent.getTokens();
            localPositions.add(new OffsetPosition(localTokens.get(0).getOffset(), 
                localTokens.get(localTokens.size()-1).getOffset() + localTokens.get(localTokens.size()-1).getText().length()-1));

            // we need to add the other component to avoid overlap
            SoftwareComponent versionComponent = entity.getVersion();
            if (versionComponent != null) {
                localTokens = versionComponent.getTokens();
                if (localTokens.size() > 0) {
                    localPositions.add(new OffsetPosition(localTokens.get(0).getOffset(), 
                        localTokens.get(localTokens.size()-1).getOffset() + localTokens.get(localTokens.size()-1).getText().length()-1));
                }
            }
            SoftwareComponent publisherComponent = entity.getCreator();
            if (publisherComponent != null) {
                localTokens = publisherComponent.getTokens();
                if (localTokens.size() > 0) {
                    localPositions.add(new OffsetPosition(localTokens.get(0).getOffset(), 
                        localTokens.get(localTokens.size()-1).getOffset() + localTokens.get(localTokens.size()-1).getText().length()-1));
                }
            }
            SoftwareComponent urlComponent = entity.getSoftwareURL();
            if (urlComponent != null) {
                localTokens = urlComponent.getTokens();
                if (localTokens.size() > 0) {
                    localPositions.add(new OffsetPosition(localTokens.get(0).getOffset(), 
                        localTokens.get(localTokens.size()-1).getOffset() + localTokens.get(localTokens.size()-1).getText().length()-1));
                }
            }
        }
        return localPositions;
    }

    public Map<String, Double> prepareTermProfiles(List<SoftwareEntity> entities) {
        Map<String, Double> result = new TreeMap<String, Double>();

        for(SoftwareEntity entity : entities) {
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
        for(SoftwareEntity entity : entities) {
            SoftwareComponent nameComponent = entity.getSoftwareName();
            if (nameComponent == null)
                continue;
            String term = nameComponent.getRawForm();
            term = term.replace("\n", " ");
            term = term.replaceAll("( )+", " ");

            if (term.trim().length() == 0)
                continue;

            // for safety, we don't propagate something that looks like a stopword with simply an Uppercase first letter
            if (FeatureFactory.getInstance().test_first_capital(term) && 
                !FeatureFactory.getInstance().test_all_capital(term) &&
                SoftwareLexicon.getInstance().isEnglishStopword(term.toLowerCase()) ) {
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
        for(SoftwareEntity entity : entities) {
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
                frequencies.put(term, new Integer(freq));
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
                if (text.trim().length() == 0 ) {
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
                    for(OffsetPosition thePosition : urlPositions) {
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
                                        boolean addParagraphContext) {
        // adjust offsets if tokenization does not start at 0
        int offsetShift = 0;
        if (tokens != null && tokens.size()>0 && tokens.get(0).getOffset() != 0) {
            offsetShift = tokens.get(0).getOffset();
        }

        // we start by segmenting the tokenized text into sentences 

        List<OffsetPosition> forbidden = new ArrayList<OffsetPosition>();
        // fill the position where entities occur to avoid segmenting in the middle of a software string, same for
        // the reference marker positions too
        for(SoftwareEntity entity : entities) {
            SoftwareComponent softwareName = entity.getSoftwareName();
            SoftwareComponent version =  entity.getVersion();
            SoftwareComponent creator =  entity.getCreator();
            SoftwareComponent softwareURL =  entity.getSoftwareURL();
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
                for(BiblioComponent biblioComponent : refMarkers) {
                    forbidden.add(new OffsetPosition(biblioComponent.getOffsetStart()-offsetShift, biblioComponent.getOffsetEnd()-offsetShift));
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
        
        for(SoftwareEntity entity : entities) {
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

                if (startSentence <= startEntity && endEntity <= endSentence) {
                    // set the context as the identified sentence
                    entity.setContext(text.substring(startSentence, endSentence));
               
                    if (fromPDF) {
                        // we relate the entity offset to the context text
                        // update the offsets of the entity components relatively to the context
                        softwareName.setOffsetStart(startEntity - startSentence);
                        softwareName.setOffsetEnd(endEntity - startSentence);

                        /*SoftwareComponent version = entity.getVersion();
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

                        List<BiblioComponent> localBibRefs = entity.getBibRefs();
                        if (localBibRefs != null) {
                            for(BiblioComponent localBibRef : localBibRefs) {
                                localBibRef.setOffsetStart(localBibRef.getOffsetStart() - startSentence - offsetShift);
                                localBibRef.setOffsetEnd(localBibRef.getOffsetEnd() - startSentence - offsetShift);
                            }
                        }*/
                    
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

            if (!clusterLabel.equals(SoftwareTaggingLabels.OTHER)) {

                // conservative check, minimal well-formedness of the content for software name
                if (clusterLabel.equals(SoftwareTaggingLabels.SOFTWARE)) {
                    if (SoftwareAnalyzer.DELIMITERS.indexOf(clusterContent) != -1 || 
                        SoftwareLexicon.getInstance().isEnglishStopword(clusterContent) ) {
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

        return components;
    }

	/**
	 *  Add XML annotations corresponding to components in a piece of text, to be included in
	 *  generated training data.
	 */
    public Element trainingExtraction(List<SoftwareComponent> components, String text, List<LayoutToken> tokenizations) {
        Element p = teiElement("p");

        int pos = 0;
		if ( (components == null) || (components.size() == 0) )
			p.appendChild(text);
        for (SoftwareComponent component : components) {
            Element componentElement = teiElement("rs");

            //if (component.getLabel() != OTHER) 
            {
                componentElement.addAttribute(new Attribute("type", component.getLabel().getLabel()));

                int startE = component.getOffsetStart();
                int endE = component.getOffsetEnd();

				p.appendChild(text.substring(pos, startE));
                componentElement.appendChild(text.substring(startE, endE));
                pos = endE;
            }
            p.appendChild(componentElement);
        }
        p.appendChild(text.substring(pos, text.length()));

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

            catRef.addAttribute(new Attribute("target", null, "#"+catCuration));

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
    public List<SoftwareEntity> processXML(File file, 
                                           boolean disambiguate, 
                                           boolean addParagraphContext) throws IOException {
        List<SoftwareEntity> entities = null;
        try {
            String tei = processXML(file);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            //tei = avoidDomParserAttributeBug(tei);

            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(tei)));
            //document.getDocumentElement().normalize();
            
            entities = processTEIDocument(document, disambiguate, addParagraphContext);
            
            //tei = restoreDomParserAttributeBug(tei); 

        } catch (final Exception exp) {
            logger.error("An error occured while processing the following XML file: "
                + file.getPath(), exp);
        } 

        return entities;
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
    public List<SoftwareEntity> processTEIDocument(org.w3c.dom.Document doc, boolean disambiguate, boolean addParagraphContext) { 
        List<SoftwareEntity> entities = new ArrayList<SoftwareEntity>();

        List<List<LayoutToken>> selectedLayoutTokenSequences = new ArrayList<>();
        List<LayoutToken> docLayoutTokens = new ArrayList<>();

        org.w3c.dom.NodeList paragraphList = doc.getElementsByTagName("p");
        for (int i = 0; i < paragraphList.getLength(); i++) {
            org.w3c.dom.Element paragraphElement = (org.w3c.dom.Element) paragraphList.item(i);

            // check that the father is not <abstract> and not <figDesc>
            org.w3c.dom.Node fatherNode = paragraphElement.getParentNode();
            if (fatherNode != null) {
                if ("availability".equals(fatherNode.getNodeName()))
                    continue;
            }

            String contentText = UnicodeUtil.normaliseText(XMLUtilities.getTextNoRefMarkers(paragraphElement));
            if (contentText != null && contentText.length()>0) {
                List<LayoutToken> paragraphTokens = 
                    SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(contentText);
                if (paragraphTokens != null && paragraphTokens.size() > 0) {
                    docLayoutTokens.addAll(paragraphTokens);
                    selectedLayoutTokenSequences.add(paragraphTokens);
                }
            }
        }

        processLayoutTokenSequenceMultiple(selectedLayoutTokenSequences, entities, disambiguate, addParagraphContext);

        // propagate the disambiguated entities to the non-disambiguated entities corresponding to the same software name
        for(SoftwareEntity entity1 : entities) {
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

        for (int i = 0; i < paragraphList.getLength(); i++) {
            org.w3c.dom.Element paragraphElement = (org.w3c.dom.Element) paragraphList.item(i);

            // check that the father is not <abstract> and not <figDesc>
            org.w3c.dom.Node fatherNode = paragraphElement.getParentNode();
            if (fatherNode != null) {
                if ("availability".equals(fatherNode.getNodeName()))
                    continue;
            }

            String contentText = UnicodeUtil.normaliseText(XMLUtilities.getTextNoRefMarkers(paragraphElement));
            if (contentText != null && contentText.length()>0) {
                List<LayoutToken> paragraphTokens = 
                    SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(contentText);
                if (paragraphTokens != null && paragraphTokens.size() > 0) {
                    propagateLayoutTokenSequence(paragraphTokens, entities, termProfiles, termPattern, placeTaken, frequencies, addParagraphContext);
                }
            }
        }
        
        // propagate the non-disambiguated entities attributes to the new propagated entities corresponding 
        // to the same software name
        for(SoftwareEntity entity1 : entities) {
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

        return entities;
    }
}
