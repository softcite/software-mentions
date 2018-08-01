# GROBID software-mentions module

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

__Work in progress.__

The goal of this GROBID module is to recognize in textual documents and PDF any mentions of softwares.   

As the other GROBID models, the module relies only on machine learning and uses linear CRF. 

## Install, build, run

Building module requires maven and JDK 1.8.  

First install and build the latest development version of GROBID as explained by the [documentation](http://grobid.readthedocs.org).

Copy the module software-mentions as sibling sub-project to grobid-core, grobid-trainer, etc.:

> cp -r software-mentions grobid/

Copy the provided pre-trained model in the standard grobid-home path:

> cd grobid/software-mentions/

> mkdir ../grobid-home/models/software

> cp resources/model/model.wapiti ../grobid-home/models/software/

Try compiling everything with:

> mvn -Dmaven.test.skip=true clean install

Run some test: 

> mvn compile test


## Start the service

> mvn -Dmaven.test.skip=true jetty:run-war

Javascript demo/console web app is then accessible at ```http://localhost:8060```

Using ```curl``` POST/GET requests with some text:

```
curl -X POST -d "text=The next step is install GROBID version 0.5.1." localhost:8060/processSoftwareText
```

```
curl -GET --data-urlencode "text=Look at GROBID logs." localhost:8060/processSoftwareText
```

Using ```curl``` POST/PUT requests with a PDF file:

```bash
curl --form input=@./thefile.pdf localhost:8060/annotateSoftwarePDF
```

## Training and evaluation

### Training only

For training the software model with all the available training data:

```
> cd PATH-TO-GROBID/grobid/software-mentions/

> mvn generate-resources -Ptrain_software
```

The training data must be under ```software-mentions/resources/dataset/software/corpus```

### Evaluating only

For evaluating under the labeled data under ```grobid-astro/resources/dataset/software/evaluation```, use the command:

```
>  mvn compile exec:exec -Peval_software
```

### Training and evaluating with automatic corpus split

The following commands will split automatically and randomly the available annotated data (under ```resources/dataset/software/corpus/```) into a training set and an evaluation set, train a model based on the first set and launch an evaluation based on the second set. 

```
>  mvn compile exec:exec -Peval_software_split
```

In this mode, by default, 90% of the available data is used for training and the remaining for evaluation. This ratio can be changed by editing the corresponding exec profile in the `pom.xml` file. 

## Training data
 
The source of training data is the [softcite dataset](https://github.com/howisonlab/softcite-dataset) developed by [James Howison](http://james.howison.name/) Lab at the University of Texas at Austin. The data need to be compiled with actual PDF content prelimiary to training in order to create XML annotated document (MUC conference style). This is done with the following command which takes 3 arguments: 

```
> mvn exec:java -Dexec.mainClass=org.grobid.trainer.AnnotatedCorpusGeneratorCSV -Dexec.args="path/to/the/pdf/repo/ /path/to/csv/softcite-dataset/data/csv_dataset/ resources/dataset/software/corpus/"
```

The path to the PDF repo is the path where the PDF corresponding to the annotated document will be downloaded (done only the first time). For instance:


```
> mvn exec:java -Dexec.mainClass=org.grobid.trainer.AnnotatedCorpusGeneratorCSV -Dexec.args="/home/lopez/tools/softcite-dataset/pdf/ /home/lopez/tools/softcite-dataset/data/csv_dataset/ resources/dataset/software/corpus/"
```

The compiled XML training files will be written in the standard GROBID training path for the softwate recognition model under `grobid/software-mentions/resources/dataset/software/corpus/`.


## Analysis of traninign data consistency

A Python 3.* script is available under `script/` to analyse XML training data and spot possible unconsistencies to review. To launch the script: 

```
> python3 script/consistency.py _absolute_path_to_training_directory_
```

For instance: 


```
> python3 script/consistency.py /home/lopez/grobid/software-mentions/resources/dataset/software/corpus/
```

See the description of the output directly in the header of the `script/consistency.py` file.


## Generation of training data

For generating training data in XML/TEI, based on the current model, from a list of text or PDF files in a input repository, use the following command: 

```
> java -Xmx4G -jar target/software-mentions/-0.5.1-SNAPSHOT.one-jar.jar -gH ../grobid-home -dIn ~/test_software/ -dOut ~/test_software/out/ -exe createTraining
```


## License

GROBID and the grobid software-mentions module are distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 
