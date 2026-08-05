package org.grobid.service;

import com.google.inject.AbstractModule;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.forms.MultiPartBundle;
import org.eclipse.jetty.server.handler.CrossOriginHandler;
import org.eclipse.jetty.server.handler.QoSHandler;
import org.grobid.service.configuration.SoftwareServiceConfiguration;
import org.grobid.service.controller.HealthCheck;
import ru.vyarus.dropwizard.guice.GuiceBundle;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SoftwareApplication extends Application<SoftwareServiceConfiguration> {
    private static final String RESOURCES = "/service";

    @Override
    public String getName() {
        return "software-mentions";
    }

    private AbstractModule getGuiceModules() {
        return new SoftwareServiceModule();
    }

    @Override
    public void initialize(Bootstrap<SoftwareServiceConfiguration> bootstrap) {
        GuiceBundle guiceBundle = GuiceBundle.builder()
                .modules(getGuiceModules())
                .build();
        bootstrap.addBundle(guiceBundle);
        bootstrap.addBundle(new MultiPartBundle());
        bootstrap.addBundle(new AssetsBundle("/web", "/", "index.html", "assets"));
        //bootstrap.addCommand(new CreateCommands());
    }

    @Override
    public void run(SoftwareServiceConfiguration configuration, Environment environment) {
        environment.healthChecks().register("health-check", new HealthCheck(configuration));

        environment.jersey().setUrlPattern(RESOURCES + "/*");

        // Enable CORS via Jetty 12's CrossOriginHandler (replaces the removed
        // org.eclipse.jetty.servlets.CrossOriginFilter). Inserted above the application
        // context so it applies to all served paths.
        CrossOriginHandler cors = new CrossOriginHandler();
        cors.setAllowedOriginPatterns(toSet(configuration.getCorsAllowedOrigins()));
        cors.setAllowedMethods(toSet(configuration.getCorsAllowedMethods()));
        cors.setAllowedHeaders(toSet(configuration.getCorsAllowedHeaders()));
        cors.setAllowCredentials(false);
        environment.getApplicationContext().insertHandler(cors);

        // Limit concurrent requests via Jetty 12's QoSHandler (replaces the removed
        // org.eclipse.jetty.servlets.QoSFilter). A non-positive value means unlimited.
        int maxParallelRequests = configuration.getMaxParallelRequests();
        if (maxParallelRequests > 0) {
            QoSHandler qos = new QoSHandler();
            qos.setMaxRequestCount(maxParallelRequests);
            environment.getApplicationContext().insertHandler(qos);
        }
    }

    /**
     * Splits a comma-separated config value (e.g. "OPTIONS,GET,POST") into a set of trimmed,
     * non-empty entries preserving order, as expected by {@link CrossOriginHandler}.
     */
    private static Set<String> toSet(String csv) {
        if (csv == null) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static void main(String[] args) throws Exception {
        new SoftwareApplication().run(args);
    }
}
