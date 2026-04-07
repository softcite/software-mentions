package org.grobid.trainer;

import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.Pair;
import org.grobid.core.utilities.SentenceUtilities;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * SAX handler for corpus-level unique TEI-style annotation file. 
 * Software entities are inline annotations.
 * The training data for the CRF models are generated during the XML parsing.
 *
 * @author Patrice
 */
public class SoftwareAnnotationSaxHandler extends DefaultHandler {

    StringBuffer accumulator = new StringBuffer(); // Accumulate parsed text

    private boolean ignore = true;

    private String currentTag = null;

    // labeling for current paragraph
    private List<Pair<String, String>> labeled = null; // store line by line the labeled data of the current paragraph

    // labeling for current document
    private List<List<Pair<String, String>>> labeledDoc = null; // accumulated paragraphs/sentences
    private List<Boolean> labeledSoftwareMarkers = null; // mark if accumulated paragraphs/sentences has at least a software label
    
    // global labeling, one list for the documents, one list for the paragraphs, one list for the words of the paragraph
    private List<List<List<Pair<String, String>>>> allLabeled = null; // accumulated documents
    private List<List<Boolean>> allLabeledSoftwareMarkers = null; // mark if accumulated document has at least a software label

    private boolean hasSoftware = false;

    // if type mode, we generate labels relative to the software typing
    private boolean typeMode = false;

    // by default we process input at paragraph level, if sentence level is true, we use sentences
    private boolean sentenceLevel = false;

    public SoftwareAnnotationSaxHandler() {
    }

    public void setTypeMode(boolean mode) {
        this.typeMode = mode;
    }

    public void setSentenceLevel(boolean sentenceLevel) {
        this.sentenceLevel = sentenceLevel;
    }

    public void characters(char[] buffer, int start, int length) {
        if (!ignore)
            accumulator.append(buffer, start, length);
    }

    public String getText() {
        if (accumulator != null) {
            return accumulator.toString().trim();
        } else {
            return null;
        }
    }

    public List<List<List<Pair<String, String>>>> getAllLabeledResult() {
        return allLabeled;
    }

    public List<List<Boolean>> getAllLabeledSoftwareFlags() {
        return allLabeledSoftwareMarkers;
    }

    public void endElement(java.lang.String uri,
                           java.lang.String localName,
                           java.lang.String qName) throws SAXException {
        try {
            if (qName.equals("rs")) {
               	writeData(qName);
                currentTag = "<other>";
			} else if (qName.equals("s") && sentenceLevel) {
                // in sentence level mode, we have one sequence per sentence
                writeData(qName);
                if (labeledDoc != null) {
                    labeledDoc.add(labeled);
                    labeledSoftwareMarkers.add(hasSoftware);
                }
            } else if ( (qName.equals("p") || qName.equals("paragraph")) && !sentenceLevel) {
                // let's consider a new sequence per paragraph when we are not working at sentence level
                writeData(qName);
                if (labeledDoc != null) {
                    labeledDoc.add(labeled);
                    labeledSoftwareMarkers.add(hasSoftware);
                }
            } else if (qName.equals("TEI")) {
                allLabeled.add(labeledDoc);
                allLabeledSoftwareMarkers.add(labeledSoftwareMarkers);
            }
        } catch (Exception e) {
            throw new GrobidException("An exception occurred while running Grobid.", e);
        }
    }

    public void startElement(String namespaceURI,
                             String localName,
                             String qName,
                             Attributes atts) throws SAXException {
        try {
            if (qName.equals("body")) {
                ignore = false;
            } else {
                // we have to write first what has been accumulated yet with the upper-level tag
                String text = getText();
                if (text != null) {
                    if (text.length() > 0) {
                        currentTag = "<other>";
                        writeData(qName);
                    }
                }
                accumulator.setLength(0);

                // we output the remaining text
                if (qName.equals("rs") && !ignore) {

                    int length = atts.getLength();

                    // Process each attribute
                    for (int i = 0; i < length; i++) {
                        // Get names and values for each attribute
                        String name = atts.getQName(i);
                        String value = atts.getValue(i);

                        if ((name != null) && (value != null)) {
                            if (name.equals("type")) {
                                if (!typeMode) {
                                    if (value.equals("software")) {
                                        currentTag = "<software>";
                                        hasSoftware = true;
    								} else if (value.equals("url")) {
                                        currentTag = "<url>";
                                    } else if (value.equals("creator") || value.equals("publisher")) {
                                        currentTag = "<creator>";
                                    } else if (value.equals("version")) {
                                        currentTag = "<version>";
                                    } else if (!value.equals("language")) {
                                   	 	System.out.println("Warning: unknown entity attribute name, " + value);
                                	}
                                } else if (value.equals("language")) {
                                    currentTag = "<language>";
                                } else if (value.equals("software")) {
                                    hasSoftware = true;
                                } 
							} else if (name.equals("subtype") && typeMode) {
                                if (value.equals("environment")) {
                                    currentTag = "<environment>";
                                } else if (value.equals("component")) {
                                    currentTag = "<component>";
                                } else if (value.equals("implicit")) {
                                    currentTag = "<implicit>";
                                } else {
                                    if (!value.equals("person") && !value.equals("url")) {
                                        System.out.println("Warning: unknown entity attribute name, " + value);
                                    }
                                }
                            }
                        }
                    }
                } else if (qName.equals("teiCorpus")) {
                    allLabeled = new ArrayList<>();
                    allLabeledSoftwareMarkers = new ArrayList<>();
                    hasSoftware = false;
                } else if ((qName.equals("p") || qName.equals("paragraph")) && !sentenceLevel) {
                    labeled = new ArrayList<>();
                    hasSoftware = false;
                } else if (qName.equals("s") && sentenceLevel) {
                    labeled = new ArrayList<>();
                    hasSoftware = false;
                } else if (qName.equals("tei") || qName.equals("TEI")) {
                    labeledDoc = new ArrayList<>();
                    labeledSoftwareMarkers = new ArrayList<>();
                    accumulator = new StringBuffer();
                    currentTag = null;
                    ignore = true;
                    hasSoftware = false;
                } 
            }
        } catch (Exception e) {
//		    e.printStackTrace();
            throw new GrobidException("An exception occurred while running Grobid.", e);
        }
    }

    private void writeData(String qName) {
//if (currentTag != null && !currentTag.equals("<other>"))
//System.out.println(currentTag + " -> " + getText());
        if (currentTag == null)
            currentTag = "<other>";
        if ((qName.equals("other")) ||
                (qName.equals("rs")) ||
                (qName.equals("paragraph")) || 
                (qName.equals("p")) ||
                (qName.equals("s")) ||
                (qName.equals("ref")) ||
                (qName.equals("div"))) {
            String text = getText();
            // we segment the text
            List<String> tokenizations = SoftwareAnalyzer.getInstance().tokenize(text);
            boolean begin = true;
            for (String tok : tokenizations) {
                //tok = tok.trim();
                if (tok.length() == 0)
                    continue;
                
                String content = tok;
                int i = 0;
                if (content.length() > 0) {
                    if (begin && (!currentTag.equals("<other>")) ) {
                        labeled.add(new Pair(content, "I-" + currentTag));
                        begin = false;
                    } else {
                        labeled.add(new Pair(content, currentTag));
                    }
                }
                
                begin = false;
            }
            accumulator.setLength(0);
        }
    }

}
