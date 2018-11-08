# Software mention recognizer in scientific literature

The goal of this component is to recognize in textual documents and in PDF any mentions of softwares with associated attribute information such as number version, author, url or version date.   

## Existing work

Existing work is mainly based on rule-based approach and gazeteers of software names. 

Using the rule-based technique, Duck et al identify software mentions with a precision of 0.58 and recall of 0.68 (Duck, Nenadic, Brass, Robertson, & Stevens, 2013). In a later paper they improve this to 0.80 and 0.64 respectively
(Duck et al., 2016).

Priem and Piwowar (2016) employ a related approach in the Depsy application (http://depsy.org), using preset search phrases to find mentions.

All these efforts rely on researcher intuition to guide the selection and weighting of rules, limiting the ability to
optimize them systematically. Pan et al. (2015) address this limitation by generating the rules automatically, using a machine-learning bootstrapping technique. Their approach sacrifices recall (0.43) but results in greatly improved precision in identifying software mentions (0.94). However, it still relies on bootstrapping from a discreet ruleset.

...

## Description

The recognition of software mentions is an information extraction task similar to NER (Named Entity Recognition), which means that it would best approached with machine learning techniques. Machine learning techniques for NER lead to signifcantly more accurate, more portable (with respect to domains), more reproducible and easier to maintain solutions (reference needed).

Software mention is here implemented as a sequence labelling problem, where the labels applied to sequence of _words_ (named _tokens_ in this context), with indications of attachment for the attribute information (_version number_, _url_, etc.) to the appropriate software name mention. 

The software component is implemented as a Java sub-module of the Open Source tool [GROBID](https://github.com/kermitt2/grobid) to take advantage of the functionalities of GROBID for parsing and automatically structuring PDF, in particular scholar PDF. This approach has several advantages:

- It is possible to apply the software mention recognizer only to relevant structures of the PDF article, ignoring for instance bibliographical sections, figures, formulas, affiliations, page number breaks, etc., with correct reading order, dehyphenized text, and correctly re-composed UTF-8 character. This is what we call __structure-aware document annotation__.

- We can reuse existing training, feature generation and evaluation functionalities of GROBID for Linear CRF (Conditional Random Field), leading to a very fast working implementation with one of the best Machine Learning model for sequence labelling.

- All the processing pipeline is integrated in a single service, which eases maintenance, deployment, portability and reliability.

- As a GROBID module, the recognizer will be able to scale very well with a production-level robustness. This scaling ability is crutial for us because our objective is to process around 10 millions scholar Open Access PDF, an amount which is usually out of reach of research prototypes. GROBID was already used to process more than 10 millions PDF by different users (ResearchGate, INIST-CNRS, Internet Archive, ...). 

For reference, the two other existing similar Open Source tools, [CERMINE](https://github.com/CeON/CERMINE) and [Science-Parse](https://github.com/allenai/science-parse), are 5 to 10 times slower than GROBID on single thread and requires 2 to 4 times more memory, while providing in average lower accuracy (_Tkaczyk and al. 2018, Lipinski and al. 2013_) and more limited structures for the document body (actually ScienceParse v1 and v2 do not address this aspect). 

We used a similar approach for recognizing [astronomical objects](https://github.com/kermitt2/grobid-astro) and [physical quantities](https://github.com/kermitt2/grobid-quantities) in scientific litteratures with satisfactory accuracy (between 80. and 90. f-score) and the ability to scale to several PDF per second on a multi-core machine.

The source of training data is the [softcite dataset](https://github.com/howisonlab/softcite-dataset) developed by [James Howison](http://james.howison.name/) Lab at the University of Texas at Austin. The data are first compiled with actual PDF content to generate XML annotated documents (MUC conference style) which are the actual input of the training process.


## Service and demo

The software mention component offers a REST API consuming text or PDF and delivering results in JSON format (see the [documentation](https://github.com/Impactstory/software-mentions#grobid-software-mentions-module)). 

http://software.science-miner.com is a first demo of the recognizer. It illustrates the ability of the tool to annotate both text and PDF. 

![Example of software mention recognition service on text](images/screen1.png)

In the case of PDF, the service allows the client to exploit the coordinates of the mentions in the PDF for displaying interactive annotations directly on top the PDF layout. 

![Example of software mention recognition service on PDF](images/screen2.png)

The text mining process is thus not limited to populating a database, but also offers the possibility to come back to users and show them in context the mentions of softwares. 
 

## Preliminary results


We present below the current metrics of the software mention model, as produced by the software component. The annotated corpus is divided randomly with 90% for training and 10% for evaluation. We use traditional precision, recall and f1 scores. Token-level evaluation indicates how good the labeling is for each token. Field-level evaluation indicates accuracy for a complete multi-word sequence, including correct attachment of attributes (`creator`, `url`, `version-date`, `version-number`) to the correct `software`.

### Linear CRF approach

Version Aug. 24th 2018 

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

Version Oct. 19th 2018

```
Labeling took: 1821 ms

===== Token-level results =====


label                precision    recall       f1     

<creator>            71.72        71           71.36  
<software>           82.48        50.37        62.54  
<url>                40           66.67        50     
<version-date>       100          6.25         11.76  
<version-number>     87.14        81.77        84.37  

all fields           79.68        64.37        71.21   (micro average)

===== Field-level results =====

label                precision    recall       f1     

<creator>            70.65        59.09        64.36  
<software>           76.61        50.61        60.95  
<url>                25           50           33.33  
<version-date>       100          6.25         11.76  
<version-number>     72.82        69.44        71.09  

all fields           73.92        54.59        62.8    (micro average)

===== Instance-level results =====

Total expected instances:   236
Correct instances:          80
Instance-level recall:      33.9

```

With this current model, 33.9% of the PDF of the evaluation set (213 documents) are entirely correctly annotated. 

Note that we present here simply intermediary results, and final evaluation metrics will be averaged over 10 random annotated corpus segmentations or via a 10-fold approach to match good practice. 


## Deep learning model

We developed a Keras deep learning framework called [DeLFT](https://github.com/kermitt2/delft) (**De**ep **L**earning **F**ramework for **T**ext) for text processing, covering in particular sequence labelling as used in GROBID. This library re-implements the most recent state-of-the-art Deep Learning architectures. 
It re-implements in particular the current state of the art model (BiLSTM-CRF with ELMo embeddings) for NER (_Peters and al. 2018_), with even [slightly improved performance](http://science-miner.com/a-reproducibility-study-on-neural-ner/) on the reference evaluation dataset CoNLL NER 2003.

The training data of GROBID is supported by DeLFT and, as a consequence, any GROBID CRF models can have an equivalent Deep Learning model counterpart. 

The following results have been obtained with a BiLSTM-CRF architecture, using GloVes 300d embeddings, with the same partition between train and test set as the above Oct. 19th 2018 results for CRF. 

```
                  precision    recall  f1-score   support

           <url>     0.3077    0.5000    0.3810         8
      <software>     0.7507    0.7341    0.7423       361
         number>     0.7018    0.7207    0.7111       111
       <creator>     0.6667    0.6022    0.6328        93
           date>     0.6000    0.6000    0.6000         5

all (micro avg.)     0.7170    0.7059    0.7114       578
```

F-score is improved almost 9 points as compared to CRF which is a very significant difference. Given that we are not using without ELMo yet, nor parameter tuning, and other possible other improvements, DL for this task appears particularly strong, in a quite similar way as general NER. 

With a high-end GPU (GTX 1080Ti), speed is 8000 tokens per second, relatively similar to CRF. However, it should be noted that using some more recent sophisticated contextalized embeddings or LM like ELMo, while certainly improving accuracy, will have a very strong impact on runtime, running between 25 and 30 slower than with traditional embeddings following our benchmarking. 

We plan to generate more Deep Learning models for the software mention recognition and benchmark them with the CRF model. We will thus be able to report reliable evaluations for all the best current named entity recognition algorithms and select the highest performing one. The evaluation will cover accuracy, but also processing speed and memory usage, with and without GPU.  

## Next iterations

The above preliminary results are given for information and to illustrate the evaluation functionality integrated in our tool. These results should not be considered significant at this early stage:

- the training data is only partially exploited, due to alignment issues between the annotations and the PDF content, which are currently addressed,

- the quality of the training data is currently the object of the effort of the James Howison Lab. Supervised training is very sensitve to the quality of the training data, and this will automatically improve the accuracy of the model by a large margin,

- no effort at this stage have been dedicated to feature engineering, which is a key aspect of CRF. We need to wait for the improvement of the quality of the training data to address this step,

- some techniques like sub- or over-sampling can be used to optimize accuracy.

Our target is a f-score between 80-90, which would allow us to address the next step of software entity disambiguation and matching in good conditions.

# References

_(Lipinski and al. 2013)_ [Evaluation of Header Metadata Extraction Approaches and Tools for Scientific PDF Documents](http://docear.org/papers/Evaluation_of_Header_Metadata_Extraction_Approaches_and_Tools_for_Scientific_PDF_Documents.pdf). M. Lipinski, K. Yao, C. Breitinger, J. Beel, and B. Gipp, in Proceedings of the 13th ACM/IEEE-CS Joint Conference on Digital Libraries (JCDL), Indianapolis, IN, USA, 2013. 

_(Peters and al. 2018)_ Deep contextualized word representations. Matthew E. Peters, Mark Neumann, Mohit Iyyer, Matt Gardner, Christopher Clark, Kenton Lee, Luke Zettlemoyer, NAACL 2018. [arXiv:1802.05365](https://arxiv.org/abs/1802.05365)

_(Tkaczyk and al. 2018)_ Evaluation and Comparison of Open Source Bibliographic Reference Parsers: A Business Use Case. Tkaczyk, D., Collins, A., Sheridan, P., & Beel, J., 2018. [arXiv:1802.01168](https://arxiv.org/pdf/1802.01168).

