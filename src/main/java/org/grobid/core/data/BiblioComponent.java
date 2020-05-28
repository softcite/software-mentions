package org.grobid.core.data;

import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.lexicon.SoftwareLexicon;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Representation of the bibliographical reference element for a software mention.
 *  The component represent the reference callout (position) and its matched full  
 *  bibliographical reference.
 * 
 *  The bibliographical reference can also be disambiguated against wikidata via
 *  its (inherited) KnowledgeEntity object attributes. 
 */
public class BiblioComponent extends SoftwareComponent {
    private static final Logger logger = LoggerFactory.getLogger(BiblioComponent.class);

    // the full matched bibliographical reference record
    private BiblioItem biblio = null;

    // identifier for relating callout and reference, should be cconsistent with 
    // a full text TEI produced by GROBID
    private int refKey = -1;

    public BiblioComponent(BiblioItem biblio, int refKey) {
        this.biblio = biblio;
        this.refKey = refKey;
    }

    public void setBiblio(BiblioItem biblio) {
        this.biblio = biblio;
    }

    public BiblioItem getBiblio() {
        return biblio;
    }

    public void setRefKey(int refKey) {
        this.refKey = refKey;
    }

    public int getRefKey() {
        return refKey;
    }

    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        
        StringBuffer buffer = new StringBuffer();
        buffer.append("{ ");
                
        try {
            buffer.append("\"label\" : " + mapper.writeValueAsString(rawForm));
        } catch (JsonProcessingException e) {
            buffer.append("\"label\" : \"" + "JsonProcessingException" + "\"");
        }
        if (normalizedForm != null)
            buffer.append(", \"normalizedForm\" : \"" + normalizedForm + "\"");

        /*if (biblio != null) {
            try {
                buffer.append(", \"tei\": " + mapper.writeValueAsString(biblio.toTEI(refKey)));
            } catch (JsonProcessingException e) {
                logger.warn("tei for biblio cannot be encoded", e);
            }
        }*/
        buffer.append(", \"refKey\": " + refKey);
        
        // knowledge information
        if (wikidataId != null) {
            buffer.append(", \"wikidataId\": \"" + wikidataId + "\"");
        }
        if (wikipediaExternalRef != -1) {
            buffer.append(", \"wikipediaExternalRef\": " + wikipediaExternalRef);
        }
        if (lang != null) {
            buffer.append(", \"lang\": \"" + lang + "\"");
        }
        if (disambiguationScore != null) {
            buffer.append(", \"confidence\": " + TextUtilities.formatFourDecimals(disambiguationScore.doubleValue()));
        }

        buffer.append(", \"offsetStart\" : " + offsets.start);
        buffer.append(", \"offsetEnd\" : " + offsets.end);  
                
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

}   
