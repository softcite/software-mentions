 package org.grobid.trainer;

import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.ArticleUtilities;
import org.grobid.core.engines.Engine;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.document.xml.XmlBuilderUtils;

import org.grobid.trainer.SoftciteAnnotation.AnnotationType;
import org.grobid.core.engines.SoftwareParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import java.text.NumberFormat;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.apache.commons.io.*;
import org.apache.commons.csv.*;
import org.apache.commons.lang3.tuple.Pair;

import java.net.URI;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

import org.xml.sax.InputSource;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;
import javax.xml.xpath.*;

import nu.xom.*;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;

/**
 * Post-process an existing TEI XML package version of the corpus which has been manually reviewed/reconciled.
 * Updated information, when possible, will be: 
 * - addition of curation level class description at corpus level
 * - addition of corpus document without any annotation, as present in the CSV export corpus
 * - addition of curation class information at document level  
 *
 * Example usage:
 * > ./gradlew post_process_corpus_no_mention -Pxml=/home/lopez/grobid/software-mentions/resources/dataset/software/corpus/all_clean_post_processed.tei.xml -Pcsv=/home/lopez/tools/softcite-dataset/data/csv_dataset/ -Ppdf=/home/lopez/tools/softcite-dataset/pdf/ -Poutput=/home/lopez/grobid/software-mentions/resources/dataset/software/corpus/all_clean_post_processed_no_mention.tei.xml 
 *
 */
public class XMLCorpusPostProcessorNoMention {
    private static final Logger logger = LoggerFactory.getLogger(XMLCorpusPostProcessorNoMention.class);

    static Charset UTF_8 = Charset.forName("UTF-8"); // StandardCharsets.UTF_8

    private ArticleUtilities articleUtilities = new ArticleUtilities();

    /**
     * Inject curation class description, document entries without mention and curation class at document level     
     */
    public void process(String xmlCorpusPath, String csvPath, String pdfPath, String newXmlCorpusPath) throws IOException {
        
        Map<String, AnnotatedDocument> documents = new HashMap<String, AnnotatedDocument>();
        Map<String, SoftciteAnnotation> annotations = new HashMap<String, SoftciteAnnotation>();

        AnnotatedCorpusGeneratorCSV converter = new AnnotatedCorpusGeneratorCSV();
        converter.importCSVFiles(csvPath, documents, annotations);
        // documents without annotation are present with a "dummy" annotation 
        
        // we unfortunately need to use DOM to update the XML file which is always a lot of pain
        String tei = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            tei = FileUtils.readFileToString(new File(xmlCorpusPath), UTF_8);

            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(tei)));
            document = enrichTEIDocumentNoMention(document, documents, pdfPath);
            //document = enrichTEIDocument(document, documents, pdfPath);

            tei = XMLCorpusPostProcessor.serialize(document, null);
        } catch(ParserConfigurationException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        } catch(Exception e) {
            e.printStackTrace();
        } 

        // write updated TEI file
        if (tei != null) {
            FileUtils.writeStringToFile(new File(newXmlCorpusPath), tei, UTF_8);
        }
    }

    private org.w3c.dom.Document enrichTEIDocument(org.w3c.dom.Document document, 
                                                   Map<String, AnnotatedDocument> documents,
                                                   String documentPath) {
        org.w3c.dom.Element documentRoot = document.getDocumentElement();
        XPathFactory xpathFactory = XPathFactory.newInstance();

        try {
            XPath xpath = xpathFactory.newXPath();
            XPathExpression expr = xpath.compile("//TEI");
            //XPathExpression expr = xpath.compile("//*[@id='"+docName+"']");
            NodeList result = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
            NodeList nodes = (NodeList) result;
            for(int i=0; i < nodes.getLength(); i++) {
                org.w3c.dom.Element teiElement = (org.w3c.dom.Element)nodes.item(i);

                org.w3c.dom.Element teiHeaderElement = XMLCorpusPostProcessor.getFirstDirectChild(teiElement, "teiHeader");
                if (teiHeaderElement != null) {
                    org.w3c.dom.Element fileDescElement = XMLCorpusPostProcessor.getFirstDirectChild(teiHeaderElement, "fileDesc");
                    if (fileDescElement != null) {
                        String docId = fileDescElement.getAttribute("xml:id");
                        //System.out.println(docId);

                        // add curation class information
                        org.w3c.dom.Element profileDesc = document.createElement("profileDesc");

                        org.w3c.dom.Element textClass = document.createElement("textClass");
                        org.w3c.dom.Element catRef = document.createElement("catRef");

                        String catCuration = "with_reconciliation_and_scripts";
                        catRef.setAttribute("target", "#"+catCuration);

                        textClass.appendChild(catRef);

                        AnnotatedDocument softciteDocument = documents.get(docId);
                        if (softciteDocument == null)
                            continue;

                        List<SoftciteAnnotation> localAnnotations = softciteDocument.getAnnotations();

                        // number of annotators
                        List<String> annotators = new ArrayList<String>();
                        for(SoftciteAnnotation localAnnotation : localAnnotations) {
                            String annotatorID = localAnnotation.getAnnotatorID();
                            if (!annotators.contains(annotatorID)) {
                                annotators.add(annotatorID);
                            }
                        }

                        if (annotators.size() == 1) {
                            org.w3c.dom.Element catRef2 = document.createElement("catRef");

                            catCuration = "unique_annotator";
                            catRef2.setAttribute("target", "#"+catCuration);

                            textClass.appendChild(catRef2);
                        } else if (annotators.size() > 1) {
                            org.w3c.dom.Element catRef2 = document.createElement("catRef");

                            catCuration = "multiple_annotator";
                            catRef2.setAttribute("target", "#"+catCuration);

                            textClass.appendChild(catRef2);
                        }

                        profileDesc.appendChild(textClass);
                        teiHeaderElement.appendChild(profileDesc);
                    }
                }
            }
        } catch(XPathExpressionException e) {
            e.printStackTrace();
        }
        return document;
    }

    private org.w3c.dom.Document enrichTEIDocumentNoMention(org.w3c.dom.Document document, 
                                                   Map<String, AnnotatedDocument> documents,
                                                   String documentPath) {

        org.w3c.dom.Element documentRoot = document.getDocumentElement();
        Engine engine = GrobidFactory.getInstance().getEngine();
        XPathFactory xpathFactory = XPathFactory.newInstance();
        int m = 0;
        for (Map.Entry<String, AnnotatedDocument> entry : documents.entrySet()) {
            /*if (m > 100) {
                break;
            }
            m++;*/
            String docName = entry.getKey();

            // ensure that the document is not already present 
            try {
                XPath xpath = xpathFactory.newXPath();
                XPathExpression expr = xpath.compile("//*[@id='"+docName+"']");
                NodeList result = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
                NodeList nodes = (NodeList) result;
                if (nodes.getLength() != 0) {
                    // document already present, nothing to do !
                    continue;
                }
            } catch(XPathExpressionException e) {
                e.printStackTrace();
            } catch(Exception e) {
                e.printStackTrace();
            }
            
            AnnotatedDocument softciteDocument = entry.getValue();

            List<SoftciteAnnotation> localAnnotations = softciteDocument.getAnnotations();
            if (localAnnotations == null) {
                System.out.println(" **** Warning **** document with null localAnnotation object");
            }
//System.out.println(docName + " - " + localAnnotations.size() + " annotations");
            /*if (localAnnotations.size() == 1) {
                System.out.println(docName + " - " + localAnnotations.get(0).getType());
            }*/
            if (localAnnotations != null && localAnnotations.size() == 1 && localAnnotations.get(0).getType() == AnnotationType.DUMMY) {
//System.out.println(docName + " - " + localAnnotations.get(0).getType());
                File pdfFile = AnnotatedCorpusGeneratorCSV.getPDF(documentPath, docName, articleUtilities);

                // process header with consolidation to get some nice header metadata for this document
                BiblioItem biblio = new BiblioItem();
                GrobidAnalysisConfig configHeader = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder()
                                        .startPage(0)
                                        .endPage(2)
                                        .consolidateHeader(1)
                                        .build();
                try {
                    engine.processHeader(pdfFile.getPath(), configHeader, biblio);
                } catch(Exception e) {
                    e.printStackTrace();
                }
                if (biblio.getTitle() == null || biblio.getTitle().trim().length() ==0) {
                    // get metadata by consolidation
                    if (docName.startsWith("10."))
                        biblio.setDOI(docName.replace("%2F", "/"));
                    else if (docName.startsWith("PMC"))
                        biblio.setPMCID(docName);

                    // consolidation
                    biblio = engine.getParsers().getHeaderParser().consolidateHeader(biblio, 1);
                }

                //if (biblio.getTitle() == null || biblio.getTitle().trim().length() ==0) 
                //    continue;

                AnnotatedDocument annotatedDocument = entry.getValue();
                annotatedDocument.setBiblio(biblio);

                // number of annotators
                List<String> annotators = new ArrayList<String>();
                for(SoftciteAnnotation localAnnotation : localAnnotations) {
                    String annotatorID = localAnnotation.getAnnotatorID();
                    if (!annotators.contains(annotatorID)) {
                        annotators.add(annotatorID);
                    }
                }

                nu.xom.Element root = null;
                if (annotators.size() > 1) {
                    root = SoftwareParser.getTEIHeaderSimple(docName, biblio, "multiple_annotator");
                } else {
                    root = SoftwareParser.getTEIHeaderSimple(docName, biblio, "unique_annotator");
                }
                // empty body for TEI conformance

                nu.xom.Element textNode = teiElement("text");
                textNode.addAttribute(new Attribute("xml:lang", "http://www.w3.org/XML/1998/namespace", "en"));

                nu.xom.Element body = teiElement("body");                
                textNode.appendChild(body);

                root.appendChild(textNode);

                // convert to DOM
                String fragmentXml = XmlBuilderUtils.toXml(root);
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    org.w3c.dom.Document fragmentDocument = builder.parse(new InputSource(new StringReader(fragmentXml)));

                    // import fragment document into the main document (true argument is for deep import)
                    org.w3c.dom.Node importedFragmentNode = document.importNode(fragmentDocument.getDocumentElement(), true);

                    // inject extra document info
                    String articleSet = annotatedDocument.getArticleSet();
                    if (articleSet != null) {
                        ((org.w3c.dom.Element)importedFragmentNode).setAttribute("subtype", articleSet.replace("_article", ""));
                    }
                    ((org.w3c.dom.Element)importedFragmentNode).setAttribute("type", "article");

                    documentRoot.appendChild(importedFragmentNode);

                } catch(ParserConfigurationException e) {
                    e.printStackTrace();
                } catch(IOException e) {
                    e.printStackTrace();
                } catch(Exception e) {
                    e.printStackTrace();
                } 
            } else {
                // we still need to add the curation level information/class for the documents having annotations
                // get the document node
                File pdfFile = AnnotatedCorpusGeneratorCSV.getPDF(documentPath, docName, articleUtilities);

                // process header with consolidation to get some nice header metadata for this document
                BiblioItem biblio = new BiblioItem();
                GrobidAnalysisConfig configHeader = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder()
                                        .startPage(0)
                                        .endPage(2)
                                        .consolidateHeader(1)
                                        .build();
                try {
                    engine.processHeader(pdfFile.getPath(), configHeader, biblio);
                } catch(Exception e) {
                    e.printStackTrace();
                }
                if (biblio.getTitle() == null || biblio.getTitle().trim().length() ==0) {
                    // get metadata by consolidation
                    if (docName.startsWith("10."))
                        biblio.setDOI(docName.replace("%2F", "/"));
                    else if (docName.startsWith("PMC"))
                        biblio.setPMCID(docName);

                    // consolidation
                    biblio = engine.getParsers().getHeaderParser().consolidateHeader(biblio, 1);
                }

                //if (biblio.getTitle() == null || biblio.getTitle().trim().length() ==0) 
                //    continue;

                AnnotatedDocument annotatedDocument = entry.getValue();
                annotatedDocument.setBiblio(biblio);

                // number of annotators
                List<String> annotators = new ArrayList<String>();
                if (localAnnotations != null) {
                    for(SoftciteAnnotation localAnnotation : localAnnotations) {
                        String annotatorID = localAnnotation.getAnnotatorID();
                        if (!annotators.contains(annotatorID)) {
                            annotators.add(annotatorID);
                        }
                    }
                }

                nu.xom.Element root = null;
                if (annotators.size() > 1) {
                    root = SoftwareParser.getTEIHeaderSimple(docName, biblio, "multiple_annotator");
                } else {
                    root = SoftwareParser.getTEIHeaderSimple(docName, biblio, "unique_annotator");
                }
                // empty body for TEI conformance

                nu.xom.Element textNode = teiElement("text");
                textNode.addAttribute(new Attribute("xml:lang", "http://www.w3.org/XML/1998/namespace", "en"));

                nu.xom.Element body = teiElement("body");                
                textNode.appendChild(body);

                root.appendChild(textNode);

                // we could imagine add the 

                // convert to DOM
                String fragmentXml = XmlBuilderUtils.toXml(root);
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    org.w3c.dom.Document fragmentDocument = builder.parse(new InputSource(new StringReader(fragmentXml)));

                    // import fragment document into the main document (true argument is for deep import)
                    org.w3c.dom.Node importedFragmentNode = document.importNode(fragmentDocument.getDocumentElement(), true);

                    // inject extra document info
                    String articleSet = annotatedDocument.getArticleSet();
                    if (articleSet != null) {
                        ((org.w3c.dom.Element)importedFragmentNode).setAttribute("subtype", articleSet.replace("_article", ""));
                    }
                    ((org.w3c.dom.Element)importedFragmentNode).setAttribute("type", "article");

                    documentRoot.appendChild(importedFragmentNode);
                    
                } catch(ParserConfigurationException e) {
                    e.printStackTrace();
                } catch(IOException e) {
                    e.printStackTrace();
                } catch(Exception e) {
                    e.printStackTrace();
                } 
            }
        }

        return document;
    }


    /**
     * Command line execution.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
       
        // we are expecting four arguments: absolute path to the curated TEI XML corpus file, 
        // absolute path to softcite data in csv and abolute path
        // absolute path to the softcite PDF directory
        // and, last, where to put the generated XML files

        if (args.length != 4) {
            System.err.println("Usage: command [absolute path to the curated TEI XML corpus file] " + 
                "[absolute path to the softcite root data in csv] " + 
                "[absolute path to the softcite PDF directory] " + 
                "[absolute path for the output of the updated TEI XML file]");
            System.exit(-1);
        }

        String xmlPath = args[0];
        File f = new File(xmlPath);
        if (!f.exists() || f.isDirectory()) {
            System.out.println("curated TEI XML corpus file path does not exist or is invalid: " + xmlPath);
            System.exit(-1);
        }   

        String csvPath = args[1];
        f = new File(csvPath);
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("path to softcite data csv directory does not exist or is invalid: " + csvPath);
            System.exit(-1);
        }

        String pdfPath = args[2];
        f = new File(pdfPath);
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("path to softcite PDF directory does not exist or is invalid: " + pdfPath);
            System.exit(-1);
        }

        String outputXmlPath = args[3];
        f = new File(outputXmlPath);
        if (f.isDirectory()) {
            System.out.println("Output path for the updated TEI XML corpus file path is invalid: " + outputXmlPath);
            System.exit(-1);
        }  

        XMLCorpusPostProcessorNoMention postProcessor = new XMLCorpusPostProcessorNoMention();
        try {
            postProcessor.process(xmlPath, csvPath, pdfPath, outputXmlPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}

