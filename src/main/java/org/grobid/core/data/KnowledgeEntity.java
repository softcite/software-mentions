package org.grobid.core.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 *  Attributes in relation to disambiguation against a Knowledge base.
 *
 */
public class KnowledgeEntity {

    public KnowledgeEntity() {
    }

    // wikidata identifier
    protected String wikidataId = null;

    // wikipedia page id
    protected int wikipediaExternalRef = -1;

    // disambiguation score if disambiguated, null otherwise
    protected Double disambiguationScore = null;


    public String getWikidataId() {
        return wikidataId;
    }

    public void setWikidataId(String id) {
        this.wikidataId = id;
    }

    public int getWikipediaExternalRef() {
        return wikipediaExternalRef;
    }

    public void setWikipediaExternalRef(int wikipediaExternalRef) {
        this.wikipediaExternalRef = wikipediaExternalRef;
    }

    public Double getDisambiguationScore() {
        return disambiguationScore;
    }

    public void setDisambiguationScore(Double disambiguationScore) {
        this.disambiguationScore = disambiguationScore;
    }

    /** 
     * Copy the knowledge entity information to another entity
     */
    public void copyKnowledgeInformationTo(KnowledgeEntity otherEntity) {
        otherEntity.setWikidataId(wikidataId);
        otherEntity.setWikipediaExternalRef(wikipediaExternalRef);
        otherEntity.setDisambiguationScore(disambiguationScore);
    }

}   
