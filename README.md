# SHACL validator

Command-line SHACL validator based on Eclipse RDF4J and PicoCLI.

The RDF input can be a local file or an URL, output is a report in Turtle, HTML or markdown.

In addition, the tool can also collect a few statistics on the number of different classes and properties
being used in the file, and even a list of the different values for one or more specific properties.

A typical use case is to validate a DCAT-AP metadata file, and produce an overview of the different categories
(dcat:theme) and licenses being used.

Requires Java 17 runtime.

## Usage

Example:
```
java -jar --data=file:///C:\data\rdf.nt --shacl=https://example.com/shapes.shacl \
    --report=C:\data\report.html --report=C:\Data\report.md \
    --countClasses --countProperties --countValues=dcat:theme --countValues=dcat:mediaType
```

| Argument | Description |
|----------|-------------|
| --data   | Input data URL, a local or remote file (N-Triples, JSON-LD, RDF/XML, Turtle) |
| --shacl  | SHACL rules URLs, on ore more local or remote files (Turtle) |
| --report | One or more report output files (HTML, Markdown, Turtle) |
| --countClasses | Count different RDF classes in input data |
| --countProperties | Count different properties (predicates) in input data |
| --countValues | Count different values for one or more properties (IRI or prefixed value °) |

° The following prefixes are supported: dcat, dcterms, foaf, org, rdf, rdfs, rov, schema, skos, vcard.


## Return codes

The return code of the validation can be used do check whether the validation was successful or not.
(%ERRORLEVEL% variable in MS-Windows CMD shell, $? in POSIX shells like Bash) 

| Return codes | Description |
|--------------|-------------|
| -1           | Something went wrong when configuring or loading data |
| 0            | No issues have been found, input data is fully in line with the SHACL rules |
| 1            | Data has issues with severity level sh:Violation |
| 2            | Data has issues with severity level sh:Warning |
| 3            | Data has issues with severity level sh:Info | 