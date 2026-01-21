# FAIR Signposting Level 1 validation

> [!NOTE]
> This example is based on the specification
> of [FAIR Signposting Level 1](https://signposting.org/FAIR/#level1). It is helpful to read the
> specification to follow the examples.

Level 1 introduces a very lightweight approach to realize machine-actionable navigation between
scholarly objects in the web.
There is no concept of the more complex `linkset` yet (like in Level 2) and the normative rules for
required minimal information is less strict than in Level 2.

This, on the other hand, makes automated validation more challenging.

---

## FAIR Signposting recipes

In FAIR Signposting, a scholarly object consists of a **persistent identifier** and different
components (**recipes**) that describe a scholarly object. These are:

- Landing Page: Usually the web resource that is returned when the persistent identifier is
  resolved. This recipe contains information about the scholarly object and references content
  resources (like an article or dataset) and metadata resources (structured metadata about the
  scholarly object).
- Content Resource: A web resource that provides information about the content related to the
  scholarly object. Easiest example: the article as PDF.
- Metadata Resource: A web resource that provides metadata about the scholarly object in a commonly
  used format (e.g., `application/json+ld`)

See https://signposting.org/FAIR/#recipe for details.

### Landing Page recipe

At the time of writing, the Landing Page recipe is the only Level 1 recipe
that can be validated automatically using a heuristic.

It requires typed Web Links with the following relations:

- "cite-as" (cardinality of 1)
- "describedby" (cardinality of 1..n)

In practice, FAIR Signposting Level 1 is implemented using typed Web Links
conveyed via the HTTP `Link` response header.

Compass does not fetch resources itself.
Instead, it operates on already parsed Web Links and validates whether
their structure and semantics conform to the FAIR Signposting Level 1 recipes.

The typical processing flow therefore looks like this:

1. Retrieve an HTTP response
2. Extract and parse the `Link` header into WebLink objects
3. Validate and interpret those WebLinks using Compass

---

#### Parsing HTTP Link header into WebLink objects

The following HTTP response is taken directly from the
*Examples Level 1* section of the FAIR Signposting specification
and serves as **real-world input data** for the Compass validation example.

```http
HTTP/1.1 200 OK
Date: Fri, 9 Oct 2020 19:19:22 GMT
Content-Type: text/html
Content-Length: 25414
Link: <https://doi.org/10.5061/dryad.5d23f> ; rel="cite-as" , <https://schema.org/ScholarlyArticle> ; rel="type" , 
 <https://schema.org/AboutPage> ; rel="type" , <https://orcid.org/0000-0002-1825-0097> ; rel="author" , 
 <https://example.org/meta/7507/bibtex> ; rel="describedby" ; type="application/x-bibtex" , 
 <https://doi.org/10.5061/dryad.5d23f> ; rel="describedby" ; type="application/vnd.datacite.datacite+json" , 
 <https://spdx.org/licenses/CC-BY-4.0> ; rel="license" ,
 <https://example.org/file/7507/1> ; rel="item" ; type="application/pdf" , 
 <https://example.org/file/7507/2> ; rel="item" ; type="text/csv" , 
 <https://gitmodo.io/johnd/ct.zip> ; rel="item" ; type="application/zip"
```

Compass builds on top of Linksmith, which is responsible for parsing
HTTP `Link` header syntax into RFC 8288–compliant WebLink objects.
We can use Linksmith to parse and process the HTTP Link content (`Link: [content]`). At this stage,
no FAIR Signposting validation has happened yet —
we have only parsed the HTTP Link header into a structured model.

```java
// Raw HTTP Link header value (extracted from an HTTP response)
String linkHeader =
    "<https://doi.org/10.5061/dryad.5d23f> ; rel=\"cite-as\" , " +
        "<https://example.org/meta/7507/bibtex> ; rel=\"describedby\" ; type=\"application/x-bibtex\"";

// Parse HTTP Link header into WebLink model objects
WebLinkProcessor webLinkProcessor = new WebLinkProcessor.Builder().build();
ValidationResult parsingResult = webLinkProcessor.process(linkHeader);

// Parsed WebLinks are now ready for semantic validation
List<WebLink> webLinks = parsingResult.weblinks();
```

At this point, we have a list of parsed WebLinks. In the next step, we let Compass validate and
interpret these WebLinks
according to the FAIR Signposting Level 1 Landing Page recipe.

--- 

#### Validating Level 1 with Compass

With the list of WebLinks, we can now use the SignPostingProcessor. The builder will create a processor with the
`Level1SignPostingValidator` as validator by default. Only a Level 1 Landing Page can be reliably validated, due to the permissive
specification.

```java

SignPostingProcessor processor = new SignPostingProcessor.Builder().build();
SignPostingResult result = processor.process(linksmithResult.weblinks());

// You can investigate the validation findings in the result's issue report
if (result.issueReport().hasErrors()) {
  // Throw or investigate, if you desire to protect your downstream process flow
  throw new IllegalStateException("Signposting validation result contains errors");
}

// The SignPostingView provides some semantic API to interact with the WebLinks
// in the context of FAIR Signposting
SignPostingView signPostingView = result.signPostingView();

// For example you can access all "cite-as" typed WebLinks
for (WebLink link : signPostingView.citeAs()) {
  System.out.println("Cite as (URI): " + link.target());
}
// or authors
for (WebLink link : signPostingView.withRelationType("author")) {
  System.out.println("Author is (URI): " + link.target());
}
// or linked content resources
for (WebLink link : signPostingView.withRelationType("item")) {
  System.out.println("Item is (URI): " + link.target());
  System.out.println("Item MIME type is: " + link.type().orElse("no type available"));
}
```

That should result in an output like this:

```bash
SignPostingView[webLinks=[WebLink[target=https://doi.org/10.5061/dryad.5d23f, params=[WebLinkParameter[name=rel, value=cite-as]]], WebLink[target=https://schema.org/ScholarlyArticle, params=[WebLinkParameter[name=rel, value=type]]], WebLink[target=https://schema.org/AboutPage, params=[WebLinkParameter[name=rel, value=type]]], WebLink[target=https://orcid.org/0000-0002-1825-0097, params=[WebLinkParameter[name=rel, value=author]]], WebLink[target=https://example.org/meta/7507/bibtex, params=[WebLinkParameter[name=rel, value=describedby], WebLinkParameter[name=type, value=application/x-bibtex]]], WebLink[target=https://doi.org/10.5061/dryad.5d23f, params=[WebLinkParameter[name=rel, value=describedby], WebLinkParameter[name=type, value=application/vnd.datacite.datacite+json]]], WebLink[target=https://spdx.org/licenses/CC-BY-4.0, params=[WebLinkParameter[name=rel, value=license]]], WebLink[target=https://example.org/file/7507/1, params=[WebLinkParameter[name=rel, value=item], WebLinkParameter[name=type, value=application/pdf]]], WebLink[target=https://example.org/file/7507/2, params=[WebLinkParameter[name=rel, value=item], WebLinkParameter[name=type, value=text/csv]]], WebLink[target=https://gitmodo.io/johnd/ct.zip, params=[WebLinkParameter[name=rel, value=item], WebLinkParameter[name=type, value=application/zip]]]]]
Cite as (URI): https://doi.org/10.5061/dryad.5d23f
Author is (URI): https://orcid.org/0000-0002-1825-0097
Item is (URI): https://example.org/file/7507/1
Item type is: application/pdf
Item is (URI): https://example.org/file/7507/2
Item type is: text/csv
Item is (URI): https://gitmodo.io/johnd/ct.zip
Item type is: application/zip
```

Awesome, you can now use this machine-actionable result and include it in your application!
