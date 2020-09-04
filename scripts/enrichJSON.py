"""
    Inject software mention annotations into articles in JSON format (converted from TEI XML format via TEI2LossyJSON.py) into into the JSON. 
    Two methods are available:
    1) dictionary/whitelist term lookup
    2) call of the Softcite software mention service
"""

import os
import argparse
import ntpath
import json
from collections import OrderedDict
import pysbd
import re
from corpus2JSON import convert_to_sentence_segments
import requests

class Annotator(object):    

    def __init__(self, config_path='./config.json'):
        # load config and white term list
        self.config = self._load_config(config_path)

        white_list_files = self.config["whitelist"]
        whitelist = []
        for file_string in white_list_files:
            print(os.path.join("../../data/software_lists/go", file_string))
            with open(os.path.join("../../data/software_lists/go", file_string)) as file:
                # these are actually text files, one term per line (no comma/tab separation)
                line = file.readline()
                while line:
                    if len(line.strip()) == 0:
                        continue
                    if not line.strip() in whitelist:
                        whitelist.append(line.strip())
                    line = file.readline()
        black_list_files = self.config["blacklist"]
        for file_string in black_list_files:
            print(os.path.join("../../data/software_lists/stop", file_string))
            with open(os.path.join("../../data/software_lists/stop", file_string)) as file:
                line = file.readline()
                while line:
                    if len(line.strip()) == 0:
                        continue
                    if line.strip() in whitelist:
                        whitelist.remove(line.strip())
                    line = file.readline()
        print("total", str(len(whitelist)), "terms")
        self.whitelist_matcher = re.compile(r'\b(?:%s)\b' % "|".join([re.escape(x) for x in whitelist]))

        self.use_service = self.service_isalive()

    def _load_config(self, path='./config.json'):
        """
        Load the json configuration 
        """
        config_json = open(path).read()
        return json.loads(config_json)

    def match_term(self, text):
        positions = []
        for match in re.finditer(self.whitelist_matcher, text):
            #groups = match.groups()
            #for idx in range(0, len(groups)):
            #print(match.start(), match.end())
            positions.append([match.start(), match.end()])
        return positions

    def inject_corpus_annotations(self, method, json_path, output_path):
        for file in os.listdir(json_path):
            if file.endswith(".json"):
                print(os.path.join(json_path, file))
                with open(os.path.join(json_path, file)) as jsonfile:
                    json_doc = json.load(jsonfile, object_pairs_hook=OrderedDict)
                    new_doc = OrderedDict()
                    document_id = None
                    if "level" in json_doc:
                        new_doc["level"] = json_doc["level"]
                    if "id" in json_doc:
                        document_id = json_doc["id"]
                        new_doc["id"] = json_doc["id"]
                    if "abstract" in json_doc:
                        new_doc["abstract"] = json_doc["abstract"]
                    if "body_text" in json_doc:
                        rank = 0
                        new_doc["body_text"] = []
                        for paragraph in json_doc["body_text"]:
                            text = paragraph["text"]
                            entities = None
                            references = None
                            if "entity_spans" in paragraph:
                                entities = paragraph["entity_spans"] 
                            else:
                                entities = []
                            if method == 'whitelist' or method == 'all':
                                spans = self.match_term(text)
                                if spans is not None and len(spans) > 0:
                                    for span in spans:
                                        entity = OrderedDict()
                                        entity['type'] = 'software'
                                        entity['start'] = span[0]
                                        entity['end'] = span[1]
                                        entity['rawForm'] = text[span[0]:span[1]]
                                        entity['resp'] = "whitelist"
                                        if document_id is not None:
                                            entity['id'] = document_id + "-"
                                        else:
                                            entity['id'] = ''
                                        entity['id'] += "software-simple-w" + str(rank)
                                        rank += 1
                                        entities.append(entity)
                            if method == 'service' or method == 'all':
                                json_results = self.software_mention_service(text)
                                if "mentions" in json_results:
                                    for mention in json_results["mentions"]:
                                        the_type = None
                                        if "software-name" in mention:
                                            the_type = "software-name"
                                        if "version" in mention:
                                            the_type = "version"
                                        if "publisher" in mention:
                                            the_type = "publisher" 
                                        if "url" in mention:
                                            the_type = "url"
                                        if the_type is not None:                                            
                                            the_mention = mention[the_type]
                                            entity = OrderedDict()
                                            if the_type == "software-name":
                                                the_type = "software"
                                            entity['type'] = the_type
                                            entity['start'] = the_mention["offsetStart"]
                                            entity['end'] = the_mention["offsetEnd"]
                                            entity['rawForm'] = the_mention['rawForm']
                                            entity['resp'] = "service"
                                            if document_id is not None:
                                                entity['id'] = document_id + "-"
                                            else:
                                                entity['id'] = ''
                                            if the_type == 'software':
                                                rank += 1
                                                entity['id'] += "software-simple-s" + str(rank)
                                            else:
                                                entity['id'] += "#software-simple-s" + str(rank)
                                            entities.append(entity)
                            if len(entities)>0:
                                paragraph["entity_spans"] = entities
                            new_doc["body_text"].append(paragraph)

                    if "level" in new_doc and new_doc["level"] == "paragraph":
                        new_doc = convert_to_sentence_segments(new_doc)
                    output_file = os.path.join(output_path, file)
                    with open(output_file, 'w') as outfile:
                        json.dump(new_doc, outfile, indent=4)  

    def software_mention_service(self, text):
        jsonObject = None
        if self.use_service:
            the_url = _grobid_software_url(self.config['software_mention_host'], self.config['software_mention_port'])
            the_url += "processSoftwareText"
            the_data = {'text': text, 'disambiguate': 0}
            response = requests.post(the_url, data=the_data)
            if response.status_code == 503:
                print('service overloaded, sleep', self.config['sleep_time'], seconds)
                time.sleep(self.config['sleep_time'])
                return self.software_mention_service(text)
            elif response.status_code >= 500:
                print('[{0}] Server Error -'.format(response.status_code), text)
            elif response.status_code == 404:
                print('[{0}] URL not found: [{1}]'.format(response.status_code,the_url))
            elif response.status_code >= 400:
                print('[{0}] Bad Request'.format(response.status_code))
                print(response.content )
            elif response.status_code == 200:
                #print('softcite succeed')
                jsonObject = response.json()
            else:
                print('Unexpected Error: [HTTP {0}]: Content: {1}'.format(response.status_code, response.content))

        '''
        if jsonObject is not None and 'mentions' in jsonObject and len(jsonObject['mentions']) != 0:
            # apply blacklist
            new_mentions = []
            for mention in jsonObject['mentions']:
                if "software-name" in mention:
                    software_name = mention["software-name"]
                    normalizedForm = software_name["normalizedForm"]
                    normalizedForm = normalizedForm.replace(" ", "").strip()
                    if normalizedForm not in self.blacklisted:
                        new_mentions.append(mention)
            jsonObject['mentions'] = new_mentions
        ''' 
        return jsonObject

    def service_isalive(self):
        # test if GROBID software mention recognizer is up and running...
        the_url = _grobid_software_url(self.config['software_mention_host'], self.config['software_mention_port'])
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

def _grobid_software_url(grobid_base, grobid_port):
    the_url = 'http://'+grobid_base
    if grobid_port is not None and len(grobid_port)>0:
        the_url += ":"+grobid_port
    the_url += "/service/"
    return the_url

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Inject automatic software mention annotations into fulltext in lossy JSON format")
    parser.add_argument("--method", type=str, 
        help="method for producing the annotations")
    parser.add_argument("--json-repo", type=str, 
        help="path to the directory of JSON files converted from TEI XML produced by GROBID, where to inject the automatic annotations")
    parser.add_argument("--output", type=str, 
        help="path to an output directory where to write the enriched JSON file(s)")
    parser.add_argument("--config", default="./config.json", help="path to the config file, default is ./config.json") 

    valid_methods = ['whitelist', 'service', 'all', 'none']

    args = parser.parse_args()
    method = args.method
    json_repo = args.json_repo
    output_path = args.output
    config = args.config

    if method not in valid_methods:
        print('error: method must be one of', valid_methods)
        exit(0)

    # check path and call methods
    if json_repo is None or not os.path.isdir(json_repo):
        print("error: the path to the JSON files is not valid: ", json_repo)
        exit(0)

    annotator = Annotator(config)
    annotator.inject_corpus_annotations(method, json_repo, output_path)

