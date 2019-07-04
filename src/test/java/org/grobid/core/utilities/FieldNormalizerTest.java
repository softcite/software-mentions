package org.grobid.core.utilities;

import org.apache.commons.io.IOUtils;
import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.document.Document;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.SoftwareProperties;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.Pair;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Patrice
 */
public class FieldNormalizerTest {

    @Test
    public void testVersion1() throws Exception {
        String testString = "version 1.1";
        String resString = FieldNormalizer.normalizeVersionNumber(testString);
        assertEquals("1.1", resString);
    }
    
    @Test
    public void testVersion2() throws Exception {
        String testString = "Version 5.0    ";
        String resString = FieldNormalizer.normalizeVersionNumber(testString);
        assertEquals("5.0", resString);
    }

    @Test
    public void testVersion3() throws Exception {
        String testString = "1.31 version";
        String resString = FieldNormalizer.normalizeVersionNumber(testString);
        assertEquals("1.31", resString);
    }

    @Test
    public void testVersion4() throws Exception {
        String testString = "v.9.3";
        String resString = FieldNormalizer.normalizeVersionNumber(testString);
        assertEquals("9.3", resString);
    }

    @Test
    public void testVersion5() throws Exception {
        String testString = "v0.9.6";
        String resString = FieldNormalizer.normalizeVersionNumber(testString);
        assertEquals("0.9.6", resString);
    }

    @Test
    public void testVersion6() throws Exception {
        String testString = "ver.12.0";
        String resString = FieldNormalizer.normalizeVersionNumber(testString);
        assertEquals("12.0", resString);
    }

    @Test
    public void testVersion7() throws Exception {
        String testString = "ver.13 ";
        String resString = FieldNormalizer.normalizeVersionNumber(testString);
        assertEquals("13", resString);
    }

    @Test
    public void testVersion8() throws Exception {
        String testString = "release  1.31";
        String resString = FieldNormalizer.normalizeVersionNumber(testString);
        assertEquals("1.31", resString);
    }

    @Test
    public void testUrl1() throws Exception {
        String testString = "(code available at http:// goddardlab.auckland.ac.nz/data-and-code/)";
        String resString = FieldNormalizer.normalizeUrl(testString);
        assertEquals("http://goddardlab.auckland.ac.nz/data-and-code/", resString);
    }

    @Test
    public void testUrl2() throws Exception {
        String testString = "(http://www.superarray.com/ pcr/arrayanalysis.php),";
        String resString = FieldNormalizer.normalizeUrl(testString);
        assertEquals("http://www.superarray.com/pcr/arrayanalysis.php", resString);
    }

    @Test
    public void testUrl3() throws Exception {
        String testString = "http:// biomoby.open-bio.org/CVS_CONTENT/moby-live/Java/docs/ Moses-generators.html";
        String resString = FieldNormalizer.normalizeUrl(testString);
        assertEquals("http://biomoby.open-bio.org/CVS_CONTENT/moby-live/Java/docs/Moses-generators.html", resString);
    }

    @Test
    public void testUrl4() throws Exception {
        String testString = "random.org";
        String resString = FieldNormalizer.normalizeUrl(testString);
        assertEquals("random.org", resString);
    }

    @Test
    public void testCreator1() throws Exception {
        String testString = "SPSS Inc., Chicago IL, USA";
        String resString = FieldNormalizer.normalizeCreator(testString);
        assertEquals("SPSS Inc.", resString);
    }

    @Test
    public void testCreator2() throws Exception {
        String testString = "(Bruker Corporation, Santa Barbara, CA, USA)";
        String resString = FieldNormalizer.normalizeCreator(testString);
        assertEquals("Bruker Corporation", resString);
    }

    @Test
    public void testCreator3() throws Exception {
        String testString = "IBM Corporation";
        String resString = FieldNormalizer.normalizeCreator(testString);
        assertEquals("IBM Corporation", resString);
    }

    @Test
    public void testCreator4() throws Exception {
        String testString = "STATA Corp LP. Package";
        String resString = FieldNormalizer.normalizeCreator(testString);
        assertEquals("STATA Corp", resString);
    }



}