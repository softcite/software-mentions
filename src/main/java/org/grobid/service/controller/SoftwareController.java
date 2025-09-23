package org.grobid.service.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.SoftwareConfiguration;
import org.grobid.core.utilities.Versioner;
import org.grobid.service.configuration.SoftwareServiceConfiguration;
import org.grobid.service.data.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

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
    private static final String ADD_PARAGRAPH_CONTEXT = "addParagraphContext";
    private static final String XML = "xml";
    private static final String PDF = "pdf";
    private static final String INPUT = "input";

    private SoftwareConfiguration configuration;

    @Inject
    public SoftwareController(SoftwareServiceConfiguration serviceConfiguration) {
        /*try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            this.configuration = mapper.readValue(new File("resources/config/config.yml"), SoftwareConfiguration.class);
        } catch(Exception e) {
            LOGGER.error("The config file does not appear valid, see resources/config/config.yml", e);
            this.configuration = null;
        }*/
        this.configuration = serviceConfiguration.getSoftwareConfiguration();
    }

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
        //LOGGER.debug(text); 
        boolean disambiguateBoolean = SoftwareServiceUtil.validateBooleanRawParam(disambiguate);

        return SoftwareProcessString.processText(text, disambiguateBoolean, this.configuration);
    }

    @Path(PATH_SOFTWARE_TEXT)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response processText_get(@QueryParam(TEXT) String text, 
                                    @DefaultValue("0") @QueryParam(DISAMBIGUATE) String disambiguate) {
        //LOGGER.info(text);
        boolean disambiguateBoolean = SoftwareServiceUtil.validateBooleanRawParam(disambiguate);
        return SoftwareProcessString.processText(text, disambiguateBoolean, this.configuration);
    }
    
    @Path(PATH_ANNOTATE_SOFTWARE_PDF)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json")
    @POST
    public Response processPDFAnnotation(@FormDataParam(INPUT) InputStream inputStream, 
                                         @DefaultValue("0") @FormDataParam(DISAMBIGUATE) String disambiguate,
                                         @DefaultValue("0") @FormDataParam(ADD_PARAGRAPH_CONTEXT) String addParagraphContext) {
        boolean disambiguateBoolean = SoftwareServiceUtil.validateBooleanRawParam(disambiguate);
        boolean addParagraphContextBoolean = SoftwareServiceUtil.validateBooleanRawParam(addParagraphContext);
        return SoftwareProcessFile.processPDFAnnotation(inputStream, disambiguateBoolean, addParagraphContextBoolean, this.configuration);
    }

    /*@Path(PATH_ANNOTATE_SOFTWARE_PDF_URL)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @POST
    public Response processPDFAnnotationURL(@FormDataParam("url") String url, 
                                            @DefaultValue("0") @FormDataParam(DISAMBIGUATE) String disambiguate,
                                            @DefaultValue("0") @FormDataParam(ADD_PARAGRAPH_CONTEXT) String addParagraphContext) {
        boolean disambiguateBoolean = SoftwareServiceUtil.validateBooleanRawParam(disambiguate);
        boolean addParagraphContextBoolean = SoftwareServiceUtil.validateBooleanRawParam(addParagraphContext);
        return SoftwareProcessFile.processPDFAnnotationURL(url, disambiguateBoolean, addParagraphContextBoolean, this.configuration);
    }*/

    @Path(PATH_EXTRACT_SOFTWARE_XML)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json")
    @POST
    public Response processXML(@FormDataParam(INPUT) InputStream inputStream, 
                                @DefaultValue("0") @FormDataParam(DISAMBIGUATE) String disambiguate,
                                @DefaultValue("0") @FormDataParam(ADD_PARAGRAPH_CONTEXT) String addParagraphContext) {
        boolean disambiguateBoolean = SoftwareServiceUtil.validateBooleanRawParam(disambiguate);
        boolean addParagraphContextBoolean = SoftwareServiceUtil.validateBooleanRawParam(addParagraphContext);
        return SoftwareProcessFile.extractXML(inputStream, disambiguateBoolean, addParagraphContextBoolean, this.configuration);
    }

    @Path(PATH_SOFTWARE_CONTEXT)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response processSoftwareContext_get(@QueryParam(TEXT) String text) {
        //LOGGER.info(text);
        return SoftwareProcessString.characterizeContext(text, this.configuration);
    }

    @Path(PATH_EXTRACT_SOFTWARE_TEI)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/json")
    @POST
    public Response processTEI(@FormDataParam(INPUT) InputStream inputStream, 
                                @DefaultValue("0") @FormDataParam(DISAMBIGUATE) String disambiguate,
                                @DefaultValue("0") @FormDataParam(ADD_PARAGRAPH_CONTEXT) String addParagraphContext) {
        boolean disambiguateBoolean = SoftwareServiceUtil.validateBooleanRawParam(disambiguate);
        boolean addParagraphContextBoolean = SoftwareServiceUtil.validateBooleanRawParam(addParagraphContext);
        return SoftwareProcessFile.extractTEI(inputStream, disambiguateBoolean, addParagraphContextBoolean, this.configuration);
    }

    @Path(PATH_VERSION)
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public ServiceInfo getVersion() {
        return new ServiceInfo(Versioner.getVersion(), Versioner.getRevision());
    }

}
