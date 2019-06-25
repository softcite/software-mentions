package org.grobid.core.sax;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.*;
import java.io.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.io.JsonStringEncoder;

import org.grobid.trainer.Annotation;
import org.grobid.core.utilities.OffsetPosition;

/**
 * Simple SAX Parser for converting TEICorpus format into an array of JSON snippets. 
 * 
 * @author Patrice Lopez
 */
public class TEICorpusSaxHandler extends DefaultHandler {

    StringBuffer accumulator = new StringBuffer(); // Accumulate parsed text
    StringBuffer localAccumulator = new StringBuffer(); // custom accumulation of parsed text

    public Writer jsonWriter = null;

    private JsonStringEncoder encoder = null;

    // fields
    private String identifier = null;
    private String title = null;
    private List<String> texts = null;

    // annotations
    private Annotation currentAnnotation = null;
    private List<Annotation> currentAnnotations = null; // one list of annotations for the current paragraph
    private List<List<Annotation>> annotations = null; // one list of annotations per paragraph
    private int currentOffset = 0;

    private boolean first = true;

    private List<String> validAnnotationTypes = Arrays.asList("software", "version-number", "version-date", "creator", "url");

    public TEICorpusSaxHandler() {
        encoder = JsonStringEncoder.getInstance();
    }

    public void characters(char[] buffer, int start, int length) {
        accumulator.append(buffer, start, length);
        localAccumulator.setLength(0);
        localAccumulator.append(buffer, start, length);
    }

    public void setWriter(Writer jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    public String getText() {
        return clean(accumulator.toString().trim());
    }

    public String getLocalText() {
        return clean(localAccumulator.toString().trim());
    }

    private String clean(String text) {
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
            texts = null;
            annotations = null;
            accumulator.setLength(0);
        } else if (qName.equals("title")) {
            title = getText();
            accumulator.setLength(0);
        } else if (qName.equals("p")) {
            String text = getText();
            if (texts == null)
                texts = new ArrayList<String>();
            texts.add(text);
            if (annotations == null)
                annotations = new ArrayList<List<Annotation>>();
            annotations.add(currentAnnotations);
            accumulator.setLength(0);
        } else if (qName.equals("rs")) {
            if (currentAnnotation != null) {
                currentAnnotation.setText(getLocalText());
                OffsetPosition occurence = new OffsetPosition();
                occurence.start = currentOffset;
                occurence.end = currentOffset+getLocalText().length()-1;
                currentAnnotation.setOccurence(occurence);
                if (currentAnnotations == null)
                    currentAnnotations = new ArrayList<Annotation>();
                currentAnnotations.add(currentAnnotation);
            }
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
            currentAnnotations = new ArrayList<Annotation>();
            accumulator.setLength(0);
        } else if (qName.equals("rs")) {
            currentAnnotation = new Annotation();
            currentOffset = getText().length();
            int length = atts.getLength();
            // Process attributes
            for (int i = 0; i < length; i++) {
                // Get names and values for each attribute
                String name = atts.getQName(i);
                String value = atts.getValue(i);

                if ((name != null) && (value != null)) {
                    if (name.equals("type")) {
                        if (validAnnotationTypes.contains(value)) {
                            currentAnnotation.addAttributeValue(name, value);
                        } 
                    } else if (name.equals("id")) {
                        currentAnnotation.addAttributeValue(name, value);
                    } else if (name.equals("corresp")) {
                        currentAnnotation.addAttributeValue(name, value);
                    }
                }
            }
        }
    }

    private void writeEntry() {
        if (texts == null || identifier == null || title == null)
            return;

        int index = 0;
        for(String text : texts) {
            StringBuilder builder = new StringBuilder();

            byte[] encoded = encoder.quoteAsUTF8(identifier+"-"+index);
            String output = new String(encoded);
            
            builder.append("\t{\n");
            builder.append("\t\t\"identifier\": \"" + output + "\",\n");

            encoded = encoder.quoteAsUTF8(title);
            output = new String(encoded);
            builder.append("\t\t\"title\": \"" + output + "\",\n");

            encoded = encoder.quoteAsUTF8(text);
            output = new String(encoded);
            builder.append("\t\t\"text\": \"" + output + "\"");

            if (annotations.size() >  index) {
                if ( (annotations.get(index) != null) && (annotations.get(index).size() > 0) ) {
                    // we have the annotations for the current paragraph
                    builder.append(",\n\t\t\"annotations\": [\n");
                    boolean localFirst = true;
                    for(Annotation annot : annotations.get(index)) {
                        if (!localFirst)
                            builder.append(",\n");
                        else {
                            localFirst = false;
                        }
                        builder.append("\t\t\t{\n");
                        encoded = encoder.quoteAsUTF8(annot.getAttributeValue("type"));
                        output = new String(encoded);
                        builder.append("\t\t\t\t\"labelName\": \"" + output + "\",\n");
                        encoded = encoder.quoteAsUTF8(annot.getText());
                        output = new String(encoded);
                        builder.append("\t\t\t\t\"text\": \"" + output + "\",\n");

                        if (annot.getAttributeValue("id") != null) {
                            encoded = encoder.quoteAsUTF8(annot.getAttributeValue("id"));
                            output = new String(encoded);
                            builder.append("\t\t\t\t\"group\": \"" + output + "\",\n");
                        }

                        if (annot.getAttributeValue("corresp") != null) {
                            encoded = encoder.quoteAsUTF8(annot.getAttributeValue("corresp"));
                            output = new String(encoded);
                            builder.append("\t\t\t\t\"group\": \"" + output + "\",\n");
                        }

                        OffsetPosition occurence = annot.getOccurence();
                        builder.append("\t\t\t\t\"start\":"  + occurence.start + ",\n");
                        builder.append("\t\t\t\t\"end\":"  + occurence.end + "\n");
                        builder.append("\t\t\t}");
                    }
                    builder.append("\n\t\t]");
                }
            }
            builder.append("\n\t}");

            ObjectMapper mapper = new ObjectMapper();
            try {
//System.out.println(builder.toString());
                Object json = mapper.readValue(builder.toString(), Object.class);
                if (!first) 
                    jsonWriter.write(",\n");
                else
                    first = false;
                jsonWriter.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
            } catch(Exception e) {
                e.printStackTrace();
            }
            index++;
        }
    }

}
