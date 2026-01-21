<div align="center">

# Compass
**Java library for FAIR Signposting validation of RFC 8288 Web Links.**

[![Build Maven Package](https://github.com/qbicsoftware/compass/actions/workflows/build_package.yml/badge.svg)](https://github.com/qbicsoftware/compass/actions/workflows/build_package.yml)
[![Run Maven Tests](https://github.com/qbicsoftware/compass/actions/workflows/run_tests.yml/badge.svg)](https://github.com/qbicsoftware/compass/actions/workflows/run_tests.yml)
[![CodeQL](https://github.com/qbicsoftware/compass/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/qbicsoftware/compass/actions/workflows/codeql-analysis.yml)

[![release](https://img.shields.io/github/v/release/qbicsoftware/compass?include_prereleases)](https://github.com/qbicsoftware/compass/releases)
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.18327777.svg)](https://doi.org/10.5281/zenodo.18327777)
[![license](https://img.shields.io/github/license/qbicsoftware/compass)](https://github.com/qbicsoftware/compass/blob/main/LICENSE)

[![codecov](https://codecov.io/github/qbicsoftware/compass/graph/badge.svg?token=DAR8MZLF4R)](https://codecov.io/github/qbicsoftware/compass)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=qbicsoftware_compass&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=qbicsoftware_compass)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=qbicsoftware_compass&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=qbicsoftware_compass)

</div>

---

## ⚠️ Development Status

This repository is in an **early and experimental stage**.

- APIs may change or be removed without prior notice
- Documentation may be incomplete or outdated
- Backward compatibility is **not guaranteed**

Use at your own risk, but provide feedback and suggestions in an issue or contribution in form of a pull-request.

---

## Quick start

### Resolve dependency

```xml
<!-- You might want to check for the latest version -->
<groupId>life.qbic</groupId>
<artifactId>compass</artifactId>
<version>1.0.0</version>
```

Check the latest component version on [Maven Central](https://central.sonatype.com/artifact/life.qbic/compass/).

### Example: Level 1 FAIR Signposting Profile validation

```bash
curl -I https://zenodo.org/records/17179862
```

A simple HTTP GET request to the [Zenodo record](https://zenodo.org/records/17179862) will result in the following HTTP header:

<details>
<summary>Full HTTP Link header returned by Zenodo (real-world example)</summary>
    
```http
HTTP/1.1 200 OK
server: nginx
date: Mon, 01 Dec 2025 12:14:33 GMT
content-type: text/html; charset=utf-8
content-length: 85404
vary: Accept-Encoding
link: <https://orcid.org/0009-0006-0929-9338> ; rel="author" , <https://ror.org/00v34f693> ; rel="author" , <https://ror.org/03a1kwz48> ; rel="author" , <https://doi.org/10.5281/zenodo.17179862> ; rel="cite-as" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/dcat+xml" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/ld+json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/ld+json;profile="https://datapackage.org/profiles/2.0/datapackage.json"" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/marcxml+xml" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.citationstyles.csl+json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.datacite.datacite+json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.datacite.datacite+xml" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.geo+json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.inveniordm.v1+json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.inveniordm.v1.full+csv" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.inveniordm.v1.simple+csv" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/x-bibtex" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/x-dc+xml" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="text/x-bibliography" , <https://zenodo.org/records/17179862/files/22-09-2025_13-National-Biobanken-Symposium_FAIR-IN-Biobanking_SG.pdf> ; rel="item" ; type="application/pdf" , <https://zenodo.org/records/17179862/files/22-09-2025_13-National-Biobanken-Symposium_FAIR-IN-Biobanking_SG.odp> ; rel="item" ; type="application/octet-stream" , <https://zenodo.org/records/17179862/files/22-09-2025_13-National-Biobanken-Symposium_FAIR-IN-Biobanking_SG.pptx> ; rel="item" ; type="application/octet-stream" , <https://creativecommons.org/licenses/by/4.0/legalcode> ; rel="license" , <https://schema.org/PresentationDigitalDocument> ; rel="type" , <https://schema.org/AboutPage> ; rel="type" , <https://zenodo.org/api/records/17179862> ; rel="linkset" ; type="application/linkset+json"
```
</details>

For the sake of simplicity and to show the FAIR signposting use case Level 1, we use only
some of the link targets:

- the author
- the citation
- the record API endpoint for additional meta-data

```java
import life.qbic.compass.SignPostingProcessor;
import life.qbic.compass.model.SignPostingView;
import life.qbic.linksmith.core.WebLinkProcessor;
import life.qbic.linksmith.spi.WebLinkValidator.ValidationResult;

// Raw header of an HTTP response with link attribute
// 'link: <https://orcid.org/0009-0006-0929-9338> ; rel="author" , <https://ror.org/00v34f693> ; rel="author"'
String rawHeader =
    '<https://orcid.org/0009-0006-0929-9338> ; rel="author" , <https://doi.org/10.5281/zenodo.17179862> ; rel="cite-as" ,  <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/json"';

WebLinkProcessor webLinkProcessor = new WebLinkProcessor.Builder().build();
ValidationResult result = webLinkProcessor.process(rawHeader);

// Investigate the validation result
// ...

// Fetch the weblinks and let compass process them
// Default builds to FAIR Signposting Level 1 validation
SignPostingProcessor processor = new SignPostingProcessor.Builder().build();
SignPostingResult signPostResult = processor.process(result.weblinks());

if(signPostResult.containsIssues()){
    // Retrieve the report
    var report = result.report();
    // Investigate the report 
    // ...
    return;
}

SignPostingView view = signPostResult.signPostingView();

// Access to cite-as typed WebLinks
List<WebLink> citeAs = view.citeAs();

// Access to describedby typed WebLinks
List<WebLink> metadata = view.describedBy();

// Access to author typed WebLinks
List<WebLink> authors = view.author();
```

### More examples

Compass comes with example-driven documentation that explains common
FAIR Signposting validation scenarios step by step:

- FAIR Signposting **Level 1** validation: [`doc/level1-basic-validation.md`](doc/level1-basic-validation.md)

- FAIR Signposting **Level 2 discovery** using Link Sets (RFC 9264)

- Handling **multiple Link Sets** with different aggregation strategies

- Validation of **incomplete or non-compliant** Signposting data

Each document is self-contained and focuses on a single concept,
using real-world HTTP Link header examples and minimal Java snippets.

---

## What is Compass?

**Compass** is a lightweight Java library for **validating and interpreting FAIR Signposting**.
It builds on [Linksmith](https://github.com/qbicsoftware/linksmith) and adds:

- defensive [RFC 8288](https://datatracker.ietf.org/doc/html/rfc8288) *model-level* validation,
- [FAIR Signposting](https://signposting.org/FAIR/) Level 1 and Level 2 recipe validation,
- semantic views and Link Set ([RFC 9264](https://www.rfc-editor.org/rfc/rfc9264.html)) processing.

Compass operates entirely **in memory**, performs **no network requests**,  
and works with `WebLink` objects regardless of how they were created.

## Why Compass?

| Without Compass | With Compass |
|-----------------|--------------|
| Assume WebLinks are well-formed | Defensive validation of RFC 8288 *model invariants* |
| Validation tied to parsing strategy | Validation independent of how WebLinks were created |
| Manual inspection of `rel` values and parameters | Semantic views for Landing Pages, Metadata Resources, and Content Resources |
| No clear distinction between Level 1 and Level 2 Signposting | Explicit validators for Level 1 and Level 2 recipes |
| Link Sets parsed and interpreted ad-hoc | Ready-to-use parsers for RFC 9264 Link Set formats |
| Ambiguous handling of multiple Link Sets | Pluggable aggregation strategies with defined semantics |
| Validation logic scattered across client code | Centralized, reusable Signposting validation pipeline |


## How Compass fits in

Compass is designed to complement — not replace — Linksmith:

- **Linksmith**
  - Parses Web Linking syntax
  - Validates [RFC 8288](https://datatracker.ietf.org/doc/html/rfc8288) during parsing
  - Produces `WebLink` model objects

- **Compass**
  - Does not assume how `WebLink`s were produced
  - Validates [RFC 8288](https://datatracker.ietf.org/doc/html/rfc8288) *model-level constraints* defensively
  - Applies [FAIR Signposting](https://signposting.org/FAIR/) semantics and recipes
  - Interprets Level 2 Link Sets ([RFC 9264](https://www.rfc-editor.org/rfc/rfc9264.html))

Together, they form a clean separation of concerns:

- *Linksmith*: “Is this syntactically valid Web Linking?”
- *Compass*: “Is this WebLink model sound, and what does it mean for FAIR Signposting?”


