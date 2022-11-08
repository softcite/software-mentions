package org.grobid.core.data;

import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.core.lexicon.SoftwareLexicon;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.engines.label.SoftwareTaggingLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.io.JsonStringEncoder;

import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Representation of the type prediction of a software entity.
 *
 */
public class SoftwareType implements Comparable<SoftwareType> {   
    private static final Logger logger = LoggerFactory.getLogger(SoftwareType.class);
    
    // type of the entity
    private SoftwareLexicon.Software_Type type = null;

    // surface form of the component as it appears in the source document
    protected String rawForm = null;
    
    // list of layout tokens corresponding to the component mention in the source document
    protected List<LayoutToken> tokens = null;

    // relative offset positions in context, if defined and expressed as (Java) character offset
    protected OffsetPosition offsets = null;

    // tagging label of the LayoutToken cluster corresponding to the component
    protected TaggingLabel label = null;

    // a status flag indicating that the corresponding component/entity was filtered 
    protected boolean filtered = false;

    // optional bounding box in the source document
    protected List<BoundingBox> boundingBoxes = null;

    public SoftwareType() {
        this.offsets = new OffsetPosition();
    }
    
    public SoftwareType(String rawForm) {
        this.rawForm = rawForm;
        this.offsets = new OffsetPosition();
    }

    /**
     * This is a deep copy of a component, excluding layout tokens, offset and bounding boxes information.
     * The usage is for propagation of the component information to entities in other position.
     */
    public SoftwareType(SoftwareType ent) {
        this.rawForm = ent.rawForm;
        this.label = ent.label;
        this.filtered = ent.filtered;
    }

    public SoftwareLexicon.Software_Type getType() {
        return type;
    }
    
    public void setType(SoftwareLexicon.Software_Type theType) {
        type = theType;
    }

    public String getRawForm() {
        return rawForm;
    }
    
    public void setRawForm(String raw) {
        this.rawForm = raw;
    }

    public OffsetPosition getOffsets() {
        return offsets;
    }
    
    public void setOffsets(OffsetPosition offsets) {
        this.offsets = offsets;
    }
    
    public void setOffsetStart(int start) {
        offsets.start = start;
    }

    public int getOffsetStart() {
        return offsets.start;
    }

    public void setOffsetEnd(int end) {
        offsets.end = end;
    }

    public int getOffsetEnd() {
        return offsets.end;
    }

    public List<BoundingBox> getBoundingBoxes() {
        return boundingBoxes;
    }

    public void setBoundingBoxes(List<BoundingBox> boundingBoxes) {
        this.boundingBoxes = boundingBoxes;
    }
    
    public List<LayoutToken> getTokens() {
        return this.tokens;
    }
    
    public void setTokens(List<LayoutToken> tokens) {
        this.tokens = tokens;
    }
    
    public TaggingLabel getLabel() {
        return label;
    }

    public void setLabel(TaggingLabel label) {
        this.label = label;
    }

    public boolean isFiltered() {
        return filtered;
    }

    public void setFiltered(boolean filtered) {
        this.filtered = filtered;
    } 
    
    @Override
    public boolean equals(Object object) {
        boolean result = false;
        if ( (object != null) && object instanceof SoftwareType) {
            int start = ((SoftwareType)object).getOffsetStart();
            int end = ((SoftwareType)object).getOffsetEnd();
            if ( (start == offsets.start) && (end == offsets.end) ) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public int compareTo(SoftwareType theEntity) {
        int start = theEntity.getOffsetStart();
        int end = theEntity.getOffsetEnd();
        
        if (offsets.start != start) 
            return offsets.start - start;
        else 
            return offsets.end - end;
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("raw form: ").append(rawForm).append("\n");
        buffer.append("offsets: ").append(offsets.start).append(" ").append(offsets.end).append("\n");
        buffer.append("type: ").append(type.getName()).append("\n");
        if (label != null)
            buffer.append("label: ").append(label.getLabel().toString()).append("\n");
        return buffer.toString();
    }

}