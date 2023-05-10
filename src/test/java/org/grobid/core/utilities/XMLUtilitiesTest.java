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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
import org.apache.commons.lang3.tuple.Pair;

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

            //System.out.println(biblio.toTEI(0));
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

            //System.out.println(biblio.toTEI(0));
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

            //System.out.println(biblio.toTEI(0));
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testXMLParagraphContentParsing() throws Exception {
        try {
            String testString = "<p>RNA was purified and prepared as described from Huh7 cells treated for 96h with DFMO or infected for 24h with CVB3. Libraries were prepared by the University of Chicago Genomics Facility and analyzed by Illumina NovaSeq 6000. Read quality was evaluated using FastQC (v0.11.5). Adapters were trimmed in parallel to a quality trimming (bbduk, <ref type=\"uri\" target=\"http://sourceforge.net/projects/bbmap\">sourceforge.net/projects/bbmap</ref>/). All remaining sequences were mapped against the human reference genome build 38 with STAR (v2.5.2b) [<ref type=\"bibr\" target=\"#ppat.1011317.ref049\">49</ref>]. HTseq (v0.6.1) was used to count all reads for each gene and set up a read count table [<ref type=\"bibr\" target=\"#ppat.1011317.ref050\">50</ref>]. Differential gene expression analyses were performed using the DESeq2 Bioconductor package (v1.30.1) [<ref type=\"bibr\" target=\"#ppat.1011317.ref051\">51</ref>]. The default “ashr” shrinkage (v2.2–47) [<ref type=\"bibr\" target=\"#ppat.1011317.ref052\">52</ref>] set up was used for our analysis. Gene set enrichment analysis (GSEA) was performed with the fgsea Bioconductor package [<ref type=\"bibr\" target=\"#ppat.1011317.ref053\">53</ref>], using Hallmark gene sets downloaded from the Molecular Signatures Database [<ref type=\"bibr\" target=\"#ppat.1011317.ref054\">54</ref>].</p>";
            org.w3c.dom.Document document = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new InputSource(new ByteArrayInputStream(testString.getBytes("utf-8"))));
            org.w3c.dom.Element element = document.getDocumentElement();

            Pair<String,Map<String,Pair<OffsetPosition,String>>> contentTextAndRef = 
                XMLUtilities.getTextNoRefMarkersAndMarkerPositions(element, 0);

            String contentText = UnicodeUtil.normaliseText(contentTextAndRef.getLeft());
            Map<String,Pair<OffsetPosition,String>> refInfos = contentTextAndRef.getRight();

            //System.out.println(contentText);

            for (Map.Entry<String,Pair<OffsetPosition,String>> entry : refInfos.entrySet()) {
                String bibString = entry.getKey();
                Pair<OffsetPosition,String> bibValue = entry.getValue();

                OffsetPosition refMarkerPosition = bibValue.getLeft();
                String refMarkerKey = bibValue.getRight();
                System.out.println(bibString + "/" + refMarkerKey + " at " + refMarkerPosition.start + "-" + refMarkerPosition.end + " in: " + contentText + " / " + 0 +"-"+(contentText.length()));
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}