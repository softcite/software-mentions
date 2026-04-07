# Softcite software mention recognition service

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Demo](https://img.shields.io/website-up-down-green-red/https/huggingface.co/spaces/lfoppiano/softcite-software-mentions)]([https://huggingface.co/spaces/lfoppiano/softcite-software-mentions](https://huggingface.co/spaces/lfoppiano/softcite-software-mentions))
[![Docker Hub](https://img.shields.io/docker/pulls/grobid/software-mentions.svg)](https://hub.docker.com/r/grobid/software-mentions "Docker Pulls")

The goal of this GROBID module is to recognize any software mentions in scholar textual documents, publisher XML and PDF. It uses as training data the [Softcite Dataset](https://github.com/howisonlab/softcite-dataset) developed by [James Howison](http://james.howison.name/) Lab at the University of Texas at Austin. This annotated corpus and the present software text mining component have been developed supported by a grant from the Alfred P. Sloan foundation to [improve credit for research software](https://blog.ourresearch.org/collaborating-635k-grant-improve-credit-research-software/).

_Code with paper_: the [following article is available in CC-BY](https://github.com/ourresearch/software-mentions/raw/master/doc/afp1085-lopezA-CC-BY.pdf): 

```
Patrice Lopez, Caifan Du, Johanna Cohoon, Karthik Ram, and James Howison. 2021. 
Mining Software Entities in Scientific Literature: Document-level NER for an Extremely Imbalance and Large-scale Task. 
In Proceedings of the 30th ACM International Conference on Information and Knowledge Management (CIKM ’21), 
November 1–5, 2021, QLD, Australia. https://doi.org/10.1145/3459637.3481936
[Best Applied Research Paper Award runner-up]
```

For more recent evaluations and a description of a use case in production to monitor Open Science in France, see:

```
Aricia Bassinet, Laetitia Bracco, Anne L'Hôte, Eric Jeangirard, Patrice Lopez, et al. 2023. 
Large-scale Machine-Learning analysis of scientific PDF for monitoring the production and the openness of research data and software in France. ⟨hal-04121339v3⟩ 
https://hal.science/hal-04121339v3
```

As the other GROBID models, the module relies only on state-of-the-art machine learning. The tool can use linear CRF (via [Wapiti](https://github.com/kermitt2/Wapiti) JNI integration) or Deep Learning model such as BiLSTM-CRF, ELMo or fine-tuned transformers BERT, e.g. SciBERT and LinkBERT (via [DeLFT](https://github.com/kermitt2/delft) JNI integration) and any combination of them. 

Thanks to its integration in the [GROBID](https://github.com/kermitt2/grobid) framework, the software mention extraction on scholar PDF is:

- __structure-aware__: the extraction is realized on the relevant textual zones, skipping for instance figure content, headnotes, formulas, bibliographical reference section, etc. and exploiting the knowledge of inline reference markers, section and paragraph boundaries, etc. for the textual zones

- __robust__: text stream in PDF is recovered and cleaned with various additional process going beyond traditional pdf-to-text low level PDF extraction tool, for instance line number removal, de-hyphenation, unicode character combination, multi-column support, handling of page/figure breaks, unicode normalization, removal of manuscript line numbers, etc. 

- __combined with bibliographical reference recognition__: the bibliographical reference markers possibly used in combination with the software mention are recognized, attached to the software mention when possible, matched with the full bibliographical reference in the bibliographical section of the article and disambiguated against CrossRef and Unpaywall

- __combined with PDF coordinates__: in case the input is a PDF, the bounding boxes of the extracted software mentions, software attributes and attached bibliographical references in the original PDF are provided, in order to create augmented interative PDF to visualize and interact directly with software mentions on the PDF, see the [console demo](https://github.com/ourresearch/software-mentions/#console-web-app)

- __combined with entity disambiguation__: extracted software names are disambiguated in context against software entities in Wikidata via [entity-fishing](https://github.com/kermitt2/entity-fishing)

- __combined with software usage, creation and sharing__ characterizations: based on the different mentions of a software in a document, each mention is classified to estimate if the software is likely used in the research work, a creation part of the research work and if it is publicly shared. A consolidation step then provides at document-level these characterization for each mentioned software.

- __scaling__: as we want to scale to the complete scientific corpus, the process is optimized in runtime and memory usage. We are able to process entirely around 2 PDF per second with the CRF model (including PDF processing and structuring, extractions, bibliographical reference disambiguation against crossref and entity disambiguation against WikiData) on one low/medium cost Ubuntu server, Intel i7-4790 (4 CPU), 4.00 GHz with 16 GB memory. Around 0.5 PDF per second is processed when using the fine-tuned SciBERT model, the best performing model - an additional GPU is however necessary when using Deep Learning models and runtime, depending on the DL architecture of choice.

Latest performance (accuracy and runtime) can be found in the most recent cited publication above, and more model comparisons [below](https://github.com/ourresearch/software-mentions#benchmarking-of-the-sequence-labeling-task).

## Demo

A public demo of the service is available at the following address: https://cloud.science-miner.com/software/

The [web console](https://github.com/ourresearch/software-mentions#console-web-app) allows you to test the processing of text or of a full scholar PDF. The component is developed targeting complete PDF, so the output of a PDF processing will be richer (attachment, parsing and DOI-matching of the bibliographical references appearing with a software mention, coordinates in the PDF of the mentions, document level propagation of mentions). The console displays extracted mentions directly on the PDF pages (via PDF.js), with infobox describing when possible Wikidata entity linking and full reference metadata (with Open Access links when found via Unpaywall).  

This demo is only provided for test, without any guaranties regarding the service quality and availability. If you plan to use this component at scale or in production, you need to install it locally (see how to deploy a [docker image](https://github.com/ourresearch/software-mentions#docker-image)). 

Note: **The demo run with the CRF model** to reduce the computational load, as the server is used for other demos and has no GPU (for cost reasons). For significantly more accurate results (see the [benchmarking](https://github.com/ourresearch/software-mentions#benchmarking-of-the-sequence-labeling-task)), sciBERT/LinkBERT models are required, the Docker image being the easiest way to achieve this (fine-tuned transformer models are included and used by default in the image). 

## The Softcite Dataset

For sampling, training and evaluation of the sequence labeling model and additional attribute attachment mechanisms, we use the Softcite dataset, a gold standard manually annotated corpus of 4,971 scholar articles, available on Zenodo (version 2.0): 

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.7995565.svg)](https://doi.org/10.5281/zenodo.7995565)


More details on the Softcite dataset can be found in the following publication:

```
Du, C, Cohoon, J, Lopez, P, Howison, J. Softcite dataset: A dataset of software mentions 
in biomedical and economic research publications. J Assoc Inf Sci Technol. (JASIST) 2021; 1–15. 
https://doi.org/10.1002/asi.24454
```

The latest version of the dataset is maintained on the following GitHub repository: https://github.com/softcite/softcite_dataset_v2

Original development was carried out at https://github.com/howisonlab/softcite-dataset

## Docker image

It is recommended to use the Docker image for running the service. The best Deep Learning models are included and are used by default by this image. 
To use a Docker image via [docker HUB](https://hub.docker.com/r/grobid/software-mentions), pull the image (around 11GB) as follow: 

```bash
docker pull grobid/software-mentions:0.8.1
```

After pulling or building the Docker image, you can now run the `software-mentions` service as a container:

```bash
>  docker run --rm --gpus all -it --ulimit core=0 -p 8060:8060 grobid/software-mentions:0.8.1
```

The build image includes the automatic support of GPU when available on the host machine via the parameter `--gpus all` (with automatic recognition of the CUDA version). The support of GPU is only available on Linux host machine. If no GPU are available on your host machine, just remove the `--gpus all` parameter, but usage of GPU is recommended for best runtime:

```bash
>  docker run --rm -it --ulimit core=0 -p 8060:8060 grobid/software-mentions:0.8.1
```

To specify to use only certain GPUs (see the [nvidia container toolkit user guide](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/user-guide.html#gpu-enumeration) for more details):

```bash
> docker run --rm --gpus '"device=1,2"' -it --init --ulimit core=0 -p 8060:8060 grobid/software-mentions:0.8.1
```

Note that starting for convenience the container with option `--ulimit core=0` avoids having possible core dumped inside the container, which can happen overwise due to the very rare crash of the PDF parsing C++ component. Starting the container with parameter `-it` allows to interact with the docker process, which is of limited use here, except conveniently stopping the docker container with control-c.  

The `software-mentions` service is available at the default host/port `localhost:8060`, but it is possible to map the port at launch time of the container as follow:

```bash
> docker run --rm --gpus all -it --init --ulimit core=0 -p 8080:8060 grobid/software-mentions:0.8.1
```

In this image, the best deep learning models are used by default. The selection of models can be modified, for example to use faster models or requiring less GPU memory. To modify the configuration without rebuilding the image - for instance rather use the CRF model, it is possible to mount a modified config file at launch as follow: 

```bash
> docker run --rm --gpus all -it --init --ulimit core=0 -p 8060:8060 -v /home/lopez/grobid/software-mentions/resources/config/config.yml:/opt/grobid/software-mentions/resources/config/config.yml:ro  grobid/software-mentions:0.8.1
```

As an alternative, a docker image for the `software-mentions` service can be built with the project Dockerfile to match the current master version. The complete process is as follow: 

- copy the `Dockerfile.software` at the root of the GROBID installation:

```bash
~/grobid/software-mentions$ cp ./Dockerfile.software ..
```

- from the GROBID root installation (`grobid/`), launch the docker build:

```bash
> docker build -t grobid/software-mentions:0.8.2-SNAPSHOT --build-arg GROBID_VERSION=0.8.2-SNAPSHOT --file Dockerfile.software .
```

Building the Docker image takes several minutes: installing GROBID, software-mentions, a complete Python Deep Learning environment based on [DeLFT](https://github.com/kermitt2/delft) and deep learning models downloaded from the internet (one fine-tuned model with a BERT layer has a size of around 400 MB). The resulting image is thus very large, around 8GB, due to the deep learning resources and models. 

## Install, build, run

The easiest way to deploy and run the service is to use the Docker image, see previous section. If you're courageous or would like to contribute to the development, this section presents the install and build process.

Building the module requires JDK 1.8 or higher (tested up to Java 15). First install and build the latest development version of GROBID as explained by the [documentation](http://grobid.readthedocs.org), together with [DeLFT](https://github.com/kermitt2/delft) for Deep Learning model support. An installation of [Pub2TEI](https://github.com/kermitt2/Pub2TEI) is also necessary to process a variety of publisher XML formats (including for example JATS).

Under the installed and built `grobid/` directory, clone the present module software-mentions (it will appear as sibling sub-project to grobid-core, grobid-trainer, etc.):

```console
cd grobid/
git clone https://github.com/softcite/software-mentions
cd software-mentions
```

Copy the provided pre-trained models into the standard grobid-home path:

```console
./gradlew copyModels 
```

Install larger models (fine-tuned transformers, currently the best performing one, total 1.5 GB size and too large to be stored in the GitHub repo), they need to be downloaded and installed with the following command:

```console
./gradlew installModels
```

Try compiling everything with:

```console
./gradlew clean install 
```

Run some test: 

```console
./gradlew test
```

To start the service:

```console
./gradlew run
```

## Console web app

Javascript demo/console web app is then accessible at ```http://localhost:8060```. From the console and the `RESTfull services` tab, you can process chunk of text (select `ProcessText`) or process a complete PDF document (select `Annotate PDF document`). 

![GROBID Software mentions Demo](doc/images/screen5.png)

When processing text, it is possible to examine the JSON output of the service with the `Response` tab:

![GROBID Software mentions Demo](doc/images/screen6.png)

When processing the PDF of a scientific article, the tool will also identify bibliographical reference markers and, when possible, attach the full parsed bibliographical reference to the identified software entity. In addition, bibliographical references can be resolved via [biblio-glutton](https://github.com/kermitt2/biblio-glutton), providing a unique DOI, and optionally additional identifiers like PubMed ID, PMC ID, etc. and a link to the Open Access full text of the reference work when available (via Unpaywall).

![GROBID Software mentions Demo](doc/images/screen4.png)

Software entity linking against Wikidata is realized by [entity-fishing](https://github.com/kermitt2/entity-fishing) and provides when possible Wikidata ID and English Wikipedia page ID. The web console allows to interact and view the entity information in the infobox:

![GROBID Software mentions Demo](doc/images/screen7.jpeg)

## Python client for the Softcite software mention recognition service

To exploit the Softcite software mention recognition service efficiently (concurrent calls) and robustly, a Python client is available [here](https://github.com/softcite/software_mentions_client).

If you want to process a directory of PDF and/or XML documents, this is the best and simplest solution: deploy a Dokcer image of the server and use this client. 

## Tutorial

A tutorial is available at https://github.com/softcite/tutorials/blob/master/process_all_of_plos.md describing how to process the "All of PLOS" collection, step by step. You can apply the same approach for any collection of XML or PDF scientific articles.  

## JSON format for the extracted software mention

The resulting software mention extractions include many attributes and information. These extractions follow the [JSON format documented on this page](https://github.com/ourresearch/software-mentions/blob/master/doc/annotation_schema.md). 


## Softcite software mention extraction from the CORD-19 publications

This dataset is the result of the extraction of software mentions from the set of publications of the CORD-19 corpus (https://allenai.org/data/cord-19) by the Softcite software recognizer using SciBERT fine-tuned model: https://zenodo.org/record/5235661 


## Web API

### /service/processSoftwareText

Identify the software mentions in text and optionally disambiguate the extracted software mentions against Wikidata.  

|  method   |  request type         |  response type     |  parameters            |  requirement  |  description  |
|---        |---                    |---                 |---                     |---            |---            |
| GET, POST | `multipart/form-data` | `application/json` | `text`            | required      | the text to be processed |
|           |                       |                    | `disambiguate` | optional      | `disambiguate` is a string of value `0` (no disambiguation, default value) or `1` (disambiguate and inject Wikidata entity id and Wikipedia pageId) |

Response status codes:

|     HTTP Status code |   reason                                               |
|---                   |---                                                     |
|         200          |     Successful operation.                              |
|         204          |     Process was completed, but no content could be extracted and structured |
|         400          |     Wrong request, missing parameters, missing header  |
|         500          |     Indicate an internal service error, further described by a provided message           |
|         503          |     The service is not available, which usually means that all the threads are currently used                       |

A `503` error normally means that all the threads available to Softcite service are currently used for processing concurrent requests. The client need to re-send the query after a wait time that will allow the server to free some threads. The wait time depends on the service and the capacities of the server, we suggest 1 seconds for the `processSoftwareText` service.

Using ```curl``` POST/GET requests with some __text__:

```console
curl -X POST -d "text=We test GROBID (version 0.7.1)." localhost:8060/service/processSoftwareText
```

```console
curl -GET --data-urlencode "text=We test GROBID (version 0.7.1)." localhost:8060/service/processSoftwareText
```

which should return this:

```json
{
    "application": "software-mentions",
    "version": "0.7.1",
    "date": "2022-09-10T07:02+0000",
    "mentions": [{
        "software-name": {
            "rawForm": "GROBID",
            "normalizedForm": "GROBID",
            "offsetStart": 8,
            "offsetEnd": 14
        },
        "type": "software",
        "version": {
            "rawForm": "0.7.1",
            "normalizedForm": "0.7.1",
            "offsetStart": 24,
            "offsetEnd": 29
        },
        "context": "We test GROBID (version 0.7.1).",
        "mentionContextAttributes": {
            "used": {
                "value": true,
                "score": 0.9999960660934448
            },
            "created": {
                "value": false,
                "score": 2.384185791015625E-7
            },
            "shared": {
                "value": false,
                "score": 1.1920928955078125E-7
            }
        },
        "documentContextAttributes": {
            "used": {
                "value": true,
                "score": 0.9999960660934448
            },
            "created": {
                "value": false,
                "score": 2.384185791015625E-7
            },
            "shared": {
                "value": false,
                "score": 1.1920928955078125E-7
            }
        }
    }],
    "runtime": 242
}
```

Runtimes are expressed in milliseconds. 

### /service/annotateSoftwarePDF

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

A `503` error normally means that all the threads available to Softcite service are currently used for processing concurrent requests. The client need to re-send the query after a wait time that will allow the server to free some threads. The wait time depends on the service and the capacities of the server, we suggest 2 seconds for the `annotateSoftwarePDF` service or 3 seconds when disambiguation is also requested.

Using ```curl``` POST request with a __PDF file__:

```console
curl --form input=@./src/test/resources/PMC1636350.pdf --form disambiguate=1 localhost:8060/service/annotateSoftwarePDF
```

For PDF, each entity will be associated with a list of bounding box coordinates relative to the PDF, see [here](https://grobid.readthedocs.io/en/latest/Coordinates-in-PDF/#coordinate-system-in-the-pdf) for more explanation about the coordinate system. 

In addition, the response will contain the bibliographical reference information associated to a software mention when found. The bibliographical information are provided in XML TEI (similar format as GROBID).  

### /service/annotateSoftwareXML

The softcite software mention service can extract software mentions with sentence context information from a variety of publisher XML formats, including not only JATS, but also a dozen of mainstream publisher native XML (Elsevier, Nature, ScholarOne, Wiley, etc.). See [Pub2TEI](https://github.com/kermitt2/Pub2TEI) for the list of supported formats. Each call with an XML file (non TEI XML) will involve a transformation of the XML file into a TEI XML file, which will slow down the overall process. This additional time (a few seconds) is due to the loading and compilation of the style sheets that need to be performed for every calls.  

|  method   |  request type         |  response type       |  parameters         |  requirement  |  description  |
|---        |---                    |---                   |---                  |---            |---            |
| POST      | `multipart/form-data` | `application/json`   | `input`             | required      | XML file to be processed |
|           |                       |                      | `disambiguate`      | optional      | `disambiguate` is a string of value `0` (no disambiguation, default value) or `1` (disambiguate and inject Wikidata entity id and Wikipedia pageId) |

Response status codes:

|     HTTP Status code |   reason                                               |
|---                   |---                                                     |
|         200          |     Successful operation.                              |
|         204          |     Process was completed, but no content could be extracted and structured |
|         400          |     Wrong request, missing parameters, missing header  |
|         500          |     Indicate an internal service error, further described by a provided message           |
|         503          |     The service is not available, which usually means that all the threads are currently used                       |

A `503` error normally means that all the threads available to Softcite service are currently used for processing concurrent requests. The client need to re-send the query after a wait time that will allow the server to free some threads. The wait time depends on the service and the capacities of the server, we suggest 2 seconds for the `extractSoftwareXML` service or 3 seconds when disambiguation is also requested.

Using ```curl``` POST request with a __XML file__:

```console
curl --form input=@./src/test/resources/PMC3130168.xml --form disambiguate=1 localhost:8060/service/annotateSoftwareXML
```

### /service/annotateSoftwareTEI

The softcite software mention service will extracts software mentions with sentence context information from TEI XML files directly, without then the need of further transformation as for the other publisher XML formats (see above). The process will thus be much faster and should preferably used if possible.  

|  method   |  request type         |  response type       |  parameters         |  requirement  |  description  |
|---        |---                    |---                   |---                  |---            |---            |
| POST      | `multipart/form-data` | `application/json`   | `input`             | required      | TEI XML file to be processed |
|           |                       |                      | `disambiguate`      | optional      | `disambiguate` is a string of value `0` (no disambiguation, default value) or `1` (disambiguate and inject Wikidata entity id and Wikipedia pageId) |

Response status codes:

|     HTTP Status code |   reason                                               |
|---                   |---                                                     |
|         200          |     Successful operation.                              |
|         204          |     Process was completed, but no content could be extracted and structured |
|         400          |     Wrong request, missing parameters, missing header  |
|         500          |     Indicate an internal service error, further described by a provided message           |
|         503          |     The service is not available, which usually means that all the threads are currently used                       |

A `503` error normally means that all the threads available to Softcite service are currently used for processing concurrent requests. The client need to re-send the query after a wait time that will allow the server to free some threads. The wait time depends on the service and the capacities of the server, we suggest 2 seconds for the `extractSoftwareXML` service or 3 seconds when disambiguation is also requested.

Using ```curl``` POST request with a __XML file__:

```console
curl --form input=@./src/test/resources/PMC3130168.tei.xml --form disambiguate=1 localhost:8060/service/annotateSoftwareTEI
```

### /service/isalive

The service check `/service/isalive` will return true/false whether the service is up and running.

### /service/config/conceptBaseUrl

Return the concept service base URL derived from the configured entity-fishing host/port (or a public default if not configured). This helps the frontend decide whether to call the external service directly or use the backend proxy when CORS blocks direct calls.

| method | request type | response type     | parameters | requirement | description                          |
|--------|---------------|-------------------|------------|------------|--------------------------------------|
| GET    | -             | application/json  | -          | -          | Returns `{ "conceptBaseUrl": "..." }` |

Response status codes:

| HTTP Status code | reason                         |
|------------------|--------------------------------|
| 200              | Successful operation           |
| 500              | Internal service error         |

### /service/kb/concept/{identifier}

Proxy a concept lookup to the URL derived from entityFishingHost/Port. Use this from the browser to avoid CORS issues. If host/port are not set, it falls back to `https://cloud.science-miner.com/nerd/service/kb/concept`.

| method | request type | response type    | parameters             | requirement | description                                   |
|--------|---------------|------------------|------------------------|------------|-----------------------------------------------|
| GET    | -             | application/json | `identifier` (path)    | required   | Concept identifier (Wikipedia page id)        |
|        |               |                  | `lang` (query)         | optional   | Language code (e.g., `en`)                    |

Response status codes:

| HTTP Status code | reason                         |
|------------------|--------------------------------|
| 200              | Successful operation           |
| 400              | Missing/invalid parameters     |
| 502              | Upstream concept service error |

## Configuration

The `software-mention` module inherits the configuration of GROBID. 

The configuration parameters specific to the `software-mention` module can be modified in the file `resources/config/config.yml`:

- entityFishingHost / entityFishingPort: Host and port for the entity-fishing service used for concept data. The concept endpoint is derived as:
  - If port is 443 (or 8443) or the host already includes an https scheme, the base is https; otherwise http.
  - Final concept base URL used by the UI/proxy: `<scheme>://<host>[:port-if-in-host]/nerd/service/kb/concept`
  - If these are not set, the system falls back to the public endpoint `https://cloud.science-miner.com/nerd/service/kb/concept`.
