package org.grobid.core.engines;

import java.util.List;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.utilities.*;
import org.grobid.core.jni.PythonEnvironmentConfig;
import org.grobid.core.jni.DeLFTClassifierModel;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

/**
 * Use a Deep Learning multiclass and multilabel classifier to characterize the context of a recognized software mention. 
 * This classifier predicts if the software introduced by a software mention in a sentence is likely:
 * - used or not by the described work (class used)
 * - a contribution of the described work (class contribution)
 * - shared (class shared)
 *
 * The prediction uses the sentence where the mention appears (sentence is context here).
 * Then given n mentions of the same software in a document, we have n predictions and we can derived from this
 * the nature of the software mention at document level. 
 */
public class SoftwareContextClassifier {
    private static final Logger logger = LoggerFactory.getLogger(SoftwareContextClassifier.class);

    private DeLFTClassifierModel classifier = null;
    private SoftwareConfiguration softwareConfiguration;

    private static volatile SoftwareContextClassifier instance;

    public static SoftwareContextClassifier getInstance(SoftwareConfiguration configuration) {
        if (instance == null) {
            getNewInstance(configuration);
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance(SoftwareConfiguration configuration) {
        instance = new SoftwareContextClassifier(configuration);
    }

    private SoftwareContextClassifier(SoftwareConfiguration configuration) {
        ModelParameters parameter = configuration.getModel("software_context");
        this.classifier = new DeLFTClassifierModel("software_context", parameter.delft.architecture);

    }

    /**
     * Classify a simple piece of text
     * @return list of predicted labels/scores pairs
     */
    public String classify(String text) throws Exception {
        if (StringUtils.isEmpty(text))
            return null;
        List<String> texts = new ArrayList<String>();
        texts.add(text);
        return classify(texts);
    }

    /**
     * Classify an array of texts
     * @return list of predicted labels/scores pairs for each text
     */
    public String classify(List<String> texts) throws Exception {
        if (texts == null || texts.size() == 0)
            return null;
        logger.info("classify: " + texts.size() + " sentence(s)");

        String the_json = this.classifier.classify(texts);
        System.out.println(the_json);

        return the_json;
    }

}