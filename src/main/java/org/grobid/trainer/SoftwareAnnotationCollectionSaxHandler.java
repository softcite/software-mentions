package org.grobid.trainer;

import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.Pair;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * SAX handler for document-level TEI-style annotations. 
 * Software entities are inline annotations.
 * The training data for the CRF models are generated during the XML parsing.
 *
 * @author Patrice
 */
public class SoftwareAnnotationCollectionSaxHandler extends DefaultHandler {

    StringBuffer accumulator = new StringBuffer(); // Accumulate parsed text

    private boolean ignore = true;

    private String currentTag = null;

    private List<Pair<String, String>> labeled = null; // store line by line the labeled data

    public SoftwareAnnotationCollectionSaxHandler() {
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

    public List<Pair<String, String>> getLabeledResult() {
        return labeled;
    }

    public void endElement(java.lang.String uri,
                           java.lang.String localName,
                           java.lang.String qName) throws SAXException {
        try {
            if ((!qName.equals("lb")) && (!qName.equals("pb"))) {
                /*if (!qName.equals("num")) && (!qName.equals("measure"))
                    currentTag = "<other>";*/
                writeData(qName);
                currentTag = null;
            }
            if (qName.equals("rs")) {
               	writeData(qName);
			} else if (qName.equals("p") || qName.equals("paragraph")) {
                // let's consider a new CRF input per paragraph too
                labeled.add(new Pair("\n", null));
            }
        } catch (Exception e) {
//		    e.printStackTrace();
            throw new GrobidException("An exception occurred while running Grobid.", e);
        }
    }

    public void startElement(String namespaceURI,
                             String localName,
                             String qName,
                             Attributes atts) throws SAXException {
        try {
            if (qName.equals("text")) {
                ignore = false;
            } else if (qName.equals("lb")) {
                accumulator.append(" +L+ ");
            } else if (qName.equals("pb")) {
                accumulator.append(" +PAGE+ ");
            } else if (qName.equals("space")) {
                accumulator.append(" ");
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
                                if (value.equals("software")) {
                                    currentTag = "<software>";
								} /*else if (value.equals("version-number")) {
                                    currentTag = "<version-number>";
                                } else if (value.equals("version-date")) {
                                    currentTag = "<version-date>";
                                }*/ else if (value.equals("url")) {
                                    currentTag = "<url>";
                                } else if (value.equals("creator")) {
                                    currentTag = "<creator>";
                                } else if (value.equals("version")) {
                                    currentTag = "<version>";
                                }else {
                               	 	System.out.println("Warning: unknown entity attribute name, " + value);
                            	}
							}
                        }
                    }
                } else if (qName.equals("TEI") || qName.equals("tei") || qName.equals("teiCorpus") ) {
                    labeled = new ArrayList<>();
                    accumulator = new StringBuffer();
                    currentTag = null;
                    ignore = true;
                }
            }
        } catch (Exception e) {
//		    e.printStackTrace();
            throw new GrobidException("An exception occurred while running Grobid.", e);
        }
    }

    private void writeData(String qName) {
        if (currentTag == null)
            currentTag = "<other>";
        if ((qName.equals("other")) ||
                (qName.equals("rs")) ||
                (qName.equals("paragraph")) || (qName.equals("p")) ||
                (qName.equals("div"))
                ) {
            if (currentTag == null) {
                return;
            }

            String text = getText();
            // we segment the text
            List<String> tokenizations = SoftwareAnalyzer.getInstance().tokenize(text);
            boolean begin = true;
            for (String tok : tokenizations) {
                tok = tok.trim();
                if (tok.length() == 0)
                    continue;

                if (tok.equals("+L+")) {
                    labeled.add(new Pair("@newline", null));
                } else if (tok.equals("+PAGE+")) {
                    // page break should be a distinct feature
                    labeled.add(new Pair("@newpage", null));
                } else {
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
                }
                begin = false;
            }
            accumulator.setLength(0);
        }
    }

}
