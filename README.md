# SHACL validator

Command-line SHACL validator based on Eclipse RDF4J and PicoCLI.

Input can be a local file or an URL, output is a Turtle, HTML or markdown report.
In addition, a few statistics on the classes and properties can written to the report as well.

Requires Java JDK 17.

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
| --shacl  | SHACL rules URL, a local or remote file (Turtle) |
| --report | One or more report output files (HTML, Markdown, Turtle) |
| --countClasses | Count different RDF classes in input data |
| --countProperties | Count different properties (predicates) in input data |
| --countValues | Count different values for one or more properties (IRI or prefixed value) |

The following prefixes are supported: dcat, dcterms, foaf, org, rdf, rdfs, rov, schema, skos, vcard.

| Return codes | Description |
|--------------|-------------|
| -1           | Something went wrong when configuring or loading data |
| 0            | No issues have been found, input data is fully in line with the SHACL rules |
| 1            | Data has issues with severity level sh:Violation |
| 2            | Data has issues with severity level sh:Warning |
| 3            | Data has issues with severity level sh:Info | 