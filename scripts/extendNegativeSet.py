'''
    Extend the working negative samples with more negative samples from the full TEI XML files
'''

import os
import argparse
import ntpath
import xml
from lxml import etree
import regex as re
from sklearn.model_selection import train_test_split
import math
from collections import OrderedDict
import csv

# this is the number of subpopulations for the sampling
nb_strata = 5

def build_annotation_map(softcite_corpus_path):
    # for each document, indicate its number of annotations
    annotation_map = OrderedDict()
    print("loading Softcite corpus:", softcite_corpus_path)
    root = etree.parse(softcite_corpus_path)

    # get all document entries with xpath
    documents = root.xpath('//tei:TEI', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})

    for doc in documents:
        # get document identifier under <teiHeader><fileDesc xml:id="b991b33626">
        local_id = doc.xpath('./tei:teiHeader/tei:fileDesc/@xml:id', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})

        # get number of <rs> annotations
        rs = []
        for t in doc.findall(u"{http://www.tei-c.org/ns/1.0}text"):
            for b in t.findall(u"{http://www.tei-c.org/ns/1.0}body"):
                for p in b.findall(u"{http://www.tei-c.org/ns/1.0}p"):
                    rs = rs + p.findall(u"{http://www.tei-c.org/ns/1.0}rs")

        # update map
        annotation_map[local_id[0]] = len(rs)

    annotation_map = OrderedDict(sorted(annotation_map.items(), key=lambda x: x[1]))

    print(str(len(annotation_map)))
    #print(annotation_map)

    return annotation_map

def create_holdout_sets(softcite_corpus_path, tei_corpus_path, negative_examples_file_path, ratio=0.2):
    annotation_map = build_annotation_map(softcite_corpus_path)

    build_resources(softcite_corpus_path, tei_corpus_path, negative_examples_file_path, annotation_map)

def build_resources(softcite_corpus_path, tei_corpus_path, negative_examples_file_path, annotation_map):
    
    # under tei_corpus_path, we will find the identifier mapping (normally)
    map_identifiers = {}
    print(os.path.join(tei_corpus_path, "ids.csv"))
    with open(os.path.join(tei_corpus_path, "ids.csv")) as csv_file:
        csv_reader = csv.DictReader(csv_file)
        first_line = True
        for row in csv_reader:
            if first_line:
                first_line = False
                continue
            map_identifiers[row["id"]] = row
    
    # build list of working documents from the two corresponding CSV identifier files
    doc_working = []
    with open(os.path.join(tei_corpus_path, "ids.working.csv"), mode='r') as csv_file:
        csv_reader = csv.DictReader(csv_file)
        line_count = 0
        for row in csv_reader:
            if line_count == 0:
                line_count += 1
                continue
            doc_working.append(row["id"])
            line_count += 1

    print("working set:", str(len(doc_working)), "documents")

    # the final softcite corpus 
    root_softcite = etree.parse(softcite_corpus_path)

    root = etree.parse(negative_examples_file_path)
    node_to_remove_working = []
    documents = root.xpath('//tei:TEI', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
    for doc in documents:
        # get document identifier under <teiHeader><fileDesc xml:id="b991b33626">
        local_id = doc.xpath('./tei:teiHeader/tei:fileDesc/@xml:id', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
        if not local_id[0] in doc_working:
            node_to_remove_working.append(doc)

    for node in node_to_remove_working:
        node.getparent().remove(node)

    # we want to inject more negative examples, we get them in the grobid's full text TEI 
    for doc in documents:
        # get document identifier under <teiHeader><fileDesc xml:id="b991b33626">
        local_id = doc.xpath('./tei:teiHeader/tei:fileDesc/@xml:id', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
        if local_id[0] in doc_working:
            local_identifiers = map_identifiers[local_id[0]]
            # forbidden content is all the content of the <rs> of type software name, publisher and url (of length>2)
            # if such content is present in the paragraph considered for addition, we skip it to avoid adding false 
            # positive
            forbidden_contents = doc.xpath('.//tei:rs[@type="software" or @type="publisher" or @type="url"]', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
            forbidden_content = []
            for content_node in forbidden_contents:
                content = content_node.xpath("string()") 
                if len(content.strip()) > 2:
                    forbidden_content.append(content)

            # get more forbidden content from the final softcite corpus (because it contains additional corrections)
            softcite_local_doc = root_softcite.xpath('//tei:teiHeader/tei:fileDesc[@xml:id="'+local_id[0]+'"]', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
            if softcite_local_doc != None and len(softcite_local_doc) == 1:
                additional_forbidden_contents = softcite_local_doc[0].xpath('../..//tei:rs[@type="software" or @type="publisher" or @type="url"]', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
                for content_node in additional_forbidden_contents:
                    content = content_node.xpath("string()") 
                    if len(content.strip()) > 2:
                        forbidden_content.append(content)

            corpus_doc_body = doc.xpath('.//tei:body', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})

            full_text_tei_path = os.path.join(tei_corpus_path, local_identifiers["origin"] + ".fulltext.tei.xml")

            if not os.path.isfile(full_text_tei_path):
                continue

            #print(full_text_tei_path)
            local_doc = etree.parse(full_text_tei_path)

            # we go through paragraph by paragraph and check possible matching with already existing/annotated ones (if any)
            local_paragraphs = local_doc.xpath('//tei:p', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
            for local_paragraph in local_paragraphs:
                if has_forbidden_content(local_paragraph, forbidden_content) or is_matching_a_paragraph(local_paragraph, corpus_doc_body[0]):
                    continue
                if content_too_short(local_paragraph, 100):
                    continue
                local_paragraph.tail = "\n            "
                corpus_doc_body[0].append(local_paragraph)

            local_figDesc = local_doc.xpath('//tei:figDesc', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
            for local_paragraph in local_paragraphs:
                if has_forbidden_content(local_paragraph, forbidden_content) or is_matching_a_paragraph(local_paragraph, corpus_doc_body[0]):
                    continue
                if content_too_short(local_paragraph, 80):
                    continue
                local_paragraph.tail = "\n            "
                corpus_doc_body[0].append(local_paragraph)    

    root.write(negative_examples_file_path.replace(".working.tei.xml", ".extended.working.tei.xml"), pretty_print=True)

 
def is_matching_a_paragraph(paragraph, body):
    '''
    Check if a given paragraph node is already present under a given <body> element, based on the textual content
    '''
    list_body_signatures = []
    local_paragraphs = body.xpath('.//tei:p', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
    for local_paragraph in local_paragraphs:
        list_body_signatures.append(signature(local_paragraph))

    if len(list_body_signatures) == 0:
        return False

    signature_paragraph = signature(paragraph)
    if signature_paragraph in list_body_signatures:
        return True
    else: 
        return False

def signature(node):
    ''' 
    create a simplify string for soft match from a given node and its text content
    '''
    # get all text content under the node

    text = node.xpath("string()") 
    text = text.lower()
    text = re.sub('[^0-9a-z]+', '', text)

    return text

def has_forbidden_content(local_paragraph, forbidden_content):
    if forbidden_content == None or len(forbidden_content) == 0:
        return False

    text = local_paragraph.xpath("string()")
    for content in forbidden_content:
        if content in text:
            return True
    return False

def content_too_short(local_paragraph, min_char_size):
    text = local_paragraph.xpath("string()")
    if len(text) < min_char_size:
        return True
    else: 
        return False

if __name__ == "__main__":
    parser = argparse.ArgumentParser( 
        description = "General count information on the full TEI softcite corpus")
    parser.add_argument("--softcite-corpus", type=str, help="path to the full softcite corpus file")
    parser.add_argument("--tei-corpus", type=str, help="path to the directory of full text TEI XML files")
    parser.add_argument("--negative-examples-file", type=str, help="path to the file containing the set of negative examples")
    parser.add_argument("--ratio", type=float, help="proportion of documents tobe assigned to the holdout set, default is 0.2")

    args = parser.parse_args()
    softcite_corpus_path = args.softcite_corpus
    tei_corpus_path = args.tei_corpus
    negative_examples_file_path = args.negative_examples_file
    ratio = args.ratio

    # check path and call methods
    if softcite_corpus_path is not None and not os.path.isfile(softcite_corpus_path):
        print("the path to the Softcite corpus files is not valid: ", softcite_corpus_path)
        exit()
    elif tei_corpus_path is not None and not os.path.isdir(tei_corpus_path):
        print("the path to the directory of TEI files is not valid: ", tei_corpus_path)
        exit()
    elif negative_examples_file_path is not None and not os.path.isfile(negative_examples_file_path):
        print("the path to the negative example files is not valid: ", negative_examples_file_path)
        exit()
    else:
        create_holdout_sets(softcite_corpus_path, tei_corpus_path, negative_examples_file_path, ratio)

