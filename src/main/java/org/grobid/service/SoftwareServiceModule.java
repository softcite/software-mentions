package org.grobid.service;

import com.google.inject.Provides;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.grobid.service.configuration.SoftwareServiceConfiguration;
import org.grobid.service.controller.HealthCheck;
import org.grobid.service.controller.SoftwareController;
import org.grobid.service.controller.SoftwareProcessFile;
import org.grobid.service.controller.SoftwareProcessString;
import ru.vyarus.dropwizard.guice.module.support.DropwizardAwareModule;



public class SoftwareServiceModule extends DropwizardAwareModule<SoftwareServiceConfiguration> {

    @Override
    public void configure() {
        // Generic modules
        bind(GrobidEngineInitialiser.class);
        bind(HealthCheck.class);

        // Core components
        bind(SoftwareProcessFile.class);
        bind(SoftwareProcessString.class);

        // REST
        bind(SoftwareController.class);
    }

    @Provides
    Client provideClient() {
        return ClientBuilder.newClient();
    }

}