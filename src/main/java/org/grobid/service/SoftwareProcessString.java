package org.grobid.service;

import java.util.List;
import java.util.ArrayList;
import java.util.NoSuchElementException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.grobid.core.data.SoftwareComponent;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.engines.SoftwareParser;
import org.grobid.core.factory.GrobidPoolingFactory;
import org.grobid.core.utilities.GrobidProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang3.StringUtils;

/**
 * 
 * @author Patrice
 * 
 */
public class SoftwareProcessString {

	/**
	 * The class Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(SoftwareProcessString.class);

	/**
	 * Parse a raw date and return the corresponding normalized date.
	 * 
	 * @param text the raw date string
	 * @param disambiguate if true, the extracted mention will be disambiguated
	 * @return a response object containing the structured xml representation of
	 *         the date
	 */
	public static Response processText(String text, boolean disambiguate) {
		LOGGER.debug(methodLogIn());
		Response response = null;
		StringBuilder retVal = new StringBuilder();
		SoftwareParser parser = SoftwareParser.getInstance();
		try {
			LOGGER.debug(">> set raw text for stateless service'...");
			
			List<SoftwareEntity> entities = null;
			text = text.replaceAll("\\n", " ").replaceAll("\\t", " ");
			long start = System.currentTimeMillis();
			entities = parser.processText(text, disambiguate);
			long end = System.currentTimeMillis();

			if (entities != null) {
				retVal.append("{ ");
				retVal.append(SoftwareServiceUtil.applicationDetails(GrobidProperties.getVersion()));
				if (entities.size() == 0)
					retVal.append(", \"mentions\" : []");
				else {
					boolean first = true;
					for(SoftwareEntity entity : entities)	{
						if (first) {
							retVal.append(", \"mentions\" : [ ");
							first = false;
						} else {	
							retVal.append(", ");
						}
						retVal.append(entity.toJson());
					}
					retVal.append("]");
				}
				retVal.append(", \"runtime\" : " + (end-start));
				retVal.append("}");
			}
			String retValString = retVal.toString();
			if (!isResultOK(retValString)) {
				response = Response.status(Status.NO_CONTENT).build();
			} else {
				response = Response.status(Status.OK).entity(retValString).type(MediaType.TEXT_PLAIN).build();
			}
		} catch (NoSuchElementException nseExp) {
			LOGGER.error("Could not get an instance of SoftwareParser. Sending service unavailable.");
			response = Response.status(Status.SERVICE_UNAVAILABLE).build();
		} catch (Exception e) {
			LOGGER.error("An unexpected exception occurs. ", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
		} 
		LOGGER.debug(methodLogOut());
		return response;
	}

	/**
     * Returns a string containing true, if the service is alive.
     *
     * @return a response object containing the string true if service
     * is alive.
     */
    public static String isAlive() {
        LOGGER.debug("called isAlive()...");

        String retVal = null;
        try {
            retVal = Boolean.valueOf(true).toString();
        } catch (Exception e) {
            LOGGER.error("software-mention service is not alive, because of: ", e);
            retVal = Boolean.valueOf(false).toString();
        }
        return retVal;
    }

	/**
	 * @return
	 */
	public static String methodLogIn() {
		return ">> " + SoftwareProcessString.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
	}

	/**
	 * @return
	 */
	public static String methodLogOut() {
		return "<< " + SoftwareProcessString.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
	}

	/**
	 * Check whether the result is null or empty.
	 */
	public static boolean isResultOK(String result) {
		return StringUtils.isBlank(result) ? false : true;
	}

}
