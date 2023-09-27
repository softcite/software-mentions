# JSON schema for software mention annotations

This page presents the current JSON schema used to represent software mention extractions from a scholar publication or a fragment of scientific text.

## Response envelope

### Text query

The response to a text fragment-level query contains first metadata about the software version used to process the document, the date of processing and the server runtime. The list of identified software `mentions` are provided as a JSON array. Note that no bibliographical reference information is associated to text queries, because Grobid extracts and resolves bibliographical references at document-level. 

```json
{
    "application": "software-mentions",
    "version": "0.7.3-SNAPSHOT",
    "date": "2022-11-15T07:58+0000",
    "runtime": 1119,
    "mentions": [...]
}
```

### PDF and XML document query

Similarly as text queries, the document-level information corresponds first to metadata about the software version used to process the document, the date of processing and the server runtime.

In addition in case of a PDF input, the size of the pages of the document are provided according to standard PDF representation (in particular using "abstract" PDF unit, see [here](https://grobid.readthedocs.io/en/latest/Coordinates-in-PDF/#coordinate-system-in-the-pdf) for more details). The coordinates of the annotations will be relative to these pages and their X/Y sizes.  

A MD5 hash is also included as a unique identifier of the PDF file, specific to the uploaded file binaries. 

A `references` elements is included together with the array of `mentions`, listing the extracted structured bibliographical references associated to the mentioned software.  

```json
    "application": "software-mentions",
    "version": "0.7.3-SNAPSHOT",
    "date": "2022-11-15T08:05+0000",
    "md5": "2E7BEFDEE469C6F80024FC03FD97FE18",
    "pages": [{
        "page_height": 791.0,
        "page_width": 612.0
    }, {
        "page_height": 791.0,
        "page_width": 612.0
    }, 
    ...
    {
        "page_height": 791.0,
        "page_width": 612.0
    }],
    "runtime": 4374,
    "mentions": [...],
    "references": [...]
}
```

In case an XML document is the input to be processed, representations are simplified to exclude layout coordinates and MD5 hash. 

## Mention annotation

A software mention is a JSON element using the following general structure, which is repeated for every mention spotted in a document: 

```json
{
    "type": "software",
    "software-type": "...",
    "wikidataId": "...",
    "wikipediaExternalRef": ...,
    "lang": "...",
    "confidence": ...,
    "software-name": {...},
    "version": {...},
    "publisher": {...},
    "url": {...},
    "language": {...},
    "context": "...",
    "paragraph": "...",
    "references": [{
            "label": "(Jones, 1999)",
            "normalizedForm": "Jones, 1999",
            "refKey": 24
        }],
    "mentionContextAttributes": {...},
    "documentContextAttributes": {...}
}
```

The `type` here is the general type of entity and will always be `software` for the software mention recognition tool. 

`software-type` refines when possible the type of software, with the following possible values:

- `environment`: a software environment is a generic software used to resolve tasks or create additional application-specific by the way of additional scripts/macros/codes to be executed or dependent on this software environment. Examples are R-Studio, Jupyter Notebook, MATLAB or SAS. When a software environment is mentioned alone in a research paper (for example SPSS), some implicit code might exist, written in this environment. However, this is not always the case, because many software environments include today point-and-click user interface and can be used without writing any scripts for solving complex scientific tasks. 

- `implicit`: the mentioned software is not named, it is simply referred to by the way of a generic phrase such as "code", "script", "program", "module", etc. It corresponds usually to some one-shot code written specifically for a research work. 

- `component`: when used in the same context of a software environment, the software mention indicated that the software is running or depends on this environment. 

- `software`: this corresponds to the default case of a named software running as standalone application.

The fields `software-name`, `version`, `publisher`, `url` and `language` correspond to extracted chunks of information identified in the mention context. They all follow the same substructure, encoding offset information relative to the context string (`offsetStart` and `offsetEnd`) and bounding box coordinates relative to the PDF (`boundingBoxes`):

```json
    "software-name": {
        "rawForm": "ClustalW",
        "normalizedForm": "ClustalW",
        "wikidataId": "Q866737",
        "wikipediaExternalRef": 1976990,
        "lang": "en",
        "confidence": 0.7827,
        "offsetStart": 31,
        "offsetEnd": 39,
        "boundingBoxes": [{
            "p": 4,
            "x": 439.144,
            "y": 111.378,
            "w": 36.0176,
            "h": 8.0517
        }]
    }
```

In case of a response to an XML document or a text fragment, coordinates in general are not present. 

`rawForm` give the exact strict as extracted from the source text content. `normalizedForm` is a normalized version of this extracted string, more appropriate for displaying results to users and further processing like deduplication (normalization ensures in particular a string without possible interrupting end-of-line and various possible PDF noise). 

The following fields are optional and present if a Wikidata disambiguation for the mentioned software entity has been successful:

- `wikidataId`: the Wikidata identifier (`Q` prefix string)

- `lang`: the ISO 639-1 language code to be associated to the entity

- `wikipediaExternalRef`: the page ID of software entity for the Wikipedia of the indicated language (integer value)

- `confidence`: a double between 0.0 and 1.0 indicating the confidence of the Wikidata entity resolution

See [here](https://grobid.readthedocs.io/en/latest/Coordinates-in-PDF/#coordinates-in-json-results) for information about the interpretation of the bounding box coordinates on the PDF page layout. 

`context` is the textual representation of the sentence where the software mention takes place. Offset used to identify chunk in the fields `software-name`, `version`, `publisher`, `url` and `language` are relative to this context string. 

`paragraph` (optional) is the texual representation of the complete paragraph where the software mention takes place. This extended context can be useful for applying subsequent process to a larger context than just the sentence where a mention takes place.

`mentionContextAttributes` and `documentContextAttributes` follow the same scheme and provide information about the mentioned software in term of usage, creation and sharing based on the different software mention contexts in the document. Mentioned software are characterized with the following attributes:

 *  __used__: the mentioned software is used by the research work disclosed in the document
 *  __created__: the mentioned software is a creation of the research work disclosed in the document or the object of a contribution of the research work
 *  __shared__: software is claimed to be shared publicly via a sharing statement (note: this does not necessarily means that the softwate is Open Source)

For each of these attributes, a confidence scores in `[0,1]` and a binary class value are provided at mention-level and at document-level. Document-level values correspond to aggregation of information from all the mention contexts for the same mentioned software. For example, the following mention context indicates that the software `Mobyle` is shared. However, at document-level, other contexts further characterize the role of the software, indicating that it is also used and is a creation described in the research work corresponding to the document:

```json
{
    "context": "Availability: The Mobyle system is distributed under the terms of the GNU GPLv2 on the project web site (http://bioweb2.pasteur.fr/ projects/mobyle/).",
    "mentionContextAttributes": {
        "used": {
            "value": false,
            "score": 0.012282907962799072
        },
        "created": {
            "value": false,
            "score": 5.9604644775390625E-6
        },
        "shared": {
            "value": true,
            "score": 0.9282650947570801
        }
    },
    "documentContextAttributes": {
        "used": {
            "value": true,
            "score": 0.9994845390319824
        },
        "created": {
            "value": true,
            "score": 0.9999511241912842
        },
        "shared": {
            "value": true,
            "score": 0.9282650947570801
        }
    }
}
```

`references` describes the possible "reference markers" (also called "reference callouts") as extracted in the document associated to this software. There can be more than one reference markers associated to a software mention. For each reference marker, a `refKey` is used to link this reference marker to the full parsed bibliographical reference identified in the document (see next section). If the reference marker is present in the mention context, it is expressed with similar information as the extracted field attributes (so it will contain offsets and bounding box information). Otherwise, the reference marker information is propagated from other contexts. The original reference string is identified with the attribte `label` and its normalized form with the attribute `normalizedForm`.

```json
"references": [{
    "label": "(Wiederstein and Sippl, 2007)",
    "normalizedForm": "Wiederstein and Sippl, 2007",
    "refKey": 52,
    "offsetStart": 155,
    "offsetEnd": 184,
    "boundingBoxes": [{
        "p": 4,
        "x": 420.435,
        "y": 340.243,
        "w": 112.34849999999989,
        "h": 8.051699999999983
    }]
}]
```

### Full mention example 

Here is an example of a full JSON mention object following the described scheme, with a software name associated to a URL and a bibliographical reference marker, for a software used in the described research work:

```json
{
    "type": "software",
    "software-type": "software",
    "software-name": {
        "rawForm": "PSIPRED",
        "normalizedForm": "PSIPRED",
        "offsetStart": 87,
        "offsetEnd": 94,
        "boundingBoxes": [{
            "p": 4,
            "x": 530.87,
            "y": 171.074,
            "w": 39.4786,
            "h": 8.0517
        }]
    },
    "url": {
        "rawForm": "http://bioinf.cs.ucl.ac. \nuk/psipred",
        "normalizedForm": "http://bioinf.cs.ucl.ac. uk/psipred"
    },
    "context": "Secondary structure prediction of the SiDREB2 protein was performed using the program PSIPRED (Jones, 1999).",
    "mentionContextAttributes": {
        "used": {
            "value": true,
            "score": 0.9999935626983643
        },
        "created": {
            "value": false,
            "score": 5.7220458984375E-6
        },
        "shared": {
            "value": false,
            "score": 1.1920928955078125E-7
        }
    },
    "documentContextAttributes": {
        "used": {
            "value": true,
            "score": 0.9999935626983643
        },
        "created": {
            "value": false,
            "score": 9.179115295410156E-6
        },
        "shared": {
            "value": false,
            "score": 2.2649765014648438E-6
        }
    },
    "references": [{
        "label": "(Jones, 1999)",
        "normalizedForm": "Jones, 1999",
        "refKey": 24,
        "offsetStart": 95,
        "offsetEnd": 108,
        "boundingBoxes": [{
            "p": 4,
            "x": 313.346,
            "y": 181.052,
            "w": 49.19891666666666,
            "h": 8.051700000000011
        }]
    }]
}
```

## List of references 

Bibliographical references identified in the mention annotation are all listed in the document-level `references` array. 

The encoding is very simple and relies on GROBID TEI results. For every involved bibliographical references:

- an numerical element `refKey` is used as local identifier to cross-reference the reference citation in the mention context and the parsed reference

- the parsed bibliographical reference is previded in a `tei` element as standard TEI XML encoding. 

The XML string can then be retrieved from the JSON result and parsed with the appropriate XML parser. 

```json
"references": [{
        "refKey": 24,
        "tei": "<biblStruct xml:id=\"b24\">\n\t<analytic>\n\t\t<title level=\"a\" type=\"main\">Protein secondary structure prediction based on position-specific scoring matrices 1 1Edited by G. Von Heijne</title>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">David</forename><forename type=\"middle\">T</forename><surname>Jones</surname></persName>\n\t\t</author>\n\t\t<idno type=\"DOI\">10.1006/jmbi.1999.3091</idno>\n\t</analytic>\n\t<monogr>\n\t\t<title level=\"j\">Journal of Molecular Biology</title>\n\t\t<title level=\"j\" type=\"abbrev\">Journal of Molecular Biology</title>\n\t\t<idno type=\"ISSN\">0022-2836</idno>\n\t\t<imprint>\n\t\t\t<biblScope unit=\"volume\">292</biblScope>\n\t\t\t<biblScope unit=\"issue\">2</biblScope>\n\t\t\t<biblScope unit=\"page\" from=\"195\" to=\"202\" />\n\t\t\t<date type=\"published\" when=\"1999-09\">1999</date>\n\t\t\t<publisher>Elsevier BV</publisher>\n\t\t</imprint>\n\t</monogr>\n</biblStruct>\n"
    }, {
        "refKey": 44,
        "tei": "<biblStruct xml:id=\"b44\">\n\t<analytic>\n\t\t<title level=\"a\" type=\"main\">Comparative protein modelling by satisfaction of spatial restraints</title>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">A</forename><surname>Sali</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">T</forename><surname>Blundell</surname></persName>\n\t\t</author>\n\t</analytic>\n\t<monogr>\n\t\t<title level=\"j\">Journal of Molecular Biology</title>\n\t\t<imprint>\n\t\t\t<biblScope unit=\"volume\">234</biblScope>\n\t\t\t<biblScope unit=\"page\" from=\"779\" to=\"815\" />\n\t\t\t<date type=\"published\" when=\"1993\">1993</date>\n\t\t</imprint>\n\t</monogr>\n</biblStruct>\n"
    }, {
        "refKey": 52,
        "tei": "<biblStruct xml:id=\"b52\">\n\t<analytic>\n\t\t<title level=\"a\" type=\"main\">ProSA-web: interactive web service for the recognition of errors in three-dimensional structures of proteins</title>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">B</forename><surname>Wei</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">R</forename><forename type=\"middle\">L</forename><surname>Jing</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">C</forename><forename type=\"middle\">S</forename><surname>Wang</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">Xp ;</forename><surname>Chang</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">M</forename><surname>Wiederstein</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">M</forename><forename type=\"middle\">J</forename><surname>Sippl</surname></persName>\n\t\t</author>\n\t</analytic>\n\t<monogr>\n\t\t<title level=\"j\">Scientia Agricola Sinica</title>\n\t\t<imprint>\n\t\t\t<biblScope unit=\"volume\">39</biblScope>\n\t\t\t<biblScope unit=\"page\" from=\"407\" to=\"410\" />\n\t\t\t<date type=\"published\" when=\"2006\">2006. 2007</date>\n\t\t</imprint>\n\t</monogr>\n\t<note>Nucleic Acids Research</note>\n</biblStruct>\n"
    }
]
```

