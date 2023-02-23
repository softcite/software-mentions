# Guidelines for software mention annotations in TEI XML 

This page presents the guidelines for the annotation of software mentions in a scholar publication or a fragment of scientific text in [TEI XML](https://tei-c.org). TEI is a comprehensive standard for text encoding. The main advantage of XML when annotating such a corpus is to maintain a readable document, to preserve the text content (without forced tokenization or reformatting) and to take advantage of validation tools for the produced mark-up.

## Text input and inline mark-up

Annotated text structures correspond to full paragraphs as extracted and structured by [Grobid](https://github.com/kermitt2/grobid) when processing the PDF versions of the articles. It is required that the text content to be annotated is never modified or corrected. Only mark-up tags are added within the text to identify entities, without any edition on the text content. It is also expected that no space or end-of-line are added within the `<p>` (paragraph) element. However, outside `<p>` elements, space or end-of-line characters can be added, for example to improve the readability of the XML file. 

The XML namespace is the TEI namespace for all these mark-up.

In practical annotation tasks, some automatically generated mark-up are usually already present and the objective is then to correct the mark-up: move, remove or add mark-up elements on the same text.  

We call `software mention` the reference to a software in a text, including the software names and every related components appearing together with the software name (e.g. software version, publisher, etc.). Software names and its software components are identified with TEI inline mark-up `<rs>`, for [referencing string](https://tei-c.org/release/doc/tei-p5-doc/en/html/ref-rs.html). 

Extra XML attributes on mark-up are used to further refine the types of the identified entities. Relations between entities are encoded with attribues `@xml:id` and attribute `@corresp` as pointer, which is illustrated in details in this documentation. 

## Mark-up description

### Software names

The main mark-up for a software mention is its software name, marked with an `<rs>` element with `@type="software"`. A software mention must have one and only one software name. A software name is identified with an attribute `@xml:id`, which also serves as a global identifier for the complete software mention. Attributes related to this software are pointing to the software name element via XML pointer `@corresp`. 

In the following example, a software mention includes a software name (CRISPRFinder) with `@xlm:id` value `b308e79ccf-software-1` and two attributes  attached to this named software via a `@corresp` pointer to this `xml:id`, a version number and a URL:

```xml
<p><rs type="software" xml:id="b308e79ccf-software-1">CRISPRFinder</rs> version <rs corresp="#b308e79ccf-software-1" 
type="version">1.0</rs> is freely accessible at <rs corresp="#b308e79ccf-software-1" 
type="url">http://crispr.u-psud.fr/Server/ CRISPRfinder.php</rs></p>.
```

**Software name and acronym**: When present, we always keep an acronym in the same mark-up span as the software name:

```xml
... using the <rs type="software">Ingenuity Pathways Analysis (IPA)</rs> tool... 

The user interface was linked to a <rs type="software">My Structured Query Language (MySQL)</rs> database 
```

When possible, we further specify the type of the mentioned software with the attribute `@subtype`: 

- **standalone software/application:** no `@subtype` attribute (default case). The software is expressed _in the mention context_ without dependency to another software. This software does not require additional code/script if used in the research work. 

```xml
the data were analyzed using <rs type="software" xml:id="PMC3203616-software-1">Multi Gauge</rs> Version 
<rs type="version" corresp="#PMC3203616-software-1">3.0</rs> software (<rs type="publisher" 
corresp="#PMC3203616-software-1">FUJIFILM</rs>). 

The open-source software <rs type="software" xml:id="PMC5367605-software-3">ITK- Snap</rs> 
<rs corresp="#PMC5367605-software-3" type="version">2.0.0</rs> <ref type="bibr">35</ref> is used to manually delineate 
the tumour on each MRI slice where it is visible.</p>

Post-acquisition image processing was carried out using <rs type="software" xml:id="PMC2652046-software-0">CorelDraw</rs> 
<rs corresp="#PMC2652046-software-0" type="version">12</rs> software.
```

- **software environment:** `@subtype="environment"`, for a mentioned software requiring some code/scripts to realize the research task,
expressed with or without dependency to another software _in the mention context_. Typical examples are MATLAB or STATA. Many statistical analysis tools are environments where analysis are programmed as scripts. 

```xml
The maximum likelihood outcrossing rates were estimated in <rs type="software" subtype="environment" 
xml:id="PMC4303630-software-3">Mathematica</rs> <rs corresp="#PMC4303630-software-3" type="version">7</rs> following 
the method used by <ref type="bibr">Johnson et al. (2004)</ref>

All image processes and statistical analyses were done using <rs type="software" subtype="environment" 
xml:id="PMC4968915-software-1">Matlab</rs> (<rs corresp="#PMC4968915-software-1" type="publisher">Mathworks</rs>, Natick, 
MA, USA).
```

- **named software component:** `@subtype="environment"`, for a named software depending on another software environment mentioned in the mention context
to run. We always have a software environment expressed in the same mention context, and the dependency of the software component to the software environment is encoded with the `@corresp` pointer to the `xml:id` of the software environment. 

```xml
Area under the curve (AUC) was estimated by <rs type="software" subtype="environment" xml:id="PMC5063056-software-3">R</rs> 
package <rs type="software" subtype="component" corresp="#PMC5063056-software-3" xml:id="PMC5063056-software-4">pROC</rs> 
to determine the 95% confidence intervals (95%CI).

For the variance ratio test, I used the <rs type="software" subtype="environment" 
xml:id="10.1111%2Fecin.12279-software-0">Stata</rs> command <rs type="software" subtype="component" 
corresp="#10.1111%2Fecin.12279-software-0" xml:id="10.1111%2Fecin.12279-software-1">sdtest</rs>. 

This library is based on the <rs type="software" subtype="environment" xml:id="PMC2881388-software-1">Python</rs> package 
<rs type="software" subtype="component" corresp="#PMC2881388-software-1" xml:id="PMC2881388-software-2">NetworkX</rs> 
<ref type="bibr">(Hagberg et al., 2008)</ref>. 

D-prime, which measures the distance between the signal and the noise means in standard deviation units 
<ref type="bibr">(40)</ref>, was calculated using <rs type="software" subtype="environment" 
xml:id="PMC5458338-software-3">matlab</rs> function <rs type="software" subtype="component" corresp="#PMC5458338-software-3" 
xml:id="PMC5458338-software-4">dprime_simple</rs> (<rs corresp="#PMC5458338-software-4" 
type="url">https://it.mathworks.com/matlabcentral/fileex change/47711-dprime-simple-m</rs>
```

- **implicit software component:** `@subtype="implicit"` for an unnamed software. The software refering expression is a generic term for program, such as program, code, script, macro, package, library, etc. Optionally, if the unnamed software is depending on another software environment to run, the software environment being expressed in the mention context, then the the dependency of the unamed software to the software environment is encoded with the `@corresp` pointer to the `xml:id` of the software environment (similarly as for a software component). 

```xml
We developed a <rs type="language" corresp="#PMC4551074-software-200">Perl</rs> <rs type="software" subtype="implicit" 
xml:id="PMC4551074-software-200">program</rs> to store all the graph paths for a given protein. 

In this paper, we aimed to evaluate the heterogeneity of liver parenchyma on Gd-EOB-DTPA-enhanced MR images, using CV 
value processed by our <rs type="software" subtype="environment" xml:id="PMC5390613-software-1">MATLAB</rs>-based 
<rs type="software" subtype="implicit" xml:id="PMC5390613-software-2" corresp="#PMC5390613-software-1">software</rs>.

The <rs type="software" subtype="environment" xml:id="10.1111%2Froie.12300-software-0">Matlab</rs> <rs type="software" 
subtype="implicit" xml:id="10.1111%2Froie.12300-software-1" corresp="#10.1111%2Froie.12300-software-0">code</rs> is 
downloadable in <rs type="url" corresp="#10.1111%2Froie.12300-software-1">mwkang.site11.com/code/rie2016</rs>.
```

For determining the type of a software, some search about the software on the internet is often necessary. 

### Version

The `version` tag identifies the version of the mentioned software, it is encoded with an `<rs>` element with `@type="version"`. It can be a number, an identifier or a date. It is expected that a mentioned software has **only one** version. 

Version annotation should cover only the specific number or date string, without any other token like "version", "v.", etc. or extra punctuations:

```xml
<p>We performed meta-analysis using <rs type="software" xml:id="PMC4898839-software-100"> Review Manager</rs> Software 
(version <rs type="version" corresp="#PMC4898839-software-100">5</rs>).</p>

<p>All statistical analyses were conducted with IBM <rs type="software" subtype="environment" 
xml:id="PMC4504996-software-1">SPSS Statistics</rs> ver. <rs type="version" corresp="#PMC4504996-software-1">20.0</rs> 
(<rs type="publisher" corresp="#PMC4504996-software-1">IBM Co.</rs>, Armonk, NY, USA).</p>

<p><rs type="software" subtype="environment" xml:id="PMC5372150-software-1">SPSS</rs> V.<rs type="version" 
corresp="#PMC5372150-software-1">15.0</rs> for Windows was used for all the statistical analyses.</p>
```

### Publisher

The mark-up `publisher` identifies the entity distributing the software to the public. It is encoded with an `<rs>` element with `@type="publisher"`. It is usually the organization or the company owning the software or having developed the software. It is expected that a mentioned software has only one publisher in the same mention context a most, but several is possible (see below). 

A publisher annotation should only contain the name of the publisher, including the possible legal form (Inc., LLC, GmbH, etc.) of the business entity when present, but not its address:

```xml
<rs id="software-0" type="software">SPSS</rs> ver. <rs corresp="#software-0" type="version-number">11.0</rs> 
(<rs corresp="#software-0" type="publisher">SPSS Inc.</rs>, Chicago, IL, USA) was used to evaluate the data.

... followed by the Tukey-Kramer post hoc test performed with <rs id="software-0" type="software">GraphPad prism</rs> 
software (version <rs corresp="#software-0" type="version-number">4.0</rs>, <rs corresp="#software-0" type="publisher">
GraphPad Software</rs>, San Diego, CA, USA).

All the analysis was performed in the <rs id="software-0" type="software">MATLAB</rs> environment 
(<rs corresp="#software-0" type="publisher">The MathWorks</rs>, Natick, MA)
```

In case the creator/developer persons are directly mentioned, they are also labeled as `publisher` with an extra XML attribute `@subtype="person"`:

```xml
<p>Sequences obtained were analyzed and edited using <rs type="software" xml:id="PMC4435018-software-1">BioEdit</rs> 
<rs type="version" corresp="#PMC4435018-software-1">7.2.5</rs>.&#169;1999-2013 software (<rs type="publisher" 
subtype="person" corresp="#PMC4435018-software-1">Tom Hall</rs>, <rs type="publisher" corresp="#PMC4435018-software-1">
Ibis Biosciences</rs>, Carlsbad, CA).</p>

<p>We have found a way to double the accuracy of <rs type="software" subtype="environment" xml:id="b3951a8cf2-99">Matlab
</rs>'s <rs type="publisher" subtype="person" corresp="#b3951a8cf2-100">Ricatti</rs> equation solver <rs type="software" 
subtype="component" xml:id="b3951a8cf2-100" corresp="#b3951a8cf2-99">lyap.m</rs> by essentially applying it twice.</p>
```

* **Combined publisher/software name:** We distinguish software publisher and software name when used in combination. For instance:

```xml
<rs corresp="#PMC0000000-software-1" type="publisher">Microsoft</rs> <rs type="software" 
xml:id="PMC0000000-software-1">Excel</rs>
```

Exceptions are for software names always including the publisher by usage, the main cases being "Lotus Notes" (we never see "Notes" alone for this software) and "GraphPad Prism" ("Prism" only is not observed). 

When the software publisher is repeated with the software name in combination in the same mention, we annotated only the more comprehensive publisher form. For instance:

```xml
<p>Observed heterozygosity was estimated in Microsoft <rs type="software" xml:id="PMC4103605-software-13">Excel</rs> 
(<rs corresp="#PMC4103605-software-13" type="publisher">Microsoft Corporation</rs>, Redmond, Washington, USA).</p>
```

The publisher appears two times, first as one word (`Microsoft`), followed by a more comprehensive form (`Microsoft Corporation`) after the software name. To simplify the mark-up, we only annotate in the mention the second form, following the above rule.  


### URL

The mark-up  `url` identifies an hyperlink associated to the software. The URL can link to the code repository, to the software project page, to its documentation, etc. Although very rare, it is possible to have several `url` component for a software mention. 

### Language

The mark-up `language` identifies the programming language of the mentioned software if present in the mention context. We only consider here the language when used to indicate how the source code is written, not the language as a broader reference to the programming environment used to develop the mentioned software, see in the interpretation section for more explanation. 

```xml
<p>Sequences were further annotated with <rs type="language" corresp="#PMC1635254-software-0">PERL</rs> 
<rs type="software" subtype="implicit" xml:id="PMC1635254-software-0">scripts</rs>.</p>
```

### Bibliographical reference callout

Biblographical reference callouts (also called reference markers) are identified with TEI mark-up `<re type="bibr">`. It is expected that the these markers are identified in the mention context. They can be optionally link to the software mention via a `@corresp` pointer like the other software components. However, this linkage is currently not required. 

## Definition/scope for software annotation

### 1) Annotating all software, not just "research software"

We consider mentions of software in scientific literature without limitation to "research software". We found the notion of "research software" unclear. 

From the point of view of software sharing, "research software" is usually understood as software produced by researchers or by research software engineers. However, mainstream commercial software are very broadly used in science and mentioned in scholar papers when describing research activities. Examples of very commonly mentioned general purposes and mainstream software are Excel, Photoshop or PostgresQL. Such general software can also be the object of a research study. So, from the point of view of software citation, any software mentioned in scholar literature is relevant - they are "software of interest" for research and should be annotated.  

### 2) What should be considered as a "software" entity?

Software products correspond in practice to various artefacts, which are not always clear to consider as "software". This is particularly challenging from the point of view of software citation, but this remains an issue even when identifying software sharing. 

A standard definition of software is "a collection of computer programs that provides the instructions for telling a computer what to do and how to do it" (Wikipedia). Everything that can provide processing instructions to a computer, whatever its form, can therefore be seen as software. This relatively broad definition of software covers a large variety of software products, for instance from macro and formula part of an Excel sheet to large software project with multiple components, hundred thousand lines of source code and binary packages for a variety of computing environments. 

Any of these software products have a potential research interest, for reuse or reproducibility purposes, and could be therefore valuable to share. Monitoring software in research supposes to be able to identify any mentions of a software product independently from the scale of the software production and independently from its form. 

The types/formats of software depend a lot on the technical domain and the used programing framework. 

**We propose to cover the notion of software in general independently from any particular distribution forms.** 

- **Software** products typically can be published as standalone applications or libraries/plugins, either as executable code (binaries), package (e.g. R package, combining script and binaries), as a more comprehensive open source project (program, script, data resources, documentation, build scripts, etc.), script program or macro to be interpreted and exectuted within a particular software environment, source code that require manual building, small standalone script (e.g. "gist"), plaform (including data, data management software and service software), web services, images to be executed as containers, or software embedded in an hardware device.

All these software distribution formats are considered as software to be annotated for the present annotations guidelines. 

- **Algorithm** versus software: as a general guideline, algorithm mention are not considered as software mention and are not be annotated. However, it is quite frequent that the name of an algorithm and its implementation (so as software) are used in papers in an interchangeable manner. While it is clear that we want to exclude "algorithm names" from software entities, they can be used to refer to the implementation. This is one of the most frequent ambiguity we have identified in the Softcite dataset and this was similarly reported for the [SoMeSci dataset](https://doi.org/10.1145/3459637.3482017). The distinction could sometime be done in context, but a guideline is necessary when the usage of the name is general and ambiguous on purpose. 

Examples: [10.1038/ng.2007.13](https://pubmed.ncbi.nlm.nih.gov/17952075/)

```
    Finally, we applied the EIGENSTRAT method [46], which relies on patterns of correlation 
    between individuals to detect stratification, to our Icelandic discovery sample. 
```

*EIGENSTRAT* here is the name of the method and of the software implementing the method. As the context describes the application of the method of the algorithm on actual data, it refers to the use of the software implementation and it should therefore be annotated as a software mention.

[10.1038/bjc.2016.25](https://www.nature.com/articles/bjc201625)

```
    Messenger RNA expression was normalised to household gene expression (GAPDH and RPL13A 
    for BON-1; HPRT and YWAZ for QGP-1) according to the geNorm algorithm (Mestdagh et 
    al, 2009). 
```

*geNorm* is an algorithm and referenced as such above, but it is software too - and the software is actually used for the normalization in the described research. It should therefore be annotated as a software mention.

As a general guidelines regarding an algorithm name and its implementation used in an interchangeable manner in a scholar publication: in case the reference is made to an implemented algorithm with the algorithm name, we consider it as software mention if the context indicates that the implemented software aspect is involved in the statement. 

- The notion of **models** (machine learning models, simulation models) versus software is often unclear. Models encode data processing and executable action/prediction process. They are however in a format closer to data, because the "instructions" are only digital transformations. Models themselves should be run in a software environment. Despite their "executable" nature, models are usually not considered as software and have dedicated distinct sharing infrastructure (e.g. the [CoMSES Net](https://www.comses.net)). 

So as a general guideline, standalone models are **not** to consider as software product. 

However, like algorithms, we observe that it can be relatively frequent (in the machine learning area for example) to use the same name to refer both for a model and a software product for implementing/running a model. For example, `BERT` is a python software project (https://github.com/google-research/bert), a model, a family of models (retrained on different domains), or a ML approach (a Deep Learning architecture and methodology for training it):

[10.48550/arXiv.2103.11943](https://arxiv.org/pdf/2103.11943.pdf)

```
    The representation of the BERT system allows it to be used as a basis for measuring the 
    similarity of sentences in natural languages
```

Similarly as for algorithm, we need to identify whether the mention refers to the model product, the approach/method or the software to decide if the mention shall be considered as software mention or not. In case the reference is made in general to the whole framework, including the software, we would consider it as software mention. 

- **Database** versus software: in scientific publications, it is quite frequent to mention a database name as a general service covering the data and  software to access/search the data (including web services and database management software, e.g. PostgresQL). 

Example from PMC4863732

```
    Scientific articles were obtained through PubMed MEDLINE
```

MEDLINE is at the same time a large metadata collection and a database service to query this catalogue. 

Example from 10.1002/pam.22030

```
    Data come from the Integrated Public Use Microdata Series (IPUMS) database
```

Integrated Public Use Microdata Series (IPUMS) is a database and an online platform.

The related guideline for the Softcite corpus is as follow: 

```
    "The relevant distinction should be whether the text is referring to a data collection/dataset 
    (ie the data in the database) or to the software that provides access to a dataset. If it is 
    clear that they are referring to the data inside, it is not a reference to a software." 
```

The guideline thus also means that when it is not clear that we refer to the data inside the database, it should be considered as software too. 

- Very common is life science, **scientific devices** are used in most of the experiments. They usually includes software, embedded or to be install on a PC to control the device, process the aquired data, export the data, etc.. 

Example: [PMC4644012](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4644012/)

```
    The Gram-negative coccobacilli were initially identified as Pasturella pneumotropica by the 
    VITEK 2 system, software version 06.01 (BioMerieux, France) using the GN card, with bionumber 
    0001010210040001 and an excellent identification (probability 99%).
```

The [VITEK 2 system](https://www.biomerieux-usa.com/clinical/vitek-2-healthcare) embeds software performing predictions. 

Given the variety of software embodiments, what is mentioned is often larger system or devices including software. It is thus important to decide in context to which part the authors are referring to, and if the statement refers to the software part of the device. 
 
- **Software components** of a more complete infrastructure: A reference is made to a general infrastructure, including some software components. For example in [10.20955/r.2018.1-16](http://herosolutions.com.pk/breera/foundation/images/whitepaper.pdf) "Bitcoin wallet". We consider that we are refering to a software environment, and thus annotate this as a software mention. 

- Reference to a **programming languages**. For example: [10.1257/jep.4.1.99](https://www.aeaweb.org/articles?id=10.1257/jep.4.1.99)

```
    It is written in BASIC, a close analogue to FORTRAN.
```

We consider that software languages (written in BASIC, in FORTRAN, ...) are not software per se, because they are specifications (a grammar), similar to a protocol specification. When used together with a specific software mention, programing language are considered as "attributes" of this software (e.g. written in R). They are not annotated as software but with the mark-up `<rs type="language">`, which identifies in context the programming language of the mentioned software. 

Software tools for implementing a software language (like a C compiler, a Java virtual machine, an Integrated Development Environment like R-Studio, etc.) are software, they are annotated as a software mention. 

- **Operating system** (OS): when used together with a specific software mention, they are considered as "attributes" of this software (e.g. "running on Windows"). The reference to the OS here is just a further specification about the main software that is discussed. In this case, OS are not annotated as additional software mention. 

However, OS can also be referenced as such when the mention refers specifically to the OS implementation and not to some software using them. In this case, the OS is annotated as software. 

- Non-named usage of a programming environment. Software was produced on the environment (some code was written), but it is implicit, not shared, nor reusable. 

Example: [10.1136/gut.2011.238386](https://gut.bmj.com/content/gutjnl/61/1/69.full.pdf)

```
    Multiple imputation was conducted in R 2.11." 
```

The programming environment here is clearly a software and should be annotated as such. In addition, the non-named usage corresponding to the written code is also a software, implicit, running in the R environment, and should be annotated as a software mention. 

- **Workflow** as high-level specifications: in data-intensive scientific domains, the complexity of data processing has led to the common definiton and usage of workflows associated to a scientific experiments. Examples of such workflow systems are [Galaxy](https://galaxyproject.org) (life science), [Kepler](https://kepler-project.org) (physics and environment sciences), [Apache Taverna](https://incubator.apache.org/projects/taverna.html) (bioinformatics, astronomy, biodiversity - now retired), or [KNIME](https://www.knime.com). As workflows are formal instructions similar to high level scripting language, interpreted in a computer environment, and actually shared for reproducibility and reuse purposes. Therefore, we consider such executable workflows as software products. 

- **API**: An API is an intermediary product between documentation and executable software. It is challenging to decide if an API should be considered as software, because it requires an implementation to be executable. On the other hand, an API corresponds to instructions that can be executed when used on an environment or with other software components implementing the API, like other software depending on other software components. Given that it is the nature of an API to be shared and used for collaborative work in software, we consider API product as software too. 

## Software dependencies expressed in the mention context

When a software package or library is developed within a larger software environment, as a _software dependency_, and that this dependency is expressed in the mention context, we identify the two software respectively as `@subtype=component` and `@subtype=environment`, with a relationship encoded with `@corresp` pointing from the component to the environment part identifier. The two related software mentions can have their own attributes. 

```xml
<rs corresp="#d984c41c4d-software-1" subtype="component" type="software" xml:id="d984c41c4d-software-2">rgp</rs> 
is an implementation of GP methods in the <rs cert="1.0" subtype="environment" type="software" 
xml:id="d984c41c4d-software-1">R</rs> environment. 
```

It is possible to encode a list of components depending on a single environment:

```xml
Mlr and decision trees were implemented in <rs subtype="environment" type="software" xml:id="d984c41c4d-software-1">r</rs> 
using <rs corresp="#d984c41c4d-software-1" subtype="component" type="software" xml:id="d984c41c4d-software-2">lm command</rs> 
and <rs corresp="#d984c41c4d-software-1" resp="#annotator22" subtype="component" type="software" 
xml:id="d984c41c4d-software-3">cubist</rs> package, respectively. 
```

Another case, less frequent, is to have several hierarchical dependency relations, which are encoded similarly as follow: 

```xml
The package <rs corresp="#d984c41c4d-software-1" subtype="component" type="software" xml:id="d984c41c4d-software-0">fscaret</rs> 
allows semiautomatic feature selection, working as a wrapper for the <rs corresp="#d984c41c4d-software-2" subtype="component" 
type="software" xml:id="d984c41c4d-software-1">caret</rs> package in <rs subtype="environment" type="software" 
xml:id="d984c41c4d-software-2">R</rs>.
```

## Annotator information

In the XML corpus, `<rs>` elements can have an addition attribute `@resp` indicating the annotator who has last edited the annotation. Annotators are identified by a number. The `@resp` attribute is an XML pointer to the annotator definition in the header part of the XML corpus. When a curator has corrected or reconciliate annotations in case of disagreement, the attribute value is `@resp="#curator"`. The `@resp` attributes are automatically generated from the annotation environment. 

```xml
Radiographic errors were recorded on individual tick sheets and the information was captured in an 
<rs cert="1.0" resp="#annotator0" type="software" xml:id="a7f72b2925-software-0">Excel</rs> spreadsheet 
(<rs corresp="#a7f72b2925-software-0" resp="#curator" type="publisher">Microsoft</rs>, Redmond, WA). 
```
