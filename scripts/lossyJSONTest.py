'''
    Some tests for the generated degraded JSON format with offset annotations.
    Check if the raw texts (rawForm/text) given in the annotations match with 
    the corresponding offset chunks in the full text snippet. 
'''

import os
import argparse
import json
from collections import OrderedDict

files = ["PMC5271396.json", "PMC4987885.json", "PMC4713128.json", "PMC3939454.json"]

def test_corpus(path_json_repo):
    for file in os.listdir(path_json_repo):
        if file.endswith(".json"):
            
            with open(os.path.join(path_json_repo, file)) as jsonfile:
                json_doc = json.load(jsonfile, object_pairs_hook=OrderedDict)

                if "body_text" in json_doc:
                    for paragraph in json_doc["body_text"]:
                        text = paragraph["text"]
                        entities = None
                        references = None
                        if "entity_spans" in paragraph:
                            entities = paragraph["entity_spans"] 
                        if "ref_spans" in paragraph:
                            references = paragraph["ref_spans"]

                        if entities is not None:
                            for entity in entities:
                                entity_str = entity["rawForm"]
                                entity_text = text[entity["start"]:entity["end"]]
                                if entity_str != entity_text:
                                    # report the entity string mismatch
                                    print("\n")
                                    print(os.path.join(path_json_repo, file))
                                    print(text, " -> ", entity_str, "/", entity_text, "|", entity["start"], entity["end"])
                        
                        if references is not None:
                            for reference in references:
                                reference_str = reference["text"]
                                reference_text = text[reference["start"]:reference["end"]]
                                if reference_str != reference_text:
                                    # report the reference string mismatch
                                    print("\n")
                                    print(os.path.join(path_json_repo, file))
                                    print(text, " -> ", reference_str, "/", reference_text, "|", reference["start"], reference["end"])            

                        # also check the length of the text segment
                        if len(text)>1500:
                            print("\n")
                            print(os.path.join(path_json_repo, file))
                            print("text length beyond 1500 characters:", str(len(text)), "/", text)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Test degraded JSON format")
    parser.add_argument("--json-repo", type=str, 
        help="path to the directory of JSON files converted from TEI XML produced by GROBID and used in the Softcite corpus")

    args = parser.parse_args()
    json_repo = args.json_repo

    # check path and call methods
    if json_repo is not None and not os.path.isdir(json_repo):
        print("the path to the JSON files is not valid: ", json_repo)

    test_corpus(json_repo)
