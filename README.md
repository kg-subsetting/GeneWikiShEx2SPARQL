# SubShEx2SPARQL

Convert a subset of ShEx schemas to SPARQL queries and run them.

The subset of ShEx is the one used to create Wikidata Subsets and has the following format:

```
<A> {
  wdt:P31 [ wd:QidA ] ;
  wdt:PX1 @<B> *       ;
  wdt:PX2 @<C> *       ;
  . . .
}

<B> {
  wdt:P31 [ wd:QidB ] ;
  wdt:PY1 @<D> *      ;
  . . .       
}
```

And the SPARQL queries generated will have the following form:

```
SELECT (count(?A) as ?count_A) ?count_PX1B ?count_PX2C 
WHERE { 
  ??A wdt:P31 wd:QidA .

  { SELECT (count(?y) as ?count_PX1B) {
    ?x wdt:P31 wd:QidA .
    ?x wdt:PX1 ?y .
    ?y wdt:P31 wd:QidB .
  }}
 { SELECT (count(?y) as ?count_PX2C) {
    ?x wdt:P31 wd:QidA .
    ?x wdt:PX2 ?y .
    ?y wdt:P31 wd:QidC .
 }}
} GROUP BY ?count_PX1B ?count_PX2C
```

Those queries can be run to obtain statistics about the generated subsets.

There are three output formats: CSV, JSON and [PlantUML](https://plantuml.com/). 

# Running the program from Docker

The program has been written in Scala using SBT and has been published in Docker.

The following example shows how it can be run to run que queries and generate the results in CSV format.

```
docker run -v `pwd`/shex:/shex -v `pwd`/target:/target wesogroup/subshex2sparql:0.0.2 --shex /shex/GeneWiki.shex --runQueries --outputResults /target/wikidataResults.csv --outputFormat CSV
```


# Command line options

```
docker run wesogroup/subshex2sparql:0.0.2 --help
Usage: subshex2sparql [--shex <file>] [--output <folder>] [--endpoint <IRI>] [--graph <IRI>] [--outputResults <FilePath>] [--outputFormat <string>] [--runQueries] [--storeQueries] [--addLabels]

Convert a subset of ShEx to SPARQL queries

Options and flags:
    --help
        Display this help text.
    --version, -v
        Print the version number and exit.
    --shex <file>
        ShEx file
    --output <folder>
        Output folder
    --endpoint <IRI>
        Endpoint. Default = Wikidata
    --graph <IRI>
        graph name from endpoint. If not specified it takes default graph
    --outputResults <FilePath>
        FilePath to store results of queries
    --outputFormat <string>
        outputFormat. Possible values: JSON,CSV,PlantUML
    --runQueries, -r
        Run queries
    --storeQueries, -s
        Store queries
    --addLabels, -r
        Adds labels to properties obtained from Wikidata
```

