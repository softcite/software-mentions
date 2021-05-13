'''
    A script to evaluate the percentage of content filtered when focusing only on 
    certain relevant structures in a text mining perspective. 
    For example, when recognizing software mention, we only want to process article's 
    paragraphs, figure/table captions, section titles, notes, abstract, keywords, and 
    ignore all the rest. This script evaluate the proportion of used/filtered content
    with a set of TEI files provided as argument. 
'''

import os
import argparse
import xml
from lxml import etree
import regex as re
import math

relevant_content = ["//tei:body//tei:p", "//tei:body//tei:note[@place='foot']", "//tei:body//tei:figDesc", "//tei:body//tei:head", "//tei:abstract//tei:p"]

def analyze(tei_corpus_path):
    total_chars = 0
    total_relevant_chars = 0
    nb_files = 0

    for file in os.listdir(tei_corpus_path):
        if file.endswith(".tei.xml"):
            nb_files += 1
            root = etree.parse(os.path.join(tei_corpus_path, file))

            # all content
            all_content = root.xpath('//text()', namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
            for content in all_content:
                content = content.replace("\n", "")
                content = content.replace("\t", "")
                total_chars += len(content)

            # relevant content
            for content_exp in relevant_content:
                all_content = root.xpath(content_exp + "//text()", namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
                for content in all_content:
                    content = content.replace("\n", "")
                    content = content.replace("\t", "")
                    total_relevant_chars += len(content)

                # to be excluded from relevant, all ref expressions
                all_content = root.xpath("//tei:ref//text()", namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
                for content in all_content:
                    content = content.replace("\n", "")
                    content = content.replace("\t", "")
                    total_relevant_chars -= len(content)

    print("total TEI files analyzed:", nb_files)
    print("total content characters:", total_chars)
    print("total relevant characters:", total_relevant_chars)
    print("ratio relevant:", str(total_relevant_chars/total_chars))

if __name__ == "__main__":
    parser = argparse.ArgumentParser( 
        description = "General count information on the full TEI softcite corpus")
    parser.add_argument("--tei-corpus", type=str, help="path to the directory of full text TEI XML files to be used for the analysis")

    args = parser.parse_args()
    tei_corpus_path = args.tei_corpus

    # check path and call methods
    if tei_corpus_path is not None and not os.path.isdir(tei_corpus_path):
        print("the path to the directory of TEI files is not valid: ", tei_corpus_path)
        exit()
    else:
        analyze(tei_corpus_path)

