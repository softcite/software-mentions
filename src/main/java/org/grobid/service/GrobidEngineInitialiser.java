package org.grobid.service;

import com.google.common.collect.ImmutableList;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.GrobidProperties;
//import org.grobid.core.utilities.GrobidPropertyKeys;
import org.grobid.core.engines.tagging.GrobidCRFEngine;
import org.grobid.core.lexicon.SoftwareLexicon;
import org.grobid.service.configuration.SoftwareServiceConfiguration;
import org.grobid.core.utilities.SoftwareConfiguration;

import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GrobidEngineInitialiser {
    private static final Logger LOGGER = LoggerFactory.getLogger(org.grobid.service.GrobidEngineInitialiser.class);

    @Inject
    public GrobidEngineInitialiser(SoftwareServiceConfiguration configuration) {
        LOGGER.info("Initialising Grobid");
        GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(ImmutableList.of(configuration.getGrobidHome()));
        GrobidProperties.getInstance(grobidHomeFinder);
        SoftwareLexicon.getInstance();

        SoftwareConfiguration softwareConfiguration = null;
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            softwareConfiguration = mapper.readValue(new File("resources/config/config.yml"), SoftwareConfiguration.class);
        } catch(Exception e) {
            LOGGER.error("The config file does not appear valid, see resources/config/config.yml", e);
            softwareConfiguration = null;
        }

        configuration.setSoftwareConfiguration(softwareConfiguration);

        if (softwareConfiguration != null && softwareConfiguration.getModel() != null)
            GrobidProperties.addModel(softwareConfiguration.getModel());
        LibraryLoader.load();
    }
}
