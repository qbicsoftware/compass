# Compass

A Java library to craft HTTP web links from serialized header data described
in [RFC 8288](https://datatracker.ietf.org/doc/html/rfc8288).

# Quick start

## Resolve dependency

```xml
<!-- You might want to check for the latest version -->
<groupId>life.qbic</groupId>
<artifactId>compass</artifactId>
<version>1.0.0</version>
```

Check the latest component version on [Maven Central](https://central.sonatype.com/artifact/life.qbic/compass/).

## Example: Level 1 FAIR Signposting Profile validation

```bash
curl -I https://zenodo.org/records/17179862
```

A simple HTTP GET request to the [Zenodo record](https://zenodo.org/records/17179862) will result in the following HTTP header:

```bash
HTTP/1.1 200 OK
server: nginx
date: Mon, 01 Dec 2025 12:14:33 GMT
content-type: text/html; charset=utf-8
content-length: 85404
vary: Accept-Encoding
link: <https://orcid.org/0009-0006-0929-9338> ; rel="author" , <https://ror.org/00v34f693> ; rel="author" , <https://ror.org/03a1kwz48> ; rel="author" , <https://doi.org/10.5281/zenodo.17179862> ; rel="cite-as" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/dcat+xml" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/ld+json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/ld+json;profile="https://datapackage.org/profiles/2.0/datapackage.json"" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/marcxml+xml" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.citationstyles.csl+json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.datacite.datacite+json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.datacite.datacite+xml" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.geo+json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.inveniordm.v1+json" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.inveniordm.v1.full+csv" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/vnd.inveniordm.v1.simple+csv" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/x-bibtex" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="application/x-dc+xml" , <https://zenodo.org/api/records/17179862> ; rel="describedby" ; type="text/x-bibliography" , <https://zenodo.org/records/17179862/files/22-09-2025_13-National-Biobanken-Symposium_FAIR-IN-Biobanking_SG.pdf> ; rel="item" ; type="application/pdf" , <https://zenodo.org/records/17179862/files/22-09-2025_13-National-Biobanken-Symposium_FAIR-IN-Biobanking_SG.odp> ; rel="item" ; type="application/octet-stream" , <https://zenodo.org/records/17179862/files/22-09-2025_13-National-Biobanken-Symposium_FAIR-IN-Biobanking_SG.pptx> ; rel="item" ; type="application/octet-stream" , <https://creativecommons.org/licenses/by/4.0/legalcode> ; rel="license" , <https://schema.org/PresentationDigitalDocument> ; rel="type" , <https://schema.org/AboutPage> ; rel="type" , <https://zenodo.org/api/records/17179862> ; rel="linkset" ; type="application/linkset+json"
```

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
// Access to cite-as relation URIs
view.citeAs();
// Access to describedby relation URIs
view.describedBy();
// Access to author relation URIs
view.author();
```
