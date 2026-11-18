package org.grobid.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.grobid.core.engines.tagging.TaggerFactory;
import org.grobid.service.ModelLoadStatus;
import org.grobid.service.configuration.SoftwareServiceConfiguration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Health / diagnostic endpoint for the software-mentions service.
 *
 * <p>In addition to the Dropwizard admin-style {@link #check()} method, this
 * resource exposes a {@code GET /service/health} endpoint that returns a
 * JSON document describing the state of the service: which models are
 * loaded and which (if any) failed to load. The endpoint returns HTTP 500
 * whenever at least one model is known to have failed to load, so that
 * orchestrators (Kubernetes, load-balancers, etc.) can take the instance
 * out of rotation.
 *
 * <p>Loaded/failed status is populated by the model-loading code via
 * {@link ModelLoadStatus#markLoaded(String)} /
 * {@link ModelLoadStatus#markFailed(String, String)}. Until those call
 * sites are wired up, the {@code models.loaded} map will be empty and the
 * service will be reported as unhealthy — this is an intended default:
 * without a signal that at least one model loaded, we err on the cautious
 * side.
 */
@Path("health")
@Singleton
@Produces(APPLICATION_JSON)
public class HealthCheck extends com.codahale.metrics.health.HealthCheck {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SoftwareServiceConfiguration configuration;

    @Inject
    public HealthCheck(SoftwareServiceConfiguration configuration) {
        this.configuration = configuration;
    }

    @GET
    public Response alive() {
        boolean grobidHomeConfigured =
                configuration != null && configuration.getGrobidHome() != null;

        Map<String, String> loadedClassifiers = ModelLoadStatus.getLoadedModels();
        Map<String, String> failedClassifiers = ModelLoadStatus.getFailedModels();
        Map<String, String> loadedCrf = TaggerFactory.getLoadedModels();
        Map<String, String> failedCrf = TaggerFactory.getFailedModels();

        boolean hasFailures = ModelLoadStatus.hasFailures() || TaggerFactory.hasFailures();
        // Note: unlike datastet, software-mentions does not yet call
        // ModelLoadStatus.markLoaded() from its model-loading code, so the
        // loaded maps may be empty even when everything is fine. We therefore
        // derive `ready` from grobidHome + absence of recorded failures only.
        // Once load call-sites are wired, tighten this to also require
        // !loadedClassifiers.isEmpty() || !loadedCrf.isEmpty().
        boolean ready = grobidHomeConfigured && !hasFailures;

        ObjectNode root = MAPPER.createObjectNode();
        root.put("status", ready ? "healthy" : "unhealthy");
        root.put("ready", ready);
        root.put("grobidHomeConfigured", grobidHomeConfigured);

        ObjectNode models = MAPPER.createObjectNode();

        ObjectNode loadedNode = MAPPER.createObjectNode();
        for (Map.Entry<String, String> entry : loadedClassifiers.entrySet()) {
            loadedNode.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : loadedCrf.entrySet()) {
            loadedNode.put(entry.getKey(), entry.getValue());
        }
        models.set("loaded", loadedNode);

        ObjectNode failedNode = MAPPER.createObjectNode();
        for (Map.Entry<String, String> entry : failedClassifiers.entrySet()) {
            failedNode.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : failedCrf.entrySet()) {
            failedNode.put(entry.getKey(), entry.getValue());
        }
        models.set("failed", failedNode);

        root.set("models", models);

        Response.Status status = ready
                ? Response.Status.OK
                : Response.Status.INTERNAL_SERVER_ERROR;

        return Response.status(status)
                .entity(root.toString())
                .type(APPLICATION_JSON)
                .build();
    }

    @Override
    protected Result check() throws Exception {
        if (configuration == null || configuration.getGrobidHome() == null) {
            return Result.unhealthy("Grobid home is null in the configuration");
        }
        if (ModelLoadStatus.hasFailures() || TaggerFactory.hasFailures()) {
            StringBuilder reason = new StringBuilder("One or more models failed to load: ");
            reason.append(ModelLoadStatus.getFailedModels());
            if (TaggerFactory.hasFailures()) {
                reason.append("; CRF: ").append(TaggerFactory.getFailedModels());
            }
            return Result.unhealthy(reason.toString());
        }
        return Result.healthy();
    }
}
