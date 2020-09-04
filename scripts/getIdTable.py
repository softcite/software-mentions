"""
    Extract all the identifiers associated to articles and output them in a csv file
"""

import os
import argparse
import ntpath
import csv
import xml
from xml.sax import make_parser, handler

class TEICorpusHandler(xml.sax.ContentHandler):
    """ 
    TEI XML SAX handler for reading the TEI corpus file corresponding to the Softcite corpus  
    """

    # working variables
    accumulated = ''
    is_origin_file = False
    origin_file = None
    nb_file = 0
    doc_id = None
    current_identifier_type = None

    def __init__(self, output_path):
        xml.sax.ContentHandler.__init__(self)
        self.output_path = output_path
        self.current_identifier = {}

    def startElement(self, name, attrs):
        self.accumulated = ''
        if name == 'idno':
            if attrs.getLength() != 0:
                if "type" in attrs:
                    if attrs.getValue("type") == 'origin':
                        # this is where we get the PDF file name
                        self.is_origin_file = True
                    self.current_identifier_type = attrs.getValue("type")
        if name == 'TEI' or name == 'tei':
            # reinit working variable
            self.is_origin_file = False
            self.nb_file += 1
            self.doc_id = None
            self.current_identifier = {}
        if name == "fileDesc":
            if attrs.getLength() != 0:
                if "xml:id" in attrs:
                    self.doc_id = attrs.getValue("xml:id")
                    self.current_identifier["id"] = attrs.getValue("xml:id")
        if name == 'teiCorpus':
            self.output_file = open(self.output_path, "w", newline='')
            self.writer = csv.writer(self.output_file)
            self.writer.writerow(["id", "origin", "DOI", "PMID", "PMCID"])

    def endElement(self, name):
        # print("endElement '" + name + "'")
        if name == 'idno':
            if self.is_origin_file:
                self.is_origin_file = False
                self.origin_file = self.accumulated.strip()
            self.current_identifier[self.current_identifier_type] = self.accumulated.strip()
        if name == "body":
            # write entry
            #print(self.current_identifier)
            doi = ""
            if "DOI" in self.current_identifier:
                doi = self.current_identifier["DOI"]
            pmid = ""
            if "PMID" in self.current_identifier:
                pmid = self.current_identifier["PMID"]
            pmcid = ""
            if "PMC" in self.current_identifier:
                pmcid = self.current_identifier["PMC"]
            self.writer.writerow([self.doc_id, self.origin_file, doi, pmid, pmcid])
        if name == 'teiCorpus':
            self.output_file.close()

        self.accumulated = ''

    def characters(self, content):
        self.accumulated += content

    def clear(self): # clear the accumulator for re-use
        self.accumulated = ""


def inject_corpus_annotations(tei_corpus_path, output_path):
    parser = make_parser()
    handler = TEICorpusHandler(output_path)
    parser.setContentHandler(handler)
    parser.parse(tei_corpus_path)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Extract all the identifiers associated to articles and output them in a csv file")
    parser.add_argument("--tei-corpus", type=str, 
        help="path to the Softcite TEI corpus file corresponding to the curated annotated corpus to inject")
    parser.add_argument("--output", type=str, 
        help="path to an output directory where to write the CSV file")

    args = parser.parse_args()
    tei_corpus_path = args.tei_corpus
    output_path = args.output

    # check path and call methods
    if tei_corpus_path is None or not os.path.isfile(tei_corpus_path):
        print("the path to the TEI corpus file is not valid: ", tei_corpus_path)

    inject_corpus_annotations(tei_corpus_path, output_path)
