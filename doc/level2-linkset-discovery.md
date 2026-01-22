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
```
Each returned WebLink identifies:
- the Link Set URI (link.target()),
- its media type (link.type()),
- optional profile or anchor parameters.
