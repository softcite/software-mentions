package org.grobid.trainer;

import java.util.*;

import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.OffsetPosition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 
 * POJO for softcite annotation, filled by parsing original softcite dataset, with JSON serialization. 
 *
 * @author Patrice
 */
public class SoftciteAnnotation extends Annotation {
    private static final Logger logger = LoggerFactory.getLogger(SoftciteAnnotation.class);

    enum AnnotationType {
        SOFTWARE, ALGORITHM, DATABASE, HARDWARE, REFERENCE, OTHER;
    }

    enum ReferenceType {
        PUBLICATION, PROJECT_PAGE, PROJECT_NAME, USER_GUIDE;
    }

    private String identifier = null;

    private String annotatorID = null;

    private AnnotationType type = null;

    private String softwareMention = null;

    private String context = null;

    private String creator = null;

    private boolean isUsed = false;

    private int certainty = -1;

    private String memo = null;

    private String referenceString = null;

    // in case we have a bibliographical reference, the following indicate the identifier
    // of the mention annotation to attach to this bibliographical reference
    private String referedAnnotationMention = null;

    // page as provided by the original dataset
    private int page = -1;

    private ReferenceType refType = null;

    private String versionNumber = null;

    private String versionDate = null;

    private String url = null;

    // provided article identifier, e.g PMC identifier
    //private String articleID = null;

    // Gives the actual occurence of the annotation in the PDF document as offset relatively to 
    // the LayoutToken of the whole document.
    // These LayoutTokens provide the exact position in the document with coordinates and so on.
    //@JsonIgnore
    //private OffsetPosition occurence = null;

    public String getIdentifier() {
        return this.identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getAnnotatorID() {
        return this.annotatorID;
    }

    public void setAnnotatorID(String annotatorID) {
        this.annotatorID = annotatorID;
    }

    public String getField(String field) {
        switch (field) {
            case "software":
                return this.softwareMention;
            case "version-date":
                return this.versionDate;
            case "version-number":
                return this.versionNumber;
            case "creator":
                return this.creator;
            case "url":
                return this.url;
            case "quote":
                return this.context;
        }
        return null;
    }

    public AnnotationType getType() {
        return this.type;
    }

    public void setType(AnnotationType type) {
        this.type = type;
    }

    public void setType(String typeString) {
        if (typeString.equals("software"))
            this.type = AnnotationType.SOFTWARE;
        else if (typeString.equals("reference"))
            this.type = AnnotationType.REFERENCE;
        else if (typeString.equals("algorithm"))
            this.type = AnnotationType.ALGORITHM;
        else if (typeString.equals("hardware"))
            this.type = AnnotationType.HARDWARE;
        /*else if (typeString.equals("database"))
            this.type = AnnotationType.DATABASE;*/
        else if (typeString.equals("other"))
            this.type = AnnotationType.OTHER;
        else
            logger.warn("Unexpected annotation type: " + typeString);
    }

    public String getSoftwareMention() {
        return this.softwareMention;
    }

    public void setSoftwareMention(String softwareMention) {
        this.softwareMention = softwareMention;
    }

    public String getContext() {
        return this.context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getCreator() {
        return this.creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public boolean getIsUsed() {
        return this.isUsed;
    }

    public void setIsUsed(boolean isUsed) {
        this.isUsed = isUsed;
    }

    public int getCertainty() {
        return this.certainty;
    }

    public void setCertainty(int certainty) {
        this.certainty = certainty;
    }

    public String getMemo() {
        return this.memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public String getReferenceString() {
        return this.referenceString;
    }

    public void setReferenceString(String referenceString) {
        this.referenceString = referenceString;
    }

    public String getReferedAnnotationMention() {
        return this.referedAnnotationMention;
    }

    public void setReferedAnnotationMention(String referedAnnotationMention) {
        this.referedAnnotationMention = referedAnnotationMention;
    }

    public int getPage() {
        return this.page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public ReferenceType getRefType() {
        return this.refType;
    }

    public void setRefType(ReferenceType refType) {
        this.refType = refType;
    }

    public void setRefType(String refTypeString) {
        if (refTypeString.equals("publication"))
           this.refType = ReferenceType.PUBLICATION;
        else if (refTypeString.equals("project_page") || refTypeString.equals("project page"))
           this.refType = ReferenceType.PROJECT_PAGE;
        else if (refTypeString.equals("project_name") || refTypeString.equals("project name"))
           this.refType = ReferenceType.PROJECT_NAME;
        else if (refTypeString.equals("user_guide"))
           this.refType = ReferenceType.USER_GUIDE;
        else
            logger.warn("Unexpected reference type: " + refTypeString);
    }

    public String getVersionNumber() {
        return this.versionNumber;
    }

    public void setVersionNumber(String versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getVersionDate() {
        return this.versionDate;
    }

    public void setVersionDate(String versionDate) {
        this.versionDate = versionDate;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
