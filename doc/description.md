# Software mention recognizer in scientific literature

The goal of this component is to recognize in textual documents and PDF any mentions of softwares with the associated attribute information such as number version, author, url or version date.   

## Description

The recognition of software mentions is an information extraction task similar to NER (Named Entity Recognition). It is implemented as a sequence labelling problem, where the labels applied to sequence of _words_ (named _tokens_ in this context), with indication of attachment for the attribute information (version number, url, etc.) to the appropriate software name mention. 

The software component is implemented as a Java sub-module of the Open Source tool [GROBID](https://github.com/kermitt2/grobid) to take advantage of the functionalities of GROBID for parsing and automatically structuring PDF, in particular scholar PDF. This approach has several advantages:

- It is possible to apply the software mention recognizer only to relevant structures of the PDF article, ignoring for instance bibliographical references, figures, formula, affiliations, page number breaks, etc., with correct reading order, dehyphenized text, and correctly re-composed UTF-8 character. This is what we call _structure-aware document annotation_.

- We can reuse existing training, feature generation and evaluation functionalities of GROBID for Linear CRF (Conditional Random Field), leading to a very fast working implementation with one of the best Machine Learning model for sequence labelling.

- All the processing pipeline is integrated in a single service, which ease maintenance, deployment, portability and reliability.

- As a GROBID module, the recognizer will be able to scale very well with a production-level robustness. This scaling ability is crutial for us because our goal to process around 10 millions scholar Open Access PDF, an amount which is usually out of reach of research prototypes. GROBID was already used to process more than 10 millions PDF by different users (ResearchGate, INIST-CNRS, Internet Archive, ...). 

For reference, the two other similar Open Source tools, CERMINE and ScienceParse, are 5 to 10 times slower than GROBID on single thread and requires 3 to 4 times more memory, while providing in average significantly lower accuracy () and more limited structures for the document body (actually ScienceParse v1 and v2 do not address this aspect). 

We used a similar approach for recognizing [astronomical objects](https://github.com/kermitt2/grobid-astro) and [physical quantities](https://github.com/kermitt2/grobid-quantities) in scientific litteratures with good accuracy (between 80. and 90. f-score) and the ability to scale to several PDF per second on a multi-core machine.

The source of training data is the [softcite dataset](https://github.com/howisonlab/softcite-dataset) developed by [James Howison](http://james.howison.name/) Lab at the University of Texas at Austin. The data are first compiled with actual PDF content to generate XML annotated documents (MUC conference style) which are the actual input of the training process.


## Service and demo

The software mention component offers a REST API consuming text or PDF and delivering results in JSON format (see the [documentation](https://github.com/Impactstory/software-mentions#grobid-software-mentions-module)). 

http://software.science-miner.com is a first demo of the recognizer. It illustrates the ability of the tool to annotate both text and PDF. 

![Example of software mention recognition service on text](images/screen1.png)

In the case of PDF, GROBID allows the client to exploit the coordinates of the mentions in the PDF for displaying interactive annotations directly on top the PDF layout. 

![Example of software mention recognition service on PDF](images/screen2.png)

The text mining process is thus not limited to populating a database, but also offers the possibility to come back to users and show them in context the mentions of softwares. 
 

## Preliminary results


We present below the current metrics of the software mention model, as produced by the software component. The annotated corpus is divided randomly with 90% for training and 10% for evaluation. We use traditional precision, recall and f1 scores. Token-level evaluation indicates how good the labeling is for each token. Field-level evaluation indicates accuracy for a complete multi-word sequence, including correct attachment of attributes (`creator`, `url`, `version-date`, `version-number`) to the correct `software`.


```
===== Token-level results =====

label                precision    recall       f1     

<creator>            58.8         60.35        59.57  
<software>           77.67        51.39        61.86  
<url>                66.67        93.62        77.88  
<version-date>       100          22.22        36.36  
<version-number>     85.53        69.52        76.7   

all fields           74.73        60.77        67.03   (micro average)

===== Field-level results =====

label                precision    recall       f1     

<creator>            61.64        52.33        56.6   
<software>           73.43        48.25        58.24  
<url>                60           75           66.67  
<version-date>       100          22.22        36.36  
<version-number>     71.74        60           65.35  

all fields           70.71        51.15        59.36   (micro average)
```

With this current model, 27.4% of the PDF are entirely correctly annotated. 

Note we present here simply intermediary results, and final evaluation metrics will be averaged over 10 different annotated corpus segmentation or via a 10-fold approach to match good practice. 

## Deep learning model

We developed a Keras deep learning framework called [DeLFT](https://github.com/kermitt2/delft) (**De**ep **L**earning **F**ramework for **T**ext) for text processing, covering in particular sequence labelling as used in GROBID. This library re-implements the most recent state-of-the-art Deep Learning architectures. 
It re-implements in particular the current state of the art model (BiLSTM-CRF with ELMo embeddings) for NER (Peters and al. 2018), with even slightly improved performance on the reference evaluation dataset CoNLL NER 2003.

The training data of GROBID is supported by DeLFT and, as a consequence, any GROBID CRF models can have an equivalent Deep Learning model counterpart. 

We plan to create different Deep Learning models for the software mention recognition and benchmark them with the CRF model. We will thus be able to report reliable evaluations for all the best current named entity recognition algorithms and select the highest performing one. The evaluation will cover accuracy, but also processing speed and memory usage, with and without GPU.  

## Next iterations

The above preliminary results are given for information and to illustrate the evaluation functionality integrated in our tool. These results should not be considered significant at this early stage:

- the training data is only partially exploited, due to alignment issues between the annotations and the PDF content, which are currently addressed,

- the quality of the training data is currently the object of the effort of the James Howison Lab. Supervised training is very sensitve to the quality of the training data, and this will automatically improve the accuracy of the model by a large margin,

- no effort at this stage have been dedicated to feature engineering, which is a key aspect of CRF. We need to wait for the completion of the current effort for improving the training data quality to address this step,

- some techniques like sub- or over-sampling can be used to optimize accuracy.

Our target is a f-score between 80-90, which would allow us to address the next step of software entity disambiguation and matching in good conditions.
