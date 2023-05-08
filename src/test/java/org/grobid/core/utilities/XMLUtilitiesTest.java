package org.grobid.core.utilities;

import org.apache.commons.io.IOUtils;
import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.data.Person;
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;

import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Patrice
 */
public class XMLUtilitiesTest {

    @Test
    public void testParsingBiblio1() throws Exception {
        String testString = "<biblStruct type=\"journal\" xml:id=\"pone.0278912.ref001\"><analytic><author><persName><surname>Hoffmann</surname> <forename type=\"first\">MP</forename></persName></author><author><persName><surname>Frodsham</surname> <forename type=\"first\">A</forename></persName></author></analytic><monogr><title level=\"j\">Natural enemies of vegetable insect pests</title><imprint><date type=\"year\">1993</date><biblScope unit=\"page\" from=\"63\">63</biblScope></imprint></monogr></biblStruct>";
        try {
            org.w3c.dom.Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new ByteArrayInputStream(testString.getBytes("utf-8"))));

            org.w3c.dom.Element biblStructElement = document.getDocumentElement();
            BiblioItem biblio = XMLUtilities.parseTEIBiblioItem(biblStructElement);

            System.out.println(biblio.toTEI(0));
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testParsingBiblio2() throws Exception {
        try {
            String testString = "<biblStruct type=\"journal\" xml:id=\"ppat.1011317.ref001\"><analytic><title level=\"a\" type=\"main\">Polyamines and Their Role in Virus Infection</title><author><persName><surname>Mounce</surname> <forename type=\"first\">BC</forename></persName></author><author><persName><surname>Olsen</surname> <forename type=\"first\">ME</forename></persName></author><author><persName><surname>Vignuzzi</surname> <forename type=\"first\">M</forename></persName></author><author><persName><surname>Connor</surname> <forename type=\"first\">JH</forename></persName></author></analytic><monogr><title level=\"j\">Microbiol Mol Biol Rev MMBR</title><imprint><date type=\"year\">2017</date><biblScope unit=\"page\" from=\"81\">81</biblScope></imprint></monogr></biblStruct>";
            org.w3c.dom.Document document = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new InputSource(new ByteArrayInputStream(testString.getBytes("utf-8"))));

            org.w3c.dom.Element biblStructElement = document.getDocumentElement();
            BiblioItem biblio = XMLUtilities.parseTEIBiblioItem(biblStructElement);

            System.out.println(biblio.toTEI(0));
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testParsingBiblio3() throws Exception {
        try {
            String testString = "<biblStruct type=\"book\" xml:id=\"pone.0278912.ref002\"><analytic><author><persName><surname>Fuxa</surname> <forename type=\"first\">JR</forename></persName></author><author><persName><surname>Tanada</surname> <forename type=\"first\">Y</forename></persName></author></analytic><monogr><title level=\"j\">Epizootiology of insect diseases</title><imprint><publisher>John Wiley &amp; Sons</publisher><date type=\"year\">1991</date><biblScope unit=\"page\" from=\"160\">160</biblScope><biblScope unit=\"page\" to=\"163\">163</biblScope></imprint></monogr></biblStruct>";
            org.w3c.dom.Document document = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new InputSource(new ByteArrayInputStream(testString.getBytes("utf-8"))));

            org.w3c.dom.Element biblStructElement = document.getDocumentElement();
            BiblioItem biblio = XMLUtilities.parseTEIBiblioItem(biblStructElement);

            System.out.println(biblio.toTEI(0));
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}