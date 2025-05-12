package org.grobid.service.configuration;

import io.dropwizard.core.Configuration;
import org.grobid.core.utilities.GrobidConfig;
import org.grobid.core.utilities.SoftwareConfiguration;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class SoftwareServiceConfiguration extends Configuration {

    private String grobidHome;
    private SoftwareConfiguration softwareConfiguration;
    private int maxParallelRequests;
    private String version;

    private String entityFishingHost;
    private String entityFishingPort;

    private String corpusPath;
    private String tmpPath;

    private String pub2teiPath;
    private Boolean useBinaryContextClassifiers;

    private List<GrobidConfig.ModelParameters> models = new ArrayList<>();

    @JsonProperty
    private String corsAllowedOrigins = "*";

    @JsonProperty
    private String corsAllowedMethods = "OPTIONS,GET,PUT,POST,DELETE,HEAD";

    @JsonProperty
    private String corsAllowedHeaders = "X-Requested-With,Content-Type,Accept,Origin";

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

    public int getMaxParallelRequests() {
        if (this.maxParallelRequests == 0) {
            this.maxParallelRequests = Runtime.getRuntime().availableProcessors();
        }
        return this.maxParallelRequests;
    }

    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(String corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public String getCorsAllowedMethods() {
        return corsAllowedMethods;
    }

    public void setCorsAllowedMethods(String corsAllowedMethods) {
        this.corsAllowedMethods = corsAllowedMethods;
    }

    public String getCorsAllowedHeaders() {
        return corsAllowedHeaders;
    }

    public void setCorsAllowedHeaders(String corsAllowedHeaders) {
        this.corsAllowedHeaders = corsAllowedHeaders;
    }

    public String getVersion() {
        return version;
    }

    public String getEntityFishingHost() {
        return entityFishingHost;
    }

    public void setEntityFishingHost(String entityFishingHost) {
        this.entityFishingHost = entityFishingHost;
    }

    public String getEntityFishingPort() {
        return entityFishingPort;
    }

    public void setEntityFishingPort(String entityFishingPort) {
        this.entityFishingPort = entityFishingPort;
    }

    public String getCorpusPath() {
        return corpusPath;
    }

    public void setCorpusPath(String corpusPath) {
        this.corpusPath = corpusPath;
    }

    public String getTmpPath() {
        return tmpPath;
    }

    public void setTmpPath(String tmpPath) {
        this.tmpPath = tmpPath;
    }

    public String getPub2teiPath() {
        return pub2teiPath;
    }

    public void setPub2teiPath(String pub2teiPath) {
        this.pub2teiPath = pub2teiPath;
    }

    public Boolean getUseBinaryContextClassifiers() {
        return useBinaryContextClassifiers;
    }

    public void setUseBinaryContextClassifiers(Boolean useBinaryContextClassifiers) {
        this.useBinaryContextClassifiers = useBinaryContextClassifiers;
    }

    public List<GrobidConfig.ModelParameters> getModels() {
        return models;
    }

    public void setModels(List<GrobidConfig.ModelParameters> models) {
        this.models = models;
    }
}
