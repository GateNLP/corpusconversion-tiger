// conversion of the Tiger corpus to GATE
// This loads the whole file into memory for simplicity so it needs quite some RAM

import groovy.util.XmlParser
import groovy.util.XmlSlurper
import gate.*
import java.utils.*


if(args.size() != 2) {
  System.err.println("Need two arguments: the tiger corpus file and the output directory")
  System.exit(1)
}

inFile = args[0]
outDir = args[1]

SMAX = 10

gate.Gate.init()
System.err.println("Loading corpus...")
corpus = new XmlSlurper().parse(new File(inFile))
System.err.println("Corpus loaded")

// now first of all, get the header info want to keep
meta = corpus.head.meta

body = corpus.body
// iterate over each sentence


def writeDocument(sb, fs, froms, tos, name) {
  //System.err.println("DEBUG: Writing doc "+name)
  // sanity check
  if(fs.size() == froms.size() && fs.size() == tos.size()) {
    // be happy
  } else {
    System.err.println("Sanity check failed!")
    System.exit(1)
  }
  content = sb.toString()
  //System.err.println("Got content: "+content.size())
  parms = Factory.newFeatureMap()
  parms.put(Document.DOCUMENT_STRING_CONTENT_PARAMETER_NAME, content)
  parms.put(Document.DOCUMENT_MIME_TYPE_PARAMETER_NAME, "text/plain")
  doc = (Document) gate.Factory.createResource("gate.corpora.DocumentImpl", parms)
  fmDoc = doc.getFeatures()
  fmDoc.put("tiger.name",meta.name.text())
  fmDoc.put("tiger.author",meta.author.text())
  fmDoc.put("tiger.date",meta.date.text())
  fmDoc.put("tiger.description",meta.description.text())
  fmDoc.put("tiger.history",meta.history.text())
  //System.err.println("DEBUG: adding features: "+fs.size())
  for(int i=0; i<fs.size(); i++) {
    gate.Utils.addAnn(doc.getAnnotations("Key"),froms.get(i),tos.get(i),"Token",fs.get(i));
  }
  //System.err.println("DEBUG: featurs added, writing")
  outFile = new File(outDir,name)
  gate.corpora.DocumentStaxUtils.writeDocument(doc,outFile)
  //System.err.println("Document saved: "+outFile)
}



snr = 0
StringBuilder sb = new StringBuilder()
ArrayList<FeatureMap> fs = new ArrayList<FeatureMap>()
ArrayList<Integer> froms = new ArrayList<Integer>()
ArrayList<Integer> tos = new ArrayList<Integer>()
curFrom = 0
curTo = 0
sidFrom = ""
body.s.each { sentence -> 
  //System.println("Processing sentence " + sentence.attributes()["id"])
  // we count sentences: whenever we got SMAX, we save what we have to
  // a new document
  snr += 1
  if(sidFrom.isEmpty()) {
    sidFrom = sentence.attributes()["id"]
  }
  sidTo = sentence.attributes()["id"]
  // get the list of terminals
  terms = sentence.graph.terminals.t
  terms.each { term -> 
    a = term.attributes()
    string = a["word"]
    fm = gate.Utils.featureMap(
      "lemma",a["lemma"],
      "pos",a["pos"],
      "morph",a["morph"],
      "case",a["case"],
      "number",a["number"],
      "gender",a["gender"],
      "person",a["person"],
      "degree",a["degree"],
      "tense",a["tense"],
      "mood",a["mood"]
    )
    // add the string to the sb and remember the start and end offset of 
    // the annotation
    if(string.equals(",") || string.equals(";") || string.equals("!") || string.equals("?") || string.equals(".") ||
       string.equals(":") || string.equals(")")) {
      sb.append(string)
      curTo += string.size()
    } else {
      sb.append(" ")
      sb.append(string)
      curFrom += 1
      curTo = curTo + string.size() +1
    }
    fs.add(fm)
    froms.add(curFrom)
    tos.add(curTo)
    curFrom = curTo
  }
  if(snr == SMAX) {
    // save what we have to a gate document
    name = "tiger_" + sidFrom + "_" + sidTo + ".xml"
    writeDocument(sb,fs,froms,tos, name)
    // reset 
    snr = 0
    curFrom = 0
    curTo = 0
    sidFrom = ""
    sb = new StringBuilder()
    fs = new ArrayList<FeatureMap>()
    froms = new ArrayList<Integer>()
    tos = new ArrayList<Integer>()
  }
  
}

if(sb.length() > 0) {
  name = "tiger_" + sidFrom + "_" + sidTo + ".xml"
  writeDocument(sb,fs,froms,tos,name)
}

