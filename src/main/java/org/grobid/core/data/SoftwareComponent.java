package org.grobid.core.data;

import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.lexicon.SoftwareLexicon;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 *  Representation of a mention of a component corresponding to a software description.
 *  The component can be the software name, the version number or date, the software creator,
 *  or the software URL.  
 *
 */
public class SoftwareComponent implements Comparable<SoftwareComponent> {   
	
	// Orign of the component definition
	public enum Origin {
		GROBID	("grobid"),
		USER	("user");
		
		private String name;

		private Origin(String name) {
          	this.name = name;
		}

		public String getName() {
			return name;
		}
	};
	
	// surface form of the component as it appears in the source document
	private String rawForm = null;
	
	// list of layout tokens corresponding to the component mention in the source document
	private List<LayoutToken> tokens = null;
	
	// normalized form of the component
    private String normalizedForm = null;
	
	// type of the component
	private SoftwareLexicon.Software_Type type = null;
	
	// relative offset positions in context, if defined
	private OffsetPosition offsets = null;
	
	// confidence score of the component in context, if defined
	private double conf = 0.8;
	
	// optional bounding box in the source document
	private List<BoundingBox> boundingBoxes = null;
		
	// orign of the component definition
	private Origin origin = Origin.GROBID;
	
	// tagging label of the LayoutToken cluster corresponding to the component
	private TaggingLabel label = null;

    public SoftwareComponent() {
		this.offsets = new OffsetPosition();
    }
	
	public SoftwareComponent(String rawForm) {
        this.rawForm = rawForm;
		this.offsets = new OffsetPosition();
    }

	public SoftwareComponent(SoftwareComponent ent) {
		rawForm = ent.rawForm;
		normalizedForm = ent.normalizedForm;
		type = ent.type;
		offsets = ent.offsets;
		conf = ent.conf;
		origin = ent.origin;
	}

    public String getRawForm() {
        return rawForm;
    }
	
	public void setRawForm(String raw) {
        this.rawForm = raw;
    }

	public String getNormalizedForm() {
        return normalizedForm;
    }
	
	public void setNormalizedForm(String normalized) {
        this.normalizedForm = normalized;
    }

	public SoftwareLexicon.Software_Type getType() {
		return type;
	}
	
	public void setType(SoftwareLexicon.Software_Type theType) {
		type = theType;
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
	
	public double getConf() {
		return this.conf;
	}
	
	public void setConf(double conf) {
		this.conf = conf;
	}
	
	public Origin getOrigin() {
		return origin;
	}
	
	public void setOrigin(Origin origin) {
		this.origin = origin;
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

	public void normalize() {
		// TBD is necessary
	}
	
	@Override
	public boolean equals(Object object) {
		boolean result = false;
		if ( (object != null) && object instanceof SoftwareComponent) {
			int start = ((SoftwareComponent)object).getOffsetStart();
			int end = ((SoftwareComponent)object).getOffsetEnd();
			if ( (start == offsets.start) && (end == offsets.end) ) {
				result = true;
			}
		}
		return result;
	}

	@Override
	public int compareTo(SoftwareComponent theEntity) {
		int start = theEntity.getOffsetStart();
		int end = theEntity.getOffsetEnd();
		
		if (offsets.start != start) 
			return offsets.start - start;
		else 
			return offsets.end - end;
	}
	
	public String toJson() {
		ObjectMapper mapper = new ObjectMapper();
		
		StringBuffer buffer = new StringBuffer();
		buffer.append("{ ");
		try {
			buffer.append("\"rawForm\" : " + mapper.writeValueAsString(rawForm));
		} catch (JsonProcessingException e) {
			buffer.append("\"rawForm\" : \"" + "JsonProcessingException" + "\"");
		}
		if (normalizedForm != null)
			buffer.append(", \"normalizedForm\" : \"" + normalizedForm + "\"");
		if (type != null)
			buffer.append(", \"type\" : \"" + type.getName() + "\"");	
		if (label != null) {
			String componentType = label.getLabel();
			componentType = componentType.replace("<", "");
			componentType = componentType.replace(">", "");
			buffer.append(", \"component-type\" : \"" + componentType + "\"");	
		}

		buffer.append(", \"offsetStart\" : " + offsets.start);
		buffer.append(", \"offsetEnd\" : " + offsets.end);	
		
		buffer.append(", \"conf\" : \"" + conf + "\"");
		
		if ( (boundingBoxes != null) && (boundingBoxes.size() > 0) ) {
			buffer.append(", \"boundingBoxes\" : [");
			boolean first = true;
			for (BoundingBox box : boundingBoxes) {
				if (first)
					first = false;
				else
					buffer.append(",");
				buffer.append("{").append(box.toJson()).append("}");
			}
			buffer.append("] ");
		}
		
		buffer.append(" }");
		return buffer.toString();
	}
	
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        if (rawForm != null) {
			buffer.append(rawForm + "\t");
		}
		if (normalizedForm != null) {
			buffer.append(normalizedForm + "\t");
		}
		if (type != null) {
			buffer.append(type + "\t");	
		}
		//if (entityId != null)
		//	buffer.append(entityId + "\t");	

		if (offsets != null) {
			buffer.append(offsets.toString() + "\t");
		}

		if ( (boundingBoxes != null) && (boundingBoxes.size()>0) ) {
			for(BoundingBox box : boundingBoxes) {
				buffer.append(box.toString() + "\t");
			}
		}

        return buffer.toString();
    }
	
	/** 
	 * Export of entity annotation in TEI standoff format 
	 */	 
	public String toTEI(String id, int n) {
		StringBuffer buffer = new StringBuffer();
		// tbd if necessary
		return buffer.toString();
	}
}
