package life.qbic.compass.validation

import life.qbic.compass.model.SignPostingView
import life.qbic.compass.spi.SignPostingResult
import life.qbic.compass.spi.SignPostingValidator
import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.model.WebLinkParameter
import life.qbic.linksmith.spi.WebLinkValidator.Issue
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport
import spock.lang.Specification
import spock.lang.Unroll

import java.net.URI

class Level2RecipeValidatorSpec extends Specification {

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

    // validators (fresh per test)
    SignPostingValidator landingValidator
    SignPostingValidator metadataValidator
    SignPostingValidator contentValidator

    Level2RecipeValidator parent

    def setup() {
        landingValidator  = Mock(SignPostingValidator)
        metadataValidator = Mock(SignPostingValidator)
        contentValidator  = Mock(SignPostingValidator)

        // Safe default: any validate(List) call returns a non-null SignPostingResult
        landingValidator.validate(_ as List)  >> { List links -> childResult(links as List<WebLink>, []) }
        metadataValidator.validate(_ as List) >> { List links -> childResult(links as List<WebLink>, []) }
        contentValidator.validate(_ as List)  >> { List links -> childResult(links as List<WebLink>, []) }

        parent = Level2RecipeValidator.create(landingValidator, metadataValidator, contentValidator)
    }

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

    private static SignPostingResult childResult(List<WebLink> links, List<Issue> issues) {
        new SignPostingResult(new SignPostingView(links), new IssueReport(issues))
    }

    private static boolean hasError(SignPostingResult r) {
        r.issueReport() != null && r.issueReport().hasErrors()
    }

    private static List<String> messages(SignPostingResult r) {
        r.issueReport()?.issues()?.collect { it.message() } ?: []
    }

    // ----------------------------------------------------------------------
    // Recipe fixtures
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

        when:
        def result = parent.validate(input)

        then:
        1 * landingValidator.validate(_) >> childResult(input, [Issue.warning("landing-warning")])
        0 * metadataValidator.validate(_)
        0 * contentValidator.validate(_)

        and:
        !hasError(result)
        messages(result).contains("landing-warning")
        result.signPostingView().webLinks().size() == input.size()
    }

    def "routes metadata recipe to MetadataResourceValidator"() {
        given:
        def input = metadataRecipeLinks()

        when:
        def result = parent.validate(input)

        then:
        0 * landingValidator.validate(_)
        1 * metadataValidator.validate(_) >> { List links ->
            childResult(links as List<WebLink>, [Issue.warning("meta-warning")])
        }
        0 * contentValidator.validate(_)

        and:
        !hasError(result)
        messages(result).contains("meta-warning")
    }

    def "routes content recipe to ContentResourceValidator"() {
        given:
        def input = contentRecipeLinks()

        when:
        def result = parent.validate(input)

        then:
        0 * landingValidator.validate(_)
        0 * metadataValidator.validate(_)
        1 * contentValidator.validate(_) >> { List links ->
            childResult(links as List<WebLink>, [Issue.warning("content-warning")])
        }

        and:
        !hasError(result)
        messages(result).contains("content-warning")
    }

    // ----------------------------------------------------------------------
    // No-recipe / ambiguous cases
    // ----------------------------------------------------------------------

    def "records error when no recipe can be determined and does not delegate"() {
        given:
        def input = [
                weblink("https://example.org/x", LANDING_ANCHOR, [LICENSE]),
                weblink("https://example.org/y", LANDING_ANCHOR, ["author"])
        ]

        when:
        def result = parent.validate(input)

        then:
        0 * landingValidator.validate(_)
        0 * metadataValidator.validate(_)
        0 * contentValidator.validate(_)

        and:
        hasError(result)
        messages(result).any { it.toLowerCase().contains("unknown") || it.toLowerCase().contains("recipe") }
        result.signPostingView().webLinks().size() == input.size()
    }

    // ----------------------------------------------------------------------
    // Null-handling / view invariants
    // ----------------------------------------------------------------------

    def "filters null elements for the final SignPostingView and records an error for null element"() {
        given:
        def input = new ArrayList<WebLink>(contentRecipeLinks())
        input.add(1, null)

        when:
        def result = parent.validate(input)

        then:
        1 * contentValidator.validate(_) >> { List safe ->
            assert !safe.contains(null)
            childResult(safe as List<WebLink>, [])
        }
        0 * landingValidator.validate(_)
        0 * metadataValidator.validate(_)

        and:
        hasError(result)
        messages(result).any { it.toLowerCase().contains("null") }
        result.signPostingView().webLinks().every { it != null }
    }

    enum ExpectedValidator {
        LANDING, METADATA, CONTENT
    }
}
