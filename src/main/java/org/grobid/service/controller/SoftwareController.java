package org.grobid.service.controller;

import org.glassfish.jersey.media.multipart.FormDataParam;
import org.grobid.service.configuration.SoftwareConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.InputStream;
import java.net.HttpURLConnection;

import java.util.Arrays;

/**
 * RESTful service for GROBID Software extension.
 *
 * @author Patrice
 */
@Singleton
@Path(SoftwarePaths.PATH_SOFTWARE)
public class SoftwareController implements SoftwarePaths {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoftwareController.class);

    private static final String TEXT = "text";
    private static final String DISAMBIGUATE = "disambiguate";
    private static final String XML = "xml";
    private static final String PDF = "pdf";
    private static final String INPUT = "input";

    private final SoftwareConfiguration configuration;

    @Inject
    public SoftwareController(SoftwareConfiguration configuration) {
        this.configuration = configuration;
    }

        /**
     * @see org.grobid.service.process.GrobidRestProcessGeneric#isAlive()
     */
    @Path(PATH_IS_ALIVE)
    @Produces(MediaType.TEXT_PLAIN)
    @GET
    public Response isAlive() {
        return Response.status(Response.Status.OK).entity(SoftwareProcessString.isAlive()).build();
    }

    @Path(PATH_SOFTWARE_TEXT)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @POST
    public Response processText_post(@FormParam(TEXT) String text, 
                                     @DefaultValue("0") @FormParam(DISAMBIGUATE) String disambiguate) {
        LOGGER.info(text); 
        boolean disambiguateBoolean = SoftwareServiceUtil.validateBooleanRawParam(disambiguate);

        return SoftwareProcessString.processText(text, disambiguateBoolean);
    }

    @Path(PATH_SOFTWARE_TEXT)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response processText_get(@QueryParam(TEXT) String text, 
                                    @DefaultValue("0") @QueryParam(DISAMBIGUATE) String disambiguate) {
        //LOGGER.info(text);
        boolean disambiguateBoolean = SoftwareServiceUtil.validateBooleanRawParam(disambiguate);
        return SoftwareProcessString.processText(text, disambiguateBoolean);
    }
    
    @Path(PATH_ANNOTATE_SOFTWARE_PDF)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json")
    @POST
    public Response processPDFAnnotation(@FormDataParam(INPUT) InputStream inputStream, 
                                         @DefaultValue("0") @FormDataParam(DISAMBIGUATE) String disambiguate) {
        boolean disambiguateBoolean = SoftwareServiceUtil.validateBooleanRawParam(disambiguate);
        return SoftwareProcessFile.processPDFAnnotation(inputStream, disambiguateBoolean);
    }

}
