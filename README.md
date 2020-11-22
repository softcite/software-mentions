# GROBID software-mentions module

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

The goal of this GROBID module is to recognize any software mentions in scholar textual documents and PDF. It uses as training data the [softcite dataset](https://github.com/howisonlab/softcite-dataset) developed by [James Howison](http://james.howison.name/) Lab at the University of Texas at Austin. This annotated corpus and the present software text mining component have been developed supported by a grant from the Alfred P. Sloan foundation to [improve credit for research software](https://blog.ourresearch.org/collaborating-635k-grant-improve-credit-research-software/).

As the other GROBID models, the module relies only on state-of-the-art machine learning. The tool can use linear CRF (via [Wapiti](https://github.com/kermitt2/Wapiti) JNI integration) or Deep Learning model such as BiLSTM-CRF, ELMo or fine-tuned transformers BERT (via [DeLFT](https://github.com/kermitt2/delft) JNI integration) and any combination of them. 

A description of the task can be found [here](doc/description.md).

Thanks to its integration in the [GROBID](https://github.com/kermitt2/grobid) framework, the software mention extraction on scholar PDF is:

- __structure-aware__: the extraction is realized on the relevant textual zones, skipping for instance figure content, headnotes, formulas, bibliographical reference section, etc. and exploiting the knowledge of inline reference markers, section and paragraph boundaries, etc. for the textual zones

- __robust__: text stream in PDF is recovered and cleaned with various additional process going beyond traditional pdf-to-text low level PDF extraction tool, for instance line number removal, de-hyphenation, unicode character combination, multi-column support, handling of page/figure breaks, unicode normalization, etc. 

- __combined with bibliographical reference recognition__: the bibliographical reference markers possibly used in combination with the software mention are recognized, attached to the software mention when possible, matched with the full bibliographical reference in the bibliographical section of the article and disambiguated against CrossRef and Unpaywall

- __combined with PDF coordinates__: the bounding boxes of the extracted software mentions, software attributes and attached bibliographical references in the original PDF are provided, in order to create augmented interative PDF to visualize and interact directly with software mentions on the PDF, see the [console demo](https://github.com/ourresearch/software-mentions/#console-web-app)

- __combined with entity disambiguation__: extracted software names are disambiguated in context against software entities in Wikidata via [entity-fishing](https://github.com/kermitt2/entity-fishing)

- __scaling__: as we want to scale to the complete scientific corpus, the process is optimized in runtime and memory usage. We are able to process entirely around 2 PDF per second (including PDF processing and structuring, extractions, bibliographical reference disambiguation against crossref and entity disambiguation against WikiData) on one low/medium cost Ubuntu server, Intel i7-4790 (4 CPU), 4.00 GHz with 16 GB memory (an additional GPU is however necessary when using Deep Learning models and runtime then depends on the DL architecture of choice).

Latest performance (accuracy and runtime) can be found [below](https://github.com/Impactstory/software-mentions#Benchmarking).

## Install, build, run

Building the module requires JDK 1.8 or higher. First install and build the latest development version of GROBID as explained by the [documentation](http://grobid.readthedocs.org).

Under the installed `grobid/` directory, clone the present module software-mentions (it will appear as sibling sub-project to grobid-core, grobid-trainer, etc.):

> cd grobid/

> git clone https://github.com/kermitt2/software-mentions

Copy the provided pre-trained models in the standard grobid-home path:

> ./gradlew copyModels 

Try compiling everything with:

> ./gradlew clean install 

Run some test: 

> ./gradlew test

## Start the service

To start the service:

> ./gradlew run

### Console web app

Javascript demo/console web app is then accessible at ```http://localhost:8060```. From the console and the `RESTfull services` tab, you can process chunk of text (select `ProcessText`) or process a complete PDF document (select `Annotate PDF document`).

![GROBID Software mentions Demo](doc/images/screen5.png)

When processing the PDF of a scientific article, the tool will also identify bibliographical reference markers and, when possible, attach the full parsed bibliographical reference to the identified software entity. In addition, bibliographical references can be resolved via [biblio-glutton](https://github.com/kermitt2/biblio-glutton), providing a unique DOI, and optionally additional identifiers like PubMed ID, PMC ID, etc. and a link to the Open Access full text of the reference work when available (via Unpaywall).

![GROBID Software mentions Demo](doc/images/screen4.png)

### Web API

#### /service/processSoftwareText

Identify the software mentions in text and optionally disambiguate the extracted software mentions against Wikidata.  

|  method   |  request type         |  response type    |  parameters            |  requirement  |  description  |
|---        |---                    |---                |---                     |---            |---            |
| GET, POST | `multipart/form-data` | `application/json` | `text`            | required      | the text to be processed |
|           |                       |                   | `disambiguate` | optional      | `disambiguate` is a string of value `0` (no disambiguation, default value) or `1` (disambiguate and inject Wikidata entity id and Wikipedia pageId) |

Response status codes:

|     HTTP Status code |   reason                                               |
|---                   |---                                                     |
|         200          |     Successful operation.                              |
|         204          |     Process was completed, but no content could be extracted and structured |
|         400          |     Wrong request, missing parameters, missing header  |
|         500          |     Indicate an internal service error, further described by a provided message           |
|         503          |     The service is not available, which usually means that all the threads are currently used                       |

A `503` error normally means that all the threads available to GROBID are currently used for processing concurrent requests. The client need to re-send the query after a wait time that will allow the server to free some threads. The wait time depends on the service and the capacities of the server, we suggest 1 seconds for the `processSoftwareText` service.

Using ```curl``` POST/GET requests with some __text__:

```console
curl -X POST -d "text=We test GROBID (version 0.6.1)." localhost:8060/service/processSoftwareText
```

```console
curl -GET --data-urlencode "text=We test GROBID (version 0.6.1)." localhost:8060/service/processSoftwareText
```

which should return this:

```json
{
    "application": "software-mentions",
    "version": "0.6.1-SNAPSHOT",
    "date": "2020-05-20T22:31+0000",
    "mentions": [
        {
            "software-name": {
                "rawForm": "GROBID",
                "normalizedForm": "GROBID",
                "offsetStart": 8,
                "offsetEnd": 15
            },
            "type": "software",
            "version": {
                "rawForm": "0.6.1",
                "normalizedForm": "0.6.1",
                "offsetStart": 24,
                "offsetEnd": 29
            }
        }
    ],
    "runtime": 1
}
```

Runtimes are expressed in milliseconds. 

#### /service/annotateSoftwarePDF

|  method   |  request type         |  response type       |  parameters         |  requirement  |  description  |
|---        |---                    |---                   |---                  |---            |---            |
| POST      | `multipart/form-data` | `application/json`   | `input`             | required      | PDF file to be processed |
|           |                       |                      | `disambiguate`      | optional      | `disambiguate` is a string of value `0` (no disambiguation, default value) or `1` (disambiguate and inject Wikidata entity id and Wikipedia pageId) |

Response status codes:

|     HTTP Status code |   reason                                               |
|---                   |---                                                     |
|         200          |     Successful operation.                              |
|         204          |     Process was completed, but no content could be extracted and structured |
|         400          |     Wrong request, missing parameters, missing header  |
|         500          |     Indicate an internal service error, further described by a provided message           |
|         503          |     The service is not available, which usually means that all the threads are currently used                       |

A `503` error normally means that all the threads available to GROBID are currently used for processing concurrent requests. The client need to re-send the query after a wait time that will allow the server to free some threads. The wait time depends on the service and the capacities of the server, we suggest 2 seconds for the `annotateSoftwarePDF` service or 3 seconds when disambiguation is also requested.

Using ```curl``` POST request with a __PDF file__:

```console
curl --form input=@./src/test/resources/PMC1636350.pdf --form disambiguate=1 localhost:8060/service/annotateSoftwarePDF
```

For PDF, each entity will be associated with a list of bounding box coordinates relative to the PDF, see [here](https://grobid.readthedocs.io/en/latest/Coordinates-in-PDF/#coordinate-system-in-the-pdf) for more explanation about the coordinate system. 

In addition, the response will contain the bibliographical reference information associated to a software mention when found. The bibliographical information are provided in XML TEI (similar format as GROBID).  


#### /service/isalive

The service check `/service/isalive` will return true/false whether the service is up and running.

### Service admin and usage information

The service provides also an admin console, reachable at <http://yourhost:8071> where some additional checks like ping, metrics, hearthbeat are available.
We recommend, in particular to have a look at the metrics (using the [Metric library](https://metrics.dropwizard.io/3.1.0/getting-started/)) which are providing the rate of execution as well as the throughput of each entry point.

## Configuration

The `software-mention` module inherits the configuration of GROBID. 

The configuration parameters specific to the `software-mention` module can be modified in the file `resources/config/config.yml`:

- to disambiguate the extracted mentions against Wikidata (match the software entity if known by Wikidata), you need to specify an [entity-fishing](https://github.com/kermitt2/entity-fishing) service. For test, the public entity-fishing instance can be used:

```yaml
entityFishingHost: cloud.science-miner.com/nerd
entityFishingPort:
```

for larger scale PDF processing and to take advantage of a more recent Wikidata dump, a local instance of entity-fishing should be installed and used:


```yaml
entityFishingHost: localhost
entityFishingPort: 8090
```

- to select the sequence labelling algorithm to be used, use the config parameter `engine`:

For CRF:

```yaml
engine=wapiti
```

For Deep Learning architectures, indicate `delft` and indicate the installation path of the `DeLFT` library. To install and take advantage of DeLFT, see the installation instructions [here](https://github.com/kermitt2/delft).


```yaml
engine: delft
delftInstall: ../../delft
delftArchitecture: bilstm-crf
delftEmbeddings: glove
```


For further selecting the Deep Learning architecture to be used:

```yaml
engine: delft
delftArchitecture: scibert
```

Possible values are:

- for __BiLSTM-CRF__: `bilstm-crf`

- for __bert-base-en+CRF__: `bert`

- for __SciBERT+CRF__: `scibert`

For __BiLSTM-CRF__ you can further specify the embeddings to be used:

- for using Gloves embeddings (default):

```yaml
delftEmbeddings: glove
```

Other possibilities are `elmo` and `bert`. Note that in the later case, BERT is used to generate contextual embeddings used by the __BiLSTM-CRF__ architecture, in contrast to the usage of a fine-tuned BERT when BERT or SciBERT are selected as `delftArchitecture`.

Note that the default setting is __CRF Wapiti__, which does not require any further installation.


## Benchmarking

The following sequence labelling algorithms have been benchmarked:

-    __CRF__: Conditional Random Fields with custom feature engineering 

-    __BiLSTM-CRF__: Bidirectional LSTM-CRF with Gloves static embeddings

-    __BiLSTM-CRF+features__: Bidirectional LSTM-CRF with Gloves static embeddings including a feature channel, the input features are the same as for the CRF model, excluding word forms

-    __BiLSTM-CRF+ELMo__: Bidirectional LSTM-CRF with Gloves static embeddings and ELMo dynamic embeddings 

-    __BiLSTM-CRF+ELMo+features__: Bidirectional LSTM-CRF with Gloves static embeddings, ELMo dynamic embeddings and including a feature channel, the input features are the same as for the CRF model, excluding word forms

-    __bert-base-en+CRF__: fine tuned standard BERT base model with CRF activation layer, pre-trained on general English text

-    __SciBERT+CRF__: fine tuned BERT base model with CRF activation layer, pre-trained on scientific text 

The CRF implementation is based on a custom fork of [Wapiti](https://github.com/kermitt2/wapiti).
The other algorithms rely on the Deep Learning library [DeLFT](https://github.com/kermitt2/delft).
All are natively integrated in the JVM to provide state-of-the-art performance both in accuracy and runtime. 


### Accuracy of the sequence labeling task

Evaluation made in October 2020.

The results (precision, recall, f-score) for all the models have been obtained using 10-fold cross-validation (average metrics over the 10 folds) at entity-level. We also indicate the best and worst results over the 10 folds in the [complete result page](https://github.com/Impactstory/software-mentions/blob/master/doc/scores-1.2.txt). See [DeLFT](https://github.com/kermitt2/delft) for more details about the models and reproducing all these evaluations. The feature-engineered CRF is based on the [custom Wapiti fork](https://github.com/kermitt2/wapiti) integrated in [GROBID](https://github.com/kermitt2/grobid) and available in the present repository. 

`<software>` label means “software name”. `<publisher>` corresponds usually to the publisher of the software or, more rarely, the main developer. `<version>` corresponds to both version number and version dates, when available. 

#### Summary

|           | `<software>` | `<publisher>` | `<version>` | `<url>`  | **micro-average** |
|---        | ---          | ---           | ---         | ---      |  ---              | 
| **CRF**   |   79.71      |   79.46       |    87.98    |   66.72  |     80.94         | 
|**BiLSTM-CRF**|   78.90   |   82.12       |    84.46    |  46.67   |     79.94         | 
|**BiLSTM-CRF+features**|   80.72 |  84.16 |    86.30    |  47.47   |     81.79         | 
|**BiLSTM-CRF+ELMo** | 82.85 | 88.32     |    86.83      |  77.96   |     84.46         | 
|**BiLSTM-CRF+ELMo+features**| 82.70 | 88.59 |   86.30   |  78.14   |     84.32         | 
|**bert-base-en+CRF**|  79.51  |  77.66      |   82.77   |  67.22   |     79.58         | 
|**SciBERT+CRF**     |  85.52  |  88.06        |   87.31     | 75.47 |     86.05         | 

__f-score__ based on 10-folds cross validation at field level.

#### Detailed scores

|          | CRF ||| BiLSTM-CRF ||| BiLSTM-CRF+ELMo|||
|---       | --- | --- | --- | --- | --- | --- | ---| --- | --- |
|**Labels**| Precision | Recall | f-score | Precision | Recall | f-score | Precision | Recall | f-score|
| `<software>` | 85.87 | 74.39 | 79.71 | 79.07 | 78.77 | 78.90 | 83.49 | 82.27 | 82.85 |
| `<publisher>`  | 84.63 | 74.95 | 79.46  | 85.16 | 79.33 | 82.12 | 90.92 | 85.92 | 88.32 |
| `<version>`  | 90.6 | 85.58 | 87.98 | 83.52 | 85.45 | 84.46 | 88.38 | 85.38 | 86.83 |
| `<url>`  | 72.62 | 62.36 | 66.72 | 43.98 | 50.00 | 46.67 | 72.65 | 85.00 | 77.96|
|**micro-average** | 86.27 | 76.23 | 80.94 | 80.23 | 79.66 | 79.94 | 85.41 | 83.56 | 84.46 |

|              | BiLSTM-CRF+features  |        |        | BiLSTM-CRF+ELMo+features |        |         |
|---           | ---                  | ---    | ---    | ---                      | ---    | ---     | 
|**Labels**    | Precision            | Recall | f-score| Precision          | Recall       | f-score |
| `<software>` | 82.81                | 78.80  | 80.72  | 83.18              | 82.29        | 82.70   |
| `<publisher>`| 87.58                | 81.08  | 84.16  | 91.57              | 85.83        | 88.59   |
| `<version>`  | 86.19                | 86.44  | 86.30  | 88.20              | 84.55        | 86.30   |
| `<url>`      | 44.80                | 50.83  | 47.47  | 72.61              | 85.00        | 78.14   |
|**micro-average** | 83.49            | 80.19  | 81.79  | 85.29              | 83.40        | 84.32   |

Evaluation BERT fine-tuned architectures:

|           | bert-base-en+CRF ||| SciBERT+CRF ||| 
|---        | --- | --- | --- | --- | --- | --- | 
|**Labels** | Precision | Recall | f-score | Precision | Recall | f-score |
| `<software>` | 80.37 | 78.70 | 79.51 | 86.65 | 84.43 | 85.52 | 
| `<publisher>` | 79.81 | 75.67 | 77.66 | 90.89 | 85.42 | 88.06 | 
| `<version>`  | 81.31 | 84.32 | 82.77 | 87.91 | 86.74 | 87.31 |
| `<url>`   | 59.44 | 77.50 | 67.22 | 64.86 | 90.83 | 75.47 | 
|**micro-average** | 79.95 | 79.23 | 79.58 | 86.95 | 85.17 | 86.05 | 

Note that the maximum sequence length is normally 1,500 tokens, except for BERT architectures, which have a limit of 512 for the input sequence length. Tokens beyond 1,500 or 512 are truncated and ignored.  

For this reason, BERT architectures are might be impacted by the input sequence length, which correspond for us to a complete paragraph to annotate. The software attributes can be distributed in more than one sentence, and some key elements introducting a software mention can be spread in the whole paragraph. The number of tokens in a paragraph can go beyond 512, which might degrade the above reported metrics for the transformer models.  

### Runtimes

The following runtimes have been obtained based on a Ubuntu 16.04 server Intel i7-4790 (4 CPU), 4.00 GHz with 16 GB memory. The runtimes for the Deep Learning architectures are based on the same machine with a nvidia GPU GeForce 1080Ti (11 GB). Runtime can be reproduced with the [python script below](#runtime-benchmark).

|CRF ||
|--- | --- |
|threads | tokens/s | 
|1 | 23,685 | 
|2 | 43,281|
|3 | 59,867 | 
|4 | 73,339|
|6 | 92,385 | 
|7 | 97,659|
|8 | 100,879 | 

| BiLSTM-CRF || 
| --- |--- | 
| batch size | tokens/s | 
| 50 | 24,774 | 
| 100 | 28,707| 
| 150 | 30,247|
| 200 | 30,520|

| BiLSTM-CRF+ELMo||
| ---| --- |
| batch size | tokens/s|
| 5 | 271|
| 7 | 365|

| SciBERT+CRF||
| ---| --- |
| batch size | tokens/s|
| 5 | 4,729|
| 6 | 5,060|

Batch size is a parameter constrained by the capacity of the available GPU. An improvement of the performance of the deep learning architecture requires increasing the number of GPU and the amount of memory of these GPU, similarly as improving CRF capacity requires increasing the number of available threads and CPU. We observed that running a Deep Learning architectures on CPU is around 50 times slower than on GPU (although it depends on the amount of RAM available with the CPU, which can allow to increase the batch size significantly). 

For the latest and complete evaluation data, see [here](https://github.com/Impactstory/software-mentions/blob/master/doc/scores-1.2.txt)


## Training and evaluation

### Training only

For training the software model with all the available training data:

```console
> cd PATH-TO-GROBID/grobid/software-mentions/

> ./gradlew train_software 
```

The training data must be under ```software-mentions/resources/dataset/software/corpus```. 



### Training and evaluating with automatic corpus split

The following commands will split automatically and randomly the available annotated data (under ```resources/dataset/software/corpus/```) into a training set and an evaluation set, train a model based on the first set and launch an evaluation based on the second set. 

```console
>  ./gradlew eval_software_split [-Ps=0.8 -PgH=/custom/grobid/home -Pt=10] 
```

In this mode, by default, 90% of the available data is used for training and the remaining for evaluation. This default ratio can be changed with the parameter `-Ps`. By default, the training will use the available number of threads of the machine, but it can also be specified by the parameter `-Pt`. The grobid home can be optionally specified with parameter `-PgH`. By default it will take `../grobid-home`. 

### Evaluation with n-fold

For n-fold evaluation using the available annotated data (under ```resources/dataset/software/corpus/```), use the command:

```console
>  ./gradlew eval_software_nfold [-Pn=10 -PgH=/path/grobid/home -Pt=10]
```

where `Pn` is the parameter for the number of folds, by default 10. Still by default, the training will use the available number of threads of the machine, but it can also be specified by the parameter `-Pt`. The grobid home can be optionally specified with parameter `-PgH`. By default it will take `../grobid-home`. 

### Evaluating only

For evaluating under the labeled data under ```grobid-astro/resources/dataset/software/evaluation``` (fixed "holdout set" approach), use the command:

```console
>  ./gradlew eval_software [-PgH=/path/grobid/home]
```

The grobid home can be optionally specified with parameter `-PgH`. By default it will take `../grobid-home`  


## Training data import

### Assembling the softcite dataset

The source of training data is the [softcite dataset](https://github.com/howisonlab/softcite-dataset) developed by [James Howison](http://james.howison.name/) Lab at the University of Texas at Austin. The data need to be compiled with actual PDF content preliminary to training in order to create XML annotated document (MUC conference style). This is done with the following command which takes 3 arguments: 

```console
> ./gradlew annotated_corpus_generator_csv -Ppdf=/path/input/pdf -Pcsv=path/csv -Poutput=/output/directory
```

The path to the PDF repo is the path where the PDF corresponding to the annotated document will be downloaded (done only the first time). For instance:

```console
> ./gradlew annotated_corpus_generator_csv -Ppdf=/home/lopez/repository/softcite-dataset/pdf/ -Pcsv=/home/lopez/tools/softcite-dataset/data/csv_dataset/ -Poutput=resources/dataset/software/corpus/
```

The compiled XML training files will be written in the standard GROBID training path for the softwate recognition model under `grobid/software-mentions/resources/dataset/software/corpus/`.

### Post-processing for adding provenance information in the corpus XML TEI file

Once the snippet-oriented corpus TEI file is generated, manually reviewed and reconciled, it is possible to re-inject back provenance information (when possible), normalize identifiers, add document entries without mention and segments not aligned with actual article content via GROBID, and filter training articles, with the following command:

```console
> ./gradlew post_process_corpus -Pxml=/path/input/corpus/tei/xml/file -Pcsv=path/csv -Ppdf=path/pdf -Poutput=/output/path/tei/corpus/file
```

For instance 

```console
> ./gradlew post_process_corpus -Pxml=/home/lopez/grobid/software-mentions/resources/dataset/software/corpus/all.clean.tei.xml -Pcsv=/home/lopez/tools/softcite-dataset/data/csv_dataset/ -Ppdf=/home/lopez/tools/softcite-dataset/pdf/ -Poutput=/home/lopez/grobid/software-mentions/resources/dataset/software/corpus/all_clean_post_processed.tei.xml
```

The post-process corpus is a TEI corpus dataset corresponding to the released and delivery format of the Softcite dataset. 

### Inter-Annotator Agreement measures

The import process includes the computation of standard Inter-Annotator Agreement (__IIA__) measures for the documents being annotated by at least two annotators. For the moment, the reported IIA is a percentage agreement measure, with standard error and confidence interval.  

See this nice [tutorial](https://dkpro.github.io/dkpro-statistics/inter-rater-agreement-tutorial.pdf) about IIA. We might need more sophisticated IIA measures than just percentage agreement for more robustness. We plan, in addition to pourcentage agreement, to also cover various IIA metrics from π, κ, and α families using the [dkpro-statistics-agreement](https://dkpro.github.io/dkpro-statistics/) library:

Christian M. Meyer, Margot Mieskes, Christian Stab, and Iryna Gurevych: [DKPro Agreement: An Open-Source Java Library for Measuring Inter-Rater Agreement](https://dkpro.github.io/dkpro-statistics/dkpro-agreement-poster.pdf), in: Proceedings of the 25th International Conference on Computational Linguistics (COLING), pp. 105–109, August 2014. Dublin, Ireland. 

For explanations on these IIA measures, see: 

Artstein, R., & Poesio, M. (2008). [Inter-coder agreement for computational linguistics](https://www.mitpressjournals.org/doi/pdf/10.1162/coli.07-034-R2). Computational Linguistics, 34(4), 555-596.

## Analysis of training data consistency

A Python 3.* script is available under `script/` to analyse XML training data and spot possible unconsistencies to review. To launch the script: 

```console
> python3 scripts/consistency.py _absolute_path_to_training_directory_
```

For instance: 


```console
> python3 scripts/consistency.py /home/lopez/grobid/software-mentions/resources/dataset/software/corpus/
```

See the description of the output directly in the header of the `script/consistency.py` file. 


## Generation of training data

For generating training data in XML/TEI, based on the current model, from a list of text or PDF files in a input repository, use the following command: 

```console
> java -Xmx4G -jar target/software-mentions/-0.5.1-SNAPSHOT.onejar.jar -gH ../grobid-home -dIn ~/test_software/ -dOut ~/test_software/out/ -exe createTraining
```

## Runtime benchmark

A python script is available for benchmarking the services. The main motivation is to evaluation the runtime of the different machine learning models from an end-to-end perspective and on a similar hardware. 

By default, the text content for the benchmark is taken from the xml files from the training/eval directory under `resources/dataset/software/corpus`, to call the script for evaluation the text processing service:

```console
> cd scripts/
> python3 runtime_eval.py
software-mention server is up and running
1000 texts to process
1000 texts to process
317 texts to process
-----------------------------
nb xml files: 1
nb texts: 2317
nb tokens: 962875
-----------------------------
total runtime: 38.769 seconds 
-----------------------------
xml files/s:     0.0258
    texts/s:     59.7642
   tokens/s:     24836.2093
```

In the above example, 24,836 tokens per second is the processing rate of the CRF model with 1 thread (it goes beyond 100K tokens per second with 8 threads). 

optionally you can provide a path to a particular repository of XML files in order to benchmark the text processing processing:

> python3 runtime_eval.py --xml-repo /my/xml/directory/

For benchmarking PDF processing, you need to provide a path to a repository of PDF in order to benchmark PDF processing:

> python3 runtime_eval.py --pdf-repo /the/path/to/the/pdf/directory

By default the config file `./config.json` will be used, but you can also set a particular config file with the parameter `--config`:

> python3 runtime_eval.py --config ./my_config.json

The config file gives the hostname and port of the software-mention service to be used. Default values are service default values (localhost:8060).

Last but not least, you can indicate the number of thread to be used for querying the service in parallel:

> python3 runtime_eval.py --threads 10

The default value is 1, so there is no parallelization in the call to the service by default.  

Tested with python 3.*


## License

GROBID and the grobid software-mentions module are distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 
