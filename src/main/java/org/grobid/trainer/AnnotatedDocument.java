package org.grobid.trainer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * POJO for annotated document, filled by parsing original softcite dataset, with JSON serialization. 
 *
 * @author Patrice
 */
public class AnnotatedDocument {
    private static final Logger logger = LoggerFactory.getLogger(AnnotatedDocument.class);

    private String documentID = null;

    /* This is the "corpus" the document belongs to, so pmc_article or econ_article */
    // TODO move that to an enumerated type for safety
    private String articleSet = null;

    /** 
     * This is a representation of the softcate annotation, capturing the particular data scheme of the dataset.
     * Note that the annotations are not necessary located/aligned with the PDF content: for this the whole PDF
     * fulltext parsing and mention/centext alignment need to be run.  
     **/
    private List<SoftciteAnnotation> annotations = null;

    /** 
     * Representation of inline text annotations for the complete document, aligned with PDF content and 
     * derived from the softcite annotations. To be used for mixed content XML training data generation. 
     */
    private List<Annotation> inlineAnnotations = null;

    // this is the download url for the document 
    private String url = null;

    //@JsonIgnore

    public String getDocumentID() {
        return this.documentID;
    }

    public void setDocumentID(String documentID) {
        this.documentID = documentID;
    }

    public String getArticleSet() {
        return this.articleSet;
    }

    public void setArticleSet(String articleSet) {
        this.articleSet = articleSet;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<SoftciteAnnotation> getAnnotations() {
        return this.annotations;
    }

    public void setAnnotations(List<SoftciteAnnotation> annotations) {
        this.annotations = annotations;
    }

    public void addAnnotation(SoftciteAnnotation annotation) {
        if (annotations == null) {
            annotations = new ArrayList<SoftciteAnnotation>();
        }
        annotations.add(annotation);
    }

    public List<Annotation> getInlineAnnotations() {
        return this.inlineAnnotations;
    }

    public void setInlineAnnotations(List<Annotation> inlineAnnotations) {
        this.inlineAnnotations = inlineAnnotations;
    }

    public void addInlineAnnotation(Annotation inlineAnnotation) {
        if (inlineAnnotations == null) {
            inlineAnnotations = new ArrayList<Annotation>();
        }
        inlineAnnotations.add(inlineAnnotation);
    }

    public String toJSON() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        mapper.enable(JsonParser.Feature.IGNORE_UNDEFINED);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);

        String json = null;
        try {
            json = mapper.writeValueAsString(this);

        } catch (IOException e) {
            logger.error("failed JSON serialization", e);
        }
        return json;
    }
}