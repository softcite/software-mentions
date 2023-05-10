package org.grobid.core.sax;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.ArrayList;
import java.util.List;

import org.grobid.core.data.BiblioItem;
import org.grobid.core.data.Person;

/**
 * SAX parser to parse a TEI biblStruct element into a BiblioItem
 * 
 * To be likely moved to Grobid core.
 *
 * @author Patrice Lopez
 */
public class BiblStructSaxHandler extends DefaultHandler {

    StringBuffer accumulator = new StringBuffer(); // Accumulate parsed text

    private boolean accumule = true;

    private BiblioItem biblioItem = null;

    // working variable
    private String level = null;
    private String type = null;
    private String entryType = null;
    private String unit = null;
    private String subUnit = null;
    private String subUnitValue = null;
    private Person currentPerson = null;
    private boolean firstName = true;

    /*
    <biblStruct type="journal" xml:id="pone.0278912.ref001">
        <analytic>
            <author>
                <persName>
                    <surname>Hoffmann</surname>
                    <forename type="first">MP</forename>
                </persName>
            </author>
            <author>
                <persName>
                    <surname>Frodsham</surname>
                    <forename type="first">A</forename>
                </persName>
            </author>
        </analytic>
        <monogr>
            <title level="j">Natural enemies of vegetable insect pests</title>
            <imprint>
                <date type="year">1993</date>
                <biblScope unit="page" from="63">63</biblScope>
            </imprint>
        </monogr>
    </biblStruct>

    <biblStruct type="book" xml:id="pone.0278912.ref002">
        <analytic>
            <author>
                <persName>
                    <surname>Fuxa</surname>
                    <forename type="first">JR</forename>
                </persName>
            </author>
            <author>
                <persName>
                    <surname>Tanada</surname>
                    <forename type="first">Y</forename>
                </persName>
            </author>
        </analytic>
        <monogr>
            <title level="b">Epizootiology of insect diseases</title>
            <imprint>
                <publisher>John Wiley &amp; Sons</publisher>
                <date type="year">1991</date>
                <biblScope unit="page" from="160">160</biblScope>
                <biblScope unit="page" to="163">163</biblScope>
            </imprint>
        </monogr>
    </biblStruct>
    */

    public BiblStructSaxHandler() {
    }

    public void characters(char[] buffer, int start, int length) {
        if (accumule) {
            accumulator.append(buffer, start, length);
        }
    }

    public String getText() {
        return clean(accumulator.toString().trim());
    }

    private String clean(String text) {
        text = text.replace("\n", " ");
        text = text.replace("\t", " ");
        text = text.replace(" ", " "); 
        // the last one is a special "large" space missed by the regex "\\p{Space}+" bellow
        text = text.replaceAll("\\p{Space}+", " ");
        return text;
    }

    public BiblioItem getBiblioItem() {
        return biblioItem;
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("biblStruct")) {
            biblioItem.postProcessPages();
            accumulator.setLength(0);
            entryType = null;
        } else if (qName.equals("title")) {
            String title = getText();
            if (this.entryType.equals("book") && level == null)
                biblioItem.setBookTitle(title);
            else if (entryType.equals("book") && level.equals("j"))
                biblioItem.setBookTitle(title);
            else if (level == null || level.equals("a"))
                biblioItem.setTitle(title);
            else if (level.equals("j")) 
                biblioItem.setJournal(title);
            else if (level.equals("m")) 
                biblioItem.setBookTitle(title);
            else if (level.equals("s")) 
                biblioItem.setSerieTitle(title);
            level = null;
        } else if (qName.equals("idno")) {
            String identifier = getText();
            if (identifier != null && identifier.length()>4) {
                if (type == null) {
                    biblioItem.setPubnum(identifier);
                } else if (type.equals("doi") || type.equals("DOI")) {
                    biblioItem.setDOI(identifier);
                } else if (type.equals("pmid") || type.equals("PMID")) {
                    biblioItem.setPMID(identifier);
                } else if (type.equals("pmcid") || type.equals("PMCID")) {
                    biblioItem.setPMCID(identifier);
                } else if (type.equals("issn") || type.equals("ISSN")) {
                    biblioItem.setISSN(identifier);
                } else if (type.equals("isbn") || type.equals("ISBN")) {
                    if (identifier.length() == 10)
                        biblioItem.setISBN10(identifier);
                    else if (identifier.length() == 13)
                        biblioItem.setISBN13(identifier);
                } else {
                    biblioItem.setPubnum(identifier);
                }
            }
            type = null;
        } else if (qName.equals("author")) {
            if (currentPerson != null) {
                currentPerson.normalizeName();
                biblioItem.addFullAuthor(currentPerson);
            }
            currentPerson = null;
        } else if (qName.equals("forename")) {
            if (currentPerson != null && firstName) {
                currentPerson.setFirstName(getText());
            } else if (currentPerson != null) {
                currentPerson.setMiddleName(getText());
            }
        } else if (qName.equals("surname")) {
            if (currentPerson != null) {
                currentPerson.setLastName(getText());
            }
        } else if (qName.equals("date")) {
            if (type != null && type.equals("year")) {
                biblioItem.setYear(getText());
            }  
            biblioItem.setPublicationDate(getText());
            type = null;
        } else if (qName.equals("biblScope")) {
            if (this.unit != null && this.unit.equals("page")) {
                if (this.subUnit != null && this.subUnit.equals("from")) {
                    if (this.subUnitValue != null) {
                        int intSubUnitValue = -1;
                        try {
                            intSubUnitValue = Integer.parseInt(this.subUnitValue);
                        } catch (Exception e) {
                            intSubUnitValue = -1;
                        }
                        if (intSubUnitValue != -1) {
                            biblioItem.setBeginPage(intSubUnitValue);
                            biblioItem.setPageRange(""+intSubUnitValue);
                        }
                        else 
                            biblioItem.setPageRange(this.subUnitValue);
                    } else {
                        int intSubUnitValue = -1;
                        subUnitValue = getText();
                        try {
                            intSubUnitValue = Integer.parseInt(subUnitValue);
                        } catch (Exception e) {
                            intSubUnitValue = -1;
                        }
                        if (intSubUnitValue != -1) {
                            biblioItem.setBeginPage(intSubUnitValue);
                            biblioItem.setPageRange(""+intSubUnitValue);
                        }
                        else 
                            biblioItem.setPageRange(subUnitValue);
                    }
                } else if (subUnit != null && subUnit.equals("to")) {
                    if (subUnitValue!= null) {
                        int intSubUnitValue = -1;
                        try {
                            intSubUnitValue = Integer.parseInt(subUnitValue);
                        } catch (Exception e) {
                            intSubUnitValue = -1;
                        }
                        if (intSubUnitValue != -1) {
                            biblioItem.setEndPage(intSubUnitValue);
                            if (biblioItem.getPageRange() != null)
                                biblioItem.setPageRange(biblioItem.getPageRange() + "--" + intSubUnitValue);
                            else
                                biblioItem.setPageRange(""+intSubUnitValue);
                        } else {
                            if (biblioItem.getPageRange() != null)
                                biblioItem.setPageRange(biblioItem.getPageRange() + "--" + subUnitValue);
                            else
                                biblioItem.setPageRange(subUnitValue);
                        }
                    } else {
                        int intSubUnitValue = -1;
                        subUnitValue = getText();
                        try {
                            intSubUnitValue = Integer.parseInt(subUnitValue);
                        } catch (Exception e) {
                            intSubUnitValue = -1;
                        }
                        if (intSubUnitValue != -1) {
                            biblioItem.setEndPage(intSubUnitValue);
                            if (biblioItem.getPageRange() != null)
                                biblioItem.setPageRange(biblioItem.getPageRange() + "--" + intSubUnitValue);
                            else
                                biblioItem.setPageRange(""+intSubUnitValue);
                        }
                        else {
                            if (biblioItem.getPageRange() != null)
                                biblioItem.setPageRange(biblioItem.getPageRange() + "--" + subUnitValue);
                            else
                                biblioItem.setPageRange(subUnitValue);
                        }
                    } 
                } else {
                    biblioItem.setPageRange(getText());
                }
            } else if (this.unit != null && (this.unit.equals("vol") || this.unit.equals("volume"))) {
                biblioItem.setVolume(getText());
            } else if (this.unit != null && (this.unit.equals("issue") || this.unit.equals("issue"))) {
                biblioItem.setIssue(getText());
            } 
            unit = null;
            subUnit = null;
            subUnitValue = null;
        } else if (qName.equals("publisher")) {
            biblioItem.setPublisher(getText());
        }
        accumulator.setLength(0);
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        if (qName.equals("biblStruct")) {
            biblioItem = new BiblioItem();
            int length = atts.getLength();
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);
                if ((name != null) && (value != null)) {
                    if (name.equals("type")) {
                        this.entryType = value;
                    }
                }
            }
        } else if (qName.equals("title")) {
            int length = atts.getLength();
            // Process attributes
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);
                if ((name != null) && (value != null)) {
                    if (name.equals("level")) {
                        this.level = value;
                    }
                }
            }
        } else if (qName.equals("idno")) {
            int length = atts.getLength();
            // Process attributes
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);
                if ((name != null) && (value != null)) {
                    if (name.equals("type")) {
                        this.type = value;
                    }
                }
            }
        } else if (qName.equals("author")) {
            currentPerson = new Person();
            firstName = true;
        } else if (qName.equals("forename")) {
            int length = atts.getLength();
            // Process attributes
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);
                if ((name != null) && (value != null)) {
                    if (name.equals("type")) {
                        if (value.equals("middle")) {
                            firstName = false;
                        } else {
                            firstName = true;
                        }
                    }
                }
            }
        } else if (qName.equals("biblScope")) {
            int length = atts.getLength();
            // Process attributes
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);
                if ((name != null) && (value != null)) {
                    if (name.equals("unit")) {
                        this.unit = value;
                    } else if (name.equals("from")) {
                        this.subUnit = "from";
                        this.subUnitValue = value;
                    } else if (name.equals("to")) {
                        this.subUnit = "to";
                        this.subUnitValue = value;
                    } 
                }
            }
        } else if (qName.equals("date")) {
            int length = atts.getLength();
            // Process attributes
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);
                if ((name != null) && (value != null)) {
                    if (name.equals("type")) {
                        this.type = value;
                    }
                }
            }
        } 
        accumulator.setLength(0);
    }

}