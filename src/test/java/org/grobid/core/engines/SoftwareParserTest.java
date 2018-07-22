package org.grobid.core.engines;

import org.apache.commons.io.IOUtils;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.document.Document;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.SoftwareProperties;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.Pair;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;

/**
 * @author Patrice
 */
public class SoftwareParserTest {
    private static Engine engine;

    @BeforeClass
    public static void setUpClass() throws Exception {
        try {
            String pGrobidHome = SoftwareProperties.get("grobid.home");

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            System.out.println(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());
        } catch (final Exception exp) {
            System.err.println("GROBID software initialisation failed: " + exp);
            exp.printStackTrace();
        }

        engine = GrobidFactory.getInstance().createEngine();
    }

    @Before
    public void getTestResourcePath() {
        GrobidProperties.getInstance();
    }

    //@Test
    public void testSoftwareParserText() throws Exception {
        System.out.println("testSoftwareParserText - testSoftwareParserText - testSoftwareParserText");
        String text = IOUtils.toString(this.getClass().getResourceAsStream("/text.txt"), StandardCharsets.UTF_8.toString());
        text = text.replaceAll("\\n", " ").replaceAll("\\t", " ");
        List<SoftwareEntity> entities = SoftwareParser.getInstance().processText(text);
        //System.out.println(text);
        //System.out.println(entities.size());
        assertThat(entities, hasSize(5));
    }

    //@Test
    public void testSoftwareParserPDF() throws Exception {
        Pair<List<SoftwareEntity>, Document> res = SoftwareParser.getInstance().processPDF(new File("./src/test/resources/annot.pdf"));
        List<SoftwareEntity> entities = res.getA();

        assertThat(entities, hasSize(19));
    }

}