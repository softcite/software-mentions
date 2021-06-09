package org.grobid.core.utilities;

import org.grobid.core.utilities.GrobidConfig.ModelParameters;

public class SoftwareConfiguration {

    private String grobidHome;

    private String entityFishingHost;
    private String entityFishingPort;

    private String corpusPath;
    private String templatePath;

    private ModelParameters model;

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

    public ModelParameters getModel() {
        return model;
    }

    public void getModel(ModelParameters model) {
        this.model = model;
    }

    /*public String getEngine() {
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
    }*/

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
