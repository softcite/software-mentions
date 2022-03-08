package org.grobid.core.utilities;

import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SoftwareConfiguration {

    private String grobidHome;

    private String entityFishingHost;
    private String entityFishingPort;

    private String corpusPath;
    private String templatePath;
    private String tmpPath;
    private String pub2teiPath;

    //private ModelParameters model;
    private List<ModelParameters> models;

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

    public List<ModelParameters> getModels() {
        return models;
    }

    public ModelParameters getModel() {
        // by default return the software mention sequence labeling model
        return getModel("software");
    }

    public ModelParameters getModel(String modelName) {
        for(ModelParameters parameters : models) {
            if (parameters.name.equals(modelName)) {
                return parameters;
            }
        }
        return null;
    }

    public void setModels(List<ModelParameters> models) {
        this.models = models;
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

    public String getTmpPath() {
        return tmpPath;
    }

    public void setTmpPath(String tmpPath) {
        this.tmpPath = tmpPath;
    }

    public String getPub2TEIPath() {
        return this.pub2teiPath;
    }

    public void setPub2teiPath(String pub2teiPath) {
        this.pub2teiPath = pub2teiPath;
    }
}
