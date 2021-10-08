"""

This modest python script uses the medline converted dump into Crossref format to retrieve the missing 
full metadata records for the Softcite corpus. 

Note: in the future biblio-glutton will also cover medline/pudmed metadata

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
import gzip
import concurrent.futures
import urllib3


def collect_missing_metadata(fail_path, medline_path, output):
    # get the identifiers corresponding to failing cases with the softcite corpus identifier
    failed_map = {}
    reverse_failed_map = {}
    failed_list = []
    failed_all = []
    with open(fail_path) as fp:
        lines = fp.readlines()
        for line in lines:
            line = line.replace("\n", "")
            pieces = line.split("\t")
            softcite_identifier = pieces[0]

            if not softcite_identifier in failed_all:
                failed_all.append(softcite_identifier)

            failed_identifies = pieces[1]        
            id_pieces = failed_identifies.split(" ")
            for id_piece in id_pieces:
                id_piece = id_piece.strip()
                if len(id_piece) == 0:
                    continue
                failed_map[id_piece] = softcite_identifier
                if softcite_identifier in reverse_failed_map:
                    reverse_failed_map[softcite_identifier].append(id_piece)
                else:
                    reverse_failed_map[softcite_identifier] = [id_piece]
                if not id_piece in failed_list:
                    failed_list.append(id_piece)

    print("we try to recover", len(failed_list), "identifiers")

    # iterate on the entries... very slow but we have time :)
    '''
    for file in os.listdir(medline_path):
        if file.endswith(".json.gz"):
            print(file)
            # this is jsonl
            with gzip.open(os.path.join(medline_path, file),'rt') as f:
                for line in f:
                    try:
                        entry = json.loads(line)
                    except:
                        print(line)
                        return
                    # check the identifiers
                    local_match = False
                    failed_softcite_identifier = None
                    if "pmcid" in entry:
                        if entry["pmcid"] in failed_list:
                            local_match = True
                            failed_softcite_identifier = failed_map[entry["pmcid"]]
                            print("local match", entry["pmcid"], failed_softcite_identifier)

                    if "DOI" in entry:
                        if entry["DOI"] in failed_list:
                            local_match = True
                            failed_softcite_identifier = failed_map[entry["DOI"]]
                            print("local match", entry["DOI"], failed_softcite_identifier)

                    # if there is a match, so we save the entry with the provided softcite identifier
                    if local_match and failed_softcite_identifier != None:
                        match_path = os.path.join(output, failed_softcite_identifier+".json")
                        with open(match_path, "w") as out_f:
                            out_f.write(line)
    '''

    # report last remaining identifiers still not resolved
    with open(os.path.join(output, "failed_all.txt"), "w") as failed_a:
        for failed_identifier in failed_all:
            failed_id_path = os.path.join(output, failed_identifier+".json")
            if not os.path.isfile(failed_id_path):
                field_ids = ''
                first = True
                for local_id in reverse_failed_map[failed_identifier]:
                    if first:
                        first = False
                    else:
                        field_ids += ' '
                    field_ids += local_id
                failed_a.write(failed_identifier + "\t" + field_ids + "\n")



if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description = "Collect the available full metadata records of the articles of the Softcite corpus")
    parser.add_argument("--fail", type=str, help="path to file listing failing documents for metadata lookup")
    parser.add_argument("--medline", type=str, help="path to the medline dump in Crossref json format")
    parser.add_argument("--output", type=str, help="path to the directory where to write the full records")

    args = parser.parse_args()
    fail_path = args.fail
    medline_path = args.medline
    output = args.output

    # check path
    if fail_path is None or os.path.isdir(fail_path):
        print("the path to the fail file is not valid: ", fail_path)
    elif output is None or not os.path.isdir(output):
        print("output directory is not valid: ", output)
    elif medline_path is None or not os.path.isdir(medline_path):
        print("medline dump directory is not valid: ", medline_path)
    else:
        collect_missing_metadata(fail_path, medline_path, output)


