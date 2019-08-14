package org.grobid.core.utilities;

import java.io.*;
import java.util.Iterator;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;

import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.apache.commons.io.FileUtils;

import org.grobid.core.document.xml.XmlBuilderUtils;

/**
 *  Some convenient methods for suffering a bit less with XML.
 */
public class XMLUtilities {

    public static String toPrettyString(String xml, int indent) {
        try {
            // Turn xml string into a document
            org.w3c.dom.Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new ByteArrayInputStream(xml.getBytes("utf-8"))));

            // Remove whitespaces outside tags
            document.normalize();
            XPath xPath = XPathFactory.newInstance().newXPath();
            org.w3c.dom.NodeList nodeList = (org.w3c.dom.NodeList) xPath.evaluate("//text()[normalize-space()='']",
                                                          document,
                                                          XPathConstants.NODESET);

            for (int i = 0; i < nodeList.getLength(); ++i) {
                org.w3c.dom.Node node = nodeList.item(i);
                node.getParentNode().removeChild(node);
            }

            // Setup pretty print options
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            // Return pretty print xml string
            StringWriter stringWriter = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
            return stringWriter.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Ensure that the TEI training corpus is well-formed, has unique identifiers for all
     * @xml:id in the whole corpus, and remove TEI entries with empty body.
     */
    public static void cleanXMLCorpus(String documentPath) throws Exception {
        File documentFile = new File(documentPath);
        File outputFile = new File(documentPath.replace(".tei.xml", ".clean.tei.xml"));

        // we use a DOM parser
        org.w3c.dom.Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(documentFile);

        // remove tei entries with empty body
        document.normalize();
        XPath xPath = XPathFactory.newInstance().newXPath();
        org.w3c.dom.NodeList nodeList = (org.w3c.dom.NodeList) xPath.evaluate("//tei/text/body",
                                                      document,
                                                      XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); ++i) {
            org.w3c.dom.Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (element.getTextContent() == null || element.getTextContent().length() == 0) {
                    Node teiNode = node.getParentNode().getParentNode();
                    teiNode.getParentNode().removeChild(teiNode);
                }
            }
        }

        // ensure uniqueness of xml:id
        nodeList = document.getElementsByTagName("rs");
        System.out.println(nodeList.getLength() + " annotations");
        for (int i = 0; i < nodeList.getLength(); i++) {
            org.w3c.dom.Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String id = element.getAttribute("id");
                if (id != null && id.length() > 0) {
                    String docId = getDocIdFromRs(node);
                    if (docId != null) {
                        //System.out.println(docId);
                        //System.out.println(id);
                        // modify id
                        element.removeAttribute("id");
                        element.setAttribute("xml:id", docId+"-"+id);
                    }
                }
                String corresp = element.getAttribute("corresp");
                if (corresp != null && corresp.length() > 0) {
                    String docId = getDocIdFromRs(node);
                    if (docId != null) {
                        //System.out.println(corresp);
                        // modify corresp
                        element.removeAttribute("corresp");
                        element.setAttribute("corresp", "#"+docId+"-"+corresp.substring(1));
                    }
                }
            }
        }

        // Setup pretty print options
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        // Return pretty print xml string
        StringWriter stringWriter = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
        
        // write result to file
        FileUtils.writeStringToFile(outputFile, stringWriter.toString(), "UTF-8");

        // check again if everything is well-formed after the changes
        try {
            document = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new InputSource(new ByteArrayInputStream(stringWriter.toString().getBytes("UTF-8"))));
        } catch(Exception e) {
            System.out.println("Problem with the final TEI XML");
            e.printStackTrace();
        }
    }


    /**
     * Return the document ID where the annotation takes place
     */ 
    private static String getDocIdFromRs(org.w3c.dom.Node node) {
        String result = null;
        // first go up to the tei element root
        Node teiNode = node.getParentNode().getParentNode().getParentNode().getParentNode();
        if (teiNode != null) {
            // then we need to go down teiHeader -> fileDesc -> id
            Element element = (Element)teiNode;
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node currentChild = children.item(i);
                if (currentChild.getNodeType() == Node.ELEMENT_NODE
                    && ((Element) currentChild).getTagName().equals("teiHeader")) {

                    Element element2 = (Element)currentChild;
                    NodeList children2 = element2.getChildNodes();
                    for (int j = 0; j < children2.getLength(); j++) {
                        Node currentChild2 = children2.item(j);
                        if (currentChild2.getNodeType() == Node.ELEMENT_NODE
                            && ((Element) currentChild2).getTagName().equals("fileDesc")) {

                            Element element3 = (Element)currentChild2;
                            // get id attribute value
                            String id = element3.getAttribute("xml:id");
                            if (id != null && id.length() > 0)
                                result = id;
                            break;
                        }
                    }
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Command line execution for cleaning the TEI training corpus.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
       
        // we are expecting one argument, absolute path to the TEICorpus document

        if (args.length != 1) {
            System.err.println("Usage: command [absolute path to the XML TEICorpus document]");
            System.exit(-1);
        }

        String documentPath = args[0];
        File f = new File(documentPath);
        if (!f.exists()) {
            System.err.println("path to XML TEICorpus document does not exist or is invalid: " + documentPath);
            System.exit(-1);
        }

        try {
            XMLUtilities.cleanXMLCorpus(documentPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

}