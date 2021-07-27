'''
    Basic script to check extracted mentions and offsets for annotations stored in mongodb. 
    Possibility to clean annotations via stopwords. 
'''

import sys
import os
import shutil
import json
import pymongo
import argparse

def _load_config(path='./config.json'):
    """
    Load the json configuration 
    """
    config_json = open(path).read()
    return json.loads(config_json)

def load_stopwords(stop_path="resources/stopwords_en.txt"):
    stopwords = []
    with open (stop_path, "r") as stopfile:
        for line in stopfile:
            line = line.replace(" ", "").strip()
            if not line.startswith("#"):
                stopwords.append(line)
    print("en stopwords size:", len(stopwords))
    return stopwords

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description = "Consistency check and optionally cleaning of MongoDB software extraction results")
    parser.add_argument("--config", default="./config.json", help="path to the config file, default is ./config.json") 
    parser.add_argument("--clean", action="store_true", help="remove annotations with invalid offsets") 

    args = parser.parse_args()
    config_path = args.config
    clean_mongo = args.clean

    config = _load_config(config_path)
    stopwords = load_stopwords()

    mongo_client = pymongo.MongoClient(config["mongo_host"], int(config["mongo_port"]))
    mongo_db = mongo_client[config["mongo_db"]]
    if mongo_db is None:
        print("MongoDB server is not available")    
        exit()
    print("MongoDB - number of documents:", mongo_db.documents.count_documents({}))
    print("MongoDB - number of software mentions:", mongo_db.annotations.count_documents({}))

    # check annotation offsets and contexts
    nb_annotation_no_context = 0
    nb_annotation_invalid_offsets = 0
    nb_annotation_to_filter = 0

    to_filter = []

    result = mongo_db.annotations.find( {"software-name": {"$exists": True}} )
    for annotation in result:
        #"software-name"
        #"rawForm"
        software_name_raw = annotation["software-name"]["rawForm"]
        if software_name_raw in stopwords or software_name_raw.isdigit():
            nb_annotation_to_filter += 1
            if clean_mongo:
                to_filter.append(annotation["_id"])

        if not "context" in annotation:
            if not software_name_raw in stopwords and not software_name_raw.isdigit():
                continue
            nb_annotation_no_context += 1
        else:
            # check software name raw mention matching the context
            if not software_name_raw in stopwords and not software_name_raw.isdigit():
                continue
            context_span = annotation["context"][annotation["software-name"]["offsetStart"]:annotation["software-name"]["offsetEnd"]]
            if software_name_raw != context_span:
                #print("match error:", software_name_raw, context_span)
                #print(annotation)
                nb_annotation_invalid_offsets += 1

    print("MongoDB - number of total annotations to filter:", str(nb_annotation_to_filter))
    print("   with - number of annotations without context field:", str(nb_annotation_no_context))
    print("   with - number of annotations without valid offset:", str(nb_annotation_invalid_offsets))

    if clean_mongo:
        print("\n")
        print("cleaning mongodb...", str(len(to_filter)), "annotations to filter")

        for local_id in to_filter:
            result = mongo_db.annotations.delete_one({'_id': local_id}) 
            #result = mongo_db.annotations.find_one({'_id': local_id}) 
