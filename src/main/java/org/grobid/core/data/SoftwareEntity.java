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
	private SoftwareComponent language = null;

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
	
	// offset of the context with respect of the paragraph 
	private int paragraphContextOffset = -1;

	// offset of the context with rspect to the complete content
	private int globalContextOffset = -1;

	// full paragraph context where the entity takes place, this is an optional field
	// relevant for certain scenarios only
	private String paragraph = null;

	// features of the mention context relatively to the referenced software: 
	// 1) software usage by the research work disclosed in the document: used
	// 2) software creation of the research work disclosed in the document (creation, extension, etc.): contribution
	// 3) software is shared
	private Boolean used = null;
	private Double usedScore = null;
	private Boolean created = null;
	private Double createdScore = null;
	private Boolean shared = null;
	private Double sharedScore = null;

	// a flag to indicate if the entity is located in the Data Availability section
	private boolean inDataAvailabilitySection = false;

	// characteristics of the mention context relatively to the referenced software for the single local mention
	private SoftwareContextAttributes mentionContextAttributes = null;

	// characteristics of the mention contexts relatively to the referenced software considering all mentions in a document
	private SoftwareContextAttributes documentContextAttributes = null;

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

	public SoftwareComponent getLanguage() {
		return this.language;
	}

	public void setLanguage(SoftwareComponent language) {
		this.language = language;
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

	public void setParagraphContextOffset(int paragraphContextOffset) {
		this.paragraphContextOffset = paragraphContextOffset;
	}

	public int getParagraphContextOffset() {
		return this.paragraphContextOffset;
	}

	public void setGlobalContextOffset(int globalContextOffset) {
		this.globalContextOffset = globalContextOffset;
	}

	public int getGlobalContextOffset() {
		return this.globalContextOffset;
	}

	public void setParagraph(String paragraph) {
		this.paragraph = paragraph;
	}

	public String getParagraph() {
		return this.paragraph;
	}

	public SoftwareContextAttributes getMentionContextAttributes() {
		return this.mentionContextAttributes;
	}

	public void setMentionContextAttributes(SoftwareContextAttributes attributes) {
		this.mentionContextAttributes = attributes;
	}

	public SoftwareContextAttributes getDocumentContextAttributes() {
		return this.documentContextAttributes;
	}

	public boolean isInDataAvailabilitySection() {
		return this.inDataAvailabilitySection;
	}

	public void setInDataAvailabilitySection(boolean inDAS) {
		this.inDataAvailabilitySection = inDAS;
	}

	public void mergeDocumentContextAttributes(SoftwareContextAttributes attributes) {
		if (this.documentContextAttributes == null)
			this.documentContextAttributes = attributes;

		if (this.documentContextAttributes.getUsed() == null || !this.documentContextAttributes.getUsed()) {
			this.documentContextAttributes.setUsed(attributes.getUsed());
		}

		if (this.documentContextAttributes.getUsedScore() != null) {
			if (attributes.getUsedScore() > this.documentContextAttributes.getUsedScore()) 
				this.documentContextAttributes.setUsedScore(attributes.getUsedScore());
		} else
			this.documentContextAttributes.setUsedScore(attributes.getUsedScore());

		if (this.documentContextAttributes.getCreated() == null || !this.documentContextAttributes.getCreated()) {
			this.documentContextAttributes.setCreated(attributes.getCreated());
		}

		if (this.documentContextAttributes.getCreatedScore() != null) {
			if (attributes.getCreatedScore() > this.documentContextAttributes.getCreatedScore()) 
				this.documentContextAttributes.setCreatedScore(attributes.getCreatedScore());
		} else
			this.documentContextAttributes.setCreatedScore(attributes.getCreatedScore());

		if (this.documentContextAttributes.getShared() == null || !this.documentContextAttributes.getShared()) {
			this.documentContextAttributes.setShared(attributes.getShared());
		}

		if (this.documentContextAttributes.getSharedScore() != null) {
			if (attributes.getSharedScore() > this.documentContextAttributes.getSharedScore()) 
				this.documentContextAttributes.setSharedScore(attributes.getSharedScore());
		} else
			this.documentContextAttributes.setSharedScore(attributes.getSharedScore());
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

		if (entity1.getLanguage() == null)
			entity1.setLanguage(entity2.getLanguage());
		else if (entity2.getLanguage() == null)
			entity2.setLanguage(entity1.getLanguage());

		if (entity1.getBibRefs() == null)
			entity1.setBibRefs(entity2.getBibRefs());
		else if (entity2.getBibRefs() == null)
			entity2.setBibRefs(entity1.getBibRefs());
	}

	/**
	 * Assuming that software names are identical, this method merges the attributes
	 * of the two entities with a copy of the added attribute component.    
	 */
	public static void mergeWithCopy(SoftwareEntity entity1, SoftwareEntity entity2) {

		if (entity1.getVersion() == null && entity2.getVersion() != null)
			entity1.setVersion(new SoftwareComponent(entity2.getVersion()));
		else if (entity2.getVersion() == null && entity1.getVersion() != null)
			entity2.setVersion(new SoftwareComponent(entity1.getVersion()));

		if (entity1.getCreator() == null && entity2.getCreator() != null)
			entity1.setCreator(new SoftwareComponent(entity2.getCreator()));
		else if (entity2.getCreator() == null && entity1.getCreator() != null)
			entity2.setCreator(new SoftwareComponent(entity1.getCreator()));

		if (entity1.getSoftwareURL() == null && entity2.getSoftwareURL() != null)
			entity1.setSoftwareURL(new SoftwareComponent(entity2.getSoftwareURL()));
		else if (entity2.getSoftwareURL() == null && entity1.getSoftwareURL() != null)
			entity2.setSoftwareURL(new SoftwareComponent(entity1.getSoftwareURL()));

		if (entity1.getLanguage() == null && entity2.getLanguage() != null)
			entity1.setLanguage(new SoftwareComponent(entity2.getLanguage()));
		else if (entity2.getLanguage() == null && entity1.getLanguage() != null)
			entity2.setLanguage(new SoftwareComponent(entity1.getLanguage()));

		if (entity1.getBibRefs() == null && entity2.getBibRefs() != null) {
			List<BiblioComponent> newBibRefs = new ArrayList<>();
			for(BiblioComponent bibComponent : entity2.getBibRefs()) {
				newBibRefs.add(new BiblioComponent(bibComponent));
			}
			if (newBibRefs.size() > 0)
				entity1.setBibRefs(newBibRefs);
		}
		else if (entity2.getBibRefs() == null && entity1.getBibRefs() != null) {
			List<BiblioComponent> newBibRefs = new ArrayList<>();
			for(BiblioComponent bibComponent : entity1.getBibRefs()) {
				newBibRefs.add(new BiblioComponent(bibComponent));
			}
			if (newBibRefs.size() > 0)
				entity2.setBibRefs(newBibRefs);
		}
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
		} else if (component.getLabel().equals(SoftwareTaggingLabels.LANGUAGE)) {
			this.language = component;
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
		byte[] encoded = null;
		String output = null;

		StringBuffer buffer = new StringBuffer();
		buffer.append("{ ");

		buffer.append("\"type\": \"software\"");

		// type of the software
		if (type != null) {
			buffer.append(", \"software-type\": \"");
			encoded = encoder.quoteAsUTF8(type.getName());
            output = new String(encoded);
            buffer.append(output).append("\"");
		}

		// knowledge information
		if (softwareName.getWikidataId() != null) {
			buffer.append(", \"wikidataId\": \"" + softwareName.getWikidataId() + "\"");
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

		buffer.append(", \"software-name\": ");
		buffer.append(softwareName.toJson());
		
		if (entityId != null) {
			buffer.append(", \"id\": \"" + entityId + "\"");	
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
		if (language != null) {
			buffer.append(", \"language\":" + language.toJson());
		}

		if (inDataAvailabilitySection) {
            buffer.append(", \"inDataAvailabilitySection\" : true");
        }

		if (context != null && context.length()>0) {
			//encoded = encoder.quoteAsUTF8(context.replace("\n", " ").replace("  ", " "));
            encoded = encoder.quoteAsUTF8(context);
            output = new String(encoded);
			buffer.append(", \"context\" : \"" + output + "\"");
		
			/*if (globalContextOffset != -1) {
				buffer.append(", \"contextOffset\" : " + globalContextOffset);
			}*/
		}

		if (paragraph != null && paragraph.length()>0) {
			if (paragraphContextOffset != -1) {
				buffer.append(", \"contextOffset\": " + paragraphContextOffset);
			}

			//encoded = encoder.quoteAsUTF8(paragraph.replace("\n", " ").replace("  ", " "));
			encoded = encoder.quoteAsUTF8(paragraph);
            output = new String(encoded);
			buffer.append(", \"paragraph\": \"" + output + "\"");
		}

		if (mentionContextAttributes != null) {
			buffer.append(", \"mentionContextAttributes\": " + mentionContextAttributes.toJson());
		}

		if (documentContextAttributes != null) {
			buffer.append(", \"documentContextAttributes\": " + documentContextAttributes.toJson());
		}

		if (bibRefs != null) {
			buffer.append(", \"references\": ["); 
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