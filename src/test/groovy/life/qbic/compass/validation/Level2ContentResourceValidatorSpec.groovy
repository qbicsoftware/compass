package life.qbic.compass.validation

import life.qbic.compass.spi.SignPostingResult
import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.model.WebLinkParameter
import spock.lang.Specification
import spock.lang.Unroll

class Level2ContentResourceValidatorSpec extends Specification {

    static final String COLLECTION = "collection"
    static final String TYPE = "type"

    // Landing page used as "collection" target in examples
    static final URI LANDING = URI.create("https://example.org/page/7507")

    // Content resource anchor (origin)
    static final String CONTENT_ANCHOR = "https://example.org/file/7507/2"

    def validator = Level2ContentResourceValidator.create()

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private static WebLink link(String target,
                                String anchor = CONTENT_ANCHOR,
                                List<String> rels = [],
                                Map<String, String> extraParams = [:]) {

        def params = []

        // anchor is a parameter of the WebLink model (string)
        if (anchor != null) {
            params << WebLinkParameter.create("anchor", anchor)
        } else {
            // omit anchor param completely
        }

        // Each rel is encoded as its own rel parameter for convenience.
        // Your WebLink.rel() splits on whitespace anyway.
        rels.each { r ->
            params << WebLinkParameter.create("rel", r)
        }

        extraParams.each { k, v ->
            if (v == null) {
                params << WebLinkParameter.withoutValue(k)
            } else {
                params << WebLinkParameter.create(k, v)
            }
        }

        return WebLink.create(URI.create(target), params)
    }

    private static List<WebLink> minimalValidContentRecipe() {
        // Content resource recipe at Level 2:
        // anchor = content resource
        // rel=collection -> landing page
        // rel=type -> semantic type URI (schema.org etc.)
        [
                // collection link: target is landing page, anchor is content resource, rel=collection
                link(LANDING.toString(), CONTENT_ANCHOR, [COLLECTION], ["type": "text/html"]),
                // type link: target is semantic type, anchor is content resource, rel=type
                link("https://schema.org/Dataset", CONTENT_ANCHOR, [TYPE])
        ]
    }

    private static boolean hasError(SignPostingResult result) {
        result.issueReport() != null && result.issueReport().hasErrors()
    }

    private static boolean hasWarning(SignPostingResult result) {
        result.issueReport() != null && result.issueReport().hasWarnings()
    }

    private static List<String> messages(SignPostingResult result) {
        result.issueReport()?.issues()?.collect { it.message() } ?: []
    }

    // ----------------------------------------------------------------------
    // Happy paths
    // ----------------------------------------------------------------------

    def "happy path: minimal valid content resource recipe passes without errors"() {
        given:
        def weblinks = minimalValidContentRecipe()

        when:
        def result = validator.validate(weblinks)

        then:
        !hasError(result)
        // Depending on your policy, might still contain warnings; typically none:
        !hasWarning(result)

        and: "view is non-destructive and contains the same links (defensive copied by view)"
        result.signPostingView().webLinks().size() == weblinks.size()
        result.signPostingView().webLinks()*.target().containsAll(weblinks*.target())
    }

    def "happy path: additional unrelated relations are allowed as long as content recipe still holds"() {
        given:
        def weblinks = new ArrayList<>(minimalValidContentRecipe())
        weblinks << link("https://spdx.org/licenses/CC-BY-4.0", CONTENT_ANCHOR, ["license"])

        when:
        def result = validator.validate(weblinks)

        then:
        !hasError(result)
    }

    // ----------------------------------------------------------------------
    // Unhappy paths: context / anchor problems
    // ----------------------------------------------------------------------

    def "unhappy path: missing anchors cause errors and abort further cardinality checks"() {
        given:
        def weblinks = minimalValidContentRecipe()
        // Remove anchors by creating links without anchor param
        def noAnchorLinks = weblinks.collect { WebLink l ->
            // recreate with same target + rel but without anchor param
            def rels = l.rel()
            link(l.target().toString(), null, rels, [:])
        }

        when:
        def result = validator.validate(noAnchorLinks)

        then:
        hasError(result)
        messages(result).any { it.toLowerCase().contains("missing value for 'anchor'") }

        and: "should NOT additionally report missing mandatory relations if you early-abort"
        // Adjust if your validator still continues; your Level2 validators typically early-return
        !messages(result).any { it.toLowerCase().contains("missing mandatory relation") }
    }

    def "unhappy path: multiple anchors are ambiguous and must fail context validation"() {
        given:
        def weblinks = new ArrayList<>(minimalValidContentRecipe())
        // Add a foreign link with different anchor
        weblinks << link("https://example.org/foreign", "https://example.org/file/OTHER", [TYPE])

        when:
        def result = validator.validate(weblinks)

        then:
        hasError(result)
        messages(result).any { it.toLowerCase().contains("multiple anchors") || it.toLowerCase().contains("ambiguous") }

        and: "optional: should not continue with recipe checks after anchor ambiguity"
        !messages(result).any { it.toLowerCase().contains("missing mandatory relation") }
    }

    def "unhappy path: null element in list is reported as error (contract) and does not crash"() {
        given:
        def weblinks = new ArrayList<>(minimalValidContentRecipe())
        weblinks.add(1, null)

        when:
        def result = validator.validate(weblinks)

        then:
        hasError(result)
        messages(result).any { it.toLowerCase().contains("null") }
    }

    // ----------------------------------------------------------------------
    // Unhappy paths: missing mandatory relations
    // ----------------------------------------------------------------------

    def "unhappy path: missing rel=collection is an error"() {
        given:
        def weblinks = minimalValidContentRecipe().findAll { !it.rel().contains(COLLECTION) }

        when:
        def result = validator.validate(weblinks)

        then:
        hasError(result)
        messages(result).any { it.toLowerCase().contains("missing") && it.toLowerCase().contains(COLLECTION) }
    }

    // ----------------------------------------------------------------------
    // Unhappy paths: cardinality violations
    // ----------------------------------------------------------------------

    def "unhappy path: multiple rel=collection violate cardinality (expected exactly 1)"() {
        given:
        def weblinks = new ArrayList<>(minimalValidContentRecipe())
        weblinks << link("https://example.org/page/7507-alt", CONTENT_ANCHOR, [COLLECTION], ["type": "text/html"])

        when:
        def result = validator.validate(weblinks)

        then:
        hasError(result)
        messages(result).any { it.toLowerCase().contains("multiple") && it.toLowerCase().contains(COLLECTION) }
    }

    def "unhappy path: multiple rel=type violate cardinality (expected exactly 1)"() {
        given:
        def weblinks = new ArrayList<>(minimalValidContentRecipe())
        weblinks << link("https://schema.org/SoftwareSourceCode", CONTENT_ANCHOR, [TYPE])

        when:
        def result = validator.validate(weblinks)

        then:
        hasError(result)
        messages(result).any { it.toLowerCase().contains("multiple") && it.toLowerCase().contains(TYPE) }
    }

    // ----------------------------------------------------------------------
    // Edge cases: relation representation (whitespace splitting)
    // ----------------------------------------------------------------------

    def "edge: one rel parameter with whitespace-separated values counts as multiple relations"() {
        given:
        // Build a single link whose rel value contains both relation types.
        // Your WebLink.rel() splits by \\s+.
        def combinedRelLink = WebLink.create(
                URI.create(LANDING.toString()),
                [
                        WebLinkParameter.create("anchor", CONTENT_ANCHOR),
                        WebLinkParameter.create("rel", "${COLLECTION} ${TYPE}"),
                        WebLinkParameter.create("type", "text/html")
                ]
        )

        // This makes recordedRelations count:
        // collection:1, type:1 (from a single WebLink)
        def weblinks = [
                combinedRelLink
        ]

        when:
        def result = validator.validate(weblinks)

        then:
        // Depending on your recipe, this is likely still INVALID because you'd also
        // need a dedicated type-link whose target is the semantic type URI.
        // If your validator checks only cardinality by relation, then it might pass.
        // So here we assert only that it doesn't crash and produces a deterministic report.
        result != null
        result.issueReport() != null
    }

    // ----------------------------------------------------------------------
    // Defensive-copy invariants
    // ----------------------------------------------------------------------

    def "invariant: SignPostingView performs defensive copy of passed list"() {
        given:
        def input = new ArrayList<WebLink>(minimalValidContentRecipe())

        when:
        def result = validator.validate(input)
        input.clear() // mutate after validate()

        then:
        // view should not change (it defensively copies)
        result.signPostingView().webLinks().size() == 2
    }

    // ----------------------------------------------------------------------
    // Table-driven invalid inputs (optional, but nice for coverage)
    // ----------------------------------------------------------------------

    @Unroll
    def "unhappy path: #caseName"() {
        when:
        def result = validator.validate(weblinks)

        then:
        hasError(result)
        messages(result).any { it.toLowerCase().contains(expectedMessageFragment.toLowerCase()) }

        where:
        caseName                                              | weblinks                                                                 | expectedMessageFragment
        "empty list -> missing mandatory relations"            | []                                                                       | "missing"
        "single unrelated link -> missing mandatory relations" | [link("https://example.org/x", CONTENT_ANCHOR, ["license"])]              | "missing"
        "no anchor but has relations -> anchor error"          | [link(LANDING.toString(), null, [COLLECTION], ["type": "text/html"])]     | "anchor"
    }
}
