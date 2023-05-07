package org.grobid.core.utilities;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.sax.BiblStructSaxHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Some convenient methods for suffering a bit less with XML.
 */
public class XMLUtilities {

    private static final Logger LOGGER = LoggerFactory.getLogger(XMLUtilities.class);

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

    public static Element getFirstDirectChild(Element parent, String name) {
        for(Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && name.equals(child.getNodeName())) 
                return (Element) child;
        }
        return null;
    }

    public static Element getLastDirectChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for(int j=children.getLength()-1; j>0; j--) {
            Node child = children.item(j); 
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

    public static String getTextNoRefMarkers(Element element) {
        StringBuffer buf = new StringBuffer();
        NodeList list = element.getChildNodes();
        boolean found = false;
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if ("ref".equals(node.getNodeName()))
                    continue;
            } 
            if (node.getNodeType() == Node.TEXT_NODE) {
                buf.append(node.getNodeValue());
                found = true;
            }
        }
        return found ? buf.toString() : null;
    }

    public static BiblioItem parseTEIBiblioItem(org.w3c.dom.Element biblStructElement) {
        BiblStructSaxHandler handler = new BiblStructSaxHandler();
        String teiXML = null;
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser p = spf.newSAXParser();
            teiXML = serialize(null, biblStructElement);
            p.parse(new InputSource(new StringReader(teiXML)), handler);
        } catch(Exception e) {
            if (teiXML != null)
                LOGGER.warn("The parsing of the biblStruct from TEI document failed for: " + teiXML);
            else 
                LOGGER.warn("The parsing of the biblStruct from TEI document failed for: " + biblStructElement.toString());
        }
        return handler.getBiblioItem();
    }

    public static Pair<String,Map<String,Pair<OffsetPosition,String>>> getTextNoRefMarkersAndMarkerPositions(Element element) {
        StringBuffer buf = new StringBuffer();
        NodeList list = element.getChildNodes();
        boolean found = false;
        int indexPos = 0;
        boolean isRefString = false;

        // map a ref string with its position and the reference key as present in the XML
        Map<String,Pair<OffsetPosition,String>> right = new TreeMap<>();

        // the key of the reference
        String bibId = null;

        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if ("ref".equals(node.getNodeName())) {
                    isRefString = true;
                    bibId = ((Element) node).getAttribute("target");
                    if (bibId != null && bibId.startsWith("#")) {
                        bibId = bibId.substring(1, bibId.length());
                    }
                }
                else
                    isRefString = false;
            } 
            if (node.getNodeType() == Node.TEXT_NODE) {
                String chunk = node.getNodeValue();
                if (isRefString) {
                    Pair<OffsetPosition, String> refInfo = Pair.of(new OffsetPosition(indexPos, indexPos+chunk.length()), bibId);
                    right.put(chunk, refInfo);
                } else
                    buf.append(chunk);
                found = true;
                indexPos =+ chunk.length();
            }
        }
        String left = found ? buf.toString() : null;
        return Pair.of(left, right);
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
     * Return the document ID where the annotation is located
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

    public static String stripNonValidXMLCharacters(String in) {
        StringBuffer out = new StringBuffer(); 
        char current; 

        if (in == null || ("".equals(in))) 
            return ""; 
        for (int i = 0; i < in.length(); i++) {
            current = in.charAt(i); // NOTE: No IndexOutOfBoundsException caught here; it should not happen.
            if ((current == 0x9) ||
                (current == 0xA) ||
                (current == 0xD) ||
                ((current >= 0x20) && (current <= 0xD7FF)) ||
                ((current >= 0xE000) && (current <= 0xFFFD)) ||
                ((current >= 0x10000) && (current <= 0x10FFFF)))
                out.append(current);
        }
        return out.toString();
    }    

    private static List<String> textualElements = Arrays.asList("p", "figDesc");

    /**
     * Segment the text content of a TEI XML document into sentences under a particular node element.
     * Sentence markers are using TEI <s> elements.
     **/
    public static void segment(org.w3c.dom.Document doc, Node node) {
        final NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node n = children.item(i);
            if ( (n.getNodeType() == Node.ELEMENT_NODE) && 
                 (textualElements.contains(n.getNodeName())) ) {
                // text content
                //String text = n.getTextContent();
                StringBuffer textBuffer = new StringBuffer();
                NodeList childNodes = n.getChildNodes();
                for(int y=0; y<childNodes.getLength(); y++) {
                    textBuffer.append(serialize(doc, childNodes.item(y)));
                    textBuffer.append(" ");
                }
                String text = textBuffer.toString();

                //List<OffsetPosition> forbiddenPositions = new ArrayList<>();
                //String theSentences[] = detector.sentDetect(text);
                List<OffsetPosition> theSentenceBoundaries = null;
                try {
                    theSentenceBoundaries = SentenceUtilities.getInstance().runSentenceDetection(text);
                } catch(Exception e) {
                    LOGGER.warn("The sentence segmentation failed for: " + text);
                }

                // we're making a first pass to ensure that there is no element broken by the segmentation
                List<String> sentences = new ArrayList<String>();
                List<String> toConcatenate = new ArrayList<String>();
                for(OffsetPosition sentPos : theSentenceBoundaries) {
                    //System.out.println("new chunk: " + sent);
                    String sent = text.substring(sentPos.start, sentPos.end);
                    String newSent = sent;
                    if (toConcatenate.size() != 0) {
                        StringBuffer conc = new StringBuffer();
                        for(String concat : toConcatenate) {
                            conc.append(concat);
                            conc.append(" ");
                        }
                        newSent = conc.toString() + sent;
                    }
                    String fullSent = "<s>" + newSent + "</s>";
                    boolean fail = false;
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        org.w3c.dom.Document d = factory.newDocumentBuilder().parse(new InputSource(new StringReader(fullSent)));                
                    } catch(Exception e) {
                        fail = true;
                    }
                    if (fail)
                        toConcatenate.add(sent);
                    else {
                        sentences.add(fullSent);
                        toConcatenate = new ArrayList<String>();
                    }
                }

                List<Node> newNodes = new ArrayList<Node>();
                for(String sent : sentences) {
                    //System.out.println("-----------------");
                    sent = sent.replace("\n", " ");
                    sent = sent.replaceAll("( )+", " ");
                
                    //Element sentenceElement = doc.createElement("s");                        
                    //sentenceElement.setTextContent(sent);
                    //newNodes.add(sentenceElement);

                    //System.out.println(sent);  

                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        org.w3c.dom.Document d = factory.newDocumentBuilder().parse(new InputSource(new StringReader(sent)));
                        //d.getDocumentElement().normalize();
                        Node newNode = doc.importNode(d.getDocumentElement(), true);
                        newNodes.add(newNode);
                        //System.out.println(serialize(doc, newNode));
                    } catch(Exception e) {

                    }
                }

                // remove old nodes 
                while (n.hasChildNodes())
                    n.removeChild(n.getFirstChild());

                // and add new ones

                // if we have a figDesc, we need to inject div/p nodes for dataseer-ml support
                if (n.getNodeName().equals("figDesc")) {
                    Element theDiv = doc.createElementNS("http://www.tei-c.org/ns/1.0", "div");
                    Element theP = doc.createElementNS("http://www.tei-c.org/ns/1.0", "p");
                    for(Node theNode : newNodes) 
                        theP.appendChild(theNode);
                    theDiv.appendChild(theP);
                    n.appendChild(theDiv);
                } else {
                    for(Node theNode : newNodes) 
                        n.appendChild(theNode);
                }

            } else if (n.getNodeType() == Node.ELEMENT_NODE) {
                // not a target text content element, we apply the segmentation recursively
                segment(doc, (Element) n);
            }
        }
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