"""

Simply test well-formedness of XML files for the moment.

"""

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
from lxml import etree

def test(file_path):
    try:
        tree = etree.parse(file_path)
    except Exception as e: 
        print(e)
        print("failed to parse:", file_path)
        return
    print("successfully parsed:", file_path)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Simple test for TEI softcite corpus")
    parser.add_argument("--tei-file", type=str, help="path to a TEI XML file to be tested")
    parser.add_argument("--tei-corpus", type=str, help="path to a directory of TEI XML files to be tested")

    args = parser.parse_args()
    tei_file_path = args.tei_file
    tei_corpus_path = args.tei_corpus

    # check path and call methods
    if tei_file_path is not None and not os.path.isdir(tei_file_path):
        test(tei_file_path)
    elif tei_corpus_path is not None and os.path.isdir(tei_corpus_path):
        for file in os.listdir(tei_corpus_path):
            # test only XML files
            if file.endswith('.xml'):
                test(os.path.join(tei_corpus_path, file))
    else:
        print("invalid path or file")
        exit()

