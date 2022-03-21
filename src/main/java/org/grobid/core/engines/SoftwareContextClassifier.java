package org.grobid.core.engines;

import java.util.*;

import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.utilities.*;
import org.grobid.core.jni.PythonEnvironmentConfig;
import org.grobid.core.jni.DeLFTClassifierModel;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.data.SoftwareContextAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

/**
 * Use a Deep Learning multiclass and multilabel classifier to characterize the context of a recognized software mention. 
 * This classifier predicts if the software introduced by a software mention in a sentence is likely:
 * - used or not by the described work (class used)
 * - a contribution of the described work (class contribution)
 * - shared (class shared)
 *
 * The prediction uses the sentence where the mention appears (sentence is context here).
 * Then given n mentions of the same software in a document, we have n predictions and we can derived from this
 * the nature of the software mention at document level. 
 */
public class SoftwareContextClassifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(SoftwareContextClassifier.class);

    private DeLFTClassifierModel classifier = null;
    private SoftwareConfiguration softwareConfiguration;
    private JsonParser parser;

    private static volatile SoftwareContextClassifier instance;
    

    public static SoftwareContextClassifier getInstance(SoftwareConfiguration configuration) {
        if (instance == null) {
            getNewInstance(configuration);
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance(SoftwareConfiguration configuration) {
        instance = new SoftwareContextClassifier(configuration);
    }

    private SoftwareContextClassifier(SoftwareConfiguration configuration) {
        ModelParameters parameter = configuration.getModel("software_context");
        this.classifier = new DeLFTClassifierModel("software_context", parameter.delft.architecture);
    }

    /**
     * Classify a simple piece of text
     * @return list of predicted labels/scores pairs
     */
    public String classify(String text) throws Exception {
        if (StringUtils.isEmpty(text))
            return null;
        List<String> texts = new ArrayList<String>();
        texts.add(text);
        return classify(texts);
    }

    /**
     * Classify an array of texts
     * @return list of predicted labels/scores pairs for each text
     */
    public String classify(List<String> texts) throws Exception {
        if (texts == null || texts.size() == 0)
            return null;

        LOGGER.info("classify: " + texts.size() + " sentence(s)");
        String the_json = this.classifier.classify(texts);
        //System.out.println(the_json);

        return the_json;
    }

    /**
     * Process the contexts of a set of entities identified in a document. Each context is
     * classified and a global decision is realized at document-level using all the mentioned 
     * contexts corresponding to the same software.  
     * 
     **/
    public List<SoftwareEntity> classifyDocumentContexts(List<SoftwareEntity> entities) {
        List<String> contexts = new ArrayList<>();
        for(SoftwareEntity entity : entities) {
            if (entity.getContext() != null && entity.getContext().length()>0) {
                String localContext = TextUtilities.dehyphenize(entity.getContext());
                localContext = localContext.replace("\n", " ");
                localContext = localContext.replaceAll("( )+", " ");
                contexts.add(localContext);
            } else {
                // dummy place holder
                contexts.add("");
            }
        }

        String results = null;
        try {
            results = classify(contexts);
        } catch(Exception e) {
            LOGGER.error("fail to classify document's set of contexts", e);
            return entities;
        }

        if (results == null) 
            return entities;

        // set resulting context classes to entity mentions
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(results);

            int entityRank =0;
            String lang = null;
            JsonNode classificationsNode = root.findPath("classifications");
            if ((classificationsNode != null) && (!classificationsNode.isMissingNode())) {
                Iterator<JsonNode> ite = classificationsNode.elements();
                while (ite.hasNext()) {
                    JsonNode classificationNode = ite.next();

                    JsonNode usedNode = classificationNode.findPath("used");
                    JsonNode createdNode = classificationNode.findPath("creation");
                    JsonNode sharedNode = classificationNode.findPath("shared");
                    JsonNode textNode = classificationNode.findPath("text");

                    double scoreUsed = 0.0;
                    if ((usedNode != null) && (!usedNode.isMissingNode())) {
                        scoreUsed = usedNode.doubleValue();
                    }

                    double scoreCreated = 0.0;
                    if ((createdNode != null) && (!createdNode.isMissingNode())) {
                        scoreCreated = createdNode.doubleValue();
                    }

                    double scoreShared = 0.0;
                    if ((sharedNode != null) && (!sharedNode.isMissingNode())) {
                        scoreShared = sharedNode.doubleValue();
                    }

                    String textValue = null;
                    if ((textNode != null) && (!textNode.isMissingNode())) {
                        textValue = textNode.textValue();
                    }
                    
                    SoftwareContextAttributes contextAttributes = new SoftwareContextAttributes();
                    contextAttributes.setUsedScore(scoreUsed);
                    contextAttributes.setCreatedScore(scoreCreated);
                    contextAttributes.setSharedScore(scoreShared);

                    if (scoreUsed>0.5) {
                        contextAttributes.setUsed(true);
                        if (scoreCreated > 0.5) {
                            contextAttributes.setCreated(true);
                            if (scoreShared > 0.5) {
                                contextAttributes.setShared(true);
                            } else {
                                contextAttributes.setShared(false);
                            }
                        } else {
                            contextAttributes.setCreated(false);
                            contextAttributes.setShared(false);
                        }
                    } else {
                        contextAttributes.setUsed(false);
                        contextAttributes.setCreated(false);
                        contextAttributes.setShared(false);
                    }

                    SoftwareEntity entity = entities.get(entityRank);
                    entity.setMentionContextAttributes(contextAttributes);

                    entityRank++;
                }
            }
        } catch(JsonProcessingException e) {
            LOGGER.error("failed to parse JSON context classification result", e);
        }

        // in a second pass, we share all predictions for mentions of the same software name in 
        // different places and apply a consistency propagation
        Map<String, List<SoftwareEntity>> entityMap = new TreeMap<>();
        for(SoftwareEntity entity : entities) {
            String softwareName = entity.getSoftwareName().getNormalizedForm();
            List<SoftwareEntity> localList = entityMap.get(softwareName);
            if (localList == null) {
                localList = new ArrayList<>();
            } 
            localList.add(entity);
            entityMap.put(softwareName, localList);
        }

        for (Map.Entry<String, List<SoftwareEntity>> entry : entityMap.entrySet()) {
            int is_used = 0;
            double best_used = 0.0;        
            int is_created = 0;
            double best_created = 0.0;
            int is_shared = 0;
            double best_shared = 0.0;
            for(SoftwareEntity entity : entry.getValue()) {
                SoftwareContextAttributes localContextAttributes = entity.getMentionContextAttributes();
                if (localContextAttributes.getUsed()) {
                    is_used++;
                    if (localContextAttributes.getUsedScore() > best_used)
                        best_used = localContextAttributes.getUsedScore();
                }
                if (localContextAttributes.getCreated()) {
                    is_created++;
                    if (localContextAttributes.getCreatedScore() > 0)
                        best_created = localContextAttributes.getCreatedScore();
                }
                if (localContextAttributes.getShared()) {
                    is_shared++;
                    if (localContextAttributes.getSharedScore() > 0)
                        best_shared = localContextAttributes.getSharedScore();
                }
            }

            SoftwareContextAttributes globalContextAttributes = new SoftwareContextAttributes();
            globalContextAttributes.init();
            if (is_used > 0) {
                globalContextAttributes.setUsed(true);
                globalContextAttributes.setUsedScore(best_used);
                if (is_created > 0) {
                    globalContextAttributes.setCreated(true);
                    globalContextAttributes.setCreatedScore(best_created);
                    if (is_shared > 0) {
                        globalContextAttributes.setShared(true);
                        globalContextAttributes.setSharedScore(best_shared);
                    }
                }
            }

            for(SoftwareEntity entity : entry.getValue()) {
                entity.setDocumentContextAttributes(globalContextAttributes);
            }
        }

        return entities;
    }

}