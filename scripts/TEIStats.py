'''
    Produce some basic count information from the full TEI corpus: 

    - one full text TEI file with software annotations per PDF, produced by command 
    annotated_corpus_generator_csv
    
    - only file with extension .software-mention.xml are considered

    A report is generated with total number of tokens, total number of annotated tokens,
    and total annotated tokens per annotation fields
'''

import os
import argparse
import ntpath
import xml
import regex as re
from xml.sax import make_parser, handler
from collections import OrderedDict
import pysbd

segmenter = pysbd.Segmenter(language='en')

# software recognizer analyzer regex
DELIMITERS = " \n\r\t(（[ ^%‰°•*,:;?.!/)）-–−‐«»„=≈<>+~\"“”‘’'`$®]*\u2666\u2665\u2663\u2660\u00A0";
reger_delimiters = '([' + '|'.join(map(re.escape, DELIMITERS)) + '])'
REGEX = "(?<=[a-zA-Z])(?=\d)|(?<=\d)(?=\D)";

class TEIContentHandler(xml.sax.ContentHandler):
    # working variables
    accumulated = ''
    current_annotation_type = None
    has_annotation = False
    open_paragraph = False
    paragraph_content = ''

    def __init__(self):
        xml.sax.ContentHandler.__init__(self)
        self.stats = init_stats()
        self.stats['total_document'] = 1

    def startElement(self, name, attrs):
        if self.accumulated != '' and name in ["ref", "rs"]:
            tokens = _tokenize(self.accumulated)
            self.stats['total_tokens'] += len(tokens)

            if self.open_paragraph:
                self.paragraph_content += self.accumulated

        if name == 'rs':
            if attrs.getLength() != 0:
                if attrs.getValue("type") != None and len(attrs.getValue("type"))>0:
                    self.current_annotation_type = attrs.getValue("type")
        
        if name == "p":
            self.has_annotation = False
            self.open_paragraph = True
            self.paragraph_content = ''

        self.accumulated = ''

    def endElement(self, name):
        if name in ["head", "note", "figDesc", "abstract", "ref", "p"]:
            tokens = _tokenize(self.accumulated)
            self.stats['total_tokens'] += len(tokens)
            #sentences = segmenter.segment(self.accumulated)
            #self.stats['total_sentences'] += len(sentences)
        
        #if name in ["head"]:
        #    self.stats['total_sentences'] += 1

        if name == "p":
            self.stats['total_paragraphs'] += 1
            
            if len(tokens) > 1:
                sentences = segmenter.segment(self.paragraph_content + self.accumulated)
                self.stats['total_sentences'] += len(sentences)

            if self.has_annotation:
                self.stats['total_paragraphs_with_annotation'] += 1
                # to fix, which sentences have annotations?
                #self.stats['total_sentences_with_annotation'] += 1

            self.has_annotation = False
            self.open_paragraph = False
            self.paragraph_content = ''

        if name == "rs":
            self.stats['total_annotations'] += 1
            tokens = _tokenize(self.accumulated)
            # annotation type
            if self.current_annotation_type != None:
                if self.current_annotation_type == 'software':
                    self.stats['total_software_names'] += 1
                    self.stats['total_software_tokens'] += len(tokens)
                    self.has_annotation = True
                elif self.current_annotation_type == 'version':
                    self.stats['total_versions'] += 1
                    self.stats['total_versions_tokens'] += len(tokens)
                    self.has_annotation = True
                elif self.current_annotation_type == 'url':
                    self.stats['total_urls'] += 1
                    self.stats['total_urls_tokens'] += len(tokens)
                    self.has_annotation = True
                elif self.current_annotation_type == 'publisher' or self.current_annotation_type == 'creator':
                    self.stats['total_publishers'] += 1
                    self.stats['total_publishers_tokens'] += len(tokens)
                    self.has_annotation = True
                self.current_annotation_type = None

        if name == "rs" or name == "ref":
            if self.open_paragraph:
                self.paragraph_content += self.accumulated
        self.accumulated = ''

    def characters(self, content):
        self.accumulated += content


def _tokenize(text):
    '''
    Tokenize following the softcite recognizer analyzer 
    '''
    tokens = []
    coarse_tokens = re.split(reger_delimiters, text)
    for coarse_token in coarse_tokens:
        fine_tokens = re.split(REGEX, coarse_token)
        for fine_token in fine_tokens:
            # note: we exclude spaces as convention
            if fine_token != ' ':
                tokens.append(fine_token)
    return tokens

def count_analysis_tei_file(tei_file):
    parser = make_parser()
    handler = TEIContentHandler()
    parser.setContentHandler(handler)
    print(tei_file)
    parser.parse(tei_file)
    local_stats = handler.stats

    return local_stats

def count_analysis_tei_corpus(tei_corpus_path):
    global_stats = init_stats()
    for file in os.listdir(tei_corpus_path):
        if file.endswith(".software-mention.xml"):
            print(file)
            local_stats = count_analysis_tei_file(os.path.join(tei_corpus_path, file))
            merge_stats(global_stats, local_stats)

    # report
    print("\n")
    for field in global_stats:
        print(field, ":", str(global_stats[field]))
    total_annotation_token = global_stats['total_software_tokens'] + global_stats['total_versions_tokens'] + \
                             global_stats['total_urls_tokens'] + global_stats['total_publishers_tokens']

    print("total annotation tokens:", str(total_annotation_token))

    print("\npercentage paragraphs with annotation / total paragraph:", _round(100*global_stats['total_paragraphs_with_annotation']/global_stats['total_paragraphs'],4))

    print("\npercentage annotation tokens / total tokens:", _round(100*total_annotation_token/global_stats['total_tokens'],4))
    print("percentage software tokens / total tokens:", _round(100*global_stats['total_software_tokens']/global_stats['total_tokens'],4))
    print("percentage version tokens / total tokens:", _round(100*global_stats['total_versions_tokens']/global_stats['total_tokens'],4))
    print("percentage publisher tokens / total tokens:", _round(100*global_stats['total_publishers_tokens']/global_stats['total_tokens'],4))
    print("percentage url tokens / total tokens:", _round(100*global_stats['total_urls_tokens']/global_stats['total_tokens'],4))

def init_stats():
    stats = OrderedDict()
    stats['total_document'] = 0 
    stats['total_paragraphs'] = 0 
    stats['total_paragraphs_with_annotation'] = 0 
    stats['total_sentences'] = 0
    #stats['total_sentences_with_annotation'] = 0
    stats['total_tokens'] = 0 
    stats['total_annotations'] = 0 
    stats['total_software_names'] = 0 
    stats['total_software_tokens'] = 0 
    stats['total_versions'] = 0 
    stats['total_versions_tokens'] = 0 
    stats['total_urls'] = 0 
    stats['total_urls_tokens'] = 0 
    stats['total_publishers'] = 0
    stats['total_publishers_tokens'] = 0
    return stats

def merge_stats(global_stats, additional_stats):
    # fields always present via common init
    for field in global_stats:
        global_stats[field] += additional_stats[field]

def _round(value, decimals):
    return str(round(value, decimals))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "General count information on the full TEI softcite corpus")
    parser.add_argument("--tei-corpus", type=str, help="path to a directory of TEI XML files to analyze")

    args = parser.parse_args()
    tei_corpus_path = args.tei_corpus

    # check path and call methods
    if tei_corpus_path is not None and not os.path.isdir(tei_corpus_path):
        print("the path to the directory of TEI files is not valid: ", xml_corpus_path)
        exit()
    elif tei_corpus_path is not None:
        count_analysis_tei_corpus(tei_corpus_path)

