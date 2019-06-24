package org.grobid.core.sax;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.*;
import java.io.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.io.JsonStringEncoder;

/**
 * Simple SAX Parser for converting TEICorpus format into an array of JSON snippets. 
 * 
 * @author Patrice Lopez
 */
public class TEICorpusSaxHandler extends DefaultHandler {

    StringBuffer accumulator = new StringBuffer(); // Accumulate parsed text

    public Writer jsonWriter = null;

    private JsonStringEncoder encoder = null;

    // fields
    private String identifier = null;
    private String title = null;
    private String text = null;

    private boolean first = true;

    public TEICorpusSaxHandler() {
        encoder = JsonStringEncoder.getInstance();
    }

    public void characters(char[] buffer, int start, int length) {
        accumulator.append(buffer, start, length);
    }

    public void setWriter(Writer jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    public String getText() {
        String text = accumulator.toString().trim();
        text = text.replace("\n", " ");
        text = text.replace("\t", " ");
        text = text.replace(" ", " "); 
        // the last one is a special "large" space missed by the regex "\\p{Space}+" bellow
        text = text.replaceAll("\\p{Space}+", " ");
        return text;
    }

    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (qName.equals("TEI") || qName.equals("tei")) {
            writeEntry();
            identifier = null;
            title = null;
            text = null;
            accumulator.setLength(0);
        } else if (qName.equals("title")) {
            title = getText();
            accumulator.setLength(0);
        } else if (qName.equals("p")) {
            text = getText();
            accumulator.setLength(0);
        }
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        if (qName.equals("fileDesc")) {
            int length = atts.getLength();
            // Process attributes
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) && (value != null)) {
                    if (name.equals("xml:id")) {
                        identifier = value;
                    }
                }
            }
            accumulator.setLength(0);
        } else if (qName.equals("title")) {
            accumulator.setLength(0);
        } else if (qName.equals("p")) {
            accumulator.setLength(0);
        }
    }

    private void writeEntry() {
        StringBuilder builder = new StringBuilder();

        byte[] encoded = encoder.quoteAsUTF8(identifier);
        String output = new String(encoded);
        if (!first) 
            builder.append(",\n");
        else
            first = false;
        builder.append("\t{\n");
        builder.append("\t\t\"identifier\": \"" + output + "\",\n");

        encoded = encoder.quoteAsUTF8(title);
        output = new String(encoded);
        builder.append("\t\t\"title\": \"" + output + "\",\n");

        encoded = encoder.quoteAsUTF8(text);
        output = new String(encoded);
        builder.append("\t\t\"text\": \"" + output + "\"\n");
        builder.append("\t}");

        ObjectMapper mapper = new ObjectMapper();
        try {
            Object json = mapper.readValue(builder.toString(), Object.class);
            jsonWriter.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
