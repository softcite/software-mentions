package org.grobid.service;

import com.google.inject.Module;
import com.hubspot.dropwizard.guicier.GuiceBundle;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.grobid.service.configuration.SoftwareConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.Arrays;
import java.util.EnumSet;

public class SoftwareApplication extends Application<SoftwareConfiguration> {
    private static final String RESOURCES = "/service";
    //private static final String RESOURCES = "";

    private static final Logger LOGGER = LoggerFactory.getLogger(SoftwareApplication.class);

    public static void main(String[] args) throws Exception {
        new SoftwareApplication().run(args);
    }

    @Override
    public String getName() {
        return "software-mentions";
    }

    private Iterable<? extends Module> getGuiceModules() {
        return Arrays.asList(new SoftwareServiceModule());
    }

    @Override
    public void initialize(Bootstrap<SoftwareConfiguration> bootstrap) {
        GuiceBundle<SoftwareConfiguration> guiceBundle = GuiceBundle.defaultBuilder(SoftwareConfiguration.class)
                .modules(getGuiceModules())
                .build();
        bootstrap.addBundle(guiceBundle);
        bootstrap.addBundle(new MultiPartBundle());
        bootstrap.addBundle(new AssetsBundle("/web", "/", "index.html", "assets"));
    }

    @Override
    public void run(SoftwareConfiguration configuration, Environment environment) {
        // Enable CORS headers
        final FilterRegistration.Dynamic cors =
                environment.servlets().addFilter("CORS", CrossOriginFilter.class);

        // Configure CORS parameters
        cors.setInitParameter("allowedOrigins", "*");
        cors.setInitParameter("allowedHeaders", "X-Requested-With,Content-Type,Accept,Origin");
        cors.setInitParameter("allowedMethods", "OPTIONS,GET,PUT,POST,DELETE,HEAD");

        // Add URL mapping
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

        environment.jersey().setUrlPattern(RESOURCES + "/*");
    }
}
