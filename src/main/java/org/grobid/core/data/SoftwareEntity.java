 package org.grobid.core.data;

import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.lexicon.SoftwareLexicon;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 *  Representation of a mention of a software entity with all its components.
 *
 */
public class SoftwareEntity implements Comparable<SoftwareEntity> {   

	private SoftwareComponent softwareName = null;
	private SoftwareComponent versionNumber = null;
	private SoftwareComponent versionDate = null;
	private SoftwareComponent creator = null;
	private SoftwareComponent softwareURL = null;

	// Entity identifier if the mention has been solved/disambiguated against the
	// knowledge base of software entities. The identifier is the unique 
	// identifier of the entity in this KB.
	private String entityId = null;

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
		buffer.append("\"name\": ");
		buffer.append(softwareName.toJson());
		if (entityId != null)
			buffer.append(", \"id\" : \"" + entityId + "\"");	
		if (versionNumber != null) {
			buffer.append(", \"version-number\":" + versionNumber.toJson());
		}
		if (versionDate != null) {
			buffer.append(", \"version-date\":" + versionDate.toJson());
		}
		if (versionNumber != null) {
			buffer.append(", \"creator\":" + creator.toJson());
		}
		if (versionNumber != null) {
			buffer.append(", \"url\":" + softwareURL.toJson());
		}
		buffer.append(" }");
		return buffer.toString();
	}
}