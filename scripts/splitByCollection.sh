#!/bin/bash

# xmlstarlet
xmlstarlet tr splitByCollection.xsl ../resources/dataset/software/corpus/softcite_corpus.tei.xml > ../resources/dataset/software/corpus/softcite_corpus_pmc.tei.xml

# xsltproc 
# xsltproc splitByCollection.xsl ../resources/dataset/software/corpus/softcite_corpus.tei.xml > ../resources/dataset/software/corpus/softcite_corpus_pmc.tei.xml
