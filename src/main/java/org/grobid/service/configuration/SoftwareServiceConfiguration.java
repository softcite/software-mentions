package org.grobid.service.configuration;

import io.dropwizard.Configuration;
import org.grobid.core.utilities.SoftwareConfiguration;

public class SoftwareServiceConfiguration extends Configuration {

    private String grobidHome;
    private SoftwareConfiguration softwareConfiguration;

    public String getGrobidHome() {
        return grobidHome;
    }

    public void setGrobidHome(String grobidHome) {
        this.grobidHome = grobidHome;
    }

    public SoftwareConfiguration getSoftwareConfiguration() {
        return this.softwareConfiguration;
    }

    public void setSoftwareConfiguration(SoftwareConfiguration conf) {
        this.softwareConfiguration = conf;
    }
}
