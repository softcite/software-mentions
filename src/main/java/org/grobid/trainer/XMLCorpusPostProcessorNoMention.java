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

                //System.out.println(identifier + " / " + annotatorName);

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
                System.out.println(" **** Warning ****" + docName + " - document with null localAnnotation object");
            } /*else {
                System.out.println(docName + " - " + localAnnotations.size() + " annotations");
                if (localAnnotations.size() == 1) {
                    System.out.println(docName + " - " + localAnnotations.get(0).getType());
                }
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

                AnnotatedDocument annotatedDocument = entry.getValue();
                annotatedDocument.setBiblio(biblio);

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

                AnnotatedDocument annotatedDocument = entry.getValue();
                annotatedDocument.setBiblio(biblio);

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

                        localContext = localContext.replaceAll("[\\000\\001\\002\\003\\006]+", "");
                        localContext = localContext.replaceAll("<[^>]+>", " ");                        
                        localContext = localContext.replace("\n", " ");
                        localContext = localContext.replaceAll("( )+", " ");
                        localContext = localContext.trim();

                        nu.xom.Element curParagraph = null;
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

                        curParagraph = teiElement("p");
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
                                curParagraph.appendChild(localContext.substring(lastPosition,position.start)+" ");
                            } else
                                curParagraph.appendChild(localContext.substring(lastPosition,position.start));

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
                            
                            curParagraph.appendChild(rs);

                            if (inlineAnnotation.getText().endsWith(" ")) {
                                lastPosition = position.end-1;
                            } else
                                lastPosition = position.end;
                        }

                        curParagraph.appendChild(localContext.substring(lastPosition));

                        if (hasSoftware)
                            body.appendChild(curParagraph);
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
        System.out.println(nbDocWithNonPresentAnnotation + " documents WithNonPresentAnnotation");
        System.out.println(nbDocWithValidNonPresentAnnotation + " documents nbDocWithValidNonPresentAnnotation");

        return document;
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

