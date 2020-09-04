"""
    Generate labeled dataset for software use classification using the Softcite corpus file.
    The produced dataset in JSON format is at sentence level, with usage information (target 
    binary class) and offset corresponding to the software mention annotations.
"""

import os
import argparse
import ntpath
import json
from collections import OrderedDict
import xml
from xml.sax import make_parser, handler
import pysbd
from corpus2JSON import validate_segmentation
from nltk.tokenize.punkt import PunktSentenceTokenizer

class TEICorpusHandler(xml.sax.ContentHandler):
    """ 
    TEI XML SAX handler for reading the TEI corpus file corresponding to the Softcite corpus  
    """
    output_path = None

    # working variables
    accumulated = ''
    currentOffset = -1
    paragraph = None
    current_reference = None
    current_entity = None
    ref_spans = None
    entity_spans = None
    document = None
    doc_id = None

    # json outout
    documents = None

    # counter
    nb_snippets = 0
    nb_file = 0

    def __init__(self, output_path):
        xml.sax.ContentHandler.__init__(self)
        self.output_path = output_path
        self.seg = pysbd.Segmenter(language="en", clean=False, char_span=True)

    def startElement(self, name, attrs):
        if self.accumulated != '':
            if self.paragraph == None:
                self.paragraph = ''
            self.paragraph += self.accumulated
            self.currentOffset += len(self.accumulated)
            self.accumulated = ''
        if name == 'teiCorpus':
            self.documents = OrderedDict() 
            self.documents["documents"] = []
        if name == 'TEI' or name == 'tei':
            self.nb_file += 1
        if name == 'body':
            self.document = OrderedDict() 
            if self.doc_id is not None:
                self.document["id"] = self.doc_id
            self.document["texts"] = []
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
        if name == "rs":
            self.paragraph += self.accumulated
            # end of entity
            self.current_entity["rawForm"] = self.accumulated
            self.current_entity["end"] = self.currentOffset + len(self.accumulated)
            self.entity_spans.append(self.current_entity)
            self.current_entity = None
        if name == "ref":
            self.paragraph += self.accumulated
        if name == "p":
            if self.document is not None:
                if self.paragraph == None:
                    self.paragraph = ''
                self.paragraph += self.accumulated
                local_paragraph = OrderedDict() 
                local_paragraph['text'] = self.paragraph
                if len(self.entity_spans) > 0:
                    local_paragraph['entity_spans'] = self.entity_spans
                self.document["texts"].append(local_paragraph)
                self.nb_snippets += 1
        if name == 'body':
            new_json = OrderedDict()
            new_json["id"] = self.document["id"]
            new_json["texts"] = []
            process_text_list(self.seg, self.document["texts"], new_json)
            self.documents["documents"].append(new_json)
            #self.documents["documents"].append(self.document)
            self.document = None
            self.doc_id = None
        if name == 'teiCorpus':
            print("total exported sentences", self.nb_snippets)
            # write ouput 
            with open(self.output_path, 'w') as outfile:
                json.dump(self.documents, outfile, indent=4)  

        self.currentOffset += len(self.accumulated)
        self.accumulated = ''

    def characters(self, content):
        self.accumulated += content

    def clear(self): # clear the accumulator for re-use
        self.accumulated = ""

def process_text_list(seg, text_list, new_json):
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

                if "entity_spans" in text_part and previous_start == -1:
                    new_entity_spans = []
                    for entity_span in text_part["entity_spans"]:
                        # check if we have a segmentation in the middle of an entity span
                        if entity_span["start"] >= offset_pos and entity_span["start"] < span["end"]  and entity_span["end"] > span["end"]:
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
                    new_json["texts"].append(sentence_structure)

def test_json_wellformedness(json_path):
    with open(json_path) as jsonfile:
        json_doc = json.load(jsonfile, object_pairs_hook=OrderedDict)

        if "documents" in json_doc:
            for document in json_doc["documents"]:
                if "texts" in document:
                    for paragraph in document["texts"]:        
                        text = paragraph["text"]
                        entities = None
                        references = None
                        if "entity_spans" in paragraph:
                            entities = paragraph["entity_spans"] 

                        if entities is not None:
                            for entity in entities:
                                entity_str = entity["rawForm"]
                                entity_text = text[entity["start"]:entity["end"]]
                                if entity_str != entity_text:
                                    # report the entity string mismatch
                                    print("\n")
                                    print(text, " -> ", entity_str, "/", entity_text, "|", entity["start"], entity["end"])     

                        # also check the length of the text segment
                        if len(text)>1500:
                            print("\n")
                            print("text length beyond 1500 characters:", str(len(text)), "/", text)

def export(xml_corpus, output_path):
    parser = make_parser()
    handler = TEICorpusHandler(output_path)
    parser.setContentHandler(handler)
    parser.parse(xml_corpus)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Generate labeled dataset for software use classification")
    parser.add_argument("--xml-corpus", type=str, 
        help="path to the TEI XML Softcite corpus file")
    parser.add_argument("--output", type=str, 
        help="path where to generate the software use classification datsset JSON file")

    args = parser.parse_args()
    xml_corpus = args.xml_corpus
    output_path = args.output

    # check path and call methods
    if xml_corpus is None or not os.path.isfile(xml_corpus):
        print("error: the path to the XML corpus files is not valid: ", xml_corpus)
        exit(0)

    export(xml_corpus, output_path)
    test_json_wellformedness(output_path)
