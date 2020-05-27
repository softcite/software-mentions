package org.grobid.core.lexicon;

import org.apache.commons.io.IOUtils;
import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.document.Document;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.SoftwareConfiguration;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.Pair;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * @author Patrice
 */
public class SoftwareLexiconTest {
    private static SoftwareLexicon softwareLexicon;

    @BeforeClass
    public static void setUpClass() throws Exception {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            SoftwareConfiguration conf = mapper.readValue(new File("resources/config/config.yml"), SoftwareConfiguration.class);

            String pGrobidHome = conf.getGrobidHome();

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            System.out.println(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());
        } catch (final Exception exp) {
            System.err.println("GROBID software initialisation failed: " + exp);
            exp.printStackTrace();
        }

        softwareLexicon = SoftwareLexicon.getInstance();
    }

    @Test
    public void testTokenPositionsSoftwareNames() throws Exception {
        String testString = "The next step is to install GROBID version 0.5.4.";

        List<LayoutToken> tokens = SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(testString);
        List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(tokens);

        /*for(OffsetPosition position : softwareTokenPositions) {
            for(int i=position.start; i <= position.end; i++)
                System.out.print(tokens.get(i));
            System.out.println(" / " + position.start + " " + position.end);
        }*/
        assertThat(softwareTokenPositions, hasSize(1));
    }

    @Test
    public void testTokenPositionsSoftwareNameShort() throws Exception {
        String testString = "The next step is to install Libreoffice version 12.1";

        List<LayoutToken> tokens = SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(testString);
        List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(tokens);

        /*for(OffsetPosition position : softwareTokenPositions) {
            for(int i=position.start; i <= position.end; i++)
                System.out.print(tokens.get(i));
            System.out.println(" / " + position.start + " " + position.end);
        }*/
        assertThat(softwareTokenPositions, hasSize(1));
    }

    @Test
    public void testTokenPositionsSoftwareNameComplex() throws Exception {
        String testString = "The next step is to install LibreOffice Draw version 12.1 and LibreOffice Math version 0.9.";

        List<LayoutToken> tokens = SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(testString);
        List<OffsetPosition> softwareTokenPositions = softwareLexicon.tokenPositionsSoftwareNames(tokens);

        /*for(OffsetPosition position : softwareTokenPositions) {
            for(int i=position.start; i <= position.end; i++) 
                System.out.print(tokens.get(i));
            System.out.println(" / " + position.start + " " + position.end);
        }*/
        assertThat(softwareTokenPositions, hasSize(4));
    }

    @Test
    public void testTokenPositionsUrl() throws Exception {
        String testString = "The next step is to install LibreOffice Draw version 12.1 from https://www.libreoffice.org/ online";

        List<LayoutToken> tokens = SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(testString);
        List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(tokens);

        /*for(OffsetPosition position : urlPositions) {
            for(int i=position.start; i <= position.end; i++) 
                System.out.print(tokens.get(i));
            System.out.println(" / " + position.start + " " + position.end);
        }*/
        assertThat(urlPositions, hasSize(1));
    }

    @Test
    public void testTokenPositionsUrlComplex() throws Exception {
        String testString = "The next step is to install LibreOffice Draw version 12.1 from https://www.libreoffice.org/download/download/ online";

        List<LayoutToken> tokens = SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(testString);
        List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(tokens);

        /*for(OffsetPosition position : urlPositions) {
            for(int i=position.start; i <= position.end; i++) 
                System.out.print(tokens.get(i));
            System.out.println(" / " + position.start + " " + position.end);
        }*/
        assertThat(urlPositions, hasSize(1));
    }

    @Test
    public void testTokenPositionsUrlFromLabels() throws Exception {
        List<Pair<String, String>> labeled = new ArrayList<Pair<String, String>>();

        String testString = "The next step is to install LibreOffice Draw version 12.1 from https://www.libreoffice.org/download/download/ online";

        List<LayoutToken> tokens = SoftwareAnalyzer.getInstance().tokenizeWithLayoutToken(testString);
        for(LayoutToken token : tokens) {
            Pair<String, String> pair = new Pair<String, String>(token.getText(), "toto");
            labeled.add(pair);
        }

        List<OffsetPosition> urlPositions = softwareLexicon.tokenPositionsUrlVectorLabeled(labeled);

        /*for(OffsetPosition position : urlPositions) {
            for(int i=position.start; i <= position.end; i++) 
                System.out.print(labeled.get(i).getA());
            System.out.println(" / " + position.start + " " + position.end);
        }*/
        assertThat(urlPositions, hasSize(1));
    }

}