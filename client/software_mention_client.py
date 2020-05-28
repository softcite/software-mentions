import sys
import os
import shutil
import json
import pickle
import lmdb
import argparse
import time
import datetime
import S3
import concurrent.futures
import requests
import pymongo

map_size = 100 * 1024 * 1024 * 1024 

# default endpoint
endpoint_pdf = '/service/annotateSoftwarePDF'
endpoint_txt = '/service/annotateSoftwareText'

class software_mention_client(object):
    """
    Python client for using the GROBID software mention service. 
    """

    def __init__(self, config_path='./config.json'):
        self.config = None
        
        # standard lmdb environment for storing processed biblio entry uuid relative to software processing
        self.env_software = None

        # lmdb environment for keeping track of PDF annotation failures relative to software processing
        self.env_fail_software = None

        self._load_config(config_path)
        self._init_lmdb()

        if self.config['bucket_name'] is not None and len(self.config['bucket_name']) > 0:
            self.s3 = S3.S3(self.config)

        self.mongo_db = None

    def _load_config(self, path='./config.json'):
        """
        Load the json configuration 
        """
        config_json = open(path).read()
        self.config = json.loads(config_json)

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
                'test call to grobid software mention failed, please check and re-start a server.')
        return False

    def _init_lmdb(self):
        # open in write mode
        envFilePath = os.path.join(self.config["data_path"], 'entries_software')
        self.env_software = lmdb.open(envFilePath, map_size=map_size)

        envFilePath = os.path.join(self.config["data_path"], 'fail_software')
        self.env_fail_software = lmdb.open(envFilePath, map_size=map_size)

    def annotate_directory(self, directory):
        # recursive directory walk for all pdf documents
        pdf_files = []
        out_files = []
        for root, directories, filenames in os.walk(directory):
            for filename in filenames: 
                if filename.endswith(".pdf") or filename.endswith(".PDF"):
                    print(os.path.join(root,filename))
                    pdf_files.append(os.path.join(root,filename))
                    if filename.endswith(".pdf"):
                        out_file = filename.replace(".pdf", ".software.json")
                    if filename.endswith(".PDF"):
                        out_file = filename.replace(".PDF", ".software.json")    
                    out_files.append(os.path.join(root,out_file))
                    if len(pdf_files) == self.config["batch_size"]:
                        self.annotate_batch(pdf_files, out_files, None)
                        pdf_files = []
                        out_files = []
        # last batch
        if len(pdf_files) > 0:
            self.annotate_batch(pdf_files, out_files, None)


    def annotate_batch(self, pdf_files, out_files=None, full_records=None):
        # process a provided list of PDF
        #print("annotate_batch", len(pdf_files))
        with concurrent.futures.ProcessPoolExecutor(max_workers=self.config["concurrency"]) as executor:
            for i, pdf_file in enumerate(pdf_files):
                out_file = None if out_files is None else out_files[i]
                full_record = None if full_records is None else full_records[i]

                executor.submit(annotate, pdf_file, self.config, self.mongo_db, out_file, full_record)


    def annotate_collection(self, data_path):
        # init lmdb transactions
        # open in read mode
        #print(os.path.join(data_path, 'entries_software'))
        envFilePath = os.path.join(data_path, 'entries')
        self.env = lmdb.open(envFilePath, map_size=map_size)

        with self.env.begin(write=True) as txn:
            nb_total = txn.stat()['entries']
        print("number of entries to process:", nb_total, "entries")

        # iterate over the entries in lmdb
        pdf_files = []
        out_files = []
        full_records = []
        i = 0
        with self.env.begin(write=True) as txn:
            cursor = txn.cursor()
            for key, value in cursor:
                local_entry = _deserialize_pickle(value)
                local_entry["id"] = key.decode(encoding='UTF-8');
                #print(local_entry)

                pdf_files.append(os.path.join(data_path, generateS3Path(local_entry['id']), local_entry['id']+".pdf"))
                out_files.append(os.path.join(data_path, generateS3Path(local_entry['id']), local_entry['id']+".software.json"))
                full_records.append(local_entry)
                i += 1

                if i == self.config["batch_size"]:
                    self.annotate_batch(pdf_files, out_files, full_records)
                    pdf_files = []
                    out_files = []
                    full_records = []
                    i = 0

        # last batch
        if len(pdf_files) > 0:
            self.annotate_batch(pdf_files, out_files, full_records)

        self.env.close()

    """
    def reprocess_failed(self):
    """

    def reset(self):
        """
        Remove the local lmdb keeping track of the state of advancement of the annotation and
        of the failed entries
        """
        # close environments
        self.env_software.close()
        self.env_fail_software.close()

        envFilePath = os.path.join(self.config["data_path"], 'entries_software')
        shutil.rmtree(envFilePath)

        envFilePath = os.path.join(self.config["data_path"], 'fail_software')
        shutil.rmtree(envFilePath)

        # re-init the environments
        self._init_lmdb()

    def load_mongo(self, directory):
        for root, directories, filenames in os.walk(directory):
            for filename in filenames: 
                if filename.endswith(".software.json"):
                    if self.config["mongo_host"] is not None:
                        # we store the result in mongo db 
                        if self.mongo_db is None:
                            mongo_client = pymongo.MongoClient(self.config["mongo_host"], int(self.config["mongo_port"]))
                            mongo_db = mongo_client[self.config["mongo_db"]]
                        the_json = open(os.path.join(root,filename)).read()
                        jsonObject = json.loads(the_json)
                        inserted_id = mongo_db.annotations.insert_one(jsonObject).inserted_id
                        #print("inserted annotations with id", inserted_id)

def generateS3Path(identifier):
    '''
    Convert a file name into a path with file prefix as directory paths:
    123456789 -> 12/34/56/123456789
    '''
    return os.path.join(identifier[:2], identifier[2:4], identifier[4:6], identifier[6:8], "")

def _serialize_pickle(a):
    return pickle.dumps(a)

def _deserialize_pickle(serialized):
    return pickle.loads(serialized)

def annotate(file_in, config, mongo_db, file_out=None, full_record=None):
    the_file = {'input': open(file_in, 'rb')}
    url = "http://" + config["software_mention_host"]
    if config["software_mention_port"] is not None:
        url += ":" + str(config["software_mention_port"])
    url += endpoint_pdf
    #print("calling... ", url)

    response = requests.post(url, files=the_file)
    jsonObject = None
    if response.status_code == 503:
        print('sleep')
        time.sleep(config['sleep_time'])
        return annotate(file_in, config, file_out, doi, pmc)
    elif response.status_code >= 500:
        print('[{0}] Server Error'.format(response.status_code))
    elif response.status_code == 404:
        print('[{0}] URL not found: [{1}]'.format(response.status_code,api_url))
    elif response.status_code >= 400:
        print('[{0}] Bad Request'.format(response.status_code))
        print(response.content )
    elif response.status_code == 200:
        #print('softcite succeed')
        jsonObject = response.json()
    else:
        print('Unexpected Error: [HTTP {0}]: Content: {1}'.format(response.status_code, response.content))

    if jsonObject is not None and len(jsonObject['mentions']) != 0:
        # add file, DOI, date and version info in the JSON, if available
        if full_record is not None:
            jsonObject['metadata'] = full_record;
            jsonObject['id'] = full_record['id']
        jsonObject['original_file_path'] = file_in
        jsonObject['file_name'] = os.path.basename(file_in)
        
        if file_out is not None: 
            # we write the json result into a file together with the processed pdf
            with open(file_out, "w", encoding="utf-8") as json_file:
                json_file.write(json.dumps(jsonObject))

        if config["mongo_host"] is not None:
            # we store the result in mongo db 
            if mongo_db is None:
                mongo_client = pymongo.MongoClient(config["mongo_host"], int(config["mongo_port"]))
                mongo_db = mongo_client[config["mongo_db"]]
            inserted_id = mongo_db.annotations.insert_one(jsonObject).inserted_id
            #print("inserted annotations with id", inserted_id)

def _grobid_software_url(grobid_base, grobid_port):
    the_url = 'http://'+grobid_base
    if grobid_port is not None and len(grobid_port)>0:
        the_url += ":"+grobid_port
    the_url += "/service/"
    return the_url

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description = "GROBID Software Mention recognition client")
    parser.add_argument("--repo-in", default=None, help="path to a directory of PDF files to be processed by the GROBID software mention recognizer")  
    parser.add_argument("--file-in", default=None, help="a single PDF input file to be processed by the GROBID software mention recognizer") 
    parser.add_argument("--file-out", default=None, help="path to a single output the software mentions in JSON format, extracted from the PDF file-in") 
    parser.add_argument("--data-path", default=None, help="path to the JSON dump file created by biblio-glutton-harvester") 
    parser.add_argument("--config", default="./config.json", help="path to the config file, default is ./config.json") 
    parser.add_argument("--reprocess", action="store_true", help="reprocessed failed PDF") 
    parser.add_argument("--reset", action="store_true", help="ignore previous processing states and re-init the annotation process from the beginning") 
    parser.add_argument("--load", action="store_true", help="load json files into the MongoDB instance, the --repo-in parameter must indicate the path "
        +"to the directory of resulting json files to be loaded") 

    args = parser.parse_args()

    data_path = args.data_path
    config_path = args.config
    reprocess = args.reprocess
    reset = args.reset
    file_in = args.file_in
    file_out = args.file_out
    repo_in = args.repo_in
    load_mongo = args.load

    client = software_mention_client(config_path=config_path)

    if not client.service_isalive():
        sys.exit("Grobid software mention service not available, leaving...")

    if reset:
        client.reset()

    if load_mongo:
        # check a mongodb server is specified in the config
        if client.config["mongo_host"] is None or len(client.config["mongo_host"]) == 0:
            sys.exit("the mongodb server where to load the json files is not indicated in the config file, leaving...")
        if repo_in is None: 
            sys.exit("the repo_in where to find the json files to be loaded is not indicated, leaving...")
        client.load_mongo(repo_in)

    elif reprocess:
        client.reprocess_failed()
    elif repo_in is not None: 
        client.annotate_directory(repo_in)
    elif file_in is not None:
        annotate(file_in, client.config, file_out)
    elif data_path is not None: 
        client.annotate_collection(data_path)
    
