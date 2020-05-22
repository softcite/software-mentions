 package org.grobid.trainer;

import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.engines.Engine;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.utilities.*;

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
import org.xml.sax.*;
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
    private Map<String,String> missingTitles = new TreeMap<>();

    /**
     * Inject curation class description, document entries without mention and curation class at document level     
     */
    public void process(String xmlCorpusPath, String csvPath, String pdfPath, String newXmlCorpusPath) throws IOException {
        
        Map<String, AnnotatedDocument> documents = new HashMap<String, AnnotatedDocument>();
        Map<String, SoftciteAnnotation> annotations = new HashMap<String, SoftciteAnnotation>();

        AnnotatedCorpusGeneratorCSV converter = new AnnotatedCorpusGeneratorCSV();
        converter.importCSVFiles(csvPath, documents, annotations);
        // documents without annotation are present with a "dummy" annotation 

        this.importCSVMissingTitles(csvPath, documents);
        
        // we unfortunately need to use DOM to update the XML file which is always a lot of pain
        String tei = null;
        org.w3c.dom.Document document = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            tei = FileUtils.readFileToString(new File(xmlCorpusPath), UTF_8);

            document = builder.parse(new InputSource(new StringReader(tei)));
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

        //this.generalCountAnnotations(documents, annotations, xmlCorpusPath);
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

    public List<String> readAnnotatorMapping(String path) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        org.w3c.dom.Document doc = builder.parse(path);
        org.w3c.dom.Element root = doc.getDocumentElement();

        List<String> result = new ArrayList<String>();

        NodeList nList = doc.getElementsByTagName("respStmt");
        for (int i = 0; i < nList.getLength(); i++) {
            org.w3c.dom.Node nNode = nList.item(i);

            if (nNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                // get identifier xml:id, which will give the index of the annotator
                String identifier = nNode.getAttributes().getNamedItem("xml:id").getNodeValue();

                // get annotator name under <name>
                org.w3c.dom.Element nameElement = XMLCorpusPostProcessor.getFirstDirectChild((org.w3c.dom.Element)nNode, "name");
                String annotatorName = XMLCorpusPostProcessor.getText(nameElement);
                result.add(annotatorName);
            }
        }
        return result;
    }

    private org.w3c.dom.Document enrichTEIDocumentNoMention(org.w3c.dom.Document document, 
                                                   Map<String, AnnotatedDocument> documents,
                                                   String documentPath) {

        org.w3c.dom.Element documentRoot = document.getDocumentElement();
        Engine engine = GrobidFactory.getInstance().getEngine();
        XPathFactory xpathFactory = XPathFactory.newInstance();
        int m = 0;
        int nbDocWithNonPresentAnnotation = 0;
        int nbDocWithValidNonPresentAnnotation = 0;
        
        List<String> annotators = null;
        try {
            annotators = this.readAnnotatorMapping("resources/dataset/software/corpus/annotators.xml");
        } catch(Exception e) {
            e.printStackTrace();
        }

        for (Map.Entry<String, AnnotatedDocument> entry : documents.entrySet()) {
            /*if (m > 100) {
                break;
            }
            m++;*/
            String docName = entry.getKey();
            AnnotatedDocument softciteDocument = entry.getValue();

            // check if the document is already present in the TEI corpus, it means it has at 
            // least one annotation matching with its PDF  
            try {
                XPath xpath = xpathFactory.newXPath();
                XPathExpression expr = xpath.compile("//*[@id='"+docName+"']");
                NodeList result = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
                NodeList nodes = (NodeList) result;
                if (nodes.getLength() != 0) {
                    // document already present, we can still add the PDF-unmatched annotations 
                    // corresponding to this document
                    this.addUnmatchedAnnotations(docName, document, softciteDocument, annotators);
                    continue;
                }
            } catch(XPathExpressionException e) {
                e.printStackTrace();
            } catch(Exception e) {
                e.printStackTrace();
            }

            List<SoftciteAnnotation> localAnnotations = softciteDocument.getAnnotations();
            if (localAnnotations == null) {
                System.out.println(" **** Warning **** " + docName + " - document with null localAnnotation object");
            } 
            
            if (localAnnotations != null && localAnnotations.size() == 1 && localAnnotations.get(0).getType() == AnnotationType.DUMMY) {
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

                // check missing title
                String missingTitle = this.missingTitles.get(softciteDocument.getDocumentID());
                if (missingTitle != null) {
                    biblio.setTitle(missingTitle);
                    biblio.setArticleTitle(missingTitle);
                }

                softciteDocument.setBiblio(biblio);

                // number of local annotators
                List<String> localAnnotators = new ArrayList<String>();
                for(SoftciteAnnotation localAnnotation : localAnnotations) {
                    String annotatorID = localAnnotation.getAnnotatorID();
                    if (!localAnnotators.contains(annotatorID)) {
                        localAnnotators.add(annotatorID);
                    }
                }

                nu.xom.Element root = null;
                if (localAnnotators.size() > 1) {
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
                    String articleSet = softciteDocument.getArticleSet();
                    if (articleSet != null) {
                        ((org.w3c.dom.Element)importedFragmentNode).setAttribute("subtype", articleSet.replace("_article", ""));
                    }
                    ((org.w3c.dom.Element)importedFragmentNode).setAttribute("type", "article");

                    if (!articleSet.equals("training_article"))
                        documentRoot.appendChild(importedFragmentNode);

                } catch(ParserConfigurationException e) {
                    e.printStackTrace();
                } catch(IOException e) {
                    e.printStackTrace();
                } catch(Exception e) {
                    e.printStackTrace();
                } 
            } else {
                // we add the curation level information/class for the documents having annotations
                // for document without annotations, we can use the ones, unmatched in the PDF, from the CSV file

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

                // check missing title
                String missingTitle = this.missingTitles.get(softciteDocument.getDocumentID());
                if (missingTitle != null) {
                    biblio.setTitle(missingTitle);
                    biblio.setArticleTitle(missingTitle);
                }

                softciteDocument.setBiblio(biblio);

                // number of annotators
                List<String> localAnnotators = new ArrayList<String>();
                if (localAnnotations != null) {
                    for(SoftciteAnnotation localAnnotation : localAnnotations) {
                        String annotatorID = localAnnotation.getAnnotatorID();
                        if (!localAnnotators.contains(annotatorID)) {
                            localAnnotators.add(annotatorID);
                        }
                    }
                }

                nu.xom.Element root = null;
                if (localAnnotators.size() > 1) {
                    root = SoftwareParser.getTEIHeaderSimple(docName, biblio, "multiple_annotator");
                } else {
                    root = SoftwareParser.getTEIHeaderSimple(docName, biblio, "unique_annotator");
                }
                // empty body for TEI conformance

                nu.xom.Element textNode = teiElement("text");
                textNode.addAttribute(new Attribute("xml:lang", "http://www.w3.org/XML/1998/namespace", "en"));

                nu.xom.Element body = teiElement("body");                
                textNode.appendChild(body);
                
                if (localAnnotations != null && localAnnotations.size() > 0) {
                    nbDocWithNonPresentAnnotation++;
                    boolean hasTextContent = false;

                    int index_entity = 0;

                    // inject annotations as they appear in the CSV files
                    List<String> previousLocalContexts = null;
                    for(SoftciteAnnotation localAnnotation : localAnnotations) {
                        if (localAnnotation.getType() != AnnotationType.SOFTWARE)
                            continue;

                        String localContext = localAnnotation.getContext();
                        if (localContext == null || localContext.trim().length() == 0)
                            continue;

                        String softwareString = localAnnotation.getSoftwareMention();
                        if (softwareString == null || softwareString.trim().length() == 0)
                            continue;

                        //System.out.println("raw: " + localContext);

                        localContext = XMLUtilities.stripNonValidXMLCharacters(localContext);
                        localContext = localContext.replaceAll("<[^>]+>", " ");                        
                        localContext = localContext.replace("\n", " ");
                        localContext = localContext.replaceAll("( )+", " ");
                        localContext = localContext.trim();

                        String localContextSignature = CrossAgreement.simplifiedField(localContext);
                        if (previousLocalContexts != null && previousLocalContexts.contains(localContextSignature)) {
                            continue;
                        } 

                        //System.out.println(localContext);

                        // convert the SoftCite annotations for this text fragments into a sorted list of 
                        // Annotation objects with offsets to simplify the serialization
                        List<Annotation> sortedAnnotations = this.alignAnnotations(localAnnotation, localContext);
                        //System.out.println("nb of inline annotations: " + sortedAnnotations.size());

                        if (sortedAnnotations == null || sortedAnnotations.size() == 0) {
                            System.out.println(" **** WARNING **** " + docName + 
                                " - No inline annotation possible for local annotation ");
                            continue;
                        }

                        //nu.xom.Element curParagraph = teiElement("p");
                        //nu.xom.Element curSentence = teiElement("s");
                        nu.xom.Element curSentence = teiElement("ab");
                        curSentence.addAttribute(new Attribute("type", "unmatched_with_pdf"));
                        int lastPosition = 0;
                        boolean hasSoftware = false;
                        List<OffsetPosition> occupiedPositions = new ArrayList<OffsetPosition>();
                        for(Annotation inlineAnnotation : sortedAnnotations) {
                            if (inlineAnnotation.getAttributeValue("type") == null)
                                continue;

                            OffsetPosition position = inlineAnnotation.getOccurence();

                            // check if the position already taken
                            if (AnnotatedCorpusGeneratorCSV.isOverlapping(occupiedPositions, position)) {
                                continue;
                            } else {
                                occupiedPositions.add(position);
                            }

                            if (inlineAnnotation.getText().startsWith(" ")) {
                                curSentence.appendChild(localContext.substring(lastPosition,position.start)+" ");
                            } else
                                curSentence.appendChild(localContext.substring(lastPosition,position.start));

                            nu.xom.Element rs = teiElement("rs");
                            rs.appendChild(inlineAnnotation.getText().trim());

                            if (inlineAnnotation.getAttributeValue("type").equals("software")) { 
                                hasSoftware = true;
                                rs.addAttribute(new Attribute("type", "software"));
                                rs.addAttribute(new Attribute("id", docName+"-software-"+index_entity));
                                
                                // do we have a "software_was_used" information?
                                if (localAnnotation.getIsUsed()) {
                                    // add an attribute
                                    rs.addAttribute(new Attribute("subtype", "used"));
                                }

                                // add certainty provided by annotator
                                if (localAnnotation.getCertainty() != -1) {
                                    // add an attribute
                                    rs.addAttribute(new Attribute("cert", String.format("%.1f", ((float)localAnnotation.getCertainty())/10)));
                                } 
                            } else if (inlineAnnotation.getAttributeValue("type").equals("version")) {
                                rs.addAttribute(new Attribute("type", "version"));
                                rs.addAttribute(new Attribute("corresp", "#software-"+index_entity));
                            } else if (inlineAnnotation.getAttributeValue("type").equals("publisher")) {
                                rs.addAttribute(new Attribute("type", "publisher"));
                                rs.addAttribute(new Attribute("corresp", "#software-"+index_entity));
                            } else if (inlineAnnotation.getAttributeValue("type").equals("url")) {
                                rs.addAttribute(new Attribute("type", "url"));
                                rs.addAttribute(new Attribute("corresp", "#software-"+index_entity));
                            }

                            int indexAnnotator = annotators.indexOf(localAnnotation.getAnnotatorID());
                            if (indexAnnotator != -1)
                                rs.addAttribute(new Attribute("resp", "#annotator"+indexAnnotator));
                            
                            curSentence.appendChild(rs);

                            if (inlineAnnotation.getText().endsWith(" ")) {
                                lastPosition = position.end-1;
                            } else
                                lastPosition = position.end;
                        }

                        curSentence.appendChild(localContext.substring(lastPosition));
                        //curParagraph.appendChild(curSentence);

                        if (hasSoftware)
                            body.appendChild(curSentence);
                        hasTextContent = true;
                        if (previousLocalContexts == null)
                            previousLocalContexts = new ArrayList<String>();
                        previousLocalContexts.add(localContextSignature);

                         index_entity++;
                    }

                    if (hasTextContent)
                        nbDocWithValidNonPresentAnnotation++;
                }

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
                    String articleSet = softciteDocument.getArticleSet();
                    if (articleSet != null) {
                        ((org.w3c.dom.Element)importedFragmentNode).setAttribute("subtype", articleSet.replace("_article", ""));
                    }
                    ((org.w3c.dom.Element)importedFragmentNode).setAttribute("type", "article");

                    if (!articleSet.equals("training_article"))
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
        //System.out.println(nbDocWithNonPresentAnnotation + " documents WithNonPresentAnnotation");
        //System.out.println(nbDocWithValidNonPresentAnnotation + " documents nbDocWithValidNonPresentAnnotation");

        return document;
    }

    private void addUnmatchedAnnotations(String docName, 
                                        org.w3c.dom.Document document, 
                                        AnnotatedDocument softciteDocument, 
                                        List<String> annotators) {
        List<SoftciteAnnotation> localAnnotations = softciteDocument.getAnnotations();
        if (localAnnotations == null) {
            // it should never be the case
            return;
        }

        // check possible missing title
        // check missing title
        String missingTitle = this.missingTitles.get(softciteDocument.getDocumentID());
        if (missingTitle != null) {   
            try {
                XPath xPath = XPathFactory.newInstance().newXPath();
                String expression = "//TEI[descendant::fileDesc[@id='"+docName+"']]//title";
                NodeList nodes = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
                if (nodes.getLength() == 1) {
                    org.w3c.dom.Element titleNode = (org.w3c.dom.Element)nodes.item(0);
                    titleNode.setTextContent(missingTitle);
                }
            } catch(Exception e) {
                e.printStackTrace();
            } 
        }

        org.w3c.dom.Element documentRoot = null;
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "//TEI[descendant::fileDesc[@id='"+docName+"']]/text/body";
            NodeList nodes = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            if (nodes.getLength() == 1)
                documentRoot = (org.w3c.dom.Element)nodes.item(0);
            else 
                System.out.println(" **** Warning ***** " + docName + 
                    ": could not get root element node for this document in the TEI XML");
        } catch(Exception e) {
            e.printStackTrace();
        } 

        if (documentRoot == null) 
            return;

        String reviewText = documentRoot.getTextContent();
        String reviewTextSimplified = CrossAgreement.simplifiedFieldNoDigits(reviewText);

        // we init the entity index beyond the highest possible high number to avoid possible clash with the existing
        // software annatation identifiers present in the TEI XML
        int index_entity = localAnnotations.size();
        List<String> previousLocalContexts = null;
        for(SoftciteAnnotation localAnnotation : localAnnotations) {
            if (localAnnotation.getType() != AnnotationType.SOFTWARE)
                continue;

            String localContext = localAnnotation.getContext();
            if (localContext == null || localContext.trim().length() == 0)
                continue;

            String softwareString = localAnnotation.getSoftwareMention();
            if (softwareString == null || softwareString.trim().length() == 0)
                continue;

            localContext = XMLUtilities.stripNonValidXMLCharacters(localContext);
            localContext = localContext.replaceAll("<[^>]+>", " ");                        
            localContext = localContext.replace("\n", " ");
            localContext = localContext.replaceAll("( )+", " ");
            localContext = localContext.trim();

            // if the local context is already present in the paragraphs reviewed by the
            // curator, we skip the annotation
            String localContextSimplified = CrossAgreement.simplifiedFieldNoDigits(localContext);
            if (reviewTextSimplified.indexOf(localContextSimplified) != -1)
                continue;

            if (previousLocalContexts != null && previousLocalContexts.contains(localContextSimplified)) {
                continue;
            } 

            List<Annotation> sortedAnnotations = this.alignAnnotations(localAnnotation, localContext);
            //System.out.println("nb of inline annotations: " + sortedAnnotations.size());

            if (sortedAnnotations == null || sortedAnnotations.size() == 0) {
                System.out.println(" **** WARNING **** " + docName + 
                    " - No inline annotation possible for local annotation ");
                continue;
            }

            //nu.xom.Element curParagraph = teiElement("p");
            //nu.xom.Element curSentence = teiElement("s");
            nu.xom.Element curSentence = teiElement("ab");
            curSentence.addAttribute(new Attribute("type", "unmatched_with_pdf"));
            int lastPosition = 0;
            boolean hasSoftware = false;
            List<OffsetPosition> occupiedPositions = new ArrayList<OffsetPosition>();
            for(Annotation inlineAnnotation : sortedAnnotations) {
                if (inlineAnnotation.getAttributeValue("type") == null)
                    continue;

                OffsetPosition position = inlineAnnotation.getOccurence();

                // check if the position already taken
                if (AnnotatedCorpusGeneratorCSV.isOverlapping(occupiedPositions, position)) {
                    continue;
                } else {
                    occupiedPositions.add(position);
                }

                if (inlineAnnotation.getText().startsWith(" ")) {
                    curSentence.appendChild(localContext.substring(lastPosition,position.start)+" ");
                } else
                    curSentence.appendChild(localContext.substring(lastPosition,position.start));

                nu.xom.Element rs = teiElement("rs");
                rs.appendChild(inlineAnnotation.getText().trim());

                if (inlineAnnotation.getAttributeValue("type").equals("software")) { 
                    hasSoftware = true;
                    rs.addAttribute(new Attribute("type", "software"));
                    rs.addAttribute(new Attribute("id", docName+"-software-"+index_entity));
                    
                    // do we have a "software_was_used" information?
                    if (localAnnotation.getIsUsed()) {
                        // add an attribute
                        rs.addAttribute(new Attribute("subtype", "used"));
                    }

                    // add certainty provided by annotator
                    if (localAnnotation.getCertainty() != -1) {
                        // add an attribute
                        rs.addAttribute(new Attribute("cert", String.format("%.1f", ((float)localAnnotation.getCertainty())/10)));
                    } 
                } else if (inlineAnnotation.getAttributeValue("type").equals("version")) {
                    rs.addAttribute(new Attribute("type", "version"));
                    rs.addAttribute(new Attribute("corresp", "#software-"+index_entity));
                } else if (inlineAnnotation.getAttributeValue("type").equals("publisher")) {
                    rs.addAttribute(new Attribute("type", "publisher"));
                    rs.addAttribute(new Attribute("corresp", "#software-"+index_entity));
                } else if (inlineAnnotation.getAttributeValue("type").equals("url")) {
                    rs.addAttribute(new Attribute("type", "url"));
                    rs.addAttribute(new Attribute("corresp", "#software-"+index_entity));
                }

                int indexAnnotator = annotators.indexOf(localAnnotation.getAnnotatorID());
                if (indexAnnotator != -1)
                    rs.addAttribute(new Attribute("resp", "#annotator"+indexAnnotator));
                
                curSentence.appendChild(rs);

                if (inlineAnnotation.getText().endsWith(" ")) {
                    lastPosition = position.end-1;
                } else
                    lastPosition = position.end;
            }

            curSentence.appendChild(localContext.substring(lastPosition));
            //curParagraph.appendChild(curSentence);

            if (!hasSoftware)
                continue;
            if (previousLocalContexts == null)
                previousLocalContexts = new ArrayList<String>();
            previousLocalContexts.add(localContextSimplified);

            index_entity++;

            // convert to DOM
            //String fragmentXml = XmlBuilderUtils.toXml(curParagraph);
            String fragmentXml = XmlBuilderUtils.toXml(curSentence);
            fragmentXml = fragmentXml.replace("xmlns=\"http://www.tei-c.org/ns/1.0\"", "");
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                org.w3c.dom.Document fragmentDocument = builder.parse(new InputSource(new StringReader(fragmentXml)));

                // import fragment document into the main document (true argument is for deep import)
                org.w3c.dom.Node importedFragmentNode = document.importNode(fragmentDocument.getDocumentElement(), true);

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

    private List<Annotation> alignAnnotations(SoftciteAnnotation annotation, 
                                            String localContext) {
        if (annotation.getType() == AnnotationType.DUMMY)
            return null;

        String softwareString = annotation.getSoftwareMention();
        if (softwareString == null || softwareString.trim().length() == 0)
            return null;

        List<Annotation> inlineAnnotations = new ArrayList<Annotation>();

        // match the annotation in the context
        int endSoftware = -1;
        // we need this hack because original annotations have no offset, for instance for R
        if (softwareString.length() == 1)
            softwareString = " " + softwareString + " "; 
        int startSoftware = localContext.toLowerCase().indexOf(softwareString.toLowerCase());
        if (startSoftware != -1) {
            endSoftware = startSoftware + softwareString.length();
        }
        if (startSoftware != -1) {
            Annotation inlineAnnotation = new Annotation();
            OffsetPosition positionSoftware = new OffsetPosition();
            positionSoftware.start = startSoftware;
            positionSoftware.end = endSoftware;
            inlineAnnotation.addAttributeValue("type", "software");

            inlineAnnotation.setText(softwareString);
            inlineAnnotation.setOccurence(positionSoftware);

            inlineAnnotations.add(inlineAnnotation);
        }

        String versionString = annotation.getVersionNumber();
        if (versionString == null) 
            versionString = annotation.getVersionDate();
        if (versionString != null) {
            // match the annotation in the context
            int endVersion = -1;
            int startVersion = localContext.toLowerCase().indexOf(versionString.toLowerCase());
            if (startVersion != -1) {
                endVersion = startVersion + versionString.length();
            }

            if (startVersion != -1) {
                Annotation inlineAnnotation = new Annotation();
                OffsetPosition positionVersion = new OffsetPosition();
                positionVersion.start = startVersion;
                positionVersion.end = endVersion;
                inlineAnnotation.addAttributeValue("type", "version");

                inlineAnnotation.setText(versionString);
                inlineAnnotation.setOccurence(positionVersion);

                inlineAnnotations.add(inlineAnnotation);
            }
        }

        String publisherString = annotation.getCreator();
        if (publisherString != null) {
            // match the annotation in the context
            int endPublisher = -1;
            int startPublisher = localContext.toLowerCase().indexOf(publisherString.toLowerCase());
            if (startPublisher != -1) {
                endPublisher = startPublisher + publisherString.length();
            }
            if (startPublisher != -1) {
                Annotation inlineAnnotation = new Annotation();
                OffsetPosition positionPublisher = new OffsetPosition();
                positionPublisher.start = startPublisher;
                positionPublisher.end = endPublisher;
                inlineAnnotation.addAttributeValue("type", "publisher");

                inlineAnnotation.setText(publisherString);
                inlineAnnotation.setOccurence(positionPublisher);

                inlineAnnotations.add(inlineAnnotation);
            }
        }

        String urlString = annotation.getUrl();
        if (urlString != null) {
            // match the annotation in the context
            int endUrl = -1;
            int startUrl = localContext.toLowerCase().indexOf(urlString.toLowerCase());
            if (startUrl != -1) {
                endUrl = startUrl + urlString.length();
            }
            if (startUrl != -1) {
                Annotation inlineAnnotation = new Annotation();
                OffsetPosition positionUrl = new OffsetPosition();
                positionUrl.start = startUrl;
                positionUrl.end = endUrl;
                inlineAnnotation.addAttributeValue("type", "url");

                inlineAnnotation.setText(urlString);
                inlineAnnotation.setOccurence(positionUrl);

                inlineAnnotations.add(inlineAnnotation);
            }
        }

        Collections.sort(inlineAnnotations);

        return inlineAnnotations;
    }

    public static void generalCountAnnotations(Map<String, AnnotatedDocument> documents, 
                                        Map<String, SoftciteAnnotation> annotations,
                                        String xmlCorpusPath) {
        System.out.println("\n|annotation type|software entity annotations|all software annotations|articles with annotations|article without annotation|");
        System.out.println("|---               |---         |---        |---         |---      |");

        // raw annotation runs (article x annotator assigment)
        int software_annotation_count = 0;
        int all_mention_annotation_count = 0;
        int nb_articles_with_annotations = 0;
        int nb_articles_without_annotations = 0;
        int totalDocuments = 0;
        for (Map.Entry<String,AnnotatedDocument> entry : documents.entrySet()) {
            List<SoftciteAnnotation> localAnnotations = entry.getValue().getAnnotations();
            totalDocuments++;
            if (localAnnotations != null && localAnnotations.size()>0) {
                boolean hasSoftwareAnnotation = false;
                for(SoftciteAnnotation annotation : localAnnotations) {
                    if (annotation.getType() == AnnotationType.SOFTWARE) {
                        if (annotation.getSoftwareMention() == null || annotation.getSoftwareMention().trim().length() == 0)
                            continue; 
                        software_annotation_count += 1;
                        hasSoftwareAnnotation = true;
                        // software mention
                        all_mention_annotation_count += 1;
                        if (annotation.getVersionDate() != null && annotation.getVersionDate().trim().length()>0)
                            all_mention_annotation_count += 1;
                        if (annotation.getVersionNumber() != null && annotation.getVersionNumber().trim().length()>0)
                            all_mention_annotation_count += 1;
                        if (annotation.getCreator() != null && annotation.getCreator().trim().length()>0)
                            all_mention_annotation_count += 1;
                        if (annotation.getUrl() != null && annotation.getUrl().trim().length()>0)
                            all_mention_annotation_count += 1;
                    }
                }
                if (hasSoftwareAnnotation) 
                    nb_articles_with_annotations += 1;
                else
                    nb_articles_without_annotations += 1;
            } else
                nb_articles_without_annotations += 1;
        }
        System.out.println("|raw annotation runs|"+software_annotation_count+"|"+all_mention_annotation_count+"|"+
            nb_articles_with_annotations+"|"+nb_articles_without_annotations+"|");

        // raw annotation largest run per article
        all_mention_annotation_count = 0;
        software_annotation_count = 0;
        // for each article we restrict the count to the annotator who has produced the largest number of annotation
        // (or do we want to merge annotations for having the largest set as alternative?)
        for (Map.Entry<String,AnnotatedDocument> entry : documents.entrySet()) {
            // count the number of annotations per annotator
            Map<String,Integer> annotatorCountAll = new TreeMap<String,Integer>();
            Map<String,Integer> annotatorCountEntities = new TreeMap<String,Integer>();

            List<SoftciteAnnotation> localAnnotations = entry.getValue().getAnnotations();
            if (localAnnotations == null || localAnnotations.size() ==0)
                continue;
            for(SoftciteAnnotation annotation : localAnnotations) {
                if (annotation.getType() == AnnotationType.SOFTWARE) {
                    if (annotation.getSoftwareMention() == null || annotation.getSoftwareMention().trim().length() == 0)
                        continue; 

                    String annotator = annotation.getAnnotatorID();
                    
                    // per global software entity
                    if (annotatorCountEntities.get(annotator) == null) {
                        annotatorCountEntities.put(annotator, new Integer(1));
                    } else {
                        annotatorCountEntities.put(annotator, annotatorCountEntities.get(annotator)+1);
                    }

                    // per annotation, software mention is always present so we start at 1
                    int theSum = 1;
                    if (annotation.getVersionDate() != null && annotation.getVersionDate().trim().length()>0) 
                        theSum++;
                    if (annotation.getVersionNumber() != null && annotation.getVersionNumber().trim().length()>0) 
                        theSum++;
                    if (annotation.getCreator() != null && annotation.getCreator().trim().length()>0) 
                        theSum++;
                    if (annotation.getUrl() != null && annotation.getUrl().trim().length()>0) 
                        theSum++;

                    if (annotatorCountAll.get(annotator) == null) {
                        annotatorCountAll.put(annotator, new Integer(theSum));
                    } else {
                        annotatorCountAll.put(annotator, annotatorCountAll.get(annotator)+theSum);
                    }
                }
            }

            // keep the largest count
            int maxCountEntities = 0;
            int maxCountAll = 0;
            for (Map.Entry<String, Integer> entry2 : annotatorCountEntities.entrySet()) {
                if (entry2.getValue() > maxCountEntities)
                    maxCountEntities = entry2.getValue();
            }
            for (Map.Entry<String, Integer> entry2 : annotatorCountAll.entrySet()) {
                if (entry2.getValue() > maxCountAll)
                    maxCountAll = entry2.getValue();
            }

            // update count for the document
            all_mention_annotation_count += maxCountAll;
            software_annotation_count += maxCountEntities;
        }

        System.out.println("|raw annotation largest run|"+software_annotation_count+"|"+all_mention_annotation_count+"|"+
            nb_articles_with_annotations+"|"+nb_articles_without_annotations+"|");

        // annotations located in PDF
        org.w3c.dom.Document document = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            String tei = FileUtils.readFileToString(new File(xmlCorpusPath), UTF_8);

            document = builder.parse(new InputSource(new StringReader(tei)));

            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "//rs";
            NodeList nodes = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            all_mention_annotation_count = nodes.getLength();

            expression = "//rs[@type='software']";
            nodes = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            software_annotation_count = nodes.getLength();

            expression = "//TEI";
            nodes = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            nb_articles_with_annotations = nodes.getLength();

            nb_articles_without_annotations = totalDocuments - nb_articles_with_annotations;
        } catch(ParserConfigurationException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        } catch(Exception e) {
            e.printStackTrace();
        } 

        System.out.println("|annotations located in PDF|"+software_annotation_count+"|"+all_mention_annotation_count+"|"+
            nb_articles_with_annotations+"|-|");

        // annotation reviewed by curator
        System.out.println("|annotation reviewed by curator|"+software_annotation_count+"|"+all_mention_annotation_count+"|"+
            nb_articles_with_annotations+"|-|");

        // annotation edited by curator
        all_mention_annotation_count = 0;
        software_annotation_count = 0;
        nb_articles_with_annotations = 0;
        nb_articles_without_annotations = 0;
        document = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            String tei = FileUtils.readFileToString(new File(xmlCorpusPath), UTF_8);

            document = builder.parse(new InputSource(new StringReader(tei)));

            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "//rs[@resp='#curator']";
            NodeList nodes = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            all_mention_annotation_count = nodes.getLength();

            expression = "//TEI[descendant::rs[@resp='#curator']]";
            nodes = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            nb_articles_with_annotations = nodes.getLength();

        } catch(ParserConfigurationException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        } catch(Exception e) {
            e.printStackTrace();
        } 

        document = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            String tei = FileUtils.readFileToString(new File(xmlCorpusPath), UTF_8);

            document = builder.parse(new InputSource(new StringReader(tei)));

            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "//rs/@id";
            List<String> localIds = new ArrayList<String>();
            NodeList nodes = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                org.w3c.dom.Node idElement = nodes.item(i);
                if (!localIds.contains(idElement.getNodeValue()))
                    localIds.add(idElement.getNodeValue());
            }

            for(String localId : localIds) {
                String expression3 = "//rs[@resp='#curator'][@corresp='#" + localId + "']";
                NodeList nodes3 = (NodeList) xPath.compile(expression3).evaluate(document, XPathConstants.NODESET);

                String expression4 = "//rs[@resp='#curator'][@id='" + localId + "']";
                NodeList nodes4 = (NodeList) xPath.compile(expression4).evaluate(document, XPathConstants.NODESET);

                if (nodes3.getLength() > 0 || nodes4.getLength() > 0) {
                    software_annotation_count++;
                }
            }

        } catch(ParserConfigurationException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        } catch(Exception e) {
            e.printStackTrace();
        } 

        System.out.println("|annotation edited by curator|"+software_annotation_count+"|"+all_mention_annotation_count+"|"+
            nb_articles_with_annotations+"|-|");
    }

    private void importCSVMissingTitles(String csvPath, Map<String, AnnotatedDocument> documents) {
        // this csv file gives missing titles for some hard to process articles
        File softciteTitles = new File(csvPath + File.separator + "imputation-tei-article-missing-title.csv");
        try {
            BufferedReader b = new BufferedReader(new FileReader(softciteTitles));
            boolean start = true;
            int nbCSVlines = 0;
            String line;
            while ((line = b.readLine()) != null) {
                // article_id,article_title 
                // however the csv file is not a valid csv so we need to go manually and not with a csv parser
                if (start) {
                    start = false;
                    continue;
                }
                int ind = line.indexOf(",");
                String documentID = line.substring(0,ind).trim();
                String title = line.substring(ind+1).trim();

                AnnotatedDocument document = documents.get(documentID);
                /*int ind = documentID.indexOf("_");
                if (ind != -1)
                    documentID = documentID.substring(0,ind);*/
                if (documents.get(documentID) == null) {
                    System.out.println(" **** Warning **** unknown document identifier: " + documentID);
                    continue;
                }
                this.missingTitles.put(documentID, title);
                //documentID = documentID.replace("%2F", "/");
                //this.missingTitles.put(documentID, title);
                nbCSVlines++;
            }
            System.out.println("number of documents with missing titles: " + nbCSVlines);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

