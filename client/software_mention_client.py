#import boto3
#import botocore
import sys
import os
import shutil
import json
import pickle
import lmdb
import subprocess
import argparse
import time
import datetime
import S3
import concurrent.futures
import requests
import subprocess
import pymongo

map_size = 100 * 1024 * 1024 * 1024 

# default endpoint
endpoint_pdf = '/annotateSoftwarePDF'
endpoint_txt = '/annotateSoftwareText'

class software_mention_client(object):
    """
    Python client for using the GROBID software mention service. 
    """

    def __init__(self, config_path='./config.json'):
        self.config = None
        
        # standard lmdb environment for storing processed biblio entry uuid
        self.env = None

        # lmdb environment for keeping track of PDF annotation failures
        self.env_fail = None

        self._load_config(config_path)
        self._init_lmdb()

        if self.config['bucket_name'] is not None and len(self.config['bucket_name']) > 0:
            self.s3 = S3.S3(self.config)

        self.mongo_client = None

    def _load_config(self, path='./config.json'):
        """
        Load the json configuration 
        """
        config_json = open(path).read()
        self.config = json.loads(config_json)

    def _init_lmdb(self):
        # open in write mode
        envFilePath = os.path.join(self.config["data_path"], 'entries')
        self.env = lmdb.open(envFilePath, map_size=map_size)

        envFilePath = os.path.join(self.config["data_path"], 'fail')
        self.env_fail = lmdb.open(envFilePath, map_size=map_size)


    def annotate_directory(self, directory):
        # recursive directory walk for all pdf documents
        pdf_files = []
        for root, directories, filenames in os.walk(directory):
            for filename in filenames: 
                if filename.endswith(".pdf") or filename.endswith(".PDF"):
                    print(os.path.join(root,filename))
                    pdf_files.append(os.path.join(root,filename))
                    if len(pdf_files) == self.config["batch_size"]:
                        self.annotate_batch(pdf_files, None, None, None)
                        pdf_files = []
        # last batch
        if len(pdf_files) > 0:
            self.annotate_batch(pdf_files, None, None, None)


    def annotate_batch(self, pdf_files, out_files=None, dois=None, pmcs=None):
        print("annotate_batch", len(pdf_files))
        with concurrent.futures.ProcessPoolExecutor(max_workers=self.config["concurrency"]) as executor:
            for i, pdf_file in enumerate(pdf_files):
                if out_files is None:
                    out_file = None
                else:
                    out_file = out_files[i]
                if dois is None:
                    doi = None
                else:
                    doi = dois[i]
                if pmcs is None:
                    pmc = None
                else:
                    pmc = pmcs[i]    
                executor.submit(annotate, pdf_file, self.config, file_out, doi, pmc)


    def annotate_collection(self):
        # init lmdb transactions
        # open in read mode
        envFilePath = os.path.join(self.config["data_path"], 'entries')
        self.env = lmdb.open(envFilePath, map_size=map_size)

        #envFilePath = os.path.join(self.config["data_path"], 'doi')
        #self.env_doi = lmdb.open(envFilePath, map_size=map_size)

        with self.env.begin(write=False) as txn:
            nb_total = txn.stat()['entries']
        print("number of entries to process:", nb_total, "entries")

        # iterate over the entries in lmdb
        pdf_files = []
        dois = []
        pmcs = []
        with self.env.begin(write=True) as txn:
            cursor = txn.cursor()
            for key, value in cursor:
                local_entry = _deserialize_pickle(value)
                local_entry["id"] = key.decode(encoding='UTF-8');

                print(local_entry)

                pdf_files.append(os.path.join(self.config["data_path"], generateUUIDPath(local_entry['id']), local_entry['id']+".pdf"))
                dois.append(local_entry['doi'])
                pmcs.append(local_entry['pmc'])

                if i == self.config["batch_size"]:
                    annotate_batch(pdf_files, None, dois, pmcs)
                    pdf_files = []
                    dois = []
                    pmcs = []
        # last batch
        if len(pdf_files) > 0:
            self.annotate_batch(pdf_files, None, dois, pmcs)


    """
    def reprocess_failed(self):
    """

    def reset(self):
        """
        Remove the local lmdb keeping track of the state of advancement of the annotation and
        of the failed entries
        """
        # close environments
        self.env.close()
        self.env_fail.close()

        envFilePath = os.path.join(self.config["data_path"], 'entries')
        shutil.rmtree(envFilePath)

        envFilePath = os.path.join(self.config["data_path"], 'fail')
        shutil.rmtree(envFilePath)

        # re-init the environments
        self._init_lmdb()

def generateUUIDPath(filename):
    '''
    Convert a file name into a path with file prefix as directory paths:
    123456789 -> 12/34/56/123456789
    '''
    return filename[:2] + '/' + filename[2:4] + '/' + filename[4:6] + "/" + filename[6:8] + "/"

def annotate(file_in, config, file_out=None, doi=None, pmc=None):
    the_file = {'input': open(file_in, 'rb')}
    
    url = "http://" + config["software_mention_host"]
    if config["software_mention_port"] is not None:
        url += ":" + str(config["software_mention_port"])
    url += endpoint_pdf
    print("calling... ", url)

    response = requests.post(url, files=the_file)
    jsonObject = None
    if status == 503:
        time.sleep(self.config['sleep_time'])
        return self.annotate(file_in, config, file_out, doi, pmc)
    elif response.status_code >= 500:
        print('[{0}] Server Error'.format(response.status_code))
    elif response.status_code == 404:
        print('[{0}] URL not found: [{1}]'.format(response.status_code,api_url))
    elif response.status_code >= 400:
        print('[{0}] Bad Request'.format(response.status_code))
        print(response.content )
    elif response.status_code == 200:
        jsonObject = response.json()
    else:
        print('Unexpected Error: [HTTP {0}]: Content: {1}'.format(response.status_code, response.content))

    if jsonObject is not None:
        print(jsonObject)

    # add file, DOI, date and version info in the JSON, if available
    if doi is not None:
        jsonObject['DOI'] = doi;
    if pmc is not None:
        jsonObject['PMC'] = pmc;
    jsonObject['file_name'] = os.path.basename(file_in)
    jsonObject['file_path'] = file_in
    jsonObject['date'] = datetime.datetime.now().isoformat();
    # TODO: get the version via the server
    jsonObject['version'] = "0.5.6-SNAPSHOT";

    if file_out is not None: 
        # we write the json result into a file
        with open(file_out, "w", encoding="utf-8") as json_file:
            json_file.write(json.dumps(jsonObject))
    else:
        # we store the result in mongo db (this is the common case)
        if self.mongo_client is None:
            self.mongo_client = pymongo.MongoClient(config["mongo_host"], int(config["mongo_port"]))
            self.mongo_db = self.mongo_client[config["mongo_db"]]

        inserted_id = self.mongo_db.annotations.insert_one(jsonObject).inserted_id


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description = "GROBID Software Mention recognition client")
    parser.add_argument("--data-path", default=None, help="path to the JSON dump file created by biblio-glutton-harvester") 
    parser.add_argument("--config", default="./config.json", help="path to the config file, default is ./config.json") 
    parser.add_argument("--reprocess", action="store_true", help="Reprocessed failed PDF") 
    parser.add_argument("--reset", action="store_true", help="Ignore previous processing states, and re-init the annotation process from the beginning") 
    parser.add_argument("--file-in", default=None, help="A PDF input file to be processed by the GROBID software mention recognizer") 
    parser.add_argument("--file-out", default=None, help="Path to output the software mentions in JSON format, extracted from the PDF file-in") 
    parser.add_argument("--repo-in", default=None, help="Path to a directory of PDF files to be processed by the GROBID software mention recognizer")  
    
    args = parser.parse_args()

    data_path = args.data_path
    config_path = args.config
    reprocess = args.reprocess
    reset = args.reset
    file_in = args.file_in
    file_out = args.file_out
    repo_in = args.repo_in

    client = software_mention_client(config_path=config_path)

    if reset:
        client.reset()

    if reprocess:
        client.reprocess_failed()
    elif repo_in is not None: 
        client.annotate_directory(repo_in)
    elif file_in is not None:
        annotate(file_in, client.config, file_out)
    elif data_path is not None: 
        client.annotate_collection()
    
