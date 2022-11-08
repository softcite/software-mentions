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
import pysbd
import textdistance
import re
from nltk.tokenize.punkt import PunktSentenceTokenizer

class TEICorpusHandler(xml.sax.ContentHandler):
    """ 
    TEI XML SAX handler for reading the TEI corpus file corresponding to the Softcite corpus  
    """
    json_path = None
    output_path = None

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
    doc_id = None

    def __init__(self, json_path, output_path):
        xml.sax.ContentHandler.__init__(self)
        self.output_path = output_path
        self.json_path = json_path

    def startElement(self, name, attrs):
        if self.accumulated != '':
            if self.paragraph == None:
                self.paragraph = ''
            self.paragraph += self.accumulated
            self.currentOffset += len(self.accumulated)
            self.accumulated = ''
        if name == 'idno':
            if attrs.getLength() != 0:
                if "type" in attrs:
                    if attrs.getValue("type") == 'origin':
                        # this is where we get the PDF file name
                        self.is_origin_file = True
        if name == 'TEI' or name == 'tei':
            # reinit working variable
            self.grobid_json = None
            self.document = OrderedDict() 
            self.nb_file += 1
            self.doc_id = None
            self.is_origin_file = False
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
                    else:
                        self.current_entity["subtype"] = attrs.getValue("subtype")
                if "role" in attrs:
                    if attrs.getValue("role") == "used":
                        self.current_entity["used"] = True
                if "xml:id" in attrs:
                    self.current_entity["id"] = attrs.getValue("xml:id")
                if "corresp" in attrs:
                    self.current_entity["id"] = attrs.getValue("corresp")
                if "cert" in attrs:
                    self.current_entity["cert"] = attrs.getValue("cert")
                self.current_entity["start"] = self.currentOffset
        if name == "fileDesc":
            if attrs.getLength() != 0:
                if "xml:id" in attrs:
                    self.doc_id = attrs.getValue("xml:id")

    def endElement(self, name):
        # print("endElement '" + name + "'")
        if name == 'idno':
            if self.is_origin_file:
                self.is_origin_file = False
                self.origin_file = self.accumulated.strip()
                json_file_path = os.path.join(self.json_path, self.origin_file + ".json")
                #print(json_file_path)

                if not os.path.isfile(json_file_path):
                    print("ERROR: no raw json file available for", self.origin_file + ".json")
                    self.grobid_json = OrderedDict();
                else:
                    with open(json_file_path,"r") as f:
                        grobid_json_string = f.read() 
                    self.grobid_json = json.loads(grobid_json_string, object_pairs_hook=OrderedDict)
                if self.doc_id is not None:
                    self.grobid_json["id"] = self.doc_id
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
                self.paragraph += self.accumulated
                local_paragraph = OrderedDict() 
                local_paragraph['text'] = self.paragraph
                if len(self.ref_spans) > 0:
                    local_paragraph['ref_spans'] = self.ref_spans
                if len(self.entity_spans) > 0:
                    local_paragraph['entity_spans'] = self.entity_spans
                if not "body_text" in self.document:
                    self.document["body_text"] = []
                self.document["body_text"].append(local_paragraph)
        if name == 'ref':
            if self.paragraph is None:
                self.paragraph = ""
            self.paragraph += self.accumulated
            if self.current_reference is not None:
                self.current_reference["text"] = self.accumulated
                self.current_reference["end"] = self.currentOffset + len(self.accumulated)
                self.ref_spans.append(self.current_reference)
            self.current_reference = None
        if name == "body":
            # inject new annotated paragraph
            for para in self.document["body_text"]:
                local_text = para["text"]
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
                            self.grobid_json["body_text"][i]["text"] = local_text
                            if "entity_spans" in para:
                                self.grobid_json["body_text"][i]["entity_spans"] = para["entity_spans"]
                            if "ref_spans" in para:    
                                self.grobid_json["body_text"][i]["ref_spans"] = para["ref_spans"]
                            else:
                                self.grobid_json["body_text"][i]["ref_spans"] = []
                            break
                        i += 1
                    i = 0
                    if not local_match:
                        # we perform a second pass, with more aggressive matching criteria for this target paragraph
                        for candidate_text in self.grobid_json["body_text"]:
                            # we check that all entity span texts are present
                            if "entity_spans" in para:
                                entity_match = True
                                for entity_block in para["entity_spans"]:
                                    if "rawForm" in entity_block:
                                        ind = -1
                                        try:
                                            ind = candidate_text["text"].index(entity_block["rawForm"])
                                        except:
                                            ind = -1     
                                        if ind == -1:
                                            entity_match = False
                                            break
                                if entity_match:
                                    # we add as constraint on a Ratcliff-Obershelp similarity, which is based on longest sequence 
                                    # match (so good for sub-string match)
                                    candidate_string = signature(candidate_text["text"])
                                    if textdistance.ratcliff_obershelp.similarity(local_text_simplified, candidate_string) > 0.5:
                                        self.grobid_json["body_text"][i]["text"] = local_text
                                        local_match = True
                                        #print("match2", local_text)
                                        if "entity_spans" in para:
                                            #print(para["entity_spans"])
                                            self.grobid_json["body_text"][i]["entity_spans"] = para["entity_spans"]
                                        if "ref_spans" in para:    
                                            self.grobid_json["body_text"][i]["ref_spans"] = para["ref_spans"]
                                        else:
                                            self.grobid_json["body_text"][i]["ref_spans"] = []
                                        break
                        i += 1
                    if not local_match:
                        # still no match for the corpus paragraph, we report this issue
                        print("\nno match in", self.origin_file)
                        print(local_text)
                        self.nb_unmatched_file += 1

            self.grobid_json = convert_to_sentence_segments(self.grobid_json)

            # and write it
            if self.origin_file is not None:
                if self.output_path is None:
                    output_file = self.origin_file + ".json"
                else:
                    output_file = os.path.join(self.output_path, self.origin_file + ".json")
                #print(output_file)
                with open(output_file, 'w') as outfile:
                    json.dump(self.grobid_json, outfile, indent=4)

        if name == 'teiCorpus':
            print("total unmatched", self.nb_unmatched_file, "/", self.nb_file)

        self.currentOffset += len(self.accumulated)
        self.accumulated = ''

    def characters(self, content):
        self.accumulated += content

    def clear(self): # clear the accumulator for re-use
        self.accumulated = ""

def signature(string):
    string = string.lower()
    string = re.sub('[^0-9a-z]+', '', string)
    #string = ''.join([i if ord(i) < 128 else '' for i in string])
    return string

def convert_to_sentence_segments(json):
    new_json = OrderedDict()
    if json is None:
        print("WARNING: json is empty")
        return new_json
    # the abstract is empty for softcite corpus
    if "id" in json:
        new_json["id"] = json["id"]
    new_json["level"] = "sentence"
    new_json["abstract"] = []
    new_json["body_text"] = []
    seg = pysbd.Segmenter(language="en", clean=False, char_span=True)
    # ['My name is Jonas E. Smith.', 'Please turn to p. 55.']
    if "abstract" in json:
        process_text_list(seg, json["abstract"], new_json, "abstract")
    if "body_text" in json:
        process_text_list(seg, json["body_text"], new_json, "body_text")
    return new_json

def process_text_list(seg, text_list, new_json, zone):
    for text_part in text_list:
        if "text" in text_part:
            the_sentences = seg.segment(text_part["text"])
            sentences = []
            for the_span in the_sentences:
                span = {}
                span["start"] = the_span.start
                span["end"] = the_span.end
                sentences.append(span)
            # check if result is acceptable
            valid_segmentation = validate_segmentation(sentences, text_part["text"])
            if not valid_segmentation:
                # fall back to NLTK
                sentences = []
                for start, end in PunktSentenceTokenizer().span_tokenize(text_part["text"]):
                    span = {}
                    span["start"] = start
                    span["end"] = end
                    sentences.append(span)
            offset_pos = 0
            # the following is to cancel a sentence segmentation because it is located in the middle of an existing span
            # if previous_start is -1, previous segmentation was correct
            previous_start = -1
            for span in sentences:
                if previous_start != -1:
                    span["start"] = previous_start
                    previous_start = -1

                offset_pos = span["start"]
                sentence_structure = OrderedDict()

                sentence_structure["text"] = text_part["text"][span["start"]:span["end"]]
                
                if "section" in text_part:
                    sentence_structure["section"] = text_part["section"]
                
                if "ref_spans" in text_part:
                    new_ref_spans = []
                    for ref_span in text_part["ref_spans"]:
                        # check if we have a segmentation in the middle of a ref span
                        if ref_span["start"] >= offset_pos and ref_span["start"] < span["end"] and ref_span["end"] > span["end"]:
                            """
                            print("\nwarning, segmentation in the middle of ref span: sentence at", 
                                span["start"], span["end"], "with ref at", ref_span["start"], ref_span["end"])
                            print("sentence:", text_part["text"][span["start"]:span["end"]])
                            print("ref:", text_part["text"][ref_span["start"]:ref_span["end"]])
                            print("\n")
                            """
                            # in this case, we cancel this sentence boundary
                            previous_start = span["start"]
                            break

                        if ref_span["start"] >= offset_pos and ref_span["end"] <= span["end"]:
                            new_ref_span = OrderedDict()
                            new_ref_span["start"] = ref_span["start"] - offset_pos
                            new_ref_span["end"] = ref_span["end"] - offset_pos
                            if "type" in ref_span:
                                new_ref_span["type"] = ref_span["type"]
                            if "ref_id" in ref_span:
                                new_ref_span["ref_id"] = ref_span["ref_id"]
                            if "text" in ref_span:
                                new_ref_span["text"] = ref_span["text"]
                            new_ref_spans.append(new_ref_span)
                    if len(new_ref_spans) > 0 and previous_start == -1:
                        sentence_structure["ref_spans"] = new_ref_spans

                if "entity_spans" in text_part and previous_start == -1:
                    new_entity_spans = []
                    for entity_span in text_part["entity_spans"]:
                        # check if we have a segmentation in the middle of an entity span
                        if entity_span["start"] >= offset_pos and entity_span["start"] < span["end"]  and entity_span["end"] > span["end"]:
                            """
                            print("\nwarning, segmentation in the middle of entity span: sentence at", 
                                span["start"], span["end"], "with entity at", entity_span["start"], entity_span["end"])
                            print("sentence:", text_part["text"][span["start"]:span["end"])
                            print("entity:", text_part["text"][entity_span["start"]:entity_span["end"]])
                            print("\n")
                            """
                            # in this case, we cancel this sentence boundary
                            previous_start = span["start"]
                            break

                        if entity_span["start"] >= offset_pos and entity_span["end"] <= span["end"]:
                            new_entity_span = OrderedDict()
                            new_entity_span["start"] = entity_span["start"] - offset_pos
                            new_entity_span["end"] = entity_span["end"] - offset_pos
                            if "type" in entity_span:
                                new_entity_span["type"] = entity_span["type"] 
                            if "rawForm" in entity_span:
                                new_entity_span["rawForm"] = entity_span["rawForm"]
                            if "resp" in entity_span:
                                new_entity_span["resp"] = entity_span["resp"]
                            if "used" in entity_span:
                                new_entity_span["used"] = entity_span["used"]
                            if "id" in entity_span:
                                new_entity_span["id"] = entity_span["id"]
                            if "cert" in entity_span:
                                new_entity_span["cert"] = entity_span["cert"]
                            new_entity_spans.append(new_entity_span)
                    if len(new_entity_spans) > 0 and previous_start == -1:
                        sentence_structure["entity_spans"] = new_entity_spans

                if previous_start == -1:
                    new_json[zone].append(sentence_structure)

def validate_segmentation(sentences, text):
    # if we observe a sentence of more than 1500 characters, we reject the segmentation
    for span in sentences:
        if span["end"] - span["start"] >= 1500:
            return False
        if text[span["start"]:span["end"]].startswith(". "):
            return False;
    return True

def inject_corpus_annotations(tei_corpus_path, json_path, output_path):
    parser = make_parser()
    handler = TEICorpusHandler(json_path, output_path)
    parser.setContentHandler(handler)
    parser.parse(tei_corpus_path)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Inject Softcite corpus manual annotations into fulltext in lossy JSON format")
    parser.add_argument("--tei-corpus", type=str, 
        help="path to the Softcite TEI corpus file corresponding to the curated annotated corpus to inject")
    parser.add_argument("--json-repo", type=str, 
        help="path to the directory of JSON files converted from TEI XML produced by GROBID, where to inject the Softcite corpus annotations")
    parser.add_argument("--output", type=str, 
        help="path to an output directory where to write the enriched JSON file(s)")

    args = parser.parse_args()
    tei_corpus_path = args.tei_corpus
    json_repo = args.json_repo
    output_path = args.output

    # check path and call methods
    if tei_corpus_path is None or not os.path.isfile(tei_corpus_path):
        print("the path to the TEI corpus file is not valid: ", tei_corpus_path)
    if json_repo is None or not os.path.isdir(json_repo):
        print("the path to the JSON files is not valid: ", json_repo)

    inject_corpus_annotations(tei_corpus_path, json_repo, output_path)
