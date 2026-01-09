package life.qbic.compass.validation

import life.qbic.compass.model.SignPostingView
import life.qbic.compass.spi.SignPostingResult
import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.model.WebLinkParameter
import life.qbic.linksmith.spi.WebLinkValidator
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Exhaustive unit tests for the "parent" Level 2 validator that determines the recipe
 * (Landing Page / Metadata Resource / Content Resource) and delegates to specialized validators.
 *
 * This spec avoids mocking final classes/records by returning real SignPostingResult instances.
 */
class Level2RecipeRoutingValidatorSpec extends Specification {

    // --- Relations used for recipe detection ---
    static final String CITE_AS = "cite-as"
    static final String DESCRIBEDBY = "describedby"
    static final String ITEM = "item"
    static final String TYPE = "type"
    static final String COLLECTION = "collection"
    static final String DESCRIBES = "describes"
    static final String LICENSE = "license"

    // anchors
    static final String LANDING_ANCHOR = "https://example.org/page/7507"
    static final String CONTENT_ANCHOR = "https://example.org/file/7507/2"
    static final String META_ANCHOR = "https://example.org/meta/7507/bibtex"

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private static WebLink weblink(String target,
                                   String anchor,
                                   List<String> rels,
                                   Map<String, String> extras = [:]) {

        def params = []
        if (anchor != null) {
            params << WebLinkParameter.create("anchor", anchor)
        }

        rels.each { r ->
            params << WebLinkParameter.create("rel", r)
        }

        extras.each { k, v ->
            if (v == null) params << WebLinkParameter.withoutValue(k)
            else params << WebLinkParameter.create(k, v)
        }

        WebLink.create(URI.create(target), params)
    }

    private static SignPostingResult childResult(List<WebLink> links, List<WebLinkValidator.Issue> issues) {
        new SignPostingResult(new SignPostingView(links), new WebLinkValidator.IssueReport(issues))
    }

    private static boolean hasError(SignPostingResult r) {
        r.issueReport() != null && r.issueReport().hasErrors()
    }

    private static List<String> messages(SignPostingResult r) {
        r.issueReport()?.issues()?.collect { it.message() } ?: []
    }

    // ----------------------------------------------------------------------
    // Given: specialized validators as injected stubs
    // ----------------------------------------------------------------------

    def landingValidator = Stub(Level2LandingPageValidator)
    def metadataValidator = Stub(Level2MetadataResourceValidator)
    def contentValidator = Stub(Level2ContentResourceValidator)

    /**
     * Replace this with your actual parent validator class.
     *
     * It should accept specialized validators so tests can verify routing without reflection.
     *
     * Example shape:
     *   new Level2RecipeValidator(landingValidator, metadataValidator, contentValidator)
     */
    def parent = Level2RecipeValidator.create(landingValidator, metadataValidator, contentValidator)

    // ----------------------------------------------------------------------
    // Recipe fixtures (minimal “detectors”)
    // ----------------------------------------------------------------------

    private static List<WebLink> landingRecipeLinks() {
        [
                weblink("https://doi.org/10.123/abc", LANDING_ANCHOR, [CITE_AS]),
                weblink("https://example.org/meta/7507/bibtex", LANDING_ANCHOR, [DESCRIBEDBY], ["type": "application/x-bibtex"]),
                weblink("https://example.org/file/7507/2", LANDING_ANCHOR, [ITEM], ["type": "text/csv"]),
                weblink("https://schema.org/ScholarlyArticle", LANDING_ANCHOR, [TYPE])
        ]
    }

    private static List<WebLink> metadataRecipeLinks() {
        [
                weblink("https://example.org/page/7507", META_ANCHOR, [DESCRIBES], ["type": "text/html"])
        ]
    }

    private static List<WebLink> contentRecipeLinks() {
        [
                weblink("https://example.org/page/7507", CONTENT_ANCHOR, [COLLECTION], ["type": "text/html"]),
                weblink("https://schema.org/Dataset", CONTENT_ANCHOR, [TYPE])
        ]
    }

    // ----------------------------------------------------------------------
    // Routing tests
    // ----------------------------------------------------------------------

    def "routes landing-page recipe to LandingPageValidator and aggregates its issues"() {
        given:
        def input = landingRecipeLinks()

        and: "child validator returns a result with an issue"
        landingValidator.validate(_ as List<WebLink>) >> { List<WebLink> links ->
            childResult(links, [Issue.warning("landing-warning")])
        }

        when:
        def result = parent.validate(input)

        then: "landing validator invoked once"
        1 * landingValidator.validate(_ as List<WebLink>)
        0 * metadataValidator.validate(_)
        0 * contentValidator.validate(_)

        and: "issues propagated"
        !hasError(result)
        messages(result).contains("landing-warning")

        and: "final view contains the original (safe) links"
        result.signPostingView().webLinks().size() == input.size()
    }

    def "routes metadata recipe to MetadataResourceValidator"() {
        given:
        def input = metadataRecipeLinks()

        metadataValidator.validate(_ as List<WebLink>) >> { List<WebLink> links ->
            childResult(links, [Issue.warning("meta-warning")])
        }

        when:
        def result = parent.validate(input)

        then:
        0 * landingValidator.validate(_)
        1 * metadataValidator.validate(_ as List<WebLink>)
        0 * contentValidator.validate(_)

        and:
        messages(result).contains("meta-warning")
    }

    def "routes content recipe to ContentResourceValidator"() {
        given:
        def input = contentRecipeLinks()

        contentValidator.validate(_ as List<WebLink>) >> { List<WebLink> links ->
            childResult(links, [Issue.warning("content-warning")])
        }

        when:
        def result = parent.validate(input)

        then:
        0 * landingValidator.validate(_)
        0 * metadataValidator.validate(_)
        1 * contentValidator.validate(_ as List<WebLink>)

        and:
        messages(result).contains("content-warning")
    }

    // ----------------------------------------------------------------------
    // No-recipe / ambiguous cases
    // ----------------------------------------------------------------------

    def "records error when no recipe can be determined (no distinguishing relations present)"() {
        given:
        def input = [
                weblink("https://example.org/x", LANDING_ANCHOR, [LICENSE]),
                weblink("https://example.org/y", LANDING_ANCHOR, ["author"])
        ]

        when:
        def result = parent.validate(input)

        then: "no child validator should be called"
        0 * landingValidator.validate(_)
        0 * metadataValidator.validate(_)
        0 * contentValidator.validate(_)

        and: "error reported"
        hasError(result)
        messages(result).any { it.toLowerCase().contains("no recipe") || it.toLowerCase().contains("cannot be determined") }

        and: "still returns a view"
        result.signPostingView().webLinks().size() == input.size()
    }

    def "records error when multiple anchors are present (context ambiguous) and does not delegate"() {
        given:
        def input = new ArrayList<WebLink>()
        input.addAll(landingRecipeLinks())
        input << weblink("https://example.org/page/OTHER", "https://example.org/page/OTHER", [CITE_AS])

        when:
        def result = parent.validate(input)

        then:
        0 * landingValidator.validate(_)
        0 * metadataValidator.validate(_)
        0 * contentValidator.validate(_)

        and:
        hasError(result)
        messages(result).any { it.toLowerCase().contains("multiple anchors") || it.toLowerCase().contains("ambiguous") }
    }

    // ----------------------------------------------------------------------
    // Null-handling and view invariants
    // ----------------------------------------------------------------------

    def "filters null elements for the final SignPostingView and records an error for null element"() {
        given:
        def input = new ArrayList<WebLink>(contentRecipeLinks())
        input.add(1, null)

        and: "content validator is still called with safe (null-filtered) links"
        contentValidator.validate(_ as List<WebLink>) >> { List<WebLink> safe ->
            assert !safe.contains(null)
            childResult(safe, [])
        }

        when:
        def result = parent.validate(input)

        then:
        1 * contentValidator.validate(_ as List<WebLink>)
        hasError(result)
        messages(result).any { it.toLowerCase().contains("null") }

        and: "view contains only non-null weblinks"
        result.signPostingView().webLinks().every { it != null }
        result.signPostingView().webLinks().size() == 2
    }

    def "defensive copy: mutating input list after validate does not affect the view"() {
        given:
        def input = new ArrayList<WebLink>(landingRecipeLinks())

        landingValidator.validate(_ as List<WebLink>) >> { List<WebLink> links ->
            childResult(links, [])
        }

        when:
        def result = parent.validate(input)
        input.clear()

        then:
        result.signPostingView().webLinks().size() == landingRecipeLinks().size()
    }

    // ----------------------------------------------------------------------
    // Table-driven: recipe determination priority (if multiple signals exist)
    // ----------------------------------------------------------------------
    // If your parent supports mixed signals, define the precedence.
    // Example precedence: Landing > Metadata > Content (or whatever you decide).
    // These tests force you to encode that contract.

    @Unroll
    def "recipe determination precedence: #caseName"() {
        given:
        def input = links

        and:
        landingValidator.validate(_ as List<WebLink>) >> { l -> childResult(l, [Issue.warning("landing")]) }
        metadataValidator.validate(_ as List<WebLink>) >> { l -> childResult(l, [Issue.warning("metadata")]) }
        contentValidator.validate(_ as List<WebLink>) >> { l -> childResult(l, [Issue.warning("content")]) }

        when:
        def result = parent.validate(input)

        then:
        expectedCalls.call()

        and:
        messages(result).contains(expectedMessage)

        where:
        caseName                                      | links                                                                 | expectedMessage | expectedCalls
        "landing beats content if both signals exist" | landingRecipeLinks() + contentRecipeLinks()                           | "landing"       | { ->
            1 * landingValidator.validate(_ as List<WebLink>)
            0 * metadataValidator.validate(_)
            0 * contentValidator.validate(_)
        }
        "metadata beats content if both signals exist" | metadataRecipeLinks() + contentRecipeLinks()                          | "metadata"      | { ->
            0 * landingValidator.validate(_)
            1 * metadataValidator.validate(_ as List<WebLink>)
            0 * contentValidator.validate(_)
        }
    }
}
