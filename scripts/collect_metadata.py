"""

This modest python script calls biblio-glutton to retrieve full metadata records for the 
Softcite corpus. 

"""

import sys
import os
from lxml import etree
import re
import subprocess
import argparse
import json
import requests
import time
import concurrent.futures
import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def collect_full_metadata(config, xml_corpus_path, outpath):
    start_time = time.time()

    # read xml file
    print("retrieving identifiers:", xml_corpus_path)
    root = etree.parse(xml_corpus_path)

    total_not_found = 0

    failed_path = os.path.join(outpath, "failed.txt")
    failedFile = open(failed_path, 'w')

    # get all articles
    articles = root.xpath('//tei:teiHeader/tei:fileDesc', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
    for article in articles:

        article_id = article.xpath('./@xml:id')[0]

        # get all identifiers of the article
        idnos = article.xpath('./tei:sourceDesc/tei:bibl/tei:idno', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
        record_json = None
        id_text_all = ""
        for idno in idnos:
            # customer.xpath('./@xml:id', )[0]
            id_type = idno.attrib['type']
            if id_type == "origin":
                continue

            the_id_text = ''.join(idno.itertext()) 

            if record_json == None and id_type == "PMC":
                # try to retrieve the record
                record_json = biblio_glutton_lookup(config, pmcid=the_id_text)
                if record_json != None:
                    #print(record_json)
                    break

            if record_json == None and id_type == "DOI":
                record_json = biblio_glutton_lookup(config, doi=the_id_text)
                if record_json != None:
                    #print(record_json)
                    break

            if record_json == None and id_type == "PMID":
                record_json = biblio_glutton_lookup(config, pmid=the_id_text)
                if record_json != None:
                    #print(record_json)
                    break

            id_text_all += " " + the_id_text

        if record_json == None:
            print("record not found:", id_text_all)
            total_not_found += 1
            failedFile.write(article_id + "\t" + id_text_all + "\n")
        else:
            record_json['id'] = article_id
            output_path = os.path.join(outpath, article_id + '.json');
            with open(output_path, 'w') as outfile:
                json.dump(record_json, outfile)
    failedFile.close()

    print("total not found:", total_not_found, "written under", failed_path)


def biblio_glutton_lookup(config, doi=None, pmcid=None, pmid=None):
    """
    Lookup on biblio_glutton with the provided strong identifiers, return the full agregated biblio_glutton record
    """
    if not "biblio_glutton_server" in config or len(config["biblio_glutton_server"]) == 0:
        return None

    biblio_glutton_url = _biblio_glutton_url(config["biblio_glutton_server"], config["biblio_glutton_port"])
    success = False
    jsonResult = None

    if doi is not None and len(doi)>0:
        response = requests.get(biblio_glutton_url, params={'doi': doi}, verify=False, timeout=5)
        success = (response.status_code == 200)
        if success:
            jsonResult = response.json()

    if not success and pmid is not None and len(pmid)>0:
        response = requests.get(biblio_glutton_url + "pmid=" + pmid, verify=False, timeout=5)
        success = (response.status_code == 200)
        if success:
            jsonResult = response.json()     

    if not success and pmcid is not None and len(pmcid)>0:
        response = requests.get(biblio_glutton_url + "pmc=" + pmcid, verify=False, timeout=5)  
        success = (response.status_code == 200)
        if success:
            jsonResult = response.json()
    
    if not success and doi is not None and len(doi)>0:
        # let's call crossref as fallback for the X-months gap
        # https://api.crossref.org/works/10.1037/0003-066X.59.1.29
        if doi.endswith(")"):
            doi = doi[:-1]

        doi = doi.replace("%2F", "/")

        print("calling crossref... ", config['crossref_base']+"/works/"+doi)
        user_agent = {'User-agent': 'Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:68.0) Gecko/20100101 Firefox/68.0 (mailto:' 
            + config['crossref_email'] + ')'} 
        try:
            response = requests.get(config['crossref_base']+"/works/"+doi, headers=user_agent, verify=False, timeout=5)
        except: 
            print("request failed")
        if response.status_code == 200:
            jsonResult = response.json()['message']
            # filter out references and re-set doi, in case there are obtained via crossref
            if "reference" in jsonResult:
                del jsonResult["reference"]
            print("success...")
        else:
            print("failure...")
            success = False
            jsonResult = None
    
    return jsonResult

def _biblio_glutton_url(biblio_glutton_base, biblio_glutton_port):
    res = "http://"
    if biblio_glutton_base.endswith("/"):
        res += biblio_glutton_base[:-1]
    else: 
        res += biblio_glutton_base
    if biblio_glutton_port is not None and len(biblio_glutton_port)>0:
        res += ":"+biblio_glutton_port
    return res+"/service/lookup?"

def load_config(path='./config.json'):
    """
    Load the json configuration. Return the config dict or None if the service check fails. 
    """
    config_json = open(path).read()
    config = json.loads(config_json)

    # test if the server is up and running...
    the_url = 'http://'+config['biblio_glutton_server']
    if len(config['biblio_glutton_port'])>0:
        the_url += ":"+config['biblio_glutton_port']
    the_url += "/service/data"
    try:
        r = requests.get(the_url)
        status = r.status_code

        if status != 200:
            print('\n-> biblio-glutton server does not appear available ' + str(status))
            config = None
        else:
            print("\n-> biblio-glutton server is up and running")
    except requests.exceptions.RequestException as e: 
        print('\n-> biblio-glutton server does not appear up and running')
        print(e)
        config = None

    return config


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Collect the available full metadata records of the articles of the Softcite corpus")
    parser.add_argument("--xml-corpus", default='../resources/dataset/software/corpus/softcite_corpus-full.tei.xml', type=str, help="path to the manually annotated corpus file with mentions and attachment")
    parser.add_argument("--config", default='config.json', help="configuration file to be used")
    parser.add_argument("--output", type=str, help="path to the directory where to write the full records")

    args = parser.parse_args()
    xml_corpus_path = args.xml_corpus
    config_path = args.config
    output = args.output

    config = load_config(config_path)

    # check xml path
    if xml_corpus_path is None or os.path.isdir(xml_corpus_path):
        print("the path to the XML directory is not valid: ", xml_corpus_path)
    elif output is None or not os.path.isdir(output):
        print("output directory is not valid: ", output)
    else:
        collect_full_metadata(config, xml_corpus_path, output)


