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
 *  Representation of a mention of a software entity with all its components.
 *
 */
public class SoftwareEntity extends KnowledgeEntity implements Comparable<SoftwareEntity> {   
	private static final Logger logger = LoggerFactory.getLogger(SoftwareEntity.class);
	
	// type of the entity
	private SoftwareLexicon.Software_Type type = null;
	
	// component of the entity
	private SoftwareComponent softwareName = null;
	private SoftwareComponent version = null;
	private SoftwareComponent creator = null;
	private SoftwareComponent softwareURL = null;

	// one or several bibliographical references attached to the software entity
	private List<BiblioComponent> bibRefs = null;

	// Entity identifier if the mention has been solved/disambiguated against the
	// knowledge base of software entities. The identifier is the unique 
	// identifier of the entity in this KB.
	private String entityId = null;

	// filtered means entity disambiguation resulted in diacarding the entity candidate
	private boolean filtered = false;
	// propagated means entity is coming from the document level propagation step 
	private boolean propagated = false;

	// the text context where the entity takes place - typically a snippet with the 
	// sentence including the mention
	private String context = null;
	private int contextOffset = -1;

	// full paragraph context where the entity takes place, this is an optional field
	// relevant for certain scenarios only
	private String paragraph = null;

	public SoftwareLexicon.Software_Type getType() {
		return type;
	}
	
	public void setType(SoftwareLexicon.Software_Type theType) {
		type = theType;
	}

	public SoftwareComponent getSoftwareName() {
		return this.softwareName;
	}

	public void setSoftwareName(SoftwareComponent softwareName) {
		this.softwareName = softwareName;
	}

	public SoftwareComponent getVersion() {
		return this.version;
	}

	public void setVersion(SoftwareComponent version) {
		this.version = version;
	}

	public SoftwareComponent getCreator() {
		return this.creator;
	}

	public void setCreator(SoftwareComponent creator) {
		this.creator = creator;
	}

	public SoftwareComponent getSoftwareURL() {
		return this.softwareURL;
	}

	public void setSoftwareURL(SoftwareComponent softwareURL) {
		this.softwareURL = softwareURL;
	}

	public List<BiblioComponent> getBibRefs() {
		return this.bibRefs;
	}

	public void setBibRefs(List<BiblioComponent> bibRefs) {
		this.bibRefs = bibRefs;
	}

	public void addBibRef(BiblioComponent bibRef) {
		if (bibRefs == null) {
			bibRefs = new ArrayList<BiblioComponent>();
		}
		bibRefs.add(bibRef);
	}

	public String getEntityId() {
		return entityId;
	}
	
	public void setEntityId(String id) {
		this.entityId = id;
	}

	public boolean isFiltered() {
		return filtered;
	}

	public void setFiltered(boolean filtered) {
		this.filtered = filtered;
	} 

	public boolean isPropagated() {
		return propagated;
	}

	public void setPropagated(boolean propagated) {
		this.propagated = propagated;
	}

	public void setContext(String context) {
		this.context = context;
	}

	public String getContext() {
		return this.context;
	}

	public void setContextOffset(int contextOffset) {
		this.contextOffset = contextOffset;
	}

	public int getContextOffset() {
		return this.contextOffset;
	}

	public void setParagraph(String paragraph) {
		this.paragraph = paragraph;
	}

	public String getParagraph() {
		return this.paragraph;
	}

	/**
	 * Assuming that software names are identical, this method merges the attributes
	 * of the two entities.    
	 */
	public static void merge(SoftwareEntity entity1, SoftwareEntity entity2) {

		if (entity1.getVersion() == null)
			entity1.setVersion(entity2.getVersion());
		else if (entity2.getVersion() == null)
			entity2.setVersion(entity1.getVersion());

		if (entity1.getCreator() == null)
			entity1.setCreator(entity2.getCreator());
		else if (entity2.getCreator() == null)
			entity2.setCreator(entity1.getCreator());

		if (entity1.getSoftwareURL() == null)
			entity1.setSoftwareURL(entity2.getSoftwareURL());
		else if (entity2.getSoftwareURL() == null)
			entity2.setSoftwareURL(entity1.getSoftwareURL());

		if (entity1.getBibRefs() == null)
			entity1.setBibRefs(entity2.getBibRefs());
		else if (entity2.getBibRefs() == null)
			entity2.setBibRefs(entity1.getBibRefs());
	}

	/**
	 * Check if a component corresponding to a given label is already present in a software entity.
	 * Bibliographical references are ignored because they can be accumulated to the same entity.
	 * SOFTWARE labels are ignored because they anchor the process of attaching components. 
	 */
	public boolean freeField(TaggingLabel label) {
		if (label.equals(SoftwareTaggingLabels.SOFTWARE_URL) && (this.softwareURL != null)) {
			return false;
		} else if (label.equals(SoftwareTaggingLabels.CREATOR) && (this.creator != null)) {
			return false;
		} else if (label.equals(SoftwareTaggingLabels.VERSION) && (this.version != null)) {
			return false;
		} 
		return true;
	}

	/**
	 * In case of duplicated field, check if the one in parameter is better quality than the 
	 * existing object instance one. 
	 *
	 * If we have competing version fields, the closest to the software name will be chosen. 
	 */
	public boolean betterField(SoftwareComponent component) {
		if (this.version != null && 
			component.getLabel().equals(SoftwareTaggingLabels.VERSION) &&
			component.getNormalizedForm() != null &&
			this.version.getNormalizedForm() != null) {

			// check positions with software name component 
			int softwareNameStart = this.softwareName.getOffsetStart();
			int softwareNameEnd = this.softwareName.getOffsetEnd();

			// parameter candidate version component
			int componentStart = component.getOffsetStart();
			int componentEnd = component.getOffsetEnd();

			// distance with software name
			int distanceComponent = componentEnd - softwareNameEnd;

			// current version component
			int currentComponentStart = this.version.getOffsetStart();
			int currentComponentEnd = this.version.getOffsetEnd();

			// distance with software name
			int distanceCurrent = currentComponentEnd - softwareNameEnd;

			if (distanceComponent < 0 && distanceCurrent > 0) {
				return false;
			} else if (distanceComponent > 0 && distanceCurrent < 0) {
				return true;
			} else if (distanceComponent < 0 && distanceCurrent < 0) {
				return true;
			} else if (distanceComponent > distanceCurrent) {
				return false;
			}

			return true;
		} 
		return false;
	}

	public void setComponent(SoftwareComponent component) {
		if (component.getLabel().equals(SoftwareTaggingLabels.SOFTWARE)) {
			this.softwareName = component;
		} else if (component.getLabel().equals(SoftwareTaggingLabels.SOFTWARE_URL)) {
			this.softwareURL = component;
		} else if (component.getLabel().equals(SoftwareTaggingLabels.CREATOR)) {
			this.creator = component;
		} else if (component.getLabel().equals(SoftwareTaggingLabels.VERSION)) {
			this.version = component;
		}
	}

	@Override
	public boolean equals(Object object) {
		boolean result = false;
		if ( (object != null) && object instanceof SoftwareEntity) {
			SoftwareEntity theEntity = (SoftwareEntity)object;

			if ( (softwareName == null) && (theEntity.getSoftwareName() == null) )
				return true;

			if ( (softwareName == null) || (theEntity.getSoftwareName() == null) )
				return false;

			return this.getSoftwareName().equals(theEntity.getSoftwareName());
		}
		return result;
	}

	@Override
	public int compareTo(SoftwareEntity theEntity) {
		if (this.softwareName == null)
			return -1;
		if (theEntity.getSoftwareName() == null)
			return 1;
		return this.getSoftwareName().compareTo(theEntity.getSoftwareName());
	}

	public String toJson() {		
		if (this.softwareName == null) {
			return "{}";
		}
		JsonStringEncoder encoder = JsonStringEncoder.getInstance();
		StringBuffer buffer = new StringBuffer();
		buffer.append("{ ");

		// knowledge information
		if (softwareName.getWikidataId() != null) {
			buffer.append("\"wikidataId\": \"" + softwareName.getWikidataId() + "\"");
		}
		if (softwareName.getWikipediaExternalRef() != -1) {
			buffer.append(", \"wikipediaExternalRef\": " + softwareName.getWikipediaExternalRef());
		}
		if (softwareName.getLang() != null) {
			buffer.append(", \"lang\": \"" + softwareName.getLang() + "\"");
		}
		if (softwareName.getDisambiguationScore() != null) {
			buffer.append(", \"confidence\": " + TextUtilities.formatFourDecimals(softwareName.getDisambiguationScore().doubleValue()));
		}

		if ((softwareName.getWikidataId() != null) || 
			(softwareName.getWikipediaExternalRef() != -1) || 
			(softwareName.getDisambiguationScore() != null) ||
			(softwareName.getLang() != null))
			buffer.append(", ");

		byte[] encoded = null;
		String output = null;
		
		buffer.append("\"software-name\": ");
		buffer.append(softwareName.toJson());
		if (type != null) {
			encoded = encoder.quoteAsUTF8(type.getName().toLowerCase());
            output = new String(encoded);
			buffer.append(", \"type\" : \"" + output + "\"");	
		}
		if (entityId != null) {
			buffer.append(", \"id\" : \"" + entityId + "\"");	
		}
		if (version != null) {
			buffer.append(", \"version\":" + version.toJson());
		}
		if (creator != null) {
			buffer.append(", \"publisher\":" + creator.toJson());
		}
		if (softwareURL != null) {
			buffer.append(", \"url\":" + softwareURL.toJson());
		}

		if (context != null && context.length()>0) {
			encoded = encoder.quoteAsUTF8(context.replace("\n", " "));
            output = new String(encoded);
			buffer.append(", \"context\" : \"" + output + "\"");
		
			if (contextOffset != -1) {
				buffer.append(", \"contextOffset\" : " + contextOffset);
			}
		}

		if (paragraph != null && paragraph.length()>0) {
			encoded = encoder.quoteAsUTF8(paragraph.replace("\n", " "));
            output = new String(encoded);
			buffer.append(", \"paragraph\" : \"" + output + "\"");
		}

		if (bibRefs != null) {
			buffer.append(", \"references\" : ["); 
			boolean first = true;
			for(BiblioComponent bibRef : bibRefs) {
				if (bibRef.getBiblio() == null)
					continue;
				if (!first)
					buffer.append(", ");
				else
					first = false;
				buffer.append(bibRef.toJson());
			}

			buffer.append(" ] ");
		}

		buffer.append(" }");
		return buffer.toString();
	}
}