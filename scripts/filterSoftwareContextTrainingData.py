import os
import argparse
import json
from collections import OrderedDict

""" 
Take a JSON software mention context classification corpus and filter cases
with selected labels.
"""

def filter_and_export(json_corpus, output_path):

    # load json corpus
    with open(json_corpus) as json_file:
        corpus = json.load(json_file)

    # check class values for each example
    for document in corpus["documents"]:
        if "texts" not in document:
            continue

        if len(document["texts"]) == 0:
            continue

        pos = 0
        toBeRemoved = []
        nbToBeKept = 0
        for text in document["texts"]:
            # if we have a URL annotation, we keep the case
            if "entity_spans" in text:
                toBeKept = False
                for annotation in text["entity_spans"]:
                    if annotation["type"] == "url":
                        nbToBeKept += 1
                        toBeKept = True
                        break

                if toBeKept:
                    pos += 1
                    continue

            # if we have edge cases regarding created/shared, we keep the case
            if "class_attributes" not in text:
                toBeRemoved.append(pos)
                pos += 1
                continue
            class_attributes = text["class_attributes"]
            classification = class_attributes["classification"]
            if classification["created"]["score"] < 0.1 and classification["shared"]["score"] < 0.1:
                toBeRemoved.append(pos) 
            else:
                nbToBeKept += 1
            pos += 1

        if nbToBeKept == 0:
            document["texts"] = []
        else:
            # remove stored positions
            new_texts = document["texts"]
            for pos, e in reversed(list(enumerate(toBeRemoved))):
                del document["texts"][e]

            document["texts"] = new_texts

    # filter empty documents
    new_corpus = {}
    new_documents = [] 

    for document in corpus["documents"]:
        if "texts" in document and len(document["texts"])>0:
            new_documents.append(document)

    new_corpus["documents"] = new_documents

    with open(output_path, 'w') as outfile:
        json.dump(new_corpus, outfile, indent=4)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Filter labeled dataset for software mention context classification")
    parser.add_argument("--json-corpus", type=str, 
        help="path to the JSON Softcite labeled mention context corpus file")
    parser.add_argument("--output", type=str, 
        help="path where to generate the filtered software context classification dataset JSON file")

    args = parser.parse_args()
    json_corpus = args.json_corpus
    output_path = args.output

    # check path and call methods
    if json_corpus is None or not os.path.isfile(json_corpus):
        print("error: the path to the JSON corpus files is not valid: ", json_corpus)
        exit(0)

    filter_and_export(json_corpus, output_path)
