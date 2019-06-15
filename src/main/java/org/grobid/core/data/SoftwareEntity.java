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

	// one or several bibliographical references attached to the software entity
	private List<BiblioComponent> bibRefs = null;

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

	/**
	 * Assuming that software names are identical, this method merges the attributes
	 * of the two entities.    
	 */
	public static void merge(SoftwareEntity entity1, SoftwareEntity entity2) {
		if (entity1.getVersionNumber() == null)
			entity1.setVersionNumber(entity2.getVersionNumber());
		else if (entity2.getVersionNumber() == null)
			entity2.setVersionNumber(entity1.getVersionNumber());

		if (entity1.getVersionDate() == null)
			entity1.setVersionDate(entity2.getVersionDate());
		else if (entity2.getVersionDate() == null)
			entity2.setVersionDate(entity1.getVersionDate());

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