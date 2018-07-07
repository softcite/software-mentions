# GROBID software-mentions module

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

__Work in progress.__

The goal of this GROBID module is to recognize in textual documents and PDF any mentions of softwares,  

As the other GROBID models, the module relies only on machine learning and uses linear CRF. 

## Install, build, run

Building module requires maven and JDK 1.8.  

First install and build the latest development version of GROBID as explained by the [documentation](http://grobid.readthedocs.org).

Copy the module software-mentions as sibling sub-project to grobid-core, grobid-trainer, etc.:
> cp -r software-mentions grobid/

Try compiling everything with:

> cd grobid/software-mentions/

> mvn -Dmaven.test.skip=true clean install

Run some test: 
> cd PATH-TO-GROBID/grobid/software-mentions/

> mvn compile test

**The models have to be trained before running the tests!** - See bellow for training a software model. 

## Start the service

> mvn -Dmaven.test.skip=true jetty:run-war

Demo/console web app is then accessible at ```http://localhost:8080```

Using ```curl``` POST/GET requests:


```
curl -X POST -d "text=The next step is install GROBID version 0.5.1." localhost:8080/processSoftwareText
```

```
curl -GET --data-urlencode "text=Look at GROBID logs." localhost:8080/processSoftwareText
```

## Training and evaluation

### Training only

For training the software model with all the available training data:

```
> cd PATH-TO-GROBID/grobid/software-mentions/

> mvn generate-resources -Ptrain_software
```

The training data must be under ```grobid-astro/resources/dataset/software/corpus```

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

In this mode, by default, 80% of the available data is used for training and the remaining for evaluation. This ratio can be changed by editing the corresponding exec profile in the pom.xml file. 

## Training data
 
... 

## Generation of training data

For generating training data in TEI, based on the current model, from a list of text or PDF files in a input repository, use the following command: 

```
> java -Xmx4G -jar target/software-mentions/-0.5.1-SNAPSHOT.one-jar.jar -gH ../grobid-home -dIn ~/test_software/ -dOut ~/test_software/out/ -exe createTraining
```


## License

GROBID and the grobid software-mentions module are distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 
