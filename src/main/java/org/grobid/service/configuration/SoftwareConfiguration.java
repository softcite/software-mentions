package org.grobid.service.configuration;

import io.dropwizard.Configuration;

public class SoftwareConfiguration extends Configuration {

    private String grobidHome;

    private String entityFishingHost;
    private String entityFishingPort;

    private String tmpPath;

    private String proxyHost;
    private String proxyPort;

    private String engine;

    private String delftInstall;
    private String delftArchitecture;
    private String delftEmbeddings;

    private String corpusPath;
    private String templatePath;

    public String getGrobidHome() {
        return grobidHome;
    }

    public void setGrobidHome(String grobidHome) {
        this.grobidHome = grobidHome;
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

    public String getTmpPath() {
        return tmpPath;
    }

    public void setTmpPath(String tmpPath) {
        this.tmpPath = tmpPath;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public String getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getDelftInstall() {
        return delftInstall;
    }

    public void setDelftInstall(String delftInstall) {
        this.delftInstall = delftInstall;
    }

    public String getDelftArchitecture() {
        return delftArchitecture;
    }

    public void setDelftArchitecture(String delftArchitecture) {
        this.delftArchitecture = delftArchitecture;
    }

    public String getDelftEmbeddings() {
        return delftEmbeddings;
    }

    public void setDelftEmbeddings(String delftEmbeddings) {
        this.delftEmbeddings = delftEmbeddings;
    }

    public String getCorpusPath() {
        return corpusPath;
    }

    public void setCorpusPath(String corpusPath) {
        this.corpusPath = corpusPath;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }
}
