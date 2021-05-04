'''
    Use the full softcite dataset (one TEI entry per document, including documents without 
    annotation) to create an Holdout set satisfying the following constraints:

    1. same distribute of document with at least one annotation as the full corpus
    2. similar distribution of the number of annotations per document as the full corpus
    3. same distribution in fields (Biomedicine / Economics)

    For ensure point 2, we use a stratified sampling with 5 strata, using scikit-learn.

    We produce:
    - partition of the Softcite dataset (positive examples) into holdout set and working 
      set
    - partition of the full text TEI (from GROBID, non-annotated) into holdout set and 
      working set
    - the holdout set of full text documents (all paragraphs), with the annotated version 
      of the paragraph including annotations
    - partition of the undersampling resource (the negative example XML file) into negative 
      examples of the holdout set and negative examples of the working set

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

    # build the strata from annotation counts
    size = (len(annotation_map) // nb_strata) + 1

    print(str(size), len(annotation_map))

    # map the doc to its "subpopulation", a category given as a int from 0 to (nb_strata -1)    
    category_map = {}
    strata_rank = 0
    strata_size = 0
    for doc in annotation_map:
        category_map[doc] = strata_rank
        strata_size += 1
        if strata_size == size:
            strata_rank += 1 
            strata_size = 0

    # create arrays for sampling
    all_docs = []
    all_cat = []
    for doc in category_map:
        all_docs.append(doc)
        all_cat.append(category_map[doc])

    holdout_size = math.floor(len(all_docs) * 0.2)
    working_size = len(all_docs) - holdout_size

    # this will perform the stratified sampling following the "number of annotation" subpopulations 
    doc_holdout, doc_working = train_test_split(all_docs, test_size=working_size, train_size=holdout_size, shuffle=True, stratify=all_cat)

    build_resources(doc_holdout, doc_working, tei_corpus_path, negative_examples_file_path, annotation_map)

def build_resources(doc_holdout, doc_working, tei_corpus_path, negative_examples_file_path, annotation_map):
    print("holdout set:", str(len(doc_holdout)), "documents")
    print("working set:", str(len(doc_working)), "documents")
    
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
    # write identifiers for the two sets
    fieldnames = ['id', 'origin', 'DOI', 'PMID', 'PMCID']
    file_holdout = open(os.path.join(tei_corpus_path, 'ids.holdout.csv'), mode='w')
    writer_holdout = csv.DictWriter(file_holdout, fieldnames=fieldnames)
    writer_holdout.writeheader()
    file_working = open(os.path.join(tei_corpus_path, 'ids.working.csv'), mode='w')
    writer_working = csv.DictWriter(file_working, fieldnames=fieldnames)
    writer_working.writeheader()
    for key in map_identifiers:
        if key in doc_holdout:
            writer_holdout.writerow(map_identifiers[key])
        else:
            writer_working.writerow(map_identifiers[key])
    file_holdout.close()
    file_working.close()

    # partition of the softcite corpus
    # working set
    root = etree.parse(softcite_corpus_path)
    node_to_remove_working = []
    documents = root.xpath('//tei:TEI', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
    for doc in documents:
        # get document identifier under <teiHeader><fileDesc xml:id="b991b33626">
        local_id = doc.xpath('./tei:teiHeader/tei:fileDesc/@xml:id', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
        if local_id[0] in doc_holdout:
            node_to_remove_working.append(doc)

    for node in node_to_remove_working:
        node.getparent().remove(node)
    root.write(softcite_corpus_path.replace(".tei.xml", ".working.tei.xml"), pretty_print=True)   

    # holdout set
    root = etree.parse(softcite_corpus_path)
    node_to_remove_holdout = []
    documents = root.xpath('//tei:TEI', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
    for doc in documents:
        # get document identifier under <teiHeader><fileDesc xml:id="b991b33626">
        local_id = doc.xpath('./tei:teiHeader/tei:fileDesc/@xml:id', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
        if not local_id[0] in doc_holdout:
            node_to_remove_holdout.append(doc)

    for node in node_to_remove_holdout:
        node.getparent().remove(node)
    root.write(softcite_corpus_path.replace(".tei.xml", ".holdout.tei.xml"), pretty_print=True)

    # full holdout set (same format as softcite full corpus, but with all the TEI content and annotations)
    documents = root.xpath('//tei:TEI', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
    for doc in documents:
        # get document identifier under <teiHeader><fileDesc xml:id="b991b33626">
        local_id = doc.xpath('./tei:teiHeader/tei:fileDesc/@xml:id', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
        # retrieve the full TEI xml document
        if local_id == None or len(local_id) == 0:
            print("Warning, identifier not found for a TEI entry")
            continue
        if not local_id[0] in map_identifiers:
            print("Warning, identifier not found in map_identifiers:". local_id[0])
            continue
        local_identifiers = map_identifiers[local_id[0]]
        corpus_doc_body = doc.xpath('.//tei:body', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})

        # forbidden content is all the content of the <rs> of type software name, publisher and url (of length>2)
        # if such content is present in the paragraph considered for addition, we skip it to avoid adding false 
        # positive
        forbidden_contents = doc.xpath('.//tei:rs[@type="software" or @type="publisher" or @type="url"]', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
        forbidden_content = []
        for content_node in forbidden_contents:
            content = content_node.xpath("string()") 
            if len(content.strip()) > 2:
                forbidden_content.append(content)

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
            local_paragraph.tail = "\n            "
            corpus_doc_body[0].append(local_paragraph)

        local_figDesc = local_doc.xpath('//tei:figDesc', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
        for local_paragraph in local_paragraphs:
            if has_forbidden_content(local_paragraph, forbidden_content) or is_matching_a_paragraph(local_paragraph, corpus_doc_body[0]):
                continue
            local_paragraph.tail = "\n            "
            corpus_doc_body[0].append(local_paragraph)

    # and print the "extended" holdout set
    root.write(softcite_corpus_path.replace(".tei.xml", ".holdout-complete.tei.xml"), pretty_print=True)  

    # finally partition of undersampling resource (the "negative" file)
    root = etree.parse(negative_examples_file_path)
    node_to_remove_holdout = []
    documents = root.xpath('//tei:TEI', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
    for doc in documents:
        # get document identifier under <teiHeader><fileDesc xml:id="b991b33626">
        local_id = doc.xpath('./tei:teiHeader/tei:fileDesc/@xml:id', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
        if not local_id[0] in doc_holdout:
            node_to_remove_holdout.append(doc)

    for node in node_to_remove_holdout:
        node.getparent().remove(node)
    root.write(negative_examples_file_path.replace(".tei.xml", ".holdout.tei.xml"), pretty_print=True)

    root = etree.parse(negative_examples_file_path)
    node_to_remove_working = []
    documents = root.xpath('//tei:TEI', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
    for doc in documents:
        # get document identifier under <teiHeader><fileDesc xml:id="b991b33626">
        local_id = doc.xpath('./tei:teiHeader/tei:fileDesc/@xml:id', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
        if local_id[0] in doc_holdout:
            node_to_remove_working.append(doc)

    for node in node_to_remove_working:
        node.getparent().remove(node)
    root.write(negative_examples_file_path.replace(".tei.xml", ".working.tei.xml"), pretty_print=True)

 
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

