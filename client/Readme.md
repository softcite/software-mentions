# GROBID software mention recognition client

Python client for using the GROBID software mention recogntion service. It can be applied to 

* individual PDF files

* recursively to a directory, processing all the encountered PDF 

* to a collection of documents harvested by https://github.com/kermitt2/biblio-glutton-harvester, with the benefit of re-using the collection manifest for injectng metadata and keeping track of progress. The collection can be stored locally or on a S3 storage. 


## Requirements

The client has been tested with Python 3.5 and 3.6. 

## Install

> cd grobid/software-mention/python/

It is advised to setup first a virtual environment to avoid falling into one of these gloomy python dependency marshlands:

> virtualenv --system-site-packages -p python3 env

> source env/bin/activate

Install the dependencies, use:

> pip3 install -r requirements.txt


## Usage and options

```
usage: software_mention_client.py [-h] [--data-path DATA_PATH]
                                  [--config CONFIG] [--reprocess] [--reset]
                                  [--file-in FILE_IN] [--file-out FILE_OUT]
                                  [--repo-in REPO_IN]

GROBID Software Mention recognition client

optional arguments:
  -h, --help            show this help message and exit
  --data-path DATA_PATH
                        path to the JSON dump file created by biblio-glutton-
                        harvester
  --config CONFIG       path to the config file, default is ./config.json
  --reprocess           Reprocessed failed PDF
  --reset               Ignore previous processing states, and re-init the
                        annotation process from the beginning
  --file-in FILE_IN     A PDF input file to be processed by the GROBID
                        software mention recognizer
  --file-out FILE_OUT   Path to output the software mentions in JSON format,
                        extracted from the PDF file-in
  --repo-in REPO_IN     Path to directory of PDf files to be processed by the
                        GROBID software mention recognizer
```


### Processing local PDF files

For processing a single file., the resulting json being written as file at the indicated output path:

> python3 software_mention_client.py --file-in toto.pdf --file-out toto.json

For processing recursively a directory of PDF files, the results will be:

* written to a mongodb server and database indicated in the config file

* *and* in the directory of PDF files, as json files, together with each processed PDF

> python3 software_mention_client.py --repo-in /mnt/data/biblio/pmc_oa_dir/

The default config file is `./config.json`, but could also be specified via the parameter `--config`: 

> python3 software_mention_client.py --repo-in /mnt/data/biblio/pmc_oa_dir/ --config ./my_config.json


### Processing a collection of PDF harvested by biblio-glutton-harvester stored locally

[biblio-glutton-harvester](https://github.com/kermitt2/biblio-glutton-harvester) creates a collection manifest as a LMDB database to keep track of the harvesting of large collection of files. Storage of the resource can be located on a local file system or on a AWS S3 storage. The `software-mention` client can use the local data repository produced by [biblio-glutton-harvester](https://github.com/kermitt2/biblio-glutton-harvester) and in particular its collection manifest to process these harvested documents. 

* locally:

> python3 software_mention_client.py --data-path /mnt/data/biblio-glutton-harvester/data/

`--data-path` indicates the path to the repository of data harvested by [biblio-glutton-harvester](https://github.com/kermitt2/biblio-glutton-harvester).

The resulting JSON files will be enriched by the metadata records of the processed PDF and will be stored together with each processed PDF in the data repository. 

## License and contact

Distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). The dependencies used in the project are either themselves also distributed under Apache 2.0 license or distributed under a compatible license. 

Main author and contact: Patrice Lopez (<patrice.lopez@science-miner.com>)
