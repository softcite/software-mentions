Generating the training data will also create a vector of software name terms with all the software names used in the training data. This json file is under `software-mentions/doc/software-term-vector.json`

To get the disambiguation for these terms (in their overall context, so software), use the command:

> curl 'http://cloud.science-miner.com/nerd/service/disambiguate' -X POST -F "query=@doc/software-term-vector.json" > doc/software-term-vector-disambiguated.json 

There might some disambiguisation errors like for "zen" which gives the schools of Buddhism because of a super high-probability and a lack of textual context. But overall, the disambiguisation are reliable and wecan then use the statements for instance to identify web sites. 
