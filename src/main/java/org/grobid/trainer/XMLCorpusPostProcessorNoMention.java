 package org.grobid.trainer;

import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.engines.Engine;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.utilities.*;
import org.grobid.core.utilities.SoftwareConfiguration;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

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
 * - addition of corrected "software_use" attributes
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
    private SoftwareConfiguration configuration;

    private Map<String, String> orgin2Key = null;

    private List<String> trainingDoc = Arrays.asList("a2008-39-NAT_BIOTECHNOL", "a2010-05-BMC_MOL_BIOL", 
        "a2007-48-UNDERSEA_HYPERBAR_M", "a2001-40-MOL_ECOL", "a2008-02-WATERBIRDS");

    // map mention with corrected usage information
    private Map<String, Boolean> softwareUsages = new TreeMap<>();

    public XMLCorpusPostProcessorNoMention(SoftwareConfiguration conf) {
        this.configuration = conf;
    }

    /**
     * Inject curation class description, document entries without mention and curation class at document level     
     */
    public void process(String xmlCorpusPath, String csvPath, String pdfPath, String newXmlCorpusPath, boolean extraContext) throws IOException {
        
        Map<String, AnnotatedDocument> documents = new HashMap<String, AnnotatedDocument>();
        Map<String, SoftciteAnnotation> annotations = new HashMap<String, SoftciteAnnotation>();

        AnnotatedCorpusGeneratorCSV converter = new AnnotatedCorpusGeneratorCSV(this.configuration);
        converter.importCSVFiles(csvPath, documents, annotations);
        // documents without annotation are present with a "dummy" annotation 

        this.importCSVMissingTitles(csvPath, documents);
        this.loadSoftwareUsage();
        
        // we unfortunately need to use DOM to update the XML file which is always a lot of pain
        String tei = null;
        org.w3c.dom.Document document = null;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            tei = FileUtils.readFileToString(new File(xmlCorpusPath), UTF_8);

            document = builder.parse(new InputSource(new StringReader(tei)));

            // if we want extra-context
            if (extraContext) {
                //Pair<String,String> extraTEIs = 
                document = addExtraContext(document, documents, pdfPath);
            }

            document = enrichTEIDocument(document, documents, pdfPath);
            document = enrichTEIDocumentNoMention(document, documents, pdfPath);

            // fix all xml:id which are not valid NCName
            //document = fixIdNCName(document);

            tei = XMLUtilities.serialize(document, null);
            document = builder.parse(new InputSource(new StringReader(tei)));

            // normalize document-level identifiers with uniform random hexa keys 
            // and remove invalid/training docs
            document = normalizeIdentifiers(document);

            // inject curated software usage attributes
            document = correctSoftwareUsage(document);

            // inject description notes for full corpus
            document = injectDescriptionNotes(document, true);

            tei = XMLUtilities.serialize(document, null);
            tei = reformatTEI(tei);
        } catch(ParserConfigurationException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        } catch(Exception e) {
            e.printStackTrace();
        } 

        // write updated full TEI file
        if (tei != null) {
            FileUtils.writeStringToFile(new File(newXmlCorpusPath.replace(".tei.xml", "-full.tei.xml")), tei, UTF_8);        

            // write a more compact TEI file without no mention entries and without the non aligned segments (<ab>)
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                document = builder.parse(new InputSource(new StringReader(tei)));
                document = prune(document);
                tei = XMLUtilities.serialize(document, null);

                document = builder.parse(new InputSource(new StringReader(tei)));

                // inject description notes for (default) "compact" corpus
                document = injectDescriptionNotes(document, false);

                tei = XMLUtilities.serialize(document, null);
                tei = reformatTEI(tei);
            } catch(ParserConfigurationException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            } catch(Exception e) {
                e.printStackTrace();
            } 

            // write updated full TEI file
            if (tei != null) {
                FileUtils.writeStringToFile(new File(newXmlCorpusPath), tei, UTF_8);
            }
        }

        // if we want extra-context
        /*if (extraContext) {
            Pair<String,String> extraTEIs = addExtraContext(document, documents, pdfPath);
        }*/

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

                org.w3c.dom.Element teiHeaderElement = XMLUtilities.getFirstDirectChild(teiElement, "teiHeader");
                if (teiHeaderElement != null) {
                    org.w3c.dom.Element fileDescElement = XMLUtilities.getFirstDirectChild(teiHeaderElement, "fileDesc");
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
                org.w3c.dom.Element nameElement = XMLUtilities.getFirstDirectChild((org.w3c.dom.Element)nNode, "name");
                String annotatorName = XMLUtilities.getText(nameElement);
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
                File pdfFile = AnnotatedCorpusGeneratorCSV.getPDF(documentPath, docName, articleUtilities, this.configuration);

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
                if (biblio.getTitle() == null || biblio.getTitle().trim().length() == 0) {
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
                File pdfFile = AnnotatedCorpusGeneratorCSV.getPDF(documentPath, docName, articleUtilities, this.configuration);

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
                                rs.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", docName+"-software-"+index_entity));
                                
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
                                rs.addAttribute(new Attribute("corresp", "#" + docName + "-software-"+index_entity));
                            } else if (inlineAnnotation.getAttributeValue("type").equals("publisher")) {
                                rs.addAttribute(new Attribute("type", "publisher"));
                                rs.addAttribute(new Attribute("corresp", "#" + docName + "-software-"+index_entity));
                            } else if (inlineAnnotation.getAttributeValue("type").equals("url")) {
                                rs.addAttribute(new Attribute("type", "url"));
                                rs.addAttribute(new Attribute("corresp", "#" + docName + "-software-"+index_entity));
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
                    rs.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", docName+"-software-"+index_entity));
                    
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
                    rs.addAttribute(new Attribute("corresp", "#" + docName + "-software-"+index_entity));
                } else if (inlineAnnotation.getAttributeValue("type").equals("publisher")) {
                    rs.addAttribute(new Attribute("type", "publisher"));
                    rs.addAttribute(new Attribute("corresp", "#" + docName + "-software-"+index_entity));
                } else if (inlineAnnotation.getAttributeValue("type").equals("url")) {
                    rs.addAttribute(new Attribute("type", "url"));
                    rs.addAttribute(new Attribute("corresp", "#" + docName + "-software-"+index_entity));
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

    private org.w3c.dom.Document normalizeIdentifiers(org.w3c.dom.Document document) {
        // for the document-level entries (<TEI>) to be removed from the corpus
        List<org.w3c.dom.Element> toRemove = new ArrayList<>();

        org.w3c.dom.Element documentRoot = document.getDocumentElement();
        XPathFactory xpathFactory = XPathFactory.newInstance();
        try {
            XPath xpath = xpathFactory.newXPath();
            XPathExpression expr = xpath.compile("//TEI");
            NodeList result = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
            NodeList nodes = (NodeList) result;
            for(int i=0; i < nodes.getLength(); i++) {
                org.w3c.dom.Element teiElement = (org.w3c.dom.Element)nodes.item(i);

                org.w3c.dom.Element teiHeaderElement = XMLUtilities.getFirstDirectChild(teiElement, "teiHeader");
                if (teiHeaderElement != null) {
                    org.w3c.dom.Element fileDescElement = XMLUtilities.getFirstDirectChild(teiHeaderElement, "fileDesc");
                    if (fileDescElement != null) {
                        String docId = fileDescElement.getAttribute("xml:id");
                        if (trainingDoc.contains(docId)) {
                            toRemove.add(teiElement);
                        }

                        // get an hexadecimal key for this document
                        // get an already existing key if available
                        String newDocId = orgin2KeyGen(docId);                        
                        // otherwise generate a new one
                        if (newDocId == null) {
                            newDocId = HexaKeyGen.getHexaKey(10, true);
                            if (!trainingDoc.contains(docId)) {
                                System.out.println("Warning: no existing document hexa key for " + docId + 
                                    " - a new key is generated: " + newDocId);
                            }
                        }

                        fileDescElement.setAttribute("xml:id", newDocId);

                        // keep a trace of the ID under sourceDesc/bibl/idno @origin
                        org.w3c.dom.Element sourceDescElement = XMLUtilities.getFirstDirectChild(fileDescElement, "sourceDesc");
                        if (sourceDescElement != null) {
                            org.w3c.dom.Element biblElement = XMLUtilities.getFirstDirectChild(sourceDescElement, "bibl");
                            if (biblElement != null) {
                                org.w3c.dom.Element idnoElement =  document.createElement("idno");
                                idnoElement.setAttribute("type", "origin");
                                idnoElement.setTextContent(docId);
                                biblElement.appendChild(idnoElement);
                            }
                        }

                        // update the @xml:id and @corresp attributes for this document
                        replaceIdNCNameElement(teiElement, docId, newDocId);
                    }
                }
            }
        } catch(XPathExpressionException e) {
            e.printStackTrace();
        }

        for(org.w3c.dom.Element element : toRemove) {
            element.getParentNode().removeChild(element);
        }

        // fix idno
        try {
            XPath xpath = xpathFactory.newXPath();
            XPathExpression expr = xpath.compile("//idno");
            NodeList result = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
            NodeList nodes = (NodeList) result;
            for(int i=0; i < nodes.getLength(); i++) {
                org.w3c.dom.Element idnoElement = (org.w3c.dom.Element)nodes.item(i);
                // check the attribute is type and fix otherwise

                NamedNodeMap attributes = idnoElement.getAttributes();
                String idnoType = null;
                for(int j=0; j < attributes.getLength(); j++) {
                    org.w3c.dom.Node theAttribute = attributes.item(j);
                    if (!theAttribute.getNodeName().equals("type")) {
                        idnoType = theAttribute.getNodeName();
                        String idnoValue = theAttribute.getNodeValue();

                        idnoElement.setAttribute("type", idnoType);
                        idnoElement.setTextContent(idnoValue);

                        break;
                    }
                }
                if (idnoType != null)
                    idnoElement.removeAttribute(idnoType);
            }
        } catch(XPathExpressionException e) {
            e.printStackTrace();
        }    

        return document;
    }

    private org.w3c.dom.Document correctSoftwareUsage(org.w3c.dom.Document document) {
        org.w3c.dom.Element documentRoot = document.getDocumentElement();
        XPathFactory xpathFactory = XPathFactory.newInstance();
        System.out.println("correctSoftwareUsage: expected " + this.softwareUsages.size() + " corrections");
        // the list of rs element id corrected, to control if all corrections have been applied
        List<String> consumed = new ArrayList<>();
        try {
            XPath xpath = xpathFactory.newXPath();
            XPathExpression expr = xpath.compile("//rs");
            NodeList result = (NodeList)expr.evaluate(document, XPathConstants.NODESET);
            NodeList nodes = (NodeList) result;
            for(int i=0; i < nodes.getLength(); i++) {
                org.w3c.dom.Element rsElement = (org.w3c.dom.Element)nodes.item(i);
                // get xml:id
                NamedNodeMap attributes = rsElement.getAttributes();
                String localId = null;
                for(int j=0; j < attributes.getLength(); j++) {
                    org.w3c.dom.Node theAttribute = attributes.item(j);
                    if (theAttribute.getNodeName().equals("xml:id")) {
                        localId = theAttribute.getNodeValue();
                        break;
                    }
                }
                if (localId != null && this.softwareUsages.get(localId) != null) {
                    if (this.softwareUsages.get(localId).booleanValue())
                        rsElement.setAttribute("subtype", "used");
                    else 
                        rsElement.removeAttribute("subtype");

                    consumed.add(localId);
                }
            }

        } catch(XPathExpressionException e) {
            e.printStackTrace();
        }    

        int notApplied = 0;
        for (Map.Entry<String, Boolean> entry : this.softwareUsages.entrySet()) {
            if (!consumed.contains(entry.getKey())) {
                System.out.println("Warning: " + entry.getKey() + "/" + entry.getValue() + " software_used correction has not been applied !" );
                notApplied++;
            }
        }
        System.out.println("total of " + notApplied + " software use corrections not applied");

        return document;

    }


    /**
     * Remove <ab> elements and doc entries without text content and without any annotations
     */
    private org.w3c.dom.Document prune(org.w3c.dom.Document document) {
        // remove <ab> elements
        NodeList theElements = document.getElementsByTagName("ab");
        for(int i=theElements.getLength()-1; i >= 0; i--) {
            org.w3c.dom.Element theElement = (org.w3c.dom.Element)theElements.item(i);
            theElement.getParentNode().removeChild(theElement);
        }

        // remove doc entries (<TEI> elements) having <body> without content 
        XPathFactory xpathFactory = XPathFactory.newInstance();
        try {
            XPath xPath = xpathFactory.newXPath();
            String expression = "//TEI/text/body";
            NodeList nodes = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            for(int i=nodes.getLength()-1; i >= 0; i--) {
                boolean hasNoAnnotation = true;
                boolean entryRemoved = false;
                org.w3c.dom.Element bodyElement = (org.w3c.dom.Element)nodes.item(i);
                // if the bodyElement has no child, we will remove the TEI entry
                if (!bodyElement.hasChildNodes()) {
                    // get TEI parent, if we are here we're sure these elements exist
                    org.w3c.dom.Element textElement = (org.w3c.dom.Element)bodyElement.getParentNode();
                    org.w3c.dom.Element teiElement = (org.w3c.dom.Element)textElement.getParentNode();
                    teiElement.getParentNode().removeChild(teiElement);
                    entryRemoved = true;
                } else {
                    // check if we have no <p> element
                    if (XMLUtilities.getFirstDirectChild(bodyElement, "p") == null) {
                        org.w3c.dom.Element textElement = (org.w3c.dom.Element)bodyElement.getParentNode();
                        org.w3c.dom.Element teiElement = (org.w3c.dom.Element)textElement.getParentNode();
                        teiElement.getParentNode().removeChild(teiElement);
                        entryRemoved = true;
                    }
                }
                // check if we have at least one annotation
                int l = 0;
                NodeList pList = bodyElement.getElementsByTagName("p");
                for (int j = pList.getLength()-1; j >= 0; j--) {
                    org.w3c.dom.Element snippetElement = (org.w3c.dom.Element) pList.item(j);

                    // find the entities
                    NodeList entityList = snippetElement.getElementsByTagName("rs");
                    if (entityList.getLength() != 0) {
                        // annotation in this snippet
                        hasNoAnnotation = false;
                    } else {
                        // without any annotations, we remove the paragraph, as we want to keep only positive contexts
                        bodyElement.removeChild(snippetElement);
                    }
                }

                if (hasNoAnnotation && !entryRemoved) {
                    // the TEI entry must be pruned
                    // get TEI parent, if we are here we're sure these elements exist
                    org.w3c.dom.Element textElement = (org.w3c.dom.Element)bodyElement.getParentNode();
                    org.w3c.dom.Element teiElement = (org.w3c.dom.Element)textElement.getParentNode();
                    teiElement.getParentNode().removeChild(teiElement);
                }

            }
        } catch(XPathExpressionException e) {
            e.printStackTrace();
        }    

        return document;
    }

    /**
     * Add extra paragraphs if the current paragraphs where mentions take place are too small. 
     * We expect 200 words context minimum. 
     * We produce two TEI variants with extra context:
     * - one considering that we need 200 words minimum in the current paragraph, not considering 
     *   a window centered on the mention
     * - one considering a minimum of 100 words before and after the mention, so a window centered 
     *   on the mention. 
     * If not enough material is present in the paragraph, we add one before and after, so that 
     * the minimum amount of words is reached.   
     */
    //private Pair<String,String> 
    private org.w3c.dom.Document addExtraContext(org.w3c.dom.Document document, 
                                                Map<String, AnnotatedDocument> documents,
                                                String documentPath) {
        Engine engine = GrobidFactory.getInstance().getEngine();
        org.w3c.dom.Element documentRoot = document.getDocumentElement();
        XPathFactory xpathFactory = XPathFactory.newInstance();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        int tooShort = 0;
        int total = 0;
        for (Map.Entry<String, AnnotatedDocument> entry : documents.entrySet()) {
            /*if (m > 100) {
                break;
            }
            m++;*/
            String docName = entry.getKey();
            AnnotatedDocument softciteDocument = entry.getValue();
            org.w3c.dom.Document fullDocument = null;
            List<org.w3c.dom.Node> localParagraphs = null;
            // check if the document is present in the TEI corpus, it means it has at 
            // least one annotation matching with its PDF  
            try {
                XPath xpath = xpathFactory.newXPath();
                //XPathExpression expr = xpath.compile("//*[@id='"+docName+"']");
                String expression = "//TEI[descendant::fileDesc[@id='"+docName+"']]/text/body/p";
                NodeList nodes = (NodeList) xpath.compile(expression).evaluate(document, XPathConstants.NODESET);
                if (nodes.getLength() != 0) {
                    List<String> localTexts = new ArrayList<>();
                    // document present with content, we can check the length of the paragraphs
                    for(int i=0; i < nodes.getLength(); i++) {
                        org.w3c.dom.Element pElement = (org.w3c.dom.Element)nodes.item(i);
                        // check text length
                        String localText = pElement.getTextContent();
                        localTexts.add(CrossAgreement.simplifiedField(localText));
                        total++;
                    }

                    for(int i=0; i < nodes.getLength(); i++) {
                        org.w3c.dom.Element pElement = (org.w3c.dom.Element)nodes.item(i);
                        String localText = pElement.getTextContent();
                        String[] pieces = localText.split("[ -,.:]");
                        //if (pieces.length < 200) 
                        {
                            //tooShort++; 

                            // get position of first and last <rs> in this pElement
                            int nbTokensLeft = -1;
                            org.w3c.dom.Element rsElement = XMLUtilities.getFirstDirectChild(pElement, "rs");
                            if (rsElement != null) {
                                Pair<String,String> context = XMLUtilities.getLeftRightTextContent(rsElement);
                                String leftContext = context.getLeft();
                                int ind = localText.indexOf(leftContext);
                                leftContext = localText.substring(0,ind) + leftContext;
                                String[] leftPieces = leftContext.split("[ -,.:]");
                                nbTokensLeft = leftPieces.length;
                            }

                            int nbTokensRight = -1;
                            rsElement = XMLUtilities.getLastDirectChild(pElement, "rs");
                            if (rsElement != null) {
                                Pair<String,String> context = XMLUtilities.getLeftRightTextContent(rsElement);
                                String rightContext = context.getRight();
                                int ind = localText.indexOf(rightContext);
                                rightContext = rightContext + localText.substring(ind+rightContext.length(), localText.length());
                                String[] rightPieces = rightContext.split("[ -,.:]");
                                nbTokensRight = rightPieces.length;
                            }

                            File pdfFile = AnnotatedCorpusGeneratorCSV.getPDF(documentPath, docName, this.articleUtilities, this.configuration);
                            String fullPath = pdfFile.getPath();
                            fullPath = fullPath.replace(".pdf", ".fulltext.tei.xml");
                            
                            // check if full text tei file is present, process with Grobid otherwise
                            String fullTei = null;
                            File teiFile = new File(fullPath);
                            if (teiFile.exists()) {
                                fullTei = FileUtils.readFileToString(teiFile, "UTF-8");
                            } else {
                                // process header with consolidation to get some nice header metadata for this document
                                GrobidAnalysisConfig configFulltext = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder()
                                                        .consolidateHeader(0)
                                                        .consolidateCitations(0)
                                                        .build();
                                try {
                                    fullTei = engine.fullTextToTEI(pdfFile,configFulltext);
                                    if (fullTei != null) {
                                        // write the file for future use
                                        FileUtils.writeStringToFile(teiFile, fullTei, "UTF-8");
                                    }
                                } catch(Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            localParagraphs = new ArrayList<>();
                            if (fullTei != null) {
                                DocumentBuilder builder = factory.newDocumentBuilder();
                                fullDocument = builder.parse(new InputSource(new StringReader(fullTei)));

                                // get all <p>
                                XPath xpath2 = xpathFactory.newXPath();
                                String expression2 = "//p";
                                NodeList nodes2 = (NodeList) xpath2.compile(expression2).evaluate(fullDocument, XPathConstants.NODESET);
                                for(int j=0; j < nodes2.getLength(); j++) {  
                                    org.w3c.dom.Element pElementFull = (org.w3c.dom.Element)nodes2.item(j);
                                    localParagraphs.add(pElementFull);
                                }
                                // find the short segment
                                int k = 0;
                                for(org.w3c.dom.Node theParagraph : localParagraphs) {
                                    String paragraphText = theParagraph.getTextContent();

                                    if (paragraphText != null && 
                                        CrossAgreement.simplifiedField(paragraphText).equals(CrossAgreement.simplifiedField(localText))) {
                                        if (k>0 && nbTokensLeft < 100 && nbTokensLeft != -1) {
                                            org.w3c.dom.Node theOtherParagraph = localParagraphs.get(k-1);
                                            String localOtherParagraphText = theOtherParagraph.getTextContent();
                                            // check if this additional paragraph is not already there
                                            if (!localTexts.contains(CrossAgreement.simplifiedField(localOtherParagraphText))) {
                                                org.w3c.dom.Node importedParagraphNode = document.importNode(theOtherParagraph, true);
                                                pElement.getParentNode().insertBefore(importedParagraphNode, pElement);
                                                localTexts.add(CrossAgreement.simplifiedField(localOtherParagraphText));

                                                String[] localOtherParagraphTextPieces = localOtherParagraphText.split("[ -,.:]");
                                                nbTokensLeft = localOtherParagraphTextPieces.length + nbTokensLeft;

                                                if (k-1>0 && nbTokensLeft < 100) {
                                                    // let's just add a new one
                                                    theOtherParagraph = localParagraphs.get(k-2);
                                                    localOtherParagraphText = theOtherParagraph.getTextContent();
                                                    // check if this additional paragraph is not already there
                                                    if (!localTexts.contains(CrossAgreement.simplifiedField(localOtherParagraphText))) {
                                                        org.w3c.dom.Node importedParagraphNode2 = document.importNode(theOtherParagraph, true);
                                                        pElement.getParentNode().insertBefore(importedParagraphNode2, importedParagraphNode);
                                                        localTexts.add(CrossAgreement.simplifiedField(localOtherParagraphText));
                                                    }
                                                }
                                            }
                                        } 

                                        if (k < localParagraphs.size()-1 && nbTokensRight < 100 && nbTokensRight != -1) {
                                            org.w3c.dom.Node theOtherParagraph = localParagraphs.get(k+1);
                                            String localOtherParagraphText = theOtherParagraph.getTextContent();
                                            // check if this additional paragraph is not already there
                                            if (!localTexts.contains(CrossAgreement.simplifiedField(localOtherParagraphText))) {
                                                org.w3c.dom.Node importedParagraphNode = document.importNode(theOtherParagraph, true);
                                                pElement.getParentNode().insertBefore(importedParagraphNode, pElement.getNextSibling());
                                                localTexts.add(CrossAgreement.simplifiedField(localOtherParagraphText));

                                                String[] localOtherParagraphTextPieces = localOtherParagraphText.split("[ -,.:]");
                                                nbTokensRight = localOtherParagraphTextPieces.length + nbTokensRight;

                                                if (k < localParagraphs.size()-2 && nbTokensRight < 100) {
                                                    theOtherParagraph = localParagraphs.get(k+2);
                                                    localOtherParagraphText = theOtherParagraph.getTextContent();
                                                    // check if this additional paragraph is not already there
                                                    if (!localTexts.contains(CrossAgreement.simplifiedField(localOtherParagraphText))) {
                                                        org.w3c.dom.Node importedParagraphNode2 = document.importNode(theOtherParagraph, true);
                                                        pElement.getParentNode().insertBefore(importedParagraphNode2, importedParagraphNode.getNextSibling());
                                                        localTexts.add(CrossAgreement.simplifiedField(localOtherParagraphText));
                                                    }
                                                }
                                            }                                            
                                        }
                                    }
                                    k++;
                                }
                            }
                        }
                    }
                }

                // add paragraph rank to identify group of contexts
                /*nodes = (NodeList) xpath.compile(expression).evaluate(document, XPathConstants.NODESET);
                if (nodes.getLength() != 0) {
                    for(int i=0; i < nodes.getLength(); i++) {
                        org.w3c.dom.Element pElement = (org.w3c.dom.Element)nodes.item(i);
                        String localText = pElement.getTextContent();

                        if (localParagraphs != null) {
                            int k = 0;
                            for(org.w3c.dom.Node theParagraph : localParagraphs) {
                                    String paragraphText = theParagraph.getTextContent();

                                if (paragraphText != null && 
                                    CrossAgreement.simplifiedField(paragraphText).equals(CrossAgreement.simplifiedField(localText))) {
                                    pElement.setAttribute("n", ""+k);
                                    break;
                                }
                                k++;
                            }
                        }
                    }
                }*/
            } catch(XPathExpressionException e) {
                e.printStackTrace();
            } catch(Exception e) {
                e.printStackTrace();
            }

        }

        //System.out.println("----------------------------" + tooShort + "/" + total);

        return document; //Pair.of("", "");
    }

    private String reformatTEI(String tei) {
        tei = tei.replaceAll("\"\n( )*", "\" ");
        tei = tei.replaceAll("<p>\n( )*<ref", "<p><ref");
        tei = tei.replaceAll("<p>\n( )*<rs", "<p><rs");
        tei = tei.replaceAll("</ref>\n( )*<ref", "</ref> <ref");
        tei = tei.replaceAll("</rs>\n( )*<rs ", "</rs> <rs ");
        tei = tei.replaceAll(" \n( )*<rs ", " <rs ");
        tei = tei.replaceAll("</rs>\n( )*<ref ", "</rs> <ref ");
        tei = tei.replaceAll("</rs>\n( )*</p>", "</rs></p>");
        tei = tei.replaceAll("</ref>\n( )*</p>", "</ref></p>");
        tei = tei.replaceAll("</ref>\n( )*<rs ", "</ref> <rs ");
        tei = tei.replaceAll("xmlns=\"\" ", "");
        return tei;
    }

    private org.w3c.dom.Document injectDescriptionNotes(org.w3c.dom.Document document, boolean full) {
        // inject descriptions as <note> under <notesStmt>
        org.w3c.dom.NodeList corpusFileDescList = document.getElementsByTagName("fileDesc");
        // take the first, which is the titleStmt of the teiCorpus header
        if (corpusFileDescList.getLength() > 0) {
            org.w3c.dom.Element corpusFileDescElement = (org.w3c.dom.Element) corpusFileDescList.item(0); 

            // remove possible existing <notesStmt>
            org.w3c.dom.NodeList corpusNotesStmtList = document.getElementsByTagName("notesStmt");
            // take the first, which is the titleStmt of the teiCorpus header
            if (corpusNotesStmtList.getLength() > 0) {
                corpusFileDescElement.removeChild((org.w3c.dom.Element) corpusNotesStmtList.item(0)); 
            }

            // create notesStmt
            org.w3c.dom.Element notesStmt = document.createElement("notesStmt");

            // create note
            org.w3c.dom.Element noteElement1 = document.createElement("note");
            noteElement1.setTextContent("The Softcite dataset is a gold standard corpus of manually annotated software mentions from academic PDFs.");
            notesStmt.appendChild(noteElement1);

            org.w3c.dom.Element noteElement2 = document.createElement("note");
            if (full)   
                noteElement2.setTextContent("This corpus file contains one TEI entry for every scholar publication, including or not manual annotations. For scholar publications containing annotations, each paragraph containing at least one manually annotated software mention is encoded under the TEI body element. All the manual annotations under p elements (paragraph) have been further validated or corrected by a curator to reach a final decision.");
            else
                noteElement2.setTextContent("This corpus file contains one TEI entry per scholar publication having at least one software mention. Each paragraph containing at least one manually annotated software mentions is encoded under the TEI body element.");
            notesStmt.appendChild(noteElement2);

            if (full) {
                org.w3c.dom.Element noteElement3 = document.createElement("note");
                noteElement3.setTextContent("For completeness, under ab elements (anonymous block), we provide additional snippets for manual annotations that could not be aligned with the full paragraph content automatically extracted from PDF. Annotations and contexts under ab elements were not validated by a curator. Therefore, these additional annotations and snippets should not be considered as \"gold\" annotation.");
                notesStmt.appendChild(noteElement3);                
            }

            // we insert the notesStmt before publicationStmt
            org.w3c.dom.NodeList corpusPublicationStmtList = document.getElementsByTagName("publicationStmt");
            if (corpusPublicationStmtList.getLength() > 0) {
                org.w3c.dom.Node corpusPublicationStmtNode = corpusPublicationStmtList.item(0); 
                corpusFileDescElement.insertBefore(notesStmt, corpusPublicationStmtNode);
            }
        }

        return document;
    }

    private org.w3c.dom.Document fixIdNCName(org.w3c.dom.Document document) {
        org.w3c.dom.Element documentRoot = document.getDocumentElement();
        fixIdNCNameElement(documentRoot);
        return document;
    }

    private void fixIdNCNameElement(org.w3c.dom.Element element) {
        String elementId = element.getAttribute("xml:id");
        if (elementId != null && elementId.trim().length()>0) {
            String newElementId = doiId2NCName(elementId);
            element.setAttribute("xml:id", newElementId);
        }
        String elementCorresp = element.getAttribute("corresp");
        if (elementCorresp != null && elementCorresp.trim().length()>0) {
            String newElementCorresp = doiId2NCName(elementCorresp);
            element.setAttribute("corresp", newElementCorresp);
        }
        for(org.w3c.dom.Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof org.w3c.dom.Element) {
                fixIdNCNameElement((org.w3c.dom.Element)child);
            }
        }
    }

    private String doiId2NCName(String doi) {
        if (doi.startsWith("10.")) {
            doi = doi.replace("%", "_");
            doi = "_" + doi;
        } else  if (doi.startsWith("#10.")) {
            doi = doi.replace("%", "_");
            doi = doi.replace("#10", "#_10");
        }
        return doi;
    }

    private void replaceIdNCNameElement(org.w3c.dom.Element element, String oldId, String newId) {
        String elementId = element.getAttribute("xml:id");
        if (elementId != null && elementId.trim().length()>0) {
            String localId = elementId.replace(oldId, newId);
            element.setAttribute("xml:id", localId);
        }
        String elementCorresp = element.getAttribute("corresp");
        if (elementCorresp != null && elementCorresp.trim().length()>0) {
            String localId = elementCorresp.replace(oldId, newId);
            element.setAttribute("corresp", localId);
        }
        for(org.w3c.dom.Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof org.w3c.dom.Element) {
                replaceIdNCNameElement((org.w3c.dom.Element)child, oldId, newId);
            }
        }
    }

    /**
     * Map the origin identifier (the identifier used sor the PDF file names) to a stable generated hexa identifier
     */
    private String orgin2KeyGen(String origin) {
        if (this.orgin2Key == null) {
            this.orgin2Key = new TreeMap<>();
            // load the map from the csv id file
            File idsFile = new File("resources" + File.separator + "dataset" + File.separator + "software"+ File.separator +
                "corpus" + File.separator + "ids.csv");
            try {
                BufferedReader b = new BufferedReader(new FileReader(idsFile));
                boolean start = true;
                String line;
                while ((line = b.readLine()) != null) {
                    // id,origin,DOI,PMID,PMCID
                    if (start) {
                        start = false;
                        continue;
                    }
                    String[] pieces = line.split(",");
                    if (pieces.length >= 2)
                        this.orgin2Key.put(pieces[1], pieces[0]);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return this.orgin2Key.get(origin);
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

    private void loadSoftwareUsage() {
        File softwareUsageFile = new File("resources" + File.separator + "dataset" + File.separator + 
            "software" + File.separator + "corpus" + File.separator + "software-use-corrections-curated-2020-11-08.csv");
        try (BufferedReader b = new BufferedReader(new FileReader(softwareUsageFile))) {
            int nbCSVlines = 0;
            String line;
            while ((line = b.readLine()) != null) {
                if (nbCSVlines == 0) {
                    nbCSVlines++;
                    continue;
                }
                String[] pieces = line.split(",");

                if (pieces.length != 3) {
                    System.out.println("invalid curated software usage attribute at line " + nbCSVlines + ": " + line);
                    continue;
                }

                String annotationId = pieces[0];
                String value = pieces[2];

                if ("TRUE".equals(value))
                    this.softwareUsages.put(annotationId, new Boolean(true));
                else
                    this.softwareUsages.put(annotationId, new Boolean(false));
                nbCSVlines++;
            }
            System.out.println(nbCSVlines + " curated software usage attribute loaded");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Command line execution.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) throws Exception {
       
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

        boolean extraContext = false;

        String outputXmlPathTmp = outputXmlPath.replace(".xml", ".tmp.xml");

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        SoftwareConfiguration conf = mapper.readValue(new File("resources/config/config.yml"), SoftwareConfiguration.class);

        // post processing has two steps
        // first one add information to the curated dataset
        XMLCorpusPostProcessor postProcessor = new XMLCorpusPostProcessor(conf);
        try {
            postProcessor.process(xmlPath, csvPath, outputXmlPathTmp);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // second one add complementary information for document without mentions and non matching contexts
        XMLCorpusPostProcessorNoMention postProcessorNoMention = new XMLCorpusPostProcessorNoMention(conf);
        try {
            postProcessorNoMention.process(outputXmlPathTmp, csvPath, pdfPath, outputXmlPath, extraContext);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // deleting tmp file
        File tmpFile = new File(outputXmlPathTmp);
        if (tmpFile.exists()) {
            tmpFile.delete();
        }

        System.exit(0);
    }
}

