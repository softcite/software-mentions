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
import requests
from collect_metadata import biblio_glutton_lookup

class TEICorpusHandler(xml.sax.ContentHandler):
    """ 
    TEI XML SAX handler for reading the TEI corpus file corresponding to the Softcite corpus  
    """
    output_path = None
    config = None

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
    doi = None
    pmc = None
    pmid = None
    current_id = None

    # json outout
    documents = None

    # counter
    nb_snippets = 0
    nb_file = 0

    def __init__(self, config, output_path):
        xml.sax.ContentHandler.__init__(self)
        self.output_path = output_path
        self.seg = pysbd.Segmenter(language="en", clean=False, char_span=True)
        self.config = config

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
            if self.doi is not None and len(self.doi)>0:
                self.document["doi"] = self.doi
            if self.pmc is not None and len(self.pmc)>0:
                self.document["pmc"] = self.pmc
            if self.pmid is not None and len(self.pmid)>0:
                self.document["pmid"] = self.pmid
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
        if name == "idno":
            if attrs.getLength() != 0:
                if "type" in attrs:
                    if attrs.getValue("type") == "DOI":
                        self.current_id = "doi"
                    elif attrs.getValue("type") == "PMC":
                        self.current_id = "pmc"
                    elif attrs.getValue("type") == "PMID":
                        self.current_id = "pmid"
                    else:
                        self.current_id = None
                elif "DOI" in attrs:
                    self.doi = attrs.getValue("DOI")
                    self.current_id = None
                elif "PMC" in attrs:
                    self.pmc = attrs.getValue("PMC")
                    self.current_id = None
                elif "PMID" in attrs:
                    self.pmid = attrs.getValue("PMID")
                    self.current_id = None
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
            local_doi = None
            local_pmc = None
            local_pmid = None
            if "doi" in self.document:
                new_json["doi"] = self.document["doi"]
                local_doi = self.document["doi"]
            if "pmc" in self.document:
                new_json["pmc"] = self.document["pmc"]
                local_pmc = self.document["pmc"]
            if "pmid" in self.document:
                new_json["pmid"] = self.document["pmid"]
                local_pmid = self.document["pmid"]

            # add a full text URL
            local_url = add_full_text_url(self.config, doi=local_doi, pmc=local_pmc, pmid=local_pmid)
            if local_url != None:
                new_json["full_text_url"] = local_url

            new_json["texts"] = []
            process_text_list(self.config, self.seg, self.document["texts"], new_json, True)
            if len(new_json["texts"])>0:
                self.documents["documents"].append(new_json)
            #self.documents["documents"].append(self.document)
            self.document = None
            self.doc_id = None
            self.doi = None
            self.pmc = None
            self.pmid = None
            self.current_id = None
        if name == 'teiCorpus':
            print("total exported sentences", self.nb_snippets)
            # write ouput 
            with open(self.output_path, 'w') as outfile:
                json.dump(self.documents, outfile, indent=4)  
        if name == 'idno':
            if self.current_id == "doi": 
                self.doi = self.accumulated
            elif self.current_id == "pmc":
                self.pmc = self.accumulated
            elif self.current_id == "pmid":
                self.pmid = self.accumulated

        self.currentOffset += len(self.accumulated)
        self.accumulated = ''

    def characters(self, content):
        self.accumulated += content

    def clear(self): # clear the accumulator for re-use
        self.accumulated = ""

def process_text_list(config, seg, text_list, new_json, predict):
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

                if previous_start == -1 and "entity_spans" in sentence_structure and len(sentence_structure["entity_spans"])>0:
                    if predict:
                        # we use the softcite service to predict and pre-annotate the characterization of the sentence mention
                        prediction = predict_sentence_usage(config, sentence_structure["text"])
                        sentence_structure["class_attributes"] = prediction
                        sentence_structure["full_context"] = text_part["text"]
                        #print(prediction)

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

def load_config(path='./config.json'):
    """
    Load the json configuration. Return the config dict or None if the service check fails. 
    """
    try:
        config_json = open(path).read()
        config = json.loads(config_json)
        print(config_json)
        service_isalive(config)
    except: 
        print('\n-> loading configuration failed')
        config = None
    return config

def _grobid_software_url(grobid_base, grobid_port):
    the_url = 'http://'+grobid_base
    if grobid_port is not None and len(grobid_port)>0:
        the_url += ":"+grobid_port
    the_url += "/service/"
    return the_url

def service_isalive(config):
    # test if GROBID software mention recognizer is up and running...
    the_url = _grobid_software_url(config['grobid_software_server'], config['grobid_software_port'])
    the_url += "isalive"
    try:
        r = requests.get(the_url)
        if r.status_code != 200:
            print('Grobid software mention server does not appear up and running ' + str(r.status_code))
        else:
            print("Grobid software mention server is up and running")
            return True
    except: 
        print('Grobid software mention server does not appear up and running:',
            'test call to grobid software mention failed, please check and re-start a server if you wish to use it for enrichment.')
    return False

def predict_sentence_usage(config, text):
    the_url = _grobid_software_url(config['grobid_software_server'], config['grobid_software_port'])
    the_url += "characterizeSoftwareContext"
    params = {'text': text}
    try:
        r = requests.get(the_url, params=params)
    except: 
        print('Failed request to Grobid software mention server.')
        return None
    return r.json()

def add_full_text_url(config, doi=None, pmc=None, pmid=None):
    if pmc != None:
        return "https://www.ncbi.nlm.nih.gov/pmc/articles/" + pmc + "/pdf"

    if doi != None:
        json_record = biblio_glutton_lookup(config, doi=doi)
        if json_record != None and "oaLink" in json_record:
            return json_record["oaLink"]

    if pmid != None:
        json_record = biblio_glutton_lookup(config, pmid=pmid)
        if json_record != None and "oaLink" in json_record:
            return json_record["oaLink"]

    return None

def export(config, xml_corpus, output_path):
    parser = make_parser()
    handler = TEICorpusHandler(config, output_path)
    parser.setContentHandler(handler)
    parser.parse(xml_corpus)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Generate labeled dataset for software use classification")
    parser.add_argument("--xml-corpus", type=str, 
        help="path to the TEI XML Softcite corpus file")
    parser.add_argument("--output", type=str, 
        help="path where to generate the software use classification datsset JSON file")
    parser.add_argument("--config", default='config.json', help="configuration file to be used")

    args = parser.parse_args()
    xml_corpus = args.xml_corpus
    output_path = args.output
    config_path = args.config

    # check path and call methods
    if xml_corpus is None or not os.path.isfile(xml_corpus):
        print("error: the path to the XML corpus files is not valid: ", xml_corpus)
        exit(0)

    config = load_config(path=config_path)

    export(config, xml_corpus, output_path)
    test_json_wellformedness(output_path)
