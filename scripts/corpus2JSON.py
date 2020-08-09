"""
    Inject software mention annotations from the Softcite Corpus in TEI corpus format into into the JSON format corresponding to
    something similar to the CORD-19 degraded JSON format.
"""

import os
import argparse
import ntpath
import json
import xml
from xml.sax import make_parser, handler
from collections import OrderedDict
from TEI2LossyJSON import TEIContentHandler, convert_tei_string

class TEICorpusHandler(xml.sax.ContentHandler):
    """ 
    TEI XML SAX handler for reading the TEI corpus file corresponding to the Softcite corpus  
    """
    tei_path = None 
    json_path = None
    output_path = None

    # working variable 
    # working variables
    accumulated = ''
    currentOffset = -1
    is_origin_file = False
    origin_file = None
    grobid_json = None
    paragraph = None
    current_reference = None
    current_entity = None
    ref_spans = None
    entity_spans = None

    document = None
    nb_file = 0
    nb_unmatched_file = 0

    def __init__(self, tei_path, json_path, output_path):
        xml.sax.ContentHandler.__init__(self)
        self.tei_path = tei_path
        self.output_path = output_path
        self.json_path = json_path

    def startElement(self, name, attrs):
        if self.accumulated != '':
            if self.paragraph == None:
                self.paragraph = ''
            self.paragraph += self.accumulated.strip()
            self.currentOffset += len(self.accumulated.strip())
            self.accumulated = ''
        if name == 'idno':
            if attrs.getLength() != 0:
                if "type" in attrs:
                    if attrs.getValue("type") == 'origin':
                        # this is where we get the PDF file name
                        self.is_origin_file = True
        if name == 'TEI' or name == 'tei':
            # reinit working variable
            self.is_origin_file = False
            self.grobid_json = None
            self.document = OrderedDict() 
            self.nb_file += 1
        if name == "body":
            self.document["body_text"] = []
            self.accumulated = ''
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
        if name == "p": 
            self.paragraph = ''
            self.ref_spans = []
            self.entity_spans = []            
            self.currentOffset = 0
        if name == "rs":
            # beginning of entity
            self.current_entity = OrderedDict() 
            if attrs.getLength() != 0:
                if "type" in attrs:
                    self.current_entity["type"] = attrs.getValue("type")
                if "resp" in attrs:
                    self.current_entity["resp"] = attrs.getValue("resp")
                if "subtype" in attrs:
                    if attrs.getValue("subtype") == "used":
                        self.current_entity["used"] = True
                if "xml:id" in attrs:
                    self.current_entity["id"] = attrs.getValue("xml:id")
                if "xml:id" in attrs:
                    self.current_entity["id"] = attrs.getValue("xml:id")
                if "corresp" in attrs:
                    self.current_entity["id"] = attrs.getValue("corresp")
                self.current_entity["start"] = self.currentOffset

    def endElement(self, name):
        # print("endElement '" + name + "'")
        if name == 'idno':
            if self.is_origin_file:
                self.origin_file = self.accumulated.strip()
                json_file_path = os.path.join(self.json_path, self.origin_file + ".json")
                print(json_file_path)

                if not os.path.isfile(json_file_path):
                    tei_file_path = os.path.join(self,tei_path, self.accumulated.strip()+".tei.xml")
                    self.is_origin_file = False
                    # load the corresponding TEI file produces by Grobid
                    tei_string = ''
                    with open(tei_file_path,"r") as f:
                        tei_string = f.read()
                    # convert the file into lossy JSON
                    grobid_json_string = convert_tei_string(tei_string)
                else:
                    with open(json_file_path,"r") as f:
                        grobid_json_string = f.read() 
                self.grobid_json = json.loads(grobid_json_string, object_pairs_hook=OrderedDict)
        if name == "rs":
            self.paragraph += self.accumulated.strip()
            # end of entity
            self.current_entity["rawForm"] = self.accumulated.strip()
            self.current_entity["end"] = self.currentOffset + len(self.accumulated.strip())
            self.entity_spans.append(self.current_entity)
            self.current_entity = None
        if name == "p":
            if self.document is not None:
                if self.paragraph == None:
                    self.paragraph = ''
                self.paragraph += self.accumulated.strip()
                local_paragraph = OrderedDict() 
                local_paragraph['text'] = self.paragraph
                if len(self.ref_spans) > 0:
                    local_paragraph['ref_spans'] = self.ref_spans
                if len(self.ref_spans) > 0:
                    local_paragraph['entity_spans'] = self.entity_spans
                if not "body_text" in self.document:
                    self.document["body_text"] = []
                self.document["body_text"].append(local_paragraph)
        if name == 'ref':
            self.paragraph += self.accumulated.strip()
            if self.current_reference is not None:
                self.current_reference["text"] = self.accumulated.strip()
                self.current_reference["end"] = self.currentOffset + len(self.accumulated.strip())
                self.ref_spans.append(self.current_reference)
            self.current_reference = None
        if name == "body":
            # inject new annotated paragraph
            for para in self.document["body_text"]:
                local_text = para["text"]
                local_text_simplified = local_text.replace(" ", "")
                local_text_simplified = signature(local_text)
                #print(local_text)
                if not self.grobid_json is None and "body_text" in self.grobid_json:
                    local_match = False
                    i = 0
                    for candidate_text in self.grobid_json["body_text"]:
                        #print(candidate_text["text"])
                        ind = -1
                        candidate_string = signature(candidate_text["text"])
                        if local_text_simplified == candidate_string:
                            ind = 0
                        if ind == -1:
                            try:
                                ind = local_text_simplified.index(candidate_string)
                            except:
                                ind = -1
                        if ind == -1:
                            try:
                                ind = candidate_string.index(local_text_simplified)
                            except:
                                ind = -1 
                        if ind != -1:
                            #print("match", local_text)
                            local_match = True
                            candidate_text["text"] = local_text
                            if "entity_spans" in para:
                                #candidate_text["entity_spans"] = para["entity_spans"]
                                #print(para["entity_spans"])
                                self.grobid_json["body_text"][i]["entity_spans"] = para["entity_spans"]
                            if "ref_spans" in para:    
                                #candidate_text["ref_spans"] = para["ref_spans"]
                                self.grobid_json["body_text"][i]["ref_spans"] = para["ref_spans"]
                            break
                        i += 1
                    if not local_match:
                        print("no match:", self.origin_file)
                        print(local_text)
                        self.nb_unmatched_file += 1

            # and write it
            if self.output_path is None:
                output_file = self.origin_file + ".json"
            else:
                output_file = os.path.join(self.output_path, self.origin_file + ".json")
            print(output_file)
            with open(output_file, 'w') as outfile:
                json.dump(self.grobid_json, outfile, indent=4)

        if name == 'teiCorpus':
            print("total unmatched", self.nb_unmatched_file, "/", self.nb_file)

        self.currentOffset += len(self.accumulated.strip())
        self.accumulated = ''

    def characters(self, content):
        self.accumulated += content

    def getDocument(self):
        return self.grobid_json

    def clear(self): # clear the accumulator for re-use
        self.accumulated = ""


def signature(string):
    string = string.replace("-", "")
    #string = ''.join([i if ord(i) < 128 else '' for i in string])
    return string.replace(" ", "")

def convert_corpus(tei_corpus_path, tei_path, json_path, output_path):
    parser = make_parser()
    handler = TEICorpusHandler(tei_path, json_path, output_path)
    parser.setContentHandler(handler)
    parser.parse(tei_corpus_path)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Convert a TEI XML file with Software mention annotations into CORD-19-style JSON format")
    parser.add_argument("--tei-corpus", type=str, 
        help="path to the TEI corpus file corresponding to the Softcite annotated corpus to convert")
    parser.add_argument("--tei-repo", type=str, 
        help="path to the directory of TEI XML file produced by GROBID and used in the Softcite corpus")
    parser.add_argument("--json-repo", type=str, 
        help="path to the directory of JSON files converted from TEI XML produced by GROBID and used in the Softcite corpus")
    parser.add_argument("--output", type=str, 
        help="path to an output directory where to write the converted JSON file(s), default is the same directory as the input file")

    args = parser.parse_args()
    tei_corpus_path = args.tei_corpus
    tei_repo = args.tei_repo
    json_repo = args.json_repo
    output_path = args.output

    # check path and call methods
    if tei_corpus_path is not None and not os.path.isdir(tei_corpus_path):
        print("the path to the TEI corpus file is not valid: ", tei_corpus_path)
    if tei_repo is not None and not os.path.isdir(tei_repo):
        print("the path to the TEI XML files is not valid: ", tei_repo)
    if json_repo is not None and not os.path.isdir(json_repo):
        print("the path to the JSON files is not valid: ", json_repo)

    convert_corpus(tei_corpus_path, tei_repo, json_repo, output_path)
