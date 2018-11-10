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

import java.util.List;

/**
 *  Representation of a mention of a software entity with all its components.
 *
 */
public class SoftwareEntity extends KnowledgeEntity implements Comparable<SoftwareEntity> {   

	// type of the entity
	private SoftwareLexicon.Software_Type type = null;
	
	// component of the entity
	private SoftwareComponent softwareName = null;
	private SoftwareComponent versionNumber = null;
	private SoftwareComponent versionDate = null;
	private SoftwareComponent creator = null;
	private SoftwareComponent softwareURL = null;

	// Entity identifier if the mention has been solved/disambiguated against the
	// knowledge base of software entities. The identifier is the unique 
	// identifier of the entity in this KB.
	private String entityId = null;

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

	public SoftwareComponent getVersionNumber() {
		return this.versionNumber;
	}

	public void setVersionNumber(SoftwareComponent versionNumber) {
		this.versionNumber = versionNumber;
	}

	public SoftwareComponent getVersionDate() {
		return this.versionDate;
	}

	public void setVersionDate(SoftwareComponent versionDate) {
		this.versionDate = versionDate;
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

	public String getEntityId() {
		return entityId;
	}
	
	public void setEntityId(String id) {
		this.entityId = id;
	}

	/**
	 * Check if a component corresponding to a given label is already present in a software entity
	 */
	public boolean freeField(TaggingLabel label) {
		if (label.equals(SoftwareTaggingLabels.SOFTWARE) && (this.softwareName != null)) {
			return false;
		} else if (label.equals(SoftwareTaggingLabels.SOFTWARE_URL) && (this.versionNumber != null)) {
			return false;
		} else if (label.equals(SoftwareTaggingLabels.CREATOR) && (this.versionDate != null)) {
			return false;
		} else if (label.equals(SoftwareTaggingLabels.VERSION_NUMBER) && (this.creator != null)) {
			return false;
		} else if (label.equals(SoftwareTaggingLabels.VERSION_DATE) && (this.softwareURL != null)) {
			return false;
		}
		return true;
	}

	public void setComponent(SoftwareComponent component) {
		if (component.getLabel().equals(SoftwareTaggingLabels.SOFTWARE)) {
			this.softwareName = component;
		} else if (component.getLabel().equals(SoftwareTaggingLabels.SOFTWARE_URL)) {
			this.softwareURL = component;
		} else if (component.getLabel().equals(SoftwareTaggingLabels.CREATOR)) {
			this.creator = component;
		} else if (component.getLabel().equals(SoftwareTaggingLabels.VERSION_NUMBER)) {
			this.versionNumber = component;
		} else if (component.getLabel().equals(SoftwareTaggingLabels.VERSION_DATE)) {
			this.versionDate = component;
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

		buffer.append("\"software-name\": ");
		buffer.append(softwareName.toJson());
		if (type != null)
			buffer.append(", \"type\" : \"" + type.getName().toLowerCase() + "\"");	
		if (entityId != null)
			buffer.append(", \"id\" : \"" + entityId + "\"");	
		if (versionNumber != null) {
			buffer.append(", \"version-number\":" + versionNumber.toJson());
		}
		if (versionDate != null) {
			buffer.append(", \"version-date\":" + versionDate.toJson());
		}
		if (creator != null) {
			buffer.append(", \"creator\":" + creator.toJson());
		}
		if (softwareURL != null) {
			buffer.append(", \"url\":" + softwareURL.toJson());
		}

		buffer.append(" }");
		return buffer.toString();
	}
}