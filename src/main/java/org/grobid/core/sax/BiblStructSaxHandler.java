package org.grobid.core.sax;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.ArrayList;
import java.util.List;

import org.grobid.core.data.BiblioItem;

/**
 * SAX parser to parse a TEI biblStruct element into a BiblioItem
 * 
 * @author Patrice Lopez
 */
public class BiblStructSaxHandler extends DefaultHandler {

    StringBuffer accumulator = new StringBuffer(); // Accumulate parsed text

    private boolean accumule = true;

    public BiblioItem biblioItem = null;

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
            accumulator.setLength(0);

        } else if (qName.equals("title")) {
            String title = getText();
            accumulator.setLength(0);
        } 


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
                    if (name.equals("")) {
                    }
                }
            }
            accumulator.setLength(0);
        } else if (qName.equals("title")) {
            int length = atts.getLength();
            // Process attributes
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) && (value != null)) {
                    if (name.equals("level")) {
                        
                    }
                }
            }
            accumulator.setLength(0);
        } 

    }

}