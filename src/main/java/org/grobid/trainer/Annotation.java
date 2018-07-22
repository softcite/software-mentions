package org.grobid.trainer;

import java.util.*;

import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.OffsetPosition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 
 * POJO for generic annotation used for mixed content XML serialization. 
 *
 * @author Patrice
 */
public class Annotation implements Comparable<Annotation> {

    private static final Logger logger = LoggerFactory.getLogger(Annotation.class);

    // Gives the actual occurence of the annotation in the PDF document as offset relatively to 
    // the LayoutToken of the whole document.
    // These LayoutTokens provide the exact position in the document with coordinates and so on.
    @JsonIgnore
    protected OffsetPosition occurence = null;

    private String text = null;

    /**
     *  Storing attribute value pairs, only one value per attribute.
     */
    private Map<String, String> attributes = null;

    /**
     *  Offset relatively of sequence of LayoutToken
     */
    public OffsetPosition getOccurence() {
        return this.occurence;
    }

    /**
     *  Offset relatively of sequence of LayoutToken
     */
    public void setOccurence(OffsetPosition occurence) {
        this.occurence = occurence;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public String getAttributeValue(String attribute) {
        if (attributes == null)
            return null;
        else
            return attributes.get(attributes);
    }

    public void addAttributeValue(String attribute, String value) {
        if (attributes == null) {
            // TreeMap to keep attributes sorted
            attributes = new TreeMap<String, String>();
        }
        attributes.put(attribute, value);
    }

    @Override
    public int compareTo(Annotation annotation) {
        OffsetPosition pos = annotation.getOccurence();
        if (pos == null && this.occurence == null)
            return 0;
        if (pos == null)
            return 1;
        if (this.occurence == null)
            return -1;
        if (pos.start < this.occurence.start)
            return 1;
        else if (pos.start == this.occurence.start) {
            if (pos.end < this.occurence.end)
                return 1;
            else if (pos.end == this.occurence.end)
                return 0;
            else 
                return -1;
        } else 
            return -1;
    }
}