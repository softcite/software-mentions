package org.grobid.service.controller;

import java.util.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.data.BiblioComponent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for GROBID Software service.
 *
 * @author Patrice
 */
public class SoftwareServiceUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(SoftwareServiceUtil.class);

    /**
     * Give application information to be added in a JSON result
     */
    public static String applicationDetails(String version, String gitRevision) {
        StringBuilder sb = new StringBuilder();

        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);
        String dateISOString = df.format(new java.util.Date());

        sb.append("\"application\": \"software-mentions\", ");
        if (version !=null) {
            sb.append("\"version\": \"" + version + "\", ");
        }
        if (gitRevision != null) {
            sb.append("\"revision\": \"" + gitRevision + "\", ");
        }
        sb.append("\"date\": \"" + dateISOString + "\"");

        return sb.toString();
    }

    /**
     * Convert REST boolean parameter value provided as string
     */
    public static boolean validateBooleanRawParam(String raw) {
        boolean result = false;
        if ((raw != null) && (raw.equals("1") || raw.toLowerCase().equals("true"))) {
            result = true;
        }
        return result;
    }


    /**
     * Serialize the bibliographical references present in a list of entities
     */ 
    public static void serializeReferences(StringBuilder json, 
                                           List<BibDataSet> bibDataSet, 
                                           List<SoftwareEntity> entities) {
        ObjectMapper mapper = new ObjectMapper();
        List<Integer> serializedKeys = new ArrayList<Integer>();
        for(SoftwareEntity entity : entities) {
            List<BiblioComponent> bibRefs = entity.getBibRefs();
            if (bibRefs != null) {
                for(BiblioComponent bibComponent : bibRefs) {
                    int refKey = bibComponent.getRefKey();
                    if (!serializedKeys.contains(refKey)) {
                        if (serializedKeys.size()>0)
                            json.append(", ");
                        if (bibComponent.getBiblio() != null) {
                            json.append("{ \"refKey\": " + refKey);
                            try {
                                json.append(", \"tei\": " + mapper.writeValueAsString(bibComponent.getBiblio().toTEI(refKey)));
                            } catch (JsonProcessingException e) {
                                LOGGER.warn("tei for biblio cannot be encoded", e);
                            }
                            json.append("}");
                        }
                        serializedKeys.add(Integer.valueOf(refKey));
                    }
                }
            }
        }

    }
}