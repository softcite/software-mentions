package org.grobid.service;

import com.sun.jersey.multipart.FormDataParam;
import com.sun.jersey.spi.resource.Singleton;

import org.grobid.core.main.LibraryLoader;
import org.grobid.core.lexicon.SoftwareLexicon;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.SoftwareProperties;
import org.grobid.core.main.GrobidHomeFinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.InputStream;

import java.util.Arrays;

/**
 * RESTful service for GROBID Software extension.
 *
 * @author Patrice
 */
@Singleton
@Path(SoftwarePaths.PATH_SOFTWARE)
public class SoftwareRestService implements SoftwarePaths {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoftwareRestService.class);

    private static final String TEXT = "text";
    private static final String XML = "xml";
    private static final String PDF = "pdf";
    private static final String INPUT = "input";

    public SoftwareRestService() {
        LOGGER.info("Init Servlet SoftwareRestService.");
        LOGGER.info("Init lexicon and KB resources.");
        try {
            String pGrobidHome = SoftwareProperties.get("grobid.home");

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            LOGGER.info(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());

            LibraryLoader.load();
            SoftwareLexicon.getInstance();
        } catch (final Exception exp) {
            System.err.println("GROBID software initialisation failed: " + exp);
            exp.printStackTrace();
        }

        LOGGER.info("Init of Servlet SoftwareRestService finished.");
    }

    @Path(PATH_SOFTWARE_TEXT)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @POST
    public Response processText_post(@FormParam(TEXT) String text) {
        LOGGER.info(text);
        return SoftwareProcessString.processText(text);
    }

    @Path(PATH_SOFTWARE_TEXT)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response processText_get(@QueryParam(TEXT) String text) {
        LOGGER.info(text);
        return SoftwareProcessString.processText(text);
    }
	
	@Path(PATH_ANNOTATE_SOFTWARE_PDF)
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces("application/json")
	@POST
	public Response processPDFAnnotation(@FormDataParam(INPUT) InputStream inputStream) {
		return SoftwareProcessFile.processPDFAnnotation(inputStream);
	}
}
