# Software mention recognizer in scientific literature

The goal of this component is to recognize in textual documents and in PDF any mentions of software with associated attribute information such as number version, author, url or version date.   

## Existing works

Existing works are mainly relying on rule-based approaches and gazeteers of software names. 

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

- We can experiment with and use in production most of the modern sequence labelling Deep Learning algorithms in a transparent and efficient manner via python [DeLFT](https://github.com/kermitt2/delft) native integration. 

For reference, the two other existing similar Open Source tools, [CERMINE](https://github.com/CeON/CERMINE) and [Science-Parse](https://github.com/allenai/science-parse), are 5 to 10 times slower than GROBID on single thread and requires 2 to 4 times more memory, while providing in average lower accuracy (_Tkaczyk and al. 2018, Lipinski and al. 2013_) and more limited structures for the document body (actually ScienceParse v1 and v2 do not address this aspect). 

We used a similar approach for recognizing [astronomical objects](https://github.com/kermitt2/grobid-astro) and [physical quantities](https://github.com/kermitt2/grobid-quantities) in scientific litteratures with satisfactory accuracy (between 80. and 90. f-score) and the ability to scale to several PDF per second on a multi-core machine.

The source of training data is the [softcite dataset](https://github.com/howisonlab/softcite-dataset) developed by [James Howison](http://james.howison.name/) Lab at the University of Texas at Austin. The data are first compiled with actual PDF content to generate XML annotated documents (MUC conference style) which are the actual input of the training process.


## Service and demo

The software mention component offers a REST API consuming text or PDF and delivering results in JSON format (see the [documentation](https://github.com/Impactstory/software-mentions#grobid-software-mentions-module)). 

http://software.science-miner.com is a first demo of the recognizer. It illustrates the ability of the tool to annotate both text and PDF. 

![Example of software mention recognition service on text](images/screen1.png)

In the case of PDF, the service allows the client to exploit the coordinates of the mentions in the PDF for displaying interactive annotations directly on top the PDF layout. 

![Example of software mention recognition service on PDF](images/screen2.png)

The text mining process is thus not limited to populating a database, but also offers the possibility to come back to users and show them in context the mentions of software. 
 

# References

Duck, G., Nenadic, G., Brass, A., Robertson, D. L., & Stevens, R. (2013). bioNerDS: exploring
bioinformatics’ database and software use through literature mining. BMC
Bioinformatics, 14, 194. http://doi.org/10.1186/1471-2105-14-194

Duck, G., Nenadic, G., Filannino, M., Brass, A., Robertson, D. L., & Stevens, R. (2016). A
Survey of Bioinformatics Database and Software Usage through Mining the Literature.
PLOS ONE, 11(6), e0157989. http://doi.org/10.1371/journal.pone.0157989

_(Lipinski and al. 2013)_ [Evaluation of Header Metadata Extraction Approaches and Tools for Scientific PDF Documents](http://docear.org/papers/Evaluation_of_Header_Metadata_Extraction_Approaches_and_Tools_for_Scientific_PDF_Documents.pdf). M. Lipinski, K. Yao, C. Breitinger, J. Beel, and B. Gipp, in Proceedings of the 13th ACM/IEEE-CS Joint Conference on Digital Libraries (JCDL), Indianapolis, IN, USA, 2013. 

Pan, X., Yan, E., Wang, Q., & Hua, W. (2015). Assessing the impact of software on science: A
bootstrapped learning of software entities in full-text papers. Journal of Informetrics,
9(4), 860–871. http://doi.org/10.1016/j.joi.2015.07.012

_(Peters and al. 2018)_ Deep contextualized word representations. Matthew E. Peters, Mark Neumann, Mohit Iyyer, Matt Gardner, Christopher Clark, Kenton Lee, Luke Zettlemoyer, NAACL 2018. [arXiv:1802.05365](https://arxiv.org/abs/1802.05365)

Piwowar, H. A., & Priem, J. (2016). Depsy: valuing the software that powers science. Retrieved
from https://github.com/Impactstory/depsy-research

_(Tkaczyk and al. 2018)_ Evaluation and Comparison of Open Source Bibliographic Reference Parsers: A Business Use Case. Tkaczyk, D., Collins, A., Sheridan, P., & Beel, J., 2018. [arXiv:1802.01168](https://arxiv.org/pdf/1802.01168).

