# Usage scenario and requirements for the software mention recognizer service

## CiteSuggest

__CiteSuggest__ is referred as Prototype 1 in the Sloan proposal.

```
CiteSuggest is a tool to recommend software citations based on submitted article text or source code. Authors will be prompted to upload their Word .doc, text, or latex manuscript. We then run the manuscript through our machine learning algorithm to find software this author mentioned.
```

The first requirements for implementing such a service are:

- a software mention recognizer able to process text from different formats,

- a software disambiguation/matching component to identify from the extracted software information a unique software entity.

```
The next step will use data from a module that identifies the preferred citation using available data.
```

For supporting such requirement, we need:

- a database of software entities,

- in the database, storing association of the best reference source for each software entity; this best reference source will be used to generate the best formal citation of the software,

- mechanism for identifying the best reference source for each software entity.

```
In addition to suggesting best-practice citations based on existing software mentions in papers, we’ll attempt to identify related software that the author may have neglected to mention at all
```

As additional services required for the above feature:

- a software entity similarity measure,

- ability to request the most relevant softwares, ranked according to the similarity measure based on an input which could be one or several software mentions, software entities and/or textual contexts. 


Code/script can also alternatively been submited by an author/user, and used software will be identified based on the library/package dependencies. Similarly for these identified software, prefered citations will be made available. 

As additional requirements: 

- ability to extract software dependencies from given software sources,

- ability to match software packages as declared in the dependency management system (e.g. PyPI, CRAN, NPM, maven central) with software entities stored in the software knowledge base, which require again some adapted disambiguation.


## CiteMeAs for GitHub 

__CiteMeAs for GitHub__ is referred as Prototype 2 in the  Sloan proposal.


```
CiteAs for GitHub will help software projects on GitHub make clear requests for their preferred citations.
```

The idea is to automatically submit pull requests to GitHub repos with an updated README containing a preferred/normalized citation.  

Note that the proposed feature might be assimilated to automatic PR / bot which is banned on GitHub (even if the bot is nice!). Such a feature would need an opt-in from the project administrators, basically being a GitHub App requesting some permissions. So we do not think that the described feature is feasable and the prototype based on an opt-in step of the project developers do not make a lot of sense. 

## Software Impactstory

__Software Impactstory__ is referred as Prototype 3 in the  Sloan proposal.

```
Software Impactstory is an interface to help scholars that contribute software to  identify their software impact in existing literature.
```

The objective is to identify all of a user’s software products and papers related to those products.

Note that such a tool might be conflicting with GDPR, European users having developed softwares have not given their explicit consent for being part of a database of software authors and for profiling mechanisms. GDPR applies to European citizens wherever a system is operating.  

The described features would imply as additional requirements:

- data model and storage for software authors

- author disambiguation mechanisms, note that author names in bibliographical references can be difficult to disambiguate because they usually come with less information (e.g. only initials) than author names in the header of an article (more often full name with affiliation)

- author identification mechanisms for software repository: For a repo, the name of an author is rarely explicit, it is often necessary to analyse profile information, emails or the readme for finding a bibliographical reference for instance (which might be inline, not in a specific bibliographical section as for scholar papers), and then parse this reference string. 


## Requirement analysis for Software Knowledge Base

### Need for software mention disambiguation

Storing the list of software mentions in the scientific litterature and their mention contexts are not enough for supporting the above scenarios. We need to conflate the mentions at an aggregated software entity level, so that if the same software is refered using different wording, we can identify this unique software in the two contexts (of course the software version might differ). Software naming might not be very ambiguous (few polysemy), but synonymy might be important (same software with different equivalent naming). 

### Need for a software knowledge base and an entity scheme

The software knwoledge base will be capable of storing entity information at general level and at version level. Boths level can be associated with mentions in scientific papers. 

Although we would in theory only need to store the attributes that can be extracted from the mention recognizer, so the attributes that are annotated in the training corpus (software name, author, version date, version numebr and url), storing more information is useful for disambiguation because it can be used to match more context information.  

### Need for software entity similarity / recommendation

The goal is from a given publication, find the related softwares not mentioned. 


### Determining best citable sources

Simple heuristics can be envisaged (document most cited when mentioning the software, document with the highest number of mentions of the software). 

Normally, this task would require the creation of an evaluation dataset to measure the performance of a proposed solution. Ideally training data would need to be available. One approach would be to enrich the gold corpus by indicating for each software mention if the current article (where the mention occurs) is the best citation for the software, to identify possible citations accompanying the software mention and if the citation can be considered as the best citation for the software. 


### Software dependencies as additional layer of relations in the KB

Depsy covers already Python and R / PyPI and CRAN (11,223 software packages).

As a general source of information, one of the challenges is the variety of dependency management systems. Each programing language comes usually with its own dependency management systems. One progamming language can have several depedency management systems (like Java with _ant_, _maven_ and _gradle_ or C++ with _make_, _cmake_, etc.). Cross-language dependencies can be handled too, for instance with _swig_.

A second difficulty is to match unambiguously a software based on the metadata and information provided in a package or a repo, with the software entity in a knowledge base. 

## Software Knowledge Base

We explore in the section the usage of Wikidata as Knowledge Base. 

### Discussion

The advantages for using Wikidata as data scheme are multiple:

- an already existing extensive scheme with 82,303 software instances and sub-classes,

- a lot of links and textual content via Wikipedia that can be used for text and graph-based disambiguation and/or entity embeddings,

- Wikidata schema representations able to scale to million of entities and hundred million of statements, resolving most of the blocking issues of the Semantic Web paradigm based on Description Logics,

- open data (CC-0) with open source tool supporting collaborative work between humans and machines,

- perspectives to contribute to Wikidata for the benefit of the public,

- possibility to use existing disambiguation tools, in particular our own implementation entity-fishing.

As drawback, we could mention that the data scheme is graph-oriented and cannot be "natively" supported at low level by a relational database (like MySQL, although the data is actually stored by Wikimedia with MySQL, but it's blob flat representation), nor by a document database (e.g. Mongo DB) without the creation of additional indexes.  

Regarding the usage of entity-fishing as a disambiguation tool:

- it's already implemented, used in production by several publishers and well-maintained,

- it works well for both text-based input and structured data input,

- it's generic, it could be rused for other scientific entities in the future (it does not require any customisation with explicit software attributes which would impact re-using the system to other type of entities)

- already built on top of wikidata.

As drawback, the current version of entity-fishing will likely not scale the processing of 12 million documents. It will require to adapt it for that performance level. The current version is designed to disambiguate all the document content and it would be simply necessary to limit the process to the context around the software mentions. 

The second drawback is that entity-fishing is a relatively heavy to install because its knowledge base contains all Wikidata content, plus at least the English Wikipedia in a an efficient compiled graph representation, representing a volume of ~ 50GB of data when uncompressed. 

## Data scheme for software entity

### Identifier

Each software entity has a unique identifier:

- Wikidata identifier for software entities already in Wikidata, e.g. Q8029 for BibTeX

- Identifier for software entities not in Wikidata: E24 for GROBID (we basically simply replace Q by E - for External, but keep the same data model)


### Data scheme

Summary of Wikidata data scheme:

...

### Disambiguation process

...


## REST API for the software KB

### Response status codes

For all services, the response status codes will be as follow:

|     HTTP Status code |   reason                                               |
|---                   |---                                                     |
|         200          |     Successful operation.                              |
|         204          |     Process was completed, but no content could be extracted and structured |
|         400          |     Wrong request, missing parameters, missing header  |
|         404          |     Indicates service/property was not found           |
|         406          |     The language is not supported                      |
|         500          |     Indicate an internal service error, further described by a provided message           |


### Retrieve information about a software entity in the KB

endpoint: `/api/concept/{software id}`

|   method  |  response type      | 
|---        |---                  |
| GET       |   application/json  |
|           |                     | 


example: `/api/concept/Q8029`

```json
{
    "id": "Q8029",
    "labels": {"en": {"language": "en", "value": "BibTeX"}}, 
    "descriptions": {"en": {"language": "en", "value": "reference management software for formatting lists of references"}},
    "aliases": [],
    "claims": {"P31": [{"datatype":"wikibase-item", "datavalue": {"value": "Q7397"}},      # instance of software
                       {"datatype":"wikibase-item", "datavalue": {"value": "Q18616720"}}], # instance of bibliographic data format
               "P178": [{"datatype":"wikibase-item", "datavalue": {"value": "Q93068"}}],   # developer is Q93068 (Oren Patashnik)
               "P856": [{"datatype":"url", "datavalue": {"value": "https://www.ctan.org/pkg/bibtex"}}] # url
              }
}

```

### Retrieve citation information for a software entity

endpoint: `/api/Q8029/citations`

method: GET

### Disambiguate a software mention in isolation

endpoint: `/api/disambiguate`

method: POST

### Extract all raw mention of a software in a PDF

endpoint: `/api/softwares/mentions`

method: POST

### Extract all disambiguated software entities in a PDF

endpoint: `/api/softwares/entities`

method: POST

### Provide the n-best citations for a software entity

endpoint: `/api/Q8029/citations/nbest`

method: GET

### Provide the most relevant related software entities with a given software entities

endpoint: `/api/Q8029/related`

method: GET

### Provide the most relevant related software entities given a PDF

endpoint: `/api/softwares/related`

method: GET





