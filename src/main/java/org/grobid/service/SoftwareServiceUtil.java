 package org.grobid.service;

import java.util.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Utility methods for GROBID Software service.
 *
 * @author Patrice
 */
public class SoftwareServiceUtil {

    /**
     * Give application information to be added in a JSON result
     */
    public static String applicationDetails(String version) {
        StringBuilder sb = new StringBuilder();

        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);
        String dateISOString = df.format(new java.util.Date());

        sb.append("\"application\": \"software-mentions\", ");
        if (version !=null)
            sb.append("\"version\": \"" + version + "\", ");
        sb.append("\"date\": \"" + dateISOString + "\"");

        return sb.toString();
    }

}