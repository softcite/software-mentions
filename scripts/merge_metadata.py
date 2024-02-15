import gzip
import sys
import os
import shutil
import json

"""
For a given document, inject into the software mention file (*.software.json), the 
document-level metadata file (obtained via CrossRef or biblio-glutton, *.json or 
*.json.gz) under the json attribute 'metadata'. 

This script needs to be  adapted to the source and location of document-level metadata.
"""

# change below to indicate the path to the software annotation repository files 
# and the path to the document metadata files, note that it might be the same 
# directory in practice considering that software annotation file is saved along
# the PDf file that is often coming with the metadata file (as produced by example
# with bibli_glutton_harvester)
directory_softcite = "data"
directory_metadata = "data"

# whether the json document metadata file is gzipped or not 
is_gzipped = False

for root, directories, filenames in os.walk(directory_softcite):
    for filename in filenames:
        if filename.endswith(".software.json"):
            print(os.path.join(root,filename))
            the_json = open(os.path.join(root,filename)).read()
            try:
                jsonObject = json.loads(the_json)
            except:
                print("the json parsing of the following file failed: ", os.path.join(root,filename))
                continue

            local_id = None
            if not 'id' in jsonObject:
                ind = filename.find(".")
                if ind != -1:
                    local_id = filename[:ind]
                    jsonObject['id'] = local_id
            else:
                local_id = jsonObject['id']

            if local_id == None:
                continue

            # open corresponding metadata file
            if is_gzipped:
                if filename.endswith(".jats.software.json"):
                    metadata_filename = filename.replace(".jats.software.json", ".json.gz")
                elif filename.endswith(".latex.software.json"):
                    metadata_filename = filename.replace(".latex.software.json", ".json.gz")
                else:
                    metadata_filename = filename.replace(".software.json", ".json.gz")
                metadata_root = root.replace(directory_softcite, directory_metadata)
                if not os.path.exists(os.path.join(metadata_root, metadata_filename)):
                    print("did not find document metadata file: " + metadata_filename)

                the_metadata_json = gzip.open(os.path.join(metadata_root, metadata_filename)).read()
            else:
                if filename.endswith(".jats.software.json"):
                    metadata_filename = filename.replace(".jats.software.json", ".json")
                elif filename.endswith(".latex.software.json"):
                    metadata_filename = filename.replace(".latex.software.json", ".json")
                else:
                    metadata_filename = filename.replace(".software.json", ".json") 
                metadata_root = root.replace(directory_softcite, directory_metadata)
                if not os.path.exists(os.path.join(metadata_root, metadata_filename)):
                    print("did not find document metadata file: " + metadata_filename)

                the_metadata_json = open(os.path.join(metadata_root, metadata_filename)).read()

            try:
                metadataJsonObject = json.loads(the_metadata_json)
            except:
                print("the json parsing of the following file failed: ", os.path.join(metadata_root, metadata_filename))
                continue

            if not 'metadata' in jsonObject:
                jsonObject['metadata'] = {}
                jsonObject['metadata']['id'] = local_id

            for field in metadataJsonObject:
                jsonObject['metadata'][field] = metadataJsonObject[field]

            #print(jsonObject)

            # update softcite json file
            with open(os.path.join(root,filename), "w") as fp:
                json.dump(jsonObject, fp)

