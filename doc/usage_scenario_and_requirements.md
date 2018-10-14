# Usage scenario and requirements for the software mention recognizer service

## CiteSuggest

__CiteSuggest__ is referred as Prototype 1 in the Sloan proposal.

```
CiteSuggest is a tool to recommend software citations based on submitted article text or source code. Authors will be prompted to upload their Word .doc, text, or latex manuscript. We then run the manuscript through our machine learning algorithm to find software this author mentioned.
```

The first requirements for implementing such a service are:

- a software mention recognizer able to process text from different formats

- a software disambiguation/matching component to identify from the extracted software information a unique software entity

```
The next step will use data from a module that identifies the preferred citation using available data.
```

For supporting such requirement, we need:

- a database of software entities

- in the database, storing association of the best reference source for each software entity; this best reference source will be used to generate the best formal citation of the software

- mechanism for identifying the best reference source for each software entity

```
In addition to suggesting best-practice citations based on existing software mentions in papers, we’ll attempt to identify related software that the author may have neglected to mention at all
```

As additional services required for the above feature:

- a software entity similarity measure

- ability to request the most relevant softwares, ranked according to the similarity measure based on an input which could be one or several software mentions, software entities and/or textual contexts 


Code/script can also alternatively been submited by an author/user, and used software will be identified based on the library/package dependencies. Similarly for these identified software, prefered citations will be made available. 

As additional requirements: 

- ability to extract software dependencies from given software sources

- ability to match software packages as declared in the dependency management system with software entities stored in the software knowledge base, which require again some adapted disambiguation



## CiteMeAs for GitHub 

__CiteMeAs for GitHub__ is referred as Prototype 2 in the  Sloan proposal.


```
CiteMeAs for GitHub will help software projects on GitHub make clear requests for their preferred citations.
```

The idea is to automatically submit pull requests to each GitHubrepo with an updated README containing a preferred/normalized citation.  

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

As we have seen, storing the list of software mentions in the scientific litterature and their mention contexts are not enough for supporting the above scenarios. We need to disambiguate the mentions at software entity level, so that if the same software is refered using different wording, we can identify this unique software in the two contexts (of course the software version might differ). 



### Need for a software knowledge base and an entity scheme



### Need for software entity similarity 



### Determining best citable sources

This task would require the creation of an evaluation dataset to measure the performance of a proposed solution. Ideally training data would need to be available. 

One approach would be to enrich the gold corpus by indicating for each software mention if the current article (where the mention occurs) is the best citation for the software, to identify possible citations accompanying the software mention and if the citation can be considered as the best citation for the software. 


### Software dependencies as additional layer of relations in the KB

As a general source of information, one of the main challenge is the variety of dependency management systems. Each programing language comes usually with its own dependency management systems. One progamming language can have several depedency management systems (like Java with _ant_, _maven_ and _gradle_ or C++ with _make_, _cmake_, etc.). Cross-language dependencies can be handled too, for instance with _swig_.



## Software Knowledge Base

We explore in the section the usage of Wikidata as Knowledge Base in view of the identified requirements. 

### Motivation

The motivation to use Wikidata as data scheme are multiple:

- an already existing extensive scheme covering 18,000 softwares

- a lot of links and textual content via Wikipedia that can be used for graph-based disambiguation and/or entity embeddings

- schema representation able to scale to million of entities and hundred million of statements, resolving most of the blocking issues of the Semantic Web paradigm based on Description Grammar

- open (CC-0) with collaborative functionalities

- perspectives to contribute to Wikidata for the benefit of the public

- possibility to use existing disambiguation tools, in particular our own implementation entity-fishing, a robust, scalable and well tested entity disambiguation library already used in production by several publishers. 






## REST API for the software KB




