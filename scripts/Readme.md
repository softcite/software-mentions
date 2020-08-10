# Some python scripts for analyzing and manipulating the corpus data. 

## Analysis of training data consistency

 Python 3.* script to analyse XML training data and spot possible unconsistencies to review. To launch the script: 

```console
> python3 scripts/consistency.py _absolute_path_to_training_directory_
```

For instance: 


```console
> python3 scripts/consistency.py /home/lopez/grobid/software-mentions/resources/dataset/software/corpus/
```

See the description of the output directly in the header of the `script/consistency.py` file. 


## Generate some JSON working formats

To simplify the usage of TEI XML and annotated documents, some degraded lossy JSON formats are produced by the following scripts.

### Conversion of TEI XML into lossy JSON 

The JSON format is similar to the CORD-19 format, without the bibliographical data. The idea is capture only what is required for using human annotation tools. 

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

> python3 TEI2LossyJSON.py --tei-corpus /home/lopez/tools/softcite-dataset/tei/ --output /home/lopez/tools/softcite-dataset/json/

### Adding the Softcote annotations into the JSON lossy format

Having produced the JSON files as indicated above, we can inject the Softcite corpus curated annotations into the JSON as follow:

> python3 corpus2JSON.py  --tei-corpus ../resources/dataset/software/corpus/softcite_corpus.tei.xml --json-repo /home/lopez/tools/softcite-dataset/json/ --output /home/lopez/tools/softcite-dataset/new

The script realign the annotations from the excerpts present in the corpus file into the complete JSON document. The added annotations are present in each JSON paragraph elements introduced by the `entity_spans` key.

```
{
    "section": "statistical analysis",
    "text": "In order to test the heterogeneity of pooled HR, Cochran's Q-test and Higgins I 2 statistics were performed. P\ue02c0.05 was considered statistically significant. Random-effects model was used to calculate pooled HR when between-study heterogeneity was revealed (P\ue02c0.05), and fixed-effects model was conducted when between-study heterogeneity did not reach the statistical significance (P\ue02e0.05). Subgroup analysis, sensitive analysis, and meta-regression were used to investigate the sources of heterogeneity. Publication bias was assessed by using Begg's test and Egger's test. 32,33 STATA version 12.0 (Stata Corporation, College Station, TX, USA) was used to perform all the analyses.",
    "ref_spans": [
        {
            "type": "bibr",
            "start": 574,
            "text": "32",
            "end": 576
        }
    ],
    "entity_spans": [
        {
            "type": "software",
            "resp": "#annotator26",
            "used": true,
            "id": "af84a43adb-software-3",
            "start": 580,
            "rawForm": "STATA",
            "end": 585
        },
        {
            "type": "version",
            "resp": "#annotator26",
            "id": "#af84a43adb-software-3",
            "start": 594,
            "rawForm": "12.0",
            "end": 598
        },
        {
            "type": "publisher",
            "resp": "#curator",
            "id": "#af84a43adb-software-3",
            "start": 600,
            "rawForm": "Stata Corporation",
            "end": 617
        }
    ]
},
```
