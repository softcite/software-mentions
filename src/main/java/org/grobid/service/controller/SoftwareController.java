package org.grobid.service.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.grobid.core.utilities.SoftwareConfiguration;
import org.grobid.core.utilities.Versioner;
import org.grobid.service.configuration.SoftwareServiceConfiguration;
import org.grobid.service.data.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

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
    private final SoftwareServiceConfiguration serviceConfiguration;
    private final Client httpClient;

    @Inject
    public SoftwareController(SoftwareServiceConfiguration serviceConfiguration, Client httpClient) {
        /*try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            this.configuration = mapper.readValue(new File("resources/config/config.yml"), SoftwareConfiguration.class);
        } catch(Exception e) {
            LOGGER.error("The config file does not appear valid, see resources/config/config.yml", e);
            this.configuration = null;
        }*/
        this.configuration = serviceConfiguration.getSoftwareConfiguration();
        this.serviceConfiguration = serviceConfiguration;
        this.httpClient = httpClient;
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

    // New endpoint: return concept service base URL derived from entity-fishing host/port
    @Path(PATH_CONFIG_CONCEPT_BASE_URL)
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public Response getConceptServiceBaseUrl() {
        String base = buildConceptBaseUrl();
        Map<String, String> payload = Collections.singletonMap("conceptBaseUrl", base);
        return Response.ok(payload).build();
    }

    // New proxy endpoint: forward concept lookup using configured host/port
    @Path("kb/concept/{identifier}")
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public Response proxyKbConcept(@PathParam("identifier") String identifier, @QueryParam("lang") String lang) {
        String base = buildConceptBaseUrl();
        String sep = base.endsWith("/") ? "" : "/";
        String target = base + sep + identifier;
        if (lang != null && !lang.isEmpty()) {
            target = target + "?lang=" + lang;
        }
        try {
            String json = httpClient.target(target).request(MediaType.APPLICATION_JSON_TYPE).get(String.class);
            return Response.ok(json, MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            LOGGER.error("Error proxying concept lookup to {}", target, e);
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(Collections.singletonMap("error", "Failed to fetch concept from upstream"))
                    .build();
        }
    }

    // Build the concept base URL from entityFishingHost/Port, with sensible defaults
    private String buildConceptBaseUrl() {
        String host = serviceConfiguration != null ? serviceConfiguration.getEntityFishingHost() : null;
        String port = serviceConfiguration != null ? serviceConfiguration.getEntityFishingPort() : null;
        if (host == null || host.isEmpty()) {
            // fall back to public endpoint
            return "https://cloud.science-miner.com/nerd/service/kb/concept";
        }

        String original = host.trim();
        String lower = original.toLowerCase();
        boolean hasScheme = lower.startsWith("http://") || lower.startsWith("https://");

        String scheme;
        if (hasScheme) {
            scheme = lower.startsWith("https://") ? "https" : "http";
        } else {
            scheme = (port != null && ("443".equals(port) || "8443".equals(port))) ? "https" : "http";
        }

        // Extract hostPart and pathPart if scheme is present
        String hostPart = original;
        String pathPart = "";
        if (hasScheme) {
            String noScheme = original.substring(original.indexOf("://") + 3);
            int slash = noScheme.indexOf("/");
            if (slash >= 0) {
                hostPart = noScheme.substring(0, slash);
                pathPart = noScheme.substring(slash); // includes leading '/'
            } else {
                hostPart = noScheme;
                pathPart = "";
            }
        } else {
            // original may already include a path like 'traces1.inria.fr/nerd'
            int slash = original.indexOf("/");
            if (slash >= 0) {
                hostPart = original.substring(0, slash);
                pathPart = original.substring(slash);
            } else {
                hostPart = original;
                pathPart = "";
            }
        }

        // Append port if missing in hostPart and provided in config (and non-default for scheme)
        boolean hostHasPort = hostPart.contains(":");
        if (!hostHasPort && port != null && !port.isEmpty()) {
            boolean defaultForScheme = ("https".equals(scheme) && "443".equals(port)) || ("http".equals(scheme) && "80".equals(port));
            if (!defaultForScheme) {
                hostPart = hostPart + ":" + port;
            }
        }

        // Ensure '/nerd' is present at the beginning of pathPart
        if (pathPart == null || pathPart.isEmpty() || !pathPart.matches("(?i)^/nerd(/.*)?$")) {
            // if pathPart is empty or doesn't start with '/nerd', prepend it
            if (pathPart == null || pathPart.isEmpty()) {
                pathPart = "/nerd";
            } else {
                // avoid double slashes
                if (!pathPart.startsWith("/")) {
                    pathPart = "/" + pathPart;
                }
                pathPart = "/nerd" + pathPart;
            }
        }

        // Build final base
        String base = scheme + "://" + hostPart;
        // remove trailing slash from pathPart
        if (pathPart.endsWith("/")) {
            pathPart = pathPart.substring(0, pathPart.length() - 1);
        }
        base += pathPart + "/service/kb/concept";
        return base;
    }

}
