"""
    Generate labeled dataset for software mention context classification using the SoMeSci corpus files.
    The produced dataset in JSON format is at sentence level, with usage information (multiclass) and 
    offset corresponding to the software mention annotations.
"""

import os
import argparse
import ntpath
import json
from collections import OrderedDict
import xml
from xml.sax import make_parser, handler
import pysbd
from pathlib import Path
import re
from corpus2JSON import validate_segmentation
from nltk.tokenize.punkt import PunktSentenceTokenizer

mention_types = ["Application_Usage", "Application_Mention", "Application_Creation", "Application_Deposition"]
seg = pysbd.Segmenter(language="en", clean=False, char_span=True)

def export_file(text_file, ann_file, documents):
    # get PMC number from file name
    filename = text_file
    doc_number = Path(text_file).stem
    document = OrderedDict()
    document["pmcid"] = doc_number
    local_text = Path(text_file).read_text()

    # to keep a sentence, we need at least one software mention - whatever its context class is             
    local_annotations = Path(ann_file).read_text()
    annotation_lines = local_annotations.split("\n")

    annotations = []
    for line in annotation_lines:
        #line = line.replace(" ", "\t")
        #line = re.sub("(\t)+", "\t", line)
        annotation_chunks = line.split("\t")
        if len(annotation_chunks) != 3:
            continue
        sub_chunks = annotation_chunks[1].split(" ")
        if len(sub_chunks) != 3:
            continue

        annotation_chunks = [ annotation_chunks[0], sub_chunks[0], sub_chunks[1], sub_chunks[2], annotation_chunks[2]]    

        annotation = {}
        if annotation_chunks[0].startswith("R"):
            # this is a relation
            continue
        print(annotation_chunks)

        annotation["id"] = annotation_chunks[0]
        annotation["type"] = annotation_chunks[1]
        annotation["start"] = int(annotation_chunks[2])
        annotation["end"] = int(annotation_chunks[3])
        annotation["chunk"] = annotation_chunks[4]
        annotations.append(annotation)

    the_sentences = seg.segment(local_text)
    sentences = []
    for the_span in the_sentences:
        span = {}
        span["start"] = the_span.start
        span["end"] = the_span.end
        sentences.append(span)
    # check if result is acceptable
    valid_segmentation = validate_segmentation(sentences, local_text)
    if not valid_segmentation:
        # fall back to NLTK
        sentences = []
        for start, end in PunktSentenceTokenizer().span_tokenize(local_text):
            span = {}
            span["start"] = start
            span["end"] = end
            sentences.append(span)

    texts = []
    for sentence in sentences:
        start = sentence["start"]
        end = sentence["end"]
        text_dic = OrderedDict()
        text_dic["text"] = local_text[start:end]
        entity_spans = []
        # do we have an annotation on this position?
        for annotation in annotations:
            if annotation["start"] >= start and annotation["end"] <= end:
                # annotation is located in the sentence
                entity_span = OrderedDict()
                entity_span["id"] = doc_number + "_" + annotation["id"]
                entity_span["start"] = annotation["start"] - start
                entity_span["end"] = annotation["end"] - start
                if annotation["type"].startswith("Application") or annotation["type"].startswith("PlugIn"):
                    # "Extension" ?
                    entity_span["type"] = "software"
                elif annotation["type"] == "Version" or annotation["type"] == "Release":
                    entity_span["type"] = "version"
                elif annotation["type"] == "Developer":
                    entity_span["type"] = "publisher"
                elif annotation["type"] == "URL":
                    entity_span["type"] = "url"
                elif annotation["type"] == "SoftwareCoreference_Deposition":
                    # context is relevant for capturing a sharing of the software, although the mention is not 
                    # directly related
                    entity_span["type"] = "software"
                elif annotation["type"] == "Citation":
                    continue
                else:
                    entity_span["type"] = "unknown"

                entity_span["raw_form"] = annotation["chunk"]
                if entity_span["type"] == "software":
                    entity_span["used"] = False
                    entity_span["contribution"] = False
                    entity_span["shared"] = False
                    if annotation["type"].find("_Usage") != -1:
                        entity_span["used"] = True
                    if annotation["type"].find("_Creation") != -1:
                        entity_span["contribution"] = True
                    if annotation["type"].find("_Deposition") != -1 or annotation["type"] == "SoftwareCoreference_Deposition":
                        entity_span["shared"] = True

                entity_spans.append(entity_span)
        if len(entity_spans)>0:
            text_dic["entity_spans"] = entity_spans

        texts.append(text_dic)
    document["texts"] = texts
    documents.append(document)

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

def export(text_corpus, output_path):
    documents = []
    # look for .txt files, with associated .ann in the same directory
    for root, directories, filenames in os.walk(text_corpus):
        for filename in filenames: 
            if filename.endswith(".txt"):
                # do we have an annotation file? normally even if we have no citation, we have an empty annotation file 
                text_filename = os.path.join(root, filename)
                annotation_filename = os.path.join(root, filename.replace(".txt", ".ann"))
                if os.path.isfile(annotation_filename):
                    export_file(text_filename, annotation_filename, documents)
    
    # print json result
    with open(output_path, 'w') as outfile:
        json.dump(documents, outfile, indent=4)  

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Generate labeled dataset for software mention context classification")
    parser.add_argument("--text-somesci", type=str, 
        help="path to the SoMeSci text format corpus file")
    parser.add_argument("--output", type=str, 
        help="path where to generate the software use classification datsset JSON file")

    args = parser.parse_args()
    text_somesci = args.text_somesci
    output_path = args.output

    # check path and call methods
    if text_somesci is None or not os.path.isdir(text_somesci):
        print("error: the path to the SoMeSci corpus files is not valid: ", text_somesci)
        exit(0)

    export(text_somesci, output_path)
    test_json_wellformedness(output_path)
