"""
    grobidTest.py
    ======================

    This modest python script will evaluate the runtime of the grobid models.
    
    to call the script for evaluation the text processing service:

    > python3 grobidTest.py --xml-repo /the/path/to/the/xml/directory

    You can indicate the number of thread to be used for querying the service in parallel:

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

import numpy as np
from delft.utilities.Embeddings import Embeddings
import delft.sequenceLabelling
from delft.sequenceLabelling import Sequence
from delft.utilities.Tokenizer import tokenizeAndFilter
from sklearn.model_selection import train_test_split
from delft.sequenceLabelling.reader import load_data_and_labels_crf_file
from delft.sequenceLabelling.reader import load_data_and_labels_crf_string
from delft.sequenceLabelling.reader import load_data_crf_string
import keras.backend as K


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

models = ['affiliation-address', 'citation', 'date', 'header', 'name-citation', 'name-header', 'software']

def run_eval_txt(xml_repo_path, model, nb_threads=1, use_ELMo=False):

    # load the model
    # load model
    model_name = 'grobid-'+model
    if use_ELMo:
        model_name += '-with_ELMo'
        
    model = Sequence(model_name)
    model.load()

    if not use_ELMo:
        model.model_config.batch_size = 200 

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
                    text = text.replace("\n", " ")
                    text = text.replace("\t", " ")
                    test = re.sub(r'( )+', ' ', text.strip())
                    texts.append(text.strip())
                    nb_texts += 1
                    nb_tokens += len(pattern.split(text))
                    if len(texts) == model.model_config.batch_size:
                        process_batch_txt(texts, model, nb_threads)
                        texts = []
                nb_files += 1
                if nb_files > 50:
                    break
    # last batch
    if len(texts) > 0:
        process_batch_txt(texts, model, nb_threads)

    print("-----------------------------")
    print("nb xml files:", nb_files)
    print("nb texts:", nb_texts)
    print("nb tokens:", nb_tokens)

    runtime = round(time.time() - start_time, 4)
    print("-----------------------------")
    print("total runtime: %s seconds " % (runtime))
    print("-----------------------------")
    print("xml files/s:\t {:.4f}".format(nb_files/runtime))
    print("    texts/s:\t {:.4f}".format(nb_texts/runtime))
    print("   tokens/s:\t {:.4f}".format(nb_tokens/runtime)) 

def process_batch_txt(texts, model, nb_threads=1):
    print(len(texts), "texts to process")
    #print(texts)
    #with concurrent.futures.ThreadPoolExecutor(max_workers=nb_threads) as executor:
    #with concurrent.futures.ProcessPoolExecutor(max_workers=nb_threads) as executor:
    #    for text in texts:
    #        executor.submit(process_txt, text, config)
    max_length = 0
    for text in texts:
        if len(text)>max_length:
            max_length = len(text)
    print("max sequence length of batch:", max_length)
    model.tag(texts, "json")

#def process_txt(texts, model):
#    model.tag(texts)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Compute some runtime statistics for the grobid models")
    parser.add_argument("model")
    parser.add_argument("--xml-repo", type=str, help="path to a directory of XML files containing text to be used for benchmarking")
    parser.add_argument("--thread", type=int, default=1, help="number of thread to be used for parallel calls to the service")
    parser.add_argument("--use-ELMo", action="store_true", help="Use ELMo contextual embeddings")

    args = parser.parse_args()
    model = args.model
    xml_repo_path = args.xml_repo
    threads = args.thread
    use_ELMo = args.use_ELMo

    nb_threads = 1
    if threads is not None:
        try:
            nb_threads = int(threads)
        except ValueError:
            print("Invalid concurrency parameter thread:", threads, "thread = 1 will be used by default")
            pass

    # check xml path
    if xml_repo_path is None or not os.path.isdir(xml_repo_path):
        print("the path to the XML directory is not valid: ", xml_repo_path)
    else:
        run_eval_txt(xml_repo_path, model, nb_threads, use_ELMo)
