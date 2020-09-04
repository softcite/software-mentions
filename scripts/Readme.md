# Some python scripts for analyzing and manipulating the corpus data

## Analysis of training data consistency

 Python 3.* script to analyse XML training data and spot possible unconsistencies to review. To launch the script: 

```console
> python3 consistency.py _absolute_path_to_training_directory_
```

All the `*.xml` files in the `_absolute_path_to_training_directory_` will be analuzed. For instance for the Softcite corpus files: 


```console
> python3 consistency.py ../../data/corpus/
```

For more details, see the description of the output directly in the header of the `consistency.py` file. 


## Generate some JSON working formats

To simplify the usage of TEI XML and annotated documents, some simplified lossy JSON formats are produced by the following scripts.

### Generate TEI XML for a set of PDF

Given a set of PDF files, for instance the PDF of the Softcite corpus, GROBID cam extract and structure automatically their content to facilitate text mining and corpus annotations. 

* install and start a Grobid server

* install the python client

> git clone https://github.com/kermitt2/grobid-client-python

> cd grobid-client-python

* convert PDF files, the following command for instance will convert the PDF under the `--input` path and write TEI XML results under the `--output` path. using a concurrency of 6 parallel conversion (to adapt according to the number of available threads on your machine): 

> python3 grobid-client.py --input ~/softcite-dataset/pdf/  --output ~/softcite-dataset/tei/ --n 6 processFulltextDocument 


### Conversion of TEI XML into lossy JSON 

The target JSON format is relatively similar to the CORD-19 format, without the bibliographical data. The idea is capture only what is required for using human annotation tools from the rich TEI XML files. 

```
usage: TEI2LossyJSON.py [-h] [--tei-file TEI_FILE] [--tei-corpus TEI_CORPUS]
                        [--output OUTPUT]

Convert a TEI XML file into CORD-19-style JSON format

optional arguments:
  -h, --help            show this help message and exit
  --tei-file TEI_FILE   path to a TEI XML file to convert
  --tei-corpus TEI_CORPUS
                        path to a directory of TEI XML files to convert
  --output OUTPUT       path to an output directory where to write the
                        converted TEI XML file, default is the same directory
                        as the input file

```

For instance, converting the set of around 5000 Softcite corpus TEI XML files produced by Grobid from the PDF (using typically a client like the [GROBID python client](https://github.com/kermitt2/grobid-client-python)):

> python3 TEI2LossyJSON.py --tei-corpus ~/tools/softcite-dataset/tei/ --output ../../data/json_raw/

In these JSON files, the segmentation in paragraphs as present in the TEI XML files are preserved. 

### Adding the Softcite annotations into the JSON lossy format

Given a set of JSON files comverted from TEI XML corresponding to the articles selected for the Softcite dataset, the following script will inject Softcate corpus manual annotations into the JSON files as span annotations with offset relative to sentence level segments. 

Install the dependency for sentence segmentation (using pysbd, a pragmatic-segmenter port to Python, see https://github.com/diasks2/pragmatic_segmenter) and for string distance (`textdistance` package):

> pip3 install -r requirements.txt

```
usage: corpus2JSON.py [-h] [--tei-corpus TEI_CORPUS] [--json-repo JSON_REPO]
                      [--output OUTPUT]

Inject Softcite corpus manual annotations into fulltext in lossy JSON format

optional arguments:
  -h, --help            show this help message and exit
  --tei-corpus TEI_CORPUS
                        path to the Softcite TEI corpus file corresponding to
                        the curated annotated corpus to inject
  --json-repo JSON_REPO
                        path to the directory of JSON files converted from TEI
                        XML produced by GROBID, where to inject the Softcite
                        corpus annotations
  --output OUTPUT       path to an output directory where to write the
                        enriched JSON file(s)
```

Having produced the JSON files as indicated above (under `~/tmp/`), we can inject the Softcite corpus curated annotations into the JSON as follow:

> python3 corpus2JSON.py  --tei-corpus ~/tools/softcite-dataset/data/corpus/softcite_corpus.tei.xml --json-repo ../../data/json_raw/ --output ../../data/json_goldstandard


The script realign the annotations from the excerpts present in the corpus file into the complete JSON document and segment the paragraphs into sentences. The added annotations are present in each JSON sentence elements introduced by the `entity_spans` key.

```json
{
    "text": "Images were contrast enhanced based on visual inspection, and individual nuclei were manually delineated in Photoshop 7.0 (Adobe, San Jose, CA), with each nucleus saved in a separate image file. ",
    "section": "Image acquisition and FISH analysis",
    "entity_spans": [
        {
            "start": 108,
            "end": 117,
            "type": "software",
            "rawForm": "Photoshop",
            "resp": "#annotator12",
            "used": true,
            "id": "c6ecc44fa8-software-0"
        },
        {
            "start": 118,
            "end": 121,
            "type": "version",
            "rawForm": "7.0",
            "resp": "#annotator12",
            "id": "#c6ecc44fa8-software-0"
        },
        {
            "start": 123,
            "end": 128,
            "type": "publisher",
            "rawForm": "Adobe",
            "resp": "#curator",
            "id": "#c6ecc44fa8-software-0"
        }
    ]
}
```

__Note:__ to preserve the sentence element order, python scripts must use `OrderedDict` and not usual `dict`, e.g. for loading a json file:

```
with open(os.path.join(path_json_repo, file)) as jsonfile:
    json_doc = json.load(jsonfile, object_pairs_hook=OrderedDict)
``` 

The loaded `json_doc` will be of type `OrderedDict` and the order of sentences in the document will be preserved. 


### Adding automatic annotations into any JSON lossy format files

Given a set of JSON files comverted from TEI XML corresponding to any selection of articles, the following script will inject automatic annotations into the JSON files as span annotations with offset relative to sentence level segments based on two methods:

+ a basic term lookup using a list of software names, an annotation being introduced for each term matching (aka dictionary look-up),

+ using the existing Softcite software mention service, relying on the existing machine learning models to annotate new text.

In case the text are provided at paragraph level (`"level": "paragraph"`), the text will be segmented into sentence. If it is already at sentence level (`"level": "sentence"`), no additional segmentation is done. 

> pip3 install -r requirements.txt

```
usage: enrichJSON.py [-h] [--method METHOD] [--json-repo JSON_REPO]
                     [--output OUTPUT] [--config CONFIG]

Inject automatic software mention annotations into fulltext in lossy JSON
format

optional arguments:
  -h, --help            show this help message and exit
  --method METHOD       method for producing the annotations
  --json-repo JSON_REPO
                        path to the directory of JSON files converted from TEI
                        XML produced by GROBID, where to inject the automatic
                        annotations
  --output OUTPUT       path to an output directory where to write the
                        enriched JSON file(s)
  --config CONFIG       path to the config file, default is ./config.json

```

The config file `config.json` contains the path to the whitelist/stoplist of software names to consider and the connection information to the software-mention service. 

> python3 enrichJSON.py --method whitelist --json-repo ../../data/json_goldstandard --output ../../data/json_whitelist

Annotations are added with `"resp": "whitelist"` following this method. 

Using the service method, after having started the software-mention recognizer service:

> python3 enrichJSON.py --method service --json-repo --json-repo ../../data/json_raw/ --output ../../data/json_service  

Annotations are added with `"resp": "service"` following this method. 

When the indicated method is none, `--method none`, no extra annotations are added and the text is simply converted to sentence-level text. 
