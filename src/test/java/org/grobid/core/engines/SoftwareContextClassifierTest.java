package org.grobid.core.engines;

import org.apache.commons.io.IOUtils;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.data.SoftwareComponent;
import org.grobid.core.document.Document;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.SoftwareConfiguration;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import org.grobid.core.main.LibraryLoader;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;

/**
 * @author Patrice
 */
public class SoftwareContextClassifierTest {
    private static SoftwareConfiguration configuration;

    @BeforeClass
    public static void setUpClass() throws Exception {
        SoftwareConfiguration softwareConfiguration = null;
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            File yamlFile = new File("resources/config/config.yml");
            yamlFile = new File(yamlFile.getAbsolutePath());
            softwareConfiguration = mapper.readValue(yamlFile, SoftwareConfiguration.class);

            String pGrobidHome = softwareConfiguration.getGrobidHome();

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            System.out.println(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());

            if (softwareConfiguration != null && softwareConfiguration.getModels() != null) {
                for (ModelParameters model : softwareConfiguration.getModels())
                    GrobidProperties.getInstance().addModel(model);
            }
            LibraryLoader.load();

        } catch (final Exception exp) {
            System.err.println("GROBID software initialisation failed: " + exp);
            exp.printStackTrace();
        }

        configuration = softwareConfiguration;
    }

    @Before
    public void getTestResourcePath() {
        GrobidProperties.getInstance();
    }

    @Test
    public void testSoftwareContextClassifierText() throws Exception {
        String text = IOUtils.toString(this.getClass().getResourceAsStream("/text.txt"), StandardCharsets.UTF_8.toString());
        text = text.replaceAll("\\n", " ").replaceAll("\\t", " ");
        //List<String> texts = new ArrayList<>();
        //texts.add(text);
        String json = SoftwareContextClassifier.getInstance(configuration).classify(texts);
        System.out.println(json);
    }

}