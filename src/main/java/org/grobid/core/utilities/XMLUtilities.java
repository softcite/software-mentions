package org.grobid.core.utilities;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.XMLConstants;
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
import org.apache.commons.lang3.StringUtils;

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

    // Cached JAXP factories. JAXP factories are not guaranteed thread-safe for
    // their new*() methods; synchronized accessors below produce per-call
    // builders/parsers/transformers that are themselves single-thread only.
    // Caching avoids repeated ServiceLoader discovery which, under sustained
    // TEI load, left classloader-backed references accumulating on the heap.
    // Each factory is also hardened against XXE/SSRF since callers parse
    // user-supplied XML/TEI; features are set defensively so unsupported
    // options on a given JAXP implementation do not break class init.
    private static final DocumentBuilderFactory DBF;
    private static final SAXParserFactory SPF;
    private static final XPathFactory XPF = XPathFactory.newInstance();
    private static final TransformerFactory TF;

    static {
        DBF = DocumentBuilderFactory.newInstance();
        DBF.setNamespaceAware(true);
        hardenDocumentBuilderFactory(DBF);

        SPF = SAXParserFactory.newInstance();
        hardenSAXParserFactory(SPF);

        TF = TransformerFactory.newInstance();
        hardenTransformerFactory(TF);
    }

    private static void hardenDocumentBuilderFactory(DocumentBuilderFactory factory) {
        trySetDBFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        trySetDBFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetDBFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetDBFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetDBFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException | AbstractMethodError ignore) {
            // older JAXP impls
        }
        factory.setExpandEntityReferences(false);
    }

    private static void trySetDBFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException e) {
            LOGGER.debug("Unsupported DocumentBuilderFactory feature: {}", feature);
        }
    }

    private static void hardenSAXParserFactory(SAXParserFactory factory) {
        trySetSPFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        trySetSPFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetSPFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetSPFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetSPFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    }

    private static void trySetSPFeature(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception e) {
            LOGGER.debug("Unsupported SAXParserFactory feature: {}", feature);
        }
    }

    private static void hardenTransformerFactory(TransformerFactory factory) {
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException e) {
            LOGGER.debug("Unsupported TransformerFactory feature: FEATURE_SECURE_PROCESSING");
        }
        trySetTFAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        trySetTFAttribute(factory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    }

    private static void trySetTFAttribute(TransformerFactory factory, String attribute, Object value) {
        try {
            factory.setAttribute(attribute, value);
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Unsupported TransformerFactory attribute: {}", attribute);
        }
    }

    private static synchronized DocumentBuilder newBuilder() throws ParserConfigurationException {
        return DBF.newDocumentBuilder();
    }

    private static synchronized SAXParser newSAXParser() throws Exception {
        return SPF.newSAXParser();
    }

    private static synchronized XPath newXPath() {
        return XPF.newXPath();
    }

    private static synchronized Transformer newTransformer() throws TransformerConfigurationException {
        return TF.newTransformer();
    }

    public static String toPrettyString(String xml, int indent) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            // Turn xml string into a document
            org.w3c.dom.Document document = newBuilder().parse(new InputSource(inputStream));

            // Remove whitespaces outside tags
            document.normalize();
            XPath xPath = newXPath();
            org.w3c.dom.NodeList nodeList = (org.w3c.dom.NodeList) xPath.evaluate("//text()[normalize-space()='']",
                                                          document,
                                                          XPathConstants.NODESET);

            for (int i = 0; i < nodeList.getLength(); ++i) {
                org.w3c.dom.Node node = nodeList.item(i);
                node.getParentNode().removeChild(node);
            }

            // Setup pretty print options
            Transformer transformer = newTransformer();
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            // Return pretty print xml string
            try (StringWriter stringWriter = new StringWriter()) {
                transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
                return stringWriter.toString();
            }
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

    /**
     * Get the text under the child nodes of the provided element at first level only
     * (this is not a recursive text concatenation for all the sub-hierarchy like 
     * Element.getTextContent()).
     **/
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

    public static BiblioItem parseTEIBiblioItem(org.w3c.dom.Document doc, org.w3c.dom.Element biblStructElement) {
        BiblStructSaxHandler handler = new BiblStructSaxHandler();
        String teiXML = null;
        try {
            SAXParser p = newSAXParser();
            teiXML = serialize(doc, biblStructElement);
            try (StringReader reader = new StringReader(teiXML)) {
                p.parse(new InputSource(reader), handler);
            }
        } catch(Exception e) {
            if (teiXML != null)
                LOGGER.warn("The parsing of the biblStruct from TEI document failed for: " + teiXML);
            else
                LOGGER.warn("The parsing of the biblStruct from TEI document failed for: " + biblStructElement.toString());
        }
        return handler.getBiblioItem();
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

    public static Pair<String,Map<String,Pair<OffsetPosition,String>>> getTextNoRefMarkersAndMarkerPositions(Element element, int globalPos) {
        StringBuffer buf = new StringBuffer();
        NodeList list = element.getChildNodes();
        boolean found = false;
        int indexPos = globalPos;

        // map a ref string with its position and the reference key as present in the XML
        Map<String,Pair<OffsetPosition,String>> right = new TreeMap<>();

        // the key of the reference
        String bibId = null;

        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if ("ref".equals(node.getNodeName())) {

                    if ("bibr".equals(((Element) node).getAttribute("type"))) {
                        bibId = ((Element) node).getAttribute("target");
                        if (bibId != null && bibId.startsWith("#")) {
                            bibId = bibId.substring(1, bibId.length());
                        }
                    }

                    // get the ref marker text
                    NodeList list2 = node.getChildNodes();
                    for (int j = 0; j < list2.getLength(); j++) {
                        Node node2 = list2.item(j);
                        if (node2.getNodeType() == Node.TEXT_NODE) {
                            String chunk = node2.getNodeValue();

                            if ("bibr".equals(((Element) node).getAttribute("type"))) {
                                Pair<OffsetPosition, String> refInfo = Pair.of(new OffsetPosition(indexPos, indexPos+chunk.length()), bibId);
                                right.put(chunk, refInfo);
                                String holder = StringUtils.repeat(" ", chunk.length());
                                buf.append(holder);
                            } else if ("uri".equals(((Element) node).getAttribute("type")) || "url".equals(((Element) node).getAttribute("type"))) {
                                // added like normal text
                                buf.append(chunk);
                                found = true;
                            } else {
                                // other ref are filtered out
                                String holder = StringUtils.repeat(" ", chunk.length());
                                buf.append(holder);
                            }
                            indexPos += chunk.length();
                        }
                    }
                } else {
                    // get the text one level deeper
                    NodeList list2 = node.getChildNodes();
                    for (int j = 0; j < list2.getLength(); j++) {
                        Node node2 = list2.item(j);
                        if (node2.getNodeType() == Node.TEXT_NODE) {
                            String chunk = node2.getNodeValue();
                            buf.append(chunk);
                            found = true;
                            indexPos += chunk.length();
                        }
                    }
                }
            } else if (node.getNodeType() == Node.TEXT_NODE) {
                String chunk = node.getNodeValue();
                buf.append(chunk);
                found = true;
                indexPos += chunk.length();
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
            // XPath to find empty text nodes.
            XPathExpression xpathExp = newXPath().compile(
                    "//text()[normalize-space(.) = '']");
            NodeList emptyTextNodes = (NodeList)
                    xpathExp.evaluate(doc, XPathConstants.NODESET);

            // Remove each empty text node from document.
            for (int i = 0; i < emptyTextNodes.getLength(); i++) {
                Node emptyTextNode = emptyTextNodes.item(i);
                emptyTextNode.getParentNode().removeChild(emptyTextNode);
            }
        } catch(Exception ex) {
            LOGGER.warn("Failed to strip empty text nodes before serialize()", ex);
        }

        DOMSource domSource = (node == null) ? new DOMSource(doc) : new DOMSource(node);
        try (StringWriter writer = new StringWriter()) {
            StreamResult result = new StreamResult(writer);
            Transformer transformer = newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            if (node != null)
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(domSource, result);
            return writer.toString();
        } catch(TransformerException | IOException ex) {
            LOGGER.warn("Failed to serialize DOM node", ex);
            return null;
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
        org.w3c.dom.Document document = newBuilder().parse(documentFile);

        // remove tei entries with empty body
        document.normalize();
        XPath xPath = newXPath();
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
        Transformer transformer = newTransformer();
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        // Return pretty print xml string
        try (StringWriter stringWriter = new StringWriter()) {
            transformer.transform(new DOMSource(document), new StreamResult(stringWriter));

            // write result to file
            FileUtils.writeStringToFile(outputFile, stringWriter.toString(), "UTF-8");

            // check again if everything is well-formed after the changes
            try (ByteArrayInputStream inputStream =
                         new ByteArrayInputStream(stringWriter.toString().getBytes(StandardCharsets.UTF_8))) {
                document = newBuilder().parse(new InputSource(inputStream));
            } catch(Exception e) {
                LOGGER.warn("Problem with the final TEI XML", e);
            }
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
                    try (StringReader reader = new StringReader(fullSent)) {
                        newBuilder().parse(new InputSource(reader));
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

                    try (StringReader reader = new StringReader(sent)) {
                        org.w3c.dom.Document d = newBuilder().parse(new InputSource(reader));
                        //d.getDocumentElement().normalize();
                        Node newNode = doc.importNode(d.getDocumentElement(), true);
                        newNodes.add(newNode);
                        //System.out.println(serialize(doc, newNode));
                    } catch(Exception e) {
                        LOGGER.debug("Failed to re-parse segmented sentence", e);
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