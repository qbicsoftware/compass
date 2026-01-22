# FAIR Signposting Level 2 Link Set discovery

> [!NOTE]
> This example follows the specification of
> [FAIR Signposting Level 2](https://signposting.org/FAIR/#level2).
> Familiarity with Level 1 Signposting is recommended.

FAIR Signposting Level 2 extends Level 1 by introducing **Link Sets**
([RFC 9264](https://www.rfc-editor.org/rfc/rfc9264.html)) to describe
relationships that cannot be expressed reliably using inline HTTP `Link` headers alone.

Instead of embedding all relations directly in the response headers,
a resource may advertise one or more **external Link Set resources**
using the `rel="linkset"` relation.

Compass supports **Level 2 discovery** by:

- detecting advertised Link Sets,
- parsing Link Set representations,
- validating their structure and semantics,
- and exposing their content in a machine-actionable way.

Compass does **not** perform network requests — Link Sets must be retrieved
by client code and passed in explicitly.

---

## Conceptual overview

At Level 2, Signposting is split into two steps:

1. **Discovery**
    - Inline HTTP `Link` headers advertise one or more Link Sets using `rel="linkset"`.
2. **Interpretation**
    - The Link Set documents contain the full Signposting graph
      (Landing Pages, Content Resources, Metadata Resources, etc.).

This separation allows:

- richer metadata descriptions,
- multiple origins per scholarly object,
- reuse of Signposting information across representations.

---

## Step 1: Discovering Link Sets

A Level 2-enabled resource advertises Link Sets via the HTTP `Link` header:

```http
HTTP/1.1 200 OK
Link: <https://example.org/linkset.json> ; rel="linkset" ; type="application/linkset+json"
```

Using Linksmith, the Link header is parsed into WebLink objects:

```java
WebLinkProcessor processor = new WebLinkProcessor.Builder().build();
ValidationResult parsingResult = processor.process(linkHeader);

List<WebLink> webLinks = parsingResult.weblinks();
```

Compass can now identify advertised Link Sets:

```java
SignPostingProcessor compass = new SignPostingProcessor.Builder().build();
SignPostingResult result = compass.process(webLinks);

SignPostingView view = result.signPostingView();

// Discover advertised Link Sets
List<WebLink> linksets = view.linkSet();
for(
WebLink linkset :linksets){
    System.out.

println("Link Set URI: "+linkset.target());
    System.out.

println("Media type  : "+linkset.type().

orElse("<unknown>"));
    }
```

Each returned WebLink identifies:

- the Link Set URI (link.target()),
- its media type (link.type()),
- optional profile or anchor parameters.

At this point you have the Link Set URIs and media types, but **you still need to retrieve the Link
Set content**.

> [!NOTE]
> Compass does not perform any network requests, so you have to query the Link Set URI yourself with
> a client of your choice. To follow through the example, an example Link Set encoded in JSON of
> MIME type ``application/linkset+json`` is available for you in [
`doc/linkset-example.json`](linkset-example.json).

--- 

## Step 2: Parsing an application/linkset+json Link Set (RFC 9264)

RFC 9264 defines a JSON serialization for Link Sets using this general structure:

- Top-level object contains a "linkset" array
- Each entry can specify an "anchor" URI
- Link relation types become JSON member names whose values are arrays of link objects
- Link objects at minimum contain "href", and may include link parameters like "type", "hreflang", "
  title", etc.##

---

## Example file: a minimal FAIR Signposting Level 2 Link Set (application/linkset+json)

Save the following JSON as a file next to this documentation, for example:

- [`doc/linkset-example.json`](linkset-example.json)

<details>
<summary>Click to expand: linkset-example.json</summary>

```json
{
  "linkset": [
    {
      "anchor": "https://example.org/landing/123",
      "cite-as": [
        {
          "href": "https://doi.org/10.1234/example.123"
        }
      ],
      "describedby": [
        {
          "href": "https://example.org/metadata/123",
          "type": "application/ld+json"
        }
      ],
      "item": [
        {
          "href": "https://example.org/content/123/article.pdf",
          "type": "application/pdf"
        },
        {
          "href": "https://example.org/content/123/data.csv",
          "type": "text/csv"
        }
      ],
      "license": [
        {
          "href": "https://creativecommons.org/licenses/by/4.0/"
        }
      ],
      "type": [
        {
          "href": "https://schema.org/Dataset"
        }
      ]
    }
  ]
}
```
</details>

This Link Set contains:
- 1x Landing Page recipe anchor: https://example.org/landing/123
- 1x Metadata Resource (rel="describedby")
- 2x Content Resources (rel="item")
- plus cite-as, license, and type links commonly used in Signposting graphs

--- 

---

## Step 3: Validating a Link Set and accessing Level 2 views

After parsing a Link Set document, Compass can validate its structure and semantics
using one or more **Level 2 validators**.

> [!IMPORTANT]
> Not every Level 2 validator produces a `Level2LinksetView`.
>
> Only validators that *interpret complete Level 2 recipes* expose a semantic
> view of the Link Set contents.

Currently, this is provided by:

- `Level2RecipeValidator`

Other Level 2 validators may:
- validate structural or normative constraints,
- report issues,
- or enforce profile-specific rules  
  **without** constructing a Link Set view.

### Running a Level 2 recipe validator

```java
LinkSetParser parser = new LinkSetJsonParser();

// Link Set content retrieved by client code (e.g., HTTP GET)
// String rawLinkSetJson = ...
List<WebLink> linksetLinks = parser.parse(rawLinkSetJson);

// Run a Level 2 validator that produces a Level2LinksetView
SignPostingProcessor processor = new SignPostingProcessor.Builder()
    .withValidators(Level2RecipeValidator.create())
    .build();

SignPostingResult result = processor.process(linksetLinks);

// The Level 2 view is attached to the result (if produced by the validator)
Level2LinksetView view = Optional.ofNullable(result.level2LinksetView())
    .orElseThrow(() -> new IllegalStateException("No Level2LinksetView produced"));
```
