package org.grobid.service.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.grobid.core.data.SoftwareComponent;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.document.Document;
import org.grobid.core.engines.Engine;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.engines.SoftwareParser;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.factory.GrobidPoolingFactory;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.IOUtilities;
import org.grobid.core.utilities.KeyGen;
import org.grobid.service.configuration.SoftwareConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import org.grobid.core.layout.Page;

/**
 *
 * @author Patrice
 */
@Singleton
public class SoftwareProcessFile {

    /**
     * The class Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SoftwareProcessFile.class);

    @Inject
    public SoftwareProcessFile() {
    }

    /**
     * Uploads the origin PDF, process it and return PDF annotations for references in JSON.
     *
     * @param inputStream the data of origin PDF
     * @param disambiguate if true, the extracted mention will be disambiguated
     * @return a response object containing the JSON annotations
     */
	public static Response processPDFAnnotation(final InputStream inputStream, boolean disambiguate, SoftwareConfiguration configuration) {
        LOGGER.debug(methodLogIn()); 
        Response response = null;
        File originFile = null;
        SoftwareParser parser = SoftwareParser.getInstance(configuration);
        Engine engine = null;

        try {
            //LibraryLoader.load();
            engine = GrobidFactory.getInstance().getEngine();
            originFile = IOUtilities.writeInputFile(inputStream);
            GrobidAnalysisConfig config = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder().build();

            if (originFile == null) {
                response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
            } else {
                long start = System.currentTimeMillis();
                Pair<List<SoftwareEntity>, Document> extractedEntities = parser.processPDF(originFile, disambiguate);
                long end = System.currentTimeMillis();

                Document doc = extractedEntities.getRight();
                List<SoftwareEntity> entities = extractedEntities.getLeft();
                StringBuilder json = new StringBuilder();
				json.append("{ ");
                json.append(SoftwareServiceUtil.applicationDetails(GrobidProperties.getVersion()));

				// page height and width
                json.append(", \"pages\":[");
				List<Page> pages = doc.getPages();
                boolean first = true;
                for(Page page : pages) {
    				if (first) 
                        first = false;
                    else
                        json.append(", ");    
    				json.append("{\"page_height\":" + page.getHeight());
    				json.append(", \"page_width\":" + page.getWidth() + "}");
                }

				json.append("], \"mentions\":[");
				first = true;
				for(SoftwareEntity entity : entities) {
					if (!first)
						json.append(", ");
					else
						first = false;
					json.append(entity.toJson());
				}
				
				json.append("]");
                json.append(", \"runtime\" :" + (end-start));
                json.append("}");

                if (json != null) {
                    response = Response
                            .ok()
                            .type("application/json")
                            .entity(json.toString())
                            .build();
                }
                else {
                    response = Response.status(Status.NO_CONTENT).build();
                }
            }
        } catch (NoSuchElementException nseExp) {
            LOGGER.error("Could not get an instance of SoftwareParser. Sending service unavailable.");
            response = Response.status(Status.SERVICE_UNAVAILABLE).build();
        } catch (Exception exp) {
            LOGGER.error("An unexpected exception occurs. ", exp);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(exp.getMessage()).build();
        } finally {
            IOUtilities.removeTempFile(originFile);
        }
        LOGGER.debug(methodLogOut());
        return response;
    }

    public static String methodLogIn() {
        return ">> " + SoftwareProcessFile.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
    }

    public static String methodLogOut() {
        return "<< " + SoftwareProcessFile.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
    }

    /**
     * Check whether the result is null or empty.
     */
    public static boolean isResultOK(String result) {
        return StringUtils.isBlank(result) ? false : true;
    }

}
