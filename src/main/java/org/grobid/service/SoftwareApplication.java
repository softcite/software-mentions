package org.grobid.service;

import com.google.inject.Module;
import com.hubspot.dropwizard.guicier.GuiceBundle;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.grobid.service.configuration.SoftwareServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.Arrays;
import java.util.EnumSet;

public class SoftwareApplication extends Application<SoftwareServiceConfiguration> {
    private static final String RESOURCES = "/service";

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
    public void initialize(Bootstrap<SoftwareServiceConfiguration> bootstrap) {
        GuiceBundle<SoftwareServiceConfiguration> guiceBundle = GuiceBundle.defaultBuilder(SoftwareServiceConfiguration.class)
                .modules(getGuiceModules())
                .build();
        bootstrap.addBundle(guiceBundle);
        bootstrap.addBundle(new MultiPartBundle());
        bootstrap.addBundle(new AssetsBundle("/web", "/", "index.html", "assets"));
        //bootstrap.addCommand(new CreateTrainingCommand());
    }

    @Override
    public void run(SoftwareServiceConfiguration configuration, Environment environment) {
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
