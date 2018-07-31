#import boto3
#import botocore
import sys
import os
import shutil
import json
import pickle
import lmdb
#import uuid
import subprocess
import argparse
import time
import S3
from concurrent.futures import ThreadPoolExecutor
#import asyncio
import requests
import subprocess

map_size = 100 * 1024 * 1024 * 1024 

endpoint_pdf = 'http://localhost:8060/annotateSoftwarePDF'
endpoint_txt = 'http://localhost:8060/annotateSoftwareText'

class software_mention_client(object):
    """
    Client for using the GROBID software mention service. 
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

    def annotation(self, file_in, file_out):
        the_file = {'input': open(file_in, 'rb')}
        response = requests.post(endpoint_pdf, files=the_file)
        jsonStr = None
        if response.status_code >= 500:
            print('[{0}] Server Error'.format(response.status_code))
        elif response.status_code == 404:
            print('[{0}] URL not found: [{1}]'.format(response.status_code,api_url))
        elif response.status_code == 401:
            print('[{0}] Authentication Failed'.format(response.status_code))
        elif response.status_code >= 400:
            print('[{0}] Bad Request'.format(response.status_code))
            print(ssh_key )
            print(response.content )
        elif response.status_code >= 300:
            print('[{0}] Unexpected redirect.'.format(response.status_code))
        elif response.status_code == 200:
            jsonStr = response.json()
        else:
            print('Unexpected Error: [HTTP {0}]: Content: {1}'.format(response.status_code, response.content))

        if jsonStr is not None:
            print(jsonStr)


    def annotation_collection(self):
        # init lmdb transactions
        txn = self.env.begin(write=True)
        txn_fail = self.env_fail.begin(write=True)

        


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


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description = "GROBID Software Mention recognition client")
    parser.add_argument("--data-path", default=None, help="path to the JSON dump file created by biblio-glutton-harvester") 
    parser.add_argument("--config", default="./config.json", help="path to the config file, default is ./config.json") 
    parser.add_argument("--reprocess", action="store_true", help="Reprocessed failed PDF") 
    parser.add_argument("--reset", action="store_true", help="Ignore previous processing states, and re-init the annotation process from the beginning") 
    parser.add_argument("--file-in", default=None, help="A PDF input file to be processed by the GROBID software mention recognizer") 
    parser.add_argument("--file-out", default=None, help="Path to output the software mentions in JSON format, extracted from the PDF file-in") 
    
    args = parser.parse_args()

    data_path = args.data_path
    config_path = args.config
    reprocess = args.reprocess
    reset = args.reset
    file_in = args.file_in
    file_out = args.file_out

    client = software_mention_client(config_path=config_path)

    if reset:
        client.reset()

    if reprocess:
        client.reprocess_failed()
    elif data_path is not None: 
        client.annotation_collection()
    elif file_in is not None:
        client.annotation(file_in, file_out)

