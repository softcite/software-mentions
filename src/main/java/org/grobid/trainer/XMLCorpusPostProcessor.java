package org.grobid.trainer;

import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.ArticleUtilities;
import org.grobid.core.utilities.SoftwareConfiguration;

import org.grobid.trainer.SoftciteAnnotation.AnnotationType;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

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

/**
 * Post-process an existing TEI XML package version of the corpus which has been manually reviewed/reconciled.
 * Updated information, when possible, will be: 
 * - addition of provenance information (annotator, reconciliation step)
 * - addition of cert values when possible/available (scaled to [0,1])
 * - addition of field value "software_was_used" (but would need to be reviewed too!)
 * - addition of a xml:id to software mention element without properties 
 *  
 */
public class XMLCorpusPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(XMLCorpusPostProcessor.class);

    static Charset UTF_8 = Charset.forName("UTF-8"); // StandardCharsets.UTF_8
    private SoftwareConfiguration configuration;

    public XMLCorpusPostProcessor(SoftwareConfiguration conf) {
        this.configuration = conf;
    }

    /**
     * Inject provenance information and field "" when possible in a manually reviewed TEI corpus "package" file.     
     */
    public void process(String xmlCorpusPath, String csvPath, String newXmlCorpusPath) throws IOException {
        
        Map<String, AnnotatedDocument> documents = new HashMap<String, AnnotatedDocument>();
        Map<String, SoftciteAnnotation> annotations = new HashMap<String, SoftciteAnnotation>();

        AnnotatedCorpusGeneratorCSV converter = new AnnotatedCorpusGeneratorCSV(this.configuration);
        converter.importCSVFiles(csvPath, documents, annotations);
        
        // we unfortunately need to use DOM to update the XML file which is always a lot of pain
        String tei = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            tei = FileUtils.readFileToString(new File(xmlCorpusPath), UTF_8);

            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(tei)));
            //document.getDocumentElement().normalize();
            document = enrichTEIDocument(document, documents);
            //document.getDocumentElement().normalize();

            tei = serialize(document, null);
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
                                                   Map<String, AnnotatedDocument> documents) {
        // this is the list of annotators, in order to anonymize them 
        List<String> annotators = new ArrayList<String>();

        // iterate through the corpus document to visit document level nodes
        NodeList sectionList = document.getElementsByTagName("TEI");
        System.out.println("number of tei elements/articles: " + sectionList.getLength());
        for (int i = 0; i < sectionList.getLength(); i++) {
            Element teiElement = (Element) sectionList.item(i);
            // get the document id (e.g. PMC identifier)
            // it is under teiHeader/fileDesc/@xml:id
            String pmcid = null;
            Element teiHeaderElement = this.getFirstDirectChild(teiElement, "teiHeader");
            if (teiHeaderElement != null) {
                Element fileDescElement = this.getFirstDirectChild(teiHeaderElement, "fileDesc");
                if (fileDescElement != null) {
                    pmcid = fileDescElement.getAttribute("xml:id");
                }
            }
            if (pmcid == null) {
                System.out.println("warning: document identifier not found for tei at rank " + i);
                continue;
            }

            AnnotatedDocument annotatedDocument = documents.get(pmcid);
            if (annotatedDocument == null) {
                System.out.println("warning no softcite annotation found for document " + pmcid);  
                continue;                        
            }

            String articleSet = annotatedDocument.getArticleSet();
            if (articleSet != null) {
                teiElement.setAttribute("subtype", articleSet.replace("_article", ""));
            }
            teiElement.setAttribute("type", "article");

            // get text element (unique) for this document
            Element textElement = this.getFirstDirectChild(teiElement, "text");
            if (textElement != null) {
                // get body element (unique) for this document
                Element bodyElement = this.getFirstDirectChild(textElement, "body");
                if (bodyElement != null) {
                    // iterate through the snippets (p)
                    NodeList pList = bodyElement.getElementsByTagName("p");
                    for (int j = 0; j < pList.getLength(); j++) {
                        Element snippetElement = (Element) pList.item(j);

                        // find the entities
                        NodeList entityList = snippetElement.getElementsByTagName("rs");
                        for (int k = 0; k < entityList.getLength(); k++) {
                            Element entityElement = (Element) entityList.item(k);

                            // check if the element is software type and without xml:id, 
                            // add one if it's the case
                            String id = entityElement.getAttribute("xml:id");

                            String type = entityElement.getAttribute("type");
                            if (type != null && type.equals("software")) {    
                                if (id == null || id.trim().length() == 0) {
                                    entityElement.setAttribute("xml:id", pmcid+"-software-simple-"+k);
                                }
                            }
                            String localText = this.getText(entityElement);

                            // rename creator to publisher
                            if (type != null && type.equals("creator")) {    
                                entityElement.removeAttribute("type");   
                                entityElement.setAttribute("type", "publisher");   
                            }

                            // try to find additional info like the provenance
                                                          
                            // softcite annotations for the document
                            List<SoftciteAnnotation> annotations = annotatedDocument.getAnnotations();
                            if (annotations == null)
                                continue;
                            for(SoftciteAnnotation annotation : annotations) {
                                //if (annotation.getType() != AnnotationType.SOFTWARE) 
                                //    continue;

                                // matching the current software entity? 
                                if (type != null && type.equals("software") && localText != null && 
                                    annotation.getSoftwareMention() != null && 
                                    annotation.getSoftwareMention().trim().toLowerCase().equals(localText.trim().toLowerCase())) {
                                    
                                    // do we have a "software_was_used" information?
                                    if (annotation.getIsUsed()) {
                                        // add an attribute
                                        entityElement.setAttribute("subtype", "used");
                                    }

                                    // add certainty provided by annotator
                                    if (annotation.getCertainty() != -1) {
                                        // add an attribute
                                        entityElement.setAttribute("cert", String.format("%.1f", ((float)annotation.getCertainty())/10));
                                    } 
                                }

                                // inject annotator if matching the current actual mention
                                if ( 
                                    (type != null && type.equals("creator") && localText != null && 
                                    annotation.getCreator() != null && 
                                    annotation.getCreator().trim().toLowerCase().equals(localText.toLowerCase())) 
                                    ||
                                    (type != null && type.equals("software") && localText != null && 
                                    annotation.getSoftwareMention() != null && 
                                    annotation.getSoftwareMention().trim().toLowerCase().equals(localText.trim().toLowerCase()))
                                    ||
                                    (type != null && type.equals("version") && localText != null && 
                                    annotation.getVersionDate() != null && 
                                    annotation.getVersionDate().trim().toLowerCase().equals(localText.toLowerCase())) 
                                    ||
                                    (type != null && type.equals("version") && localText != null && 
                                    annotation.getVersionNumber() != null && 
                                    annotation.getVersionNumber().trim().toLowerCase().equals(localText.toLowerCase())) 
                                    ||
                                    (type != null && type.equals("url") && localText != null && 
                                    annotation.getUrl() != null && 
                                    annotation.getUrl().trim().toLowerCase().equals(localText.toLowerCase())) 
                                    ) {
                                    // we need to check the context around to validate the match
                                    // get the first text node on the right and left 
                                    Pair<String,String> textImmediateContexts = getLeftRightTextContent(entityElement);
                                    String annotationContext = annotation.getContext();
                                    String annotationContextSimplified = CrossAgreement.simplifiedField(annotationContext);
                                    String mentionSimplified = CrossAgreement.simplifiedField(localText); 
                                    int posMention = annotationContextSimplified.indexOf(mentionSimplified);
                                    if (posMention == -1) {
                                        // we can't find the mention on the context, so we won't be able to validate the 
                                        // RDF/CSV annotation
                                        continue; 
                                    }

                                    // check left context
                                    String xmlLeftString = textImmediateContexts.getLeft();
                                    String xmlLeftSignature = CrossAgreement.simplifiedField(xmlLeftString);
                                    xmlLeftSignature = xmlLeftSignature.substring(xmlLeftSignature.length()-Math.min(xmlLeftSignature.length(),10), 
                                        xmlLeftSignature.length());

                                    int lowerBound = Math.min(10, posMention);
                                    String annotationContextLeftSignature = annotationContextSimplified.substring(
                                        posMention-Math.min(lowerBound, 10), posMention);

                                    boolean matchLeft = false;
                                    if (annotationContextLeftSignature.length() != 0 &&
                                        xmlLeftSignature.length() != 0) {
                                        if (annotationContextLeftSignature.endsWith(xmlLeftSignature) || 
                                            xmlLeftSignature.endsWith(annotationContextLeftSignature)) {
                                            matchLeft = true;
                                        } 
                                    } else
                                        matchLeft = true;

                                    // check right context
                                    String xmlRightString = textImmediateContexts.getRight();
                                    String xmlRightSignature = CrossAgreement.simplifiedField(xmlRightString);
                                    xmlRightSignature = xmlRightSignature.substring(0, Math.min(xmlRightSignature.length(),10));

                                    int upperBound = Math.min(10, annotationContextSimplified.length() - (posMention+mentionSimplified.length()));
                                    String annotationContextRightSignature = annotationContextSimplified.substring(
                                        posMention+mentionSimplified.length(), posMention+mentionSimplified.length()+upperBound);

                                    boolean matchRight = false;
                                    if (annotationContextRightSignature.length() != 0 &&
                                        xmlRightSignature.length() != 0) {
                                        if (annotationContextRightSignature.startsWith(xmlRightSignature) || 
                                            xmlRightSignature.startsWith(annotationContextRightSignature)) {
                                            matchRight = true;
                                        } 
                                    } else
                                        matchRight = true;

/*System.out.println("------------------------------------------------");
System.out.println(xmlLeftString + " / " + xmlRightString);
System.out.println(xmlLeftSignature + " / " + xmlRightSignature);
System.out.println(annotationContextLeftSignature + " / " + annotationContextRightSignature);*/

                                    if (matchRight && matchLeft) {
                                        // add provenance information
                                        String annotatorID = annotation.getAnnotatorID();
                                        if (annotatorID != null) {
                                            int index = annotators.indexOf(annotatorID);
                                            if (index == -1) {   
                                                annotators.add(annotatorID);
                                                index = annotators.size()-1;
                                            }
                                            // add an attribute (xml pointer)
                                            entityElement.setAttribute("resp", "#annotator"+index);
                                            
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    // second pass to put "curator" for the entity annotation without resp
                    pList = bodyElement.getElementsByTagName("p");
                    for (int j = 0; j < pList.getLength(); j++) {
                        Element snippetElement = (Element) pList.item(j);

                        // find the entities
                        NodeList entityList = snippetElement.getElementsByTagName("rs");
                        for (int k = 0; k < entityList.getLength(); k++) {
                            Element entityElement = (Element) entityList.item(k);
                            String resp = entityElement.getAttribute("resp");
                            if (resp == null || resp.length() == 0) {
                                entityElement.setAttribute("resp", "#curator");
                            }
                        }
                    }
                }
            }
        }

        // inject annotator descriptions 
        NodeList corpusTitleStmtList = document.getElementsByTagName("titleStmt");
        // take the first, which is the titleStmt of the teiCorpus header
        if (corpusTitleStmtList.getLength() > 0) {
            Element corpusTitleStmtElement = (Element) corpusTitleStmtList.item(0); 
            for(int index=0; index < annotators.size(); index++) {
                Element respStmt = document.createElement("respStmt");
                respStmt.setAttribute("xml:id", "annotator"+index);
                Element resp = document.createElement("resp");
                resp.setTextContent("annotator");

                Element name = document.createElement("name");
                name.setTextContent("ANONYMIZED");
                //name.setTextContent(annotators.get(index));

                respStmt.appendChild(resp);
                respStmt.appendChild(name);

                corpusTitleStmtElement.appendChild(respStmt);
            }
        }

        return document;
    }

    public static Element getFirstDirectChild(Element parent, String name) {
        for(Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && name.equals(child.getNodeName())) 
                return (Element) child;
        }
        return null;
    }

    public static String getText(Element element) {
        StringBuffer buf = new StringBuffer();
        NodeList list = element.getChildNodes();
        boolean found = false;
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                buf.append(node.getNodeValue());
                found = true;
            }
        }
        return found ? buf.toString() : null;
    }

    public static Pair<String,String> getLeftRightTextContent(Element current) {
        // right text
        Node sibling = current.getNextSibling();
        while (null != sibling && sibling.getNodeType() != Node.TEXT_NODE) {
            sibling = sibling.getNextSibling();
        }
        String right = null;
        if (sibling != null)
            right = ((Text)sibling).getNodeValue();

        // left text
        sibling = current.getPreviousSibling();
        while (null != sibling && sibling.getNodeType() != Node.TEXT_NODE) {
            sibling = sibling.getPreviousSibling();
        }
        String left = null;
        if (sibling != null)
            left = ((Text)sibling).getNodeValue();

        return Pair.of(left, right);
    }

    public static String serialize(org.w3c.dom.Document doc, Node node) {
        // to avoid issues with space reamining from deleted nodes
        try {
            XPathFactory xpathFactory = XPathFactory.newInstance();
            // XPath to find empty text nodes.
            XPathExpression xpathExp = xpathFactory.newXPath().compile(
                    "//text()[normalize-space(.) = '']");  
            NodeList emptyTextNodes = (NodeList) 
                    xpathExp.evaluate(doc, XPathConstants.NODESET);

            // Remove each empty text node from document.
            for (int i = 0; i < emptyTextNodes.getLength(); i++) {
                Node emptyTextNode = emptyTextNodes.item(i);
                emptyTextNode.getParentNode().removeChild(emptyTextNode);
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }

        DOMSource domSource = null;
        String xml = null;
        try {
            if (node == null) {
                domSource = new DOMSource(doc);
            } else {
                domSource = new DOMSource(node);
            }
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            if (node != null)
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(domSource, result);
            xml = writer.toString();
        } catch(TransformerException ex) {
            ex.printStackTrace();
        }
        return xml;
    }

    /**
     * Command line execution.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) throws Exception {
       
        // we are expecting three arguments, absolute path to the curated TEI XML file
        // documents, absolute path to the softcite data in csv and abolute path
        // where to put the generated XML files

        if (args.length != 3) {
            System.err.println("Usage: command [absolute path to the TEI XML corpus file] " + 
                "[absolute path to the softcite root data in csv] " + 
                "[absolute path for the output of the updated TEI XML file]");
            System.exit(-1);
        }

        String xmlPath = args[0];
        File f = new File(xmlPath);
        if (!f.exists() || f.isDirectory()) {
            System.out.println("TEI XML corpus file path does not exist or is invalid: " + xmlPath);
            System.exit(-1);
        }   

        String csvPath = args[1];
        f = new File(csvPath);
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("path to softcite data csv directory does not exist or is invalid: " + csvPath);
            System.exit(-1);
        }

        String outputXmlPath = args[2];
        f = new File(outputXmlPath);
        if (f.isDirectory()) {
            System.out.println("Output path for the updated TEI XML corpus file path is invalid: " + outputXmlPath);
            System.exit(-1);
        }  

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        SoftwareConfiguration conf = mapper.readValue(new File("resources/config/config.yml"), SoftwareConfiguration.class);

        XMLCorpusPostProcessor postProcessor = new XMLCorpusPostProcessor(conf);
        try {
            postProcessor.process(xmlPath, csvPath, outputXmlPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}

