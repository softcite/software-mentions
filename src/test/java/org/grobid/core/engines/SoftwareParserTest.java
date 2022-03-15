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

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;

/**
 * @author Patrice
 */
public class SoftwareParserTest {
    private static SoftwareConfiguration configuration;

    @BeforeClass
    public static void setUpClass() throws Exception {
        SoftwareConfiguration softwareConfiguration = null;
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            softwareConfiguration = mapper.readValue(new File("resources/config/config.yml"), SoftwareConfiguration.class);

            String pGrobidHome = softwareConfiguration.getGrobidHome();

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            System.out.println(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());

            if (softwareConfiguration != null && softwareConfiguration.getModel() != null) {
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
    public void testSoftwareParserText() throws Exception {
        System.out.println("testSoftwareParserText - testSoftwareParserText - testSoftwareParserText");
        String text = IOUtils.toString(this.getClass().getResourceAsStream("/text.txt"), StandardCharsets.UTF_8.toString());
        text = text.replaceAll("\\n", " ").replaceAll("\\t", " ");
        List<SoftwareEntity> entities = SoftwareParser.getInstance(configuration).processText(text, false);
        System.out.println(text);
        System.out.println(entities.size());
        assertThat(entities, hasSize(3));
    }

    //@Test
    public void testSoftwareParserPDF() throws Exception {
        Pair<List<SoftwareEntity>, Document> res = 
            SoftwareParser.getInstance(configuration).processPDF(new File("./src/test/resources/annot.pdf"), false, false);
        List<SoftwareEntity> entities = res.getLeft();

        assertThat(entities, hasSize(19));
    }

}