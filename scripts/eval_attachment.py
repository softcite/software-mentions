"""
    eval_attachment.py
    ======================

    This modest python script will evaluate the attachment of software attributes to the right software names, creating complete software entities.
    
    The software-mentions service must first be started, the config.json file being updated accordingly if necessary for service host name and port. 
    To call the script for evaluation:

    > python3 eval_attachment.py --xml-corpus path_to_corpus_file

    --xml-corpus parameter points to the XML manually annotated corpus file with mentions and attachment (default is the repo TEI XML corpus, 
      under ../resources/dataset/software/corpus/softcite_corpus.tei.xml). 
    --config allows to set a different config file (default is ./config.json)

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

def run_eval_e2e(config, xml_corpus_path, attribute_type=None):
    start_time = time.time()

    # read xml file
    print("evaluating against:", xml_corpus_path)
    root = etree.parse(xml_corpus_path)

    # get all paragraphs with xpath
    paragraphs = root.xpath('//tei:body/tei:p', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})

    print("Evaluation on", len(paragraphs), "paragraphs")
    diff = 0
    total_attachment_expected = 0
    total_attachment_predicted = 0
    total_attachmnet_correct = 0
    for p in paragraphs:
        text =  ' '.join(p.itertext())
        text = text.strip()
        text = text.replace("  ", " ")
        text = text.replace("\n", " ")
        #print(text)

        # send text to the service
        json_string = process_txt(text, config)
        #print(json_string)
        json_result = json.loads(json_string)
        
        # get predicted result groups
        predicted_groups = []
        if "mentions" in json_result:
            for mention in json_result["mentions"]:
                predicted_group = []
                if "software-name" in mention:
                    # software name always first
                    predicted_group.append(mention["software-name"]["rawForm"])
                    # then the grouped attributes
                    if "version" in mention and (attribute_type is None or attribute_type == "version"):
                        predicted_group.append(mention["version"]["rawForm"])
                    if "publisher" in mention and (attribute_type == None or attribute_type == "publisher"):
                        predicted_group.append(mention["publisher"]["rawForm"])
                    if "url" in mention and (attribute_type == None or attribute_type == "url"):
                        predicted_group.append(mention["url"]["rawForm"])
                    predicted_groups.append(predicted_group)
                    #print("adding", predicted_group, predicted_groups)

        # get expected result groups
        groups = {}
        rss = p.xpath('./tei:rs', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
        group = []
        for rs in rss:
            # get entity identifier
            identifiers = rs.xpath('./@xml:id', namespaces={'xml': 'http://www.w3.org/XML/1998/namespace'})
            identifier = None
            if len(identifiers) > 0:
                identifier = identifiers[0]
            if identifier is None:
                identifier = rs.get("corresp")
                identifier = identifier.replace("#", "")
            if not identifier in groups:
                groups[identifier] = []
            local_type = rs.get("type")
            local_text = ' '.join(rs.itertext())
            local_text = local_text.replace("  ", " ")

            if attribute_type is None or attribute_type == local_type or local_type == "software":
                groups[identifier].append(local_text.strip())
        expected_groups = list(groups.values())

        # update grouping statistics
        if len(expected_groups) != len(predicted_groups):
            #print("\n", text)
            #print(json_string)
            #print("\n expected:", expected_groups)
            #print("predicted:", predicted_groups)
            diff += 1
        else:
            # check predicted component are present in the expected ones, otherwise grouping can not be assessed
            reject = False
            for predicted_group in predicted_groups:
                for component in predicted_group:
                    expected_grouping = _get_groups(expected_groups, component)
                    if len(expected_grouping) == 0:
                        reject = True
                        break
            if reject:
                continue

            for group in expected_groups:
                total_attachment_expected += len(group)-1
            for group in predicted_groups:
                total_attachment_predicted += len(group)-1

            for predicted_group in predicted_groups:
                if len(predicted_group) == 1:
                    continue    
                for comp in range(0,len(predicted_group)):
                    if comp == 0:
                        head = predicted_group[comp]
                        expected_grouping = _get_groups(expected_groups, head)
                        if len(expected_grouping) == 0:
                            break
                    else:
                        argument = predicted_group[comp]
                        found = False
                        for the_expected_grouping in expected_grouping:
                            if argument in the_expected_grouping:
                                total_attachmnet_correct += 1
                                found = True
                                break
                        # uncomment below to get a report of error cases
                        '''
                        if not found:
                            print("\n expected:", expected_groups)
                            print("predicted:", predicted_groups)
                        '''

    if attribute_type == None:
        print("\n----- all attributes -----\n")
    else:
        print("\n-----", attribute_type, "-----\n")

    print("total attachment expected: ", total_attachment_expected)
    print("total_attachment_predicted: ", total_attachment_predicted)
    print("total_attachmnet_correct: ", total_attachmnet_correct)

    precision = total_attachmnet_correct/total_attachment_predicted
    recall = total_attachmnet_correct/total_attachment_expected
    fscore = (2*precision*recall) / (precision + recall)

    print("precision attachment:", "{:.2f}".format(precision*100))
    print("recall attachment: ", "{:.2f}".format(recall*100))
    print("f-score attachment: ", "{:.2f}".format(fscore*100))

    print("number of diff:", diff, "/", len(paragraphs))

    runtime = round(time.time() - start_time, 4)
    print("-----------------------------")
    print("total runtime: %s seconds " % (runtime))
    print("-----------------------------")


def _get_groups(groups, string):
    result = []
    for group in groups:
        if len(group)>0 and string in group:
            result.append(group)
    return result


def process_txt(text, config):
    the_url = 'http://'+config['grobid_server']
    if len(config['grobid_port'])>0:
        the_url += ":"+config['grobid_port']
    the_url += "/service/processSoftwareText"

    the_data = {}
    the_data['text'] = text
    the_data['disambiguate'] = '0'

    response = requests.post(the_url, data=the_data)
    
    status = response.status_code
    if status == 503:
        time.sleep(config['sleep_time'])
        return process_txt(text, config)
    elif status != 200 and status != 204:
        print('Processing failed with error ' + str(status))
        return 
    else:
        return response.text

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
    the_url += "/service/isalive"
    try:
        r = requests.get(the_url)
        status = r.status_code

        if status != 200:
            print('\n-> software-mention server does not appear available ' + str(status))
            config = None
        else:
            print("\n-> software-mention server is up and running")
    except requests.exceptions.RequestException as e: 
        print('\n-> software-mention server does not appear up and running')
        print(e)
        config = None

    return config

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Evaluate the attachment of software attributes to the right software names")
    parser.add_argument("--xml-corpus", default='../resources/dataset/software/corpus/softcite_corpus.tei.xml', type=str, help="path to the manually annotated corpus file with mentions and attachment")
    parser.add_argument("--config", default='config.json', help="configuration file to be used")

    args = parser.parse_args()
    xml_corpus_path = args.xml_corpus
    config_path = args.config

    config = load_config(config_path)

    # check xml path
    if xml_corpus_path is None or os.path.isdir(xml_corpus_path):
        print("the path to the XML directory is not valid: ", xml_corpus_path)
    else:
        run_eval_e2e(config, xml_corpus_path)
        run_eval_e2e(config, xml_corpus_path, attribute_type="version")
        run_eval_e2e(config, xml_corpus_path, attribute_type="url")
        run_eval_e2e(config, xml_corpus_path, attribute_type="publisher")
