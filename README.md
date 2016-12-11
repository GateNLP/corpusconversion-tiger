# Tool to convert the Tiger XML format to GATE

For a description of the format, see http://www.ims.uni-stuttgart.de/forschung/ressourcen/werkzeuge/TIGERSearch/doc/html/TigerXML.html

## How to run

* copy or link the uncompressed/unpacked corpus file into the current directory, this script expects the file
  tiger_release_aug07.corrected.16012013.xml
  Adapt the script if the name is different
* make sure run.sh is executable, groovy is installed and GATE_HOME is set
* ./run.sh
* this should create directory out with documents which contain 10 sentences each, 5048 documents in total

## Metadata

We extract some metadata from the header and add it to every GATE document as document features.
This is done for convenience and for legal reasons, so that it is clear from looking at each 
document that it was converted from the Tiger corpus.

The following fields are converted to document features:
* meta.name
* meta.author
* meta.date
* meta.description
* meta.history

## Document data

The corpus has been collected from complete articles of Frankurter Allgemeine Zeitung, but there seems 
to be no explicity indication where one article ends and another starts, let alone any identification
of article metadata.
An indirect indication of article end could be the a sentence of the form "sch" "/" "rtr" or of the form
"GABRIELE" "VENZKY".
if "MARTIN DAHMS (Göttingen)"

But there are other boundaries which cannot be detected like this, e.g. between sentence s177 s178

Maybe also "Mit ... sprach .. FR-Redakteur/in ..."?

After looking through more of the corpus, it becomes clear that none of those heuristics will work,
sometimes the author is missing, sometimes specified at the top of the article etc. 
So for now we just ignore this and split the corpus up so that each document contains a maximum 
of 50 sentences.

## Converter strategy 

* go through the XML 
* if in head, parse and store metainformation we want to add to each document
* if in body, iterate over the s elements
* for each s element, get the graph element
* from the graph element, get the terminals element first and iterate over the t elements to get the words
  Also get the id of the root from the graph element
* for each t element, create a feature map first, map the id to the number in the feature map array.
  Store the string in a second array of word strings
* Each feature map gets the lemma, pos, morph, case, number, gender, person, degree, thense and mood features
* from the graph element get the nonterminals element and iterate over the nt elements
* for each nt element get all the contained edge elements
  Represent the tree somehow, one possibility is:
  * each nt is an annotation that coveres all the annotations associated with the edge nodes 
  * each edge is an annotation of type label covering the destination node and having an id field for
    from and to node annotations
  * or each nt annotation contains, for each label, a list of ids for edges with that label, e.g. NK_ids=[23,55]
* NOTE in the first version for just testing the lemmatizer, we can ignore the no-terminals


 
