"""
    runtime_eval.py
    ======================

    This modest python script will evaluate the runtime of the software mention service.
    Depending of the deployed model for the service (CRF, BidLSTM-CRF, with ot without ELMo 
    or other deep learning architecture for sequence labelling), we can benchmark the runtime
    of different approach in a fair manner.

    The text content for the benchmark is taken from the xml files from the training/eval 
    directory under resources/dataset/software/corpus

    to call the script for evaluation the text processing service:

    > python3 runtime_eval.py

    optionally you can provide a path to a repository of PDF in order to benchmark PDF processing:

    > python3 runtime_eval.py --pdf-repo /the/path/to/the/pdf/directory

    By default the config file ./config.json will be used, but you can also set a particular config
    file with the parameter --config:

    > python3 runtime_eval.py --config ./my_config.json

    The config file gives the hostname and port of the software-mention service to be used. Default 
    values are service default values (localhost:8060).

    Finally you can indicate the number of thread to be used for querying the service in parallel:

    > python3 runtime_eval.py --threads 10

    The default value is 1, so there is no parallelization in the call to the service by default.  

    Tested with python 3.*

"""

import sys
import os
import xml.etree.ElementTree as ET
import re
import subprocess
import argparse
import json
import requests
import time
import concurrent.futures
from client import ApiClient

# for making console output less boring
green = '\x1b[32m'
red = '\x1b[31m'
bold_red = '\x1b[1;31m'
orange = '\x1b[33m'
white = '\x1b[37m'
blue = '\x1b[34m'
score = '\x1b[7m'
bright = '\x1b[1m'
bold_yellow = '\x1b[1;33m'
reset = '\x1b[0m'

delimiters = "\n\r\t\f\u00A0([ •*,:;?.!/)-−–‐\"“”‘’'`$]*\u2666\u2665\u2663\u2660\u00A0"
regex = '|'.join(map(re.escape, delimiters))
pattern = re.compile('('+regex+')') 

def run_eval_pdf(pdf_repo_path, config, nb_threads=1):
    start_time = time.time()

    batch_size_pdf = config['batch_size']
    pdf_files = []

    for (dirpath, dirnames, filenames) in os.walk(input2):
        for filename in filenames:
            if filename.endswith('.pdf') or filename.endswith('.PDF'): 
                pdf_files.append(os.sep.join([dirpath, filename]))

                if len(pdf_files) == batch_size_pdf:
                    process_batch_pdf(pdf_files, output, n, service, generateIDs, consolidate_header, consolidate_citations, force, teiCoordinates)
                    pdf_files = []

    # last batch
    if len(pdf_files) > 0:
        process_batch_pdf(pdf_files, config, nb_threads)

    runtime = round(time.time() - start_time, 3)
    print("runtime: %s seconds " % (runtime))


def process_batch_pdf(pdf_files, config, nb_threads=1):
    print(len(pdf_files), "PDF files to process")
    #with concurrent.futures.ThreadPoolExecutor(max_workers=nb_threads) as executor:
    with concurrent.futures.ProcessPoolExecutor(max_workers=nb_threads) as executor:
        for pdf_file in pdf_files:
            executor.submit(process_pdf, pdf_file, config)

def process_pdf(pdf_file, config):
    # we use ntpath here to be sure it will work on Windows too
    pdf_file_name = ntpath.basename(pdf_file)

    print(pdf_file)
    files = {
        'input': (
            pdf_file,
            open(pdf_file, 'rb'),
            'application/pdf',
            {'Expires': '0'}
        )
    }
    
    the_url = 'http://'+config['grobid_server']
    if len(config['grobid_port'])>0:
        the_url += ":"+config['grobid_port']
    the_url += "/annotateSoftwarePDF"

    client = ApiClient()
    res, status = client.post(
        url=the_url,
        files=files,
        headers={'Accept': 'text/plain'}
    )

    if status == 503:
        time.sleep(config['sleep_time'])
        return process_pdf(pdf_file, config)
    elif status != 200:
        print('Processing failed with error ' + str(status))
        

def run_eval_txt(xml_repo_path, config, nb_threads=1):
    start_time = time.time()

    # acquisition of texts
    texts = [] 
    nb_texts = 0
    nb_tokens = 0
    nb_files = 0
    for (dirpath, dirnames, filenames) in os.walk(xml_repo_path):
        for filename in filenames:
            if filename.endswith('.xml') or filename.endswith('.tei'): 
                #try:
                tree = ET.parse(os.path.join(dirpath,filename))
                #except:
                #    print("XML parsing error with", filename)
                for paragraph in tree.findall(".//{http://www.tei-c.org/ns/1.0}p"):
                    #texts.append(paragraph.text)
                    text = ET.tostring(paragraph, encoding='utf-8', method='text').decode('utf-8')
                    texts.append(text)
                    nb_texts += 1
                    nb_tokens += len(pattern.split(text))
                    if len(texts) == config['batch_size']:
                        process_batch_txt(texts, config, nb_threads)
                        texts = []
                nb_files += 1
                if nb_files > 50:
                    break
    # last batch
    if len(texts) > 0:
        process_batch_txt(texts, config, nb_threads)

    print("nb xml files:", nb_files)
    print("nb texts:", nb_texts)
    print("nb tokens:", nb_tokens)

    runtime = round(time.time() - start_time, 3)

    print("runtime: %s seconds " % (runtime))
    print("xml files/s:", nb_files/runtime)
    print("texts/s:", nb_texts/runtime)
    print("tokens/s:", nb_tokens/runtime)


def process_batch_txt(texts, config, nb_threads=1):
    print(len(texts), "texts to process")
    #with concurrent.futures.ThreadPoolExecutor(max_workers=nb_threads) as executor:
    with concurrent.futures.ProcessPoolExecutor(max_workers=nb_threads) as executor:
        for text in texts:
            executor.submit(process_txt, text, config)

def process_txt(text, config):
    the_url = 'http://'+config['grobid_server']
    if len(config['grobid_port'])>0:
        the_url += ":"+config['grobid_port']
    the_url += "/processSoftwareText"

    the_data = {}
    the_data['text'] = text

    #print(the_url)
    #print(the_data)

    response = requests.post(the_url, data=the_data)
    
    status = response.status_code

    if status == 503:
        time.sleep(config['sleep_time'])
        return process_txt(text, config)
    elif status != 200 and status != 204:
        print('Processing failed with error ' + str(status))
    #else:
        #print(response.json())

def load_config(path='./config.json'):
    """
    Load the json configuration. Return the config dict or None if the service check fails. 
    """
    config_json = open(path).read()
    config = json.loads(config_json)

    # test if the server is up and running...
    the_url = 'http://'+config['grobid_server']
    if len(config['grobid_port'])>0:
        the_url += ":"+config['grobid_port']
    the_url += "/isalive"
    try:
        r = requests.get(the_url)
        status = r.status_code

        if status != 200:
            print('software-mention server does not appear available ' + str(status))
            config = None
        else:
            print("software-mention server is up and running")
    except requests.exceptions.RequestException as e: 
        print('software-mention server does not appear up and running')
        print(e)
        config = None

    return config


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Compute some runtime statistics for the software-mention service")
    parser.add_argument("--config", default='config.json', help="configuration file to be used")
    parser.add_argument("--xml-repo", type=str, help="in case we want to benchmark text processing, path to a directory of XML files")
    parser.add_argument("--pdf-repo", type=str, help="in case we want to benchmark PDF processing, path to a directory of PDF files")
    parser.add_argument("--thread", type=int, default=1, help="number of thread to be used for parallel calls to the service")

    args = parser.parse_args()

    config_path = args.config
    pdf_repo_path = args.pdf_repo
    xml_repo_path = args.xml_repo
    if xml_repo_path is None:
        xml_repo_path = "../resources/dataset/software/corpus"
    threads = args.thread

    nb_threads = 1
    if threads is not None:
        try:
            nb_threads = int(threads)
        except ValueError:
            print("Invalid concurrency parameter thread:", threads, "thread = 1 will be used by default")
            pass

    config = load_config(config_path)

    if config is not None:
        if pdf_repo_path is not None:
            # check pdf path
            if not os.path.isdir(pdf_repo_path):
                print("the path to the PDF directory is not valid: ", pdf_repo_path)
            else:
               run_eval_pdf(pdf_repo_path, config, nb_threads)
        else:
            # check xml path
            if xml_repo_path is None or not os.path.isdir(xml_repo_path):
                print("the path to the XML directory is not valid: ", xml_repo_path)
            else:
                run_eval_txt(xml_repo_path, config, nb_threads)
    else: 
        print("software-mention service not available for runtime test")
