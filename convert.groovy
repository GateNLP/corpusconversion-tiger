// conversion of the Tiger corpus to GATE
// This loads the whole file into memory for simplicity so it needs quite some RAM

import groovy.util.XmlParser
import groovy.util.XmlSlurper
import gate.*
import java.utils.*
import groovy.util.CliBuilder

def cli = new CliBuilder(usage:'convert.groovy [-h] [-v] [-n 1] infile outdir')
cli.h(longOpt: 'help', "Show usage information")
cli.n(longOpt: 'nsent', args: 1, argName: 'nsent', "Number of sentences per output document")
cli.v(longOpt: 'verbose', "Log each written document")
cli.f(longOpt: 'filepref', args: 1, argName: 'filepref', "Prefix to use for the output files, default is input file basename")

def options = cli.parse(args)
if(options.h) {
  cli.usage()
  return
}

verbose=false
if(options.v) verbose=true

def nsent = 1
if(options.n) {
  nsent = options.n.toInteger()
}

filepref = ""
if(options.f) filepref = options.f

def posArgs = options.arguments()
if(posArgs.size() != 2) {
  cli.usage()
  System.exit(1)
}

inFile = new File(posArgs[0])
outDir = new File(posArgs[1])

if(!inFile.exists()) {
  System.err.println("ERROR: file does not exist: "+inFile.getAbsolutePath())
  System.exit(1)
}
if(!outDir.exists() || !outDir.isDirectory()) {
  System.err.println("ERROR: file does not exist or is not a directory: "+outDir.getAbsolutePath())
  System.exit(1)
}

if(filepref.isEmpty()) {
  filepref = inFile.getName()
  filepref = filepref.replaceAll('\\.[a-zA-Z]+$',"")
}

System.err.println("INFO: input file is:        "+inFile)
System.err.println("INFO: output dir is:        "+outDir)
System.err.println("INFO: sentences per doc:    "+nsent)
System.err.println("INFO: output file prefix:   "+filepref)

SMAX = nsent

gate.Gate.init()
rt = Runtime.getRuntime()
rt.gc()
memTotal = rt.totalMemory()
memFree  = rt.freeMemory()
System.err.println("Loading corpus, total memory="+memTotal+", free="+memFree+" ...")
corpus = new XmlSlurper().parse(inFile)
rt.gc()
memTotal = rt.totalMemory()
memFree  = rt.freeMemory()
System.err.println("Corpus loaded, total memory="+memTotal+", free="+memFree)

// now first of all, get the header info want to keep
meta = corpus.head.meta

body = corpus.body
// iterate over each sentence

nDocs = 0

def makeDocname(from,to) {
  if(from.equals(to)) return filepref+"_"+from+".xml"
  else return filepref+"_"+from+"_"+to+".xml"
}

def writeDocument(sb, fs, froms, tos, name, sentenceInfos) {
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
  outputAS = doc.getAnnotations("Key")
  for(int i=0; i<fs.size(); i++) {
    gate.Utils.addAnn(outputAS,froms.get(i),tos.get(i),"Token",fs.get(i));
  }
  for(sentenceInfo in sentenceInfos) {
    gate.Utils.addAnn(outputAS,sentenceInfo['from'],sentenceInfo['to'],"Sentence",gate.Utils.featureMap("sentenceId",sentenceInfo['sId']))
  }
  //System.err.println("DEBUG: featurs added, writing")
  outFile = new File(outDir,name)
  gate.corpora.DocumentStaxUtils.writeDocument(doc,outFile)
  gate.Factory.deleteResource(doc)
  if(verbose) System.err.println("Saved GATE document "+outFile)
  nDocs += 1
}

def tokenInfo2Fm(tokenInfo) {
  fm = gate.Utils.featureMap()
  fm.putAll(tokenInfo)
  return fm
}

snr = 0
StringBuilder sb = new StringBuilder()
ArrayList<FeatureMap> fs = new ArrayList<FeatureMap>()
ArrayList<Integer> froms = new ArrayList<Integer>()
ArrayList<Integer> tos = new ArrayList<Integer>()
curFrom = 0
curFromPrev = 0
curTo = 0
sidFrom = ""
sentenceInfos = []
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
  addSpace = false     // at the beginning of a sentence, we do not add a space either
  sFrom = curFrom
  terms.each { term -> 
    a = term.attributes()
    string = a["word"]
    fm = tokenInfo2Fm(a)
    // add the string to the sb and remember the start and end offset of 
    // the annotation. Make sure not to add a space before the current word
    // if the current word is one of the characters in the regexp
    if(string.equals("*T*")) {
      // this special construct indicates some kind of multitoken word so we 
      // create another token for this without adding any string to the document text
      // and with the same offsets as the previous token (assuming this will 
      // always occur after the actual word token)
      curFrom = curFromPrev
    } else if(string.matches("[,;!?.:)}\\]']")) {
      sb.append(string)
      curTo += string.size()
      addSpace = true
    } else {
      // if the current word is not one of the characters above, add a space
      // before it, except the last word was one after which we do not want to
      // add a space either
      if(addSpace) { 
        sb.append(" ")
        curFrom += 1
      }
      sb.append(string)
      curTo = curFrom + string.size()
      addSpace = true
    }
    if(string.matches("[`({\\[]")) addSpace = false
    fs.add(fm)
    froms.add(curFrom)
    tos.add(curTo)
    curFromPrev = curFrom
    curFrom = curTo
  }
  sTo = curTo
  // add the sentence span and id
  sentenceInfos.add([from:sFrom, to:sTo, sId:sidTo])
  if(snr == SMAX) {
    // save what we have to a gate document
    writeDocument(sb,fs,froms,tos, makeDocname(sidFrom,sidTo), sentenceInfos)
    // reset 
    snr = 0
    curFrom = 0
    curFromPrev = 0
    curTo = 0
    sidFrom = ""
    sb = new StringBuilder()
    fs = new ArrayList<FeatureMap>()
    froms = new ArrayList<Integer>()
    tos = new ArrayList<Integer>()
    sentenceInfos = []
  } else {
    // not the last sentence, add a new line
    sb.append("\n");
    curFrom += 1
    curTo += 1
  }
  
}

System.err.println("Processing finished, documents written: "+nDocs)

if(sb.length() > 0) {
  writeDocument(sb,fs,froms,tos,makeDocname(sidFrom,sidTo),sentenceInfos)
}

