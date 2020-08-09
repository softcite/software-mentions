"""
    Convert the rich, unambiguous, standard, generic, extendable TEI XML format of GROBID and Pub2TEI into 
    something similar to CORD-19 degraded JSON format (let's call it a working format)
"""

import os
import argparse
import ntpath
import json
import xml
from xml.sax import make_parser, handler
from collections import OrderedDict

class TEIContentHandler(xml.sax.ContentHandler):
    """ 
    TEI XML SAX handler for reading sections/paragraph with mixed content within xml text tags  
    """

    # local paragraph
    section = None
    paragraph = None
    ref_spans = None
    entity_spans = None

    # working variables
    accumulated = ''
    currentOffset = -1
    abstract = False
    current_reference = None
    current_entity = None

    # dict corresponding to the converted json document
    document = None

    def __init__(self):
        xml.sax.ContentHandler.__init__(self)

    def startElement(self, name, attrs):
        if self.accumulated != '':
            if self.paragraph == None:
                self.paragraph = ''
            self.paragraph += self.accumulated
            self.currentOffset += len(self.accumulated)
        if name == 'TEI' or name == 'tei':
            # beginning of a document, reinit all
            self.section = None
            self.paragraph = None
            self.ref_spans = None
            self.entity_spans = None
            self.current_reference = None
            self.current_entity = None
            self.document = OrderedDict() 
            self.accumulated = ''
            self.abstract = False
        if name == "abstract":
            self.abstract = True
            self.document["abstract"] = []
            self.ref_spans = []
            self.entity_spans = []
        if name == "head":
            # beginning of paragraph
            self.section = self.accumulated                
        if name == "p":
            # beginning of paragraph
            self.paragraph = ''
            self.ref_spans = []
            self.entity_spans = []
            self.currentOffset = 0
        if name == "rs":
            # beginning of entity
            self.current_entity = OrderedDict() 
            if attrs.getLength() != 0:
                if attrs.getValue("type") == 'software':
                    self.current_entity["type"] = "software"
                self.current_entity["start"] = self.currentOffset
        if name == "ref":            
            if attrs.getLength() != 0:
                if "type" in attrs:
                    self.current_reference = OrderedDict() 
                    if attrs.getValue("type") == 'bibr':
                        self.current_reference["type"] = "bibr"
                    if attrs.getValue("type") == 'table':
                        self.current_reference["type"] = "table"
                    if attrs.getValue("type") == 'figure':
                        self.current_reference["type"] = "figure"
                    if "target" in attrs:
                        if attrs.getValue("target") != None:
                            self.current_reference["ref_id"] = attrs.getValue("target").replace("#", "")
                    self.current_reference["start"] = self.currentOffset
        if name == "body":
            self.document["body_text"] = []
        self.accumulated = ''

    def endElement(self, name):
        # print("endElement '" + name + "'")
        if name == "head":
            self.section = self.accumulated  
        if name == 'div':
            self.section = None
        if name == "p":
            # end of paragraph 
            if self.paragraph == None:
                self.paragraph = ''
            self.paragraph += self.accumulated

            local_paragraph = OrderedDict() 
            if self.section is not None:
                local_paragraph['section'] = self.section
            local_paragraph['text'] = self.paragraph
            if len(self.ref_spans) > 0:
                local_paragraph['ref_spans'] = self.ref_spans
            if self.abstract:
                self.document["abstract"].append(local_paragraph)
            else:
                if "body_text" in self.document:
                    self.document["body_text"].append(local_paragraph)
            self.paragraph = None
        if name == "rs":
            self.paragraph += self.accumulated
            # end of entity
            self.current_entity["rawForm"] = self.accumulated
            self.current_entity["end"] = self.currentOffset + len(self.accumulated)
            self.entity_spans.append(self.current_entity)
            self.current_entity = None
        if name == "abstract":
            self.abstract = False
        if name == 'ref':
            if self.paragraph is None:
                self.paragraph = ""
            self.paragraph += self.accumulated
            if self.current_reference is not None:
                self.current_reference["text"] = self.accumulated
                self.current_reference["end"] = self.currentOffset + len(self.accumulated)
                self.ref_spans.append(self.current_reference)
            self.current_reference = None

        self.currentOffset += len(self.accumulated)
        self.accumulated = ''

    def characters(self, content):
        self.accumulated += content

    def getDocument(self):
        return self.document

    def clear(self): # clear the accumulator for re-use
        self.accumulated = ""

def convert_tei_string(stringXml):
    # as we have XML mixed content, we need a real XML parser...
    parser = make_parser()
    handler = TEIContentHandler()
    parser.setContentHandler(handler)
    parser.parseString(stringXml)
    document = handler.getDocument()
    return json.dumps(document, indent=4)

def convert_tei_file(tei_file, output_path=None):
    # as we have XML mixed content, we need a real XML parser...
    parser = make_parser()
    handler = TEIContentHandler()
    parser.setContentHandler(handler)
    print(tei_file)
    parser.parse(tei_file)
    document = handler.getDocument()
    if output_path is None:
        output_file = tei_file.replace(".tei.xml", ".json")
    else:
        output_file = os.path.join(output_path, ntpath.basename(tei_file).replace(".tei.xml", ".json"))
    print(output_file)
    with open(output_file, 'w') as outfile:
        json.dump(document, outfile, indent=4)

def convert_batch_tei_files(path_to_tei_files, output_path=None):
    for file in os.listdir(path_to_tei_files):
        if file.endswith(".tei.xml"):
            if output_path is None:
                convert_tei_file(os.path.join(path_to_tei_files, file), path_to_tei_files)
            else:
                convert_tei_file(os.path.join(path_to_tei_files, file), output_path)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Convert a TEI XML file into CORD-19-style JSON format")
    parser.add_argument("--tei-file", type=str, help="path to a TEI XML file to convert")
    parser.add_argument("--tei-corpus", type=str, help="path to a directory of TEI XML files to convert")
    parser.add_argument("--output", type=str, 
        help="path to an output directory where to write the converted TEI XML file, default is the same directory as the input file")

    args = parser.parse_args()
    tei_file = args.tei_file
    tei_corpus_path = args.tei_corpus
    output_path = args.output

    # check path and call methods
    if tei_file is not None and not os.path.isfile(tei_file):
        print("the path to the TEI XML file is not valid: ", tei_file)
    if tei_corpus_path is not None and not os.path.isdir(tei_corpus_path):
        print("the path to the directory of TEI files is not valid: ", xml_corpus_path)
    if tei_file is not None:
        if tei_file.endswith(".tei.xml"):
            convert_tei_file(tei_file, output_path)
        else:    
            print("TEI XML file must end with entension .tei.xml")
            exit()
    elif tei_corpus_path is not None:
        convert_batch_tei_files(tei_corpus_path, output_path=output_path)
