package life.qbic.compass.validation

import life.qbic.compass.model.Level2LinksetView
import life.qbic.compass.model.SignPostingView
import life.qbic.compass.spi.SignPostingResult
import life.qbic.compass.spi.SignPostingValidator
import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.model.WebLinkParameter
import life.qbic.linksmith.spi.WebLinkValidator.Issue
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport
import spock.lang.Specification

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

    // origins (anchors)
    static final URI LANDING_ORIGIN = URI.create("https://example.org/page/7507")
    static final URI CONTENT_ORIGIN = URI.create("https://example.org/file/7507/2")
    static final URI META_ORIGIN    = URI.create("https://example.org/meta/7507/bibtex")

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
                                   URI origin,
                                   List<String> rels,
                                   Map<String, String> extras = [:]) {

        def params = []
        if (origin != null) {
            params << WebLinkParameter.create("anchor", origin.toString())
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
        // IMPORTANT: child validators return their own view; parent aggregates issues and builds its own.
        // Adjust constructor if your SignPostingResult signature differs.
        new SignPostingResult(new SignPostingView(links), new IssueReport(issues), null)
    }

    private static boolean hasError(SignPostingResult r) {
        r.issueReport() != null && r.issueReport().hasErrors()
    }

    private static List<String> messages(SignPostingResult r) {
        r.issueReport()?.issues()?.collect { it.message() } ?: []
    }

    private static Level2LinksetView linksetView(SignPostingResult r) {
        r.level2LinksetView()
    }

    // Convenience finders for list-based view API
    private static def landingView(Level2LinksetView v, URI origin) {
        v.landingPages().find { it.origin() == origin }
    }

    private static def contentView(Level2LinksetView v, URI origin) {
        v.contentResources().find { it.origin() == origin }
    }

    private static def metadataView(Level2LinksetView v, URI origin) {
        v.metadataResources().find { it.origin() == origin }
    }

    // ----------------------------------------------------------------------
    // Recipe fixtures
    // ----------------------------------------------------------------------

    private static List<WebLink> landingRecipeLinks() {
        [
                weblink("https://doi.org/10.123/abc",           LANDING_ORIGIN, [CITE_AS]),
                weblink("https://example.org/meta/7507/bibtex", LANDING_ORIGIN, [DESCRIBEDBY], ["type": "application/x-bibtex"]),
                weblink("https://example.org/file/7507/2",      LANDING_ORIGIN, [ITEM],       ["type": "text/csv"]),
                weblink("https://schema.org/ScholarlyArticle",  LANDING_ORIGIN, [TYPE])
        ]
    }

    private static List<WebLink> metadataRecipeLinks() {
        [
                weblink("https://example.org/page/7507", META_ORIGIN, [DESCRIBES], ["type": "text/html"])
        ]
    }

    private static List<WebLink> contentRecipeLinks() {
        [
                weblink("https://example.org/page/7507", CONTENT_ORIGIN, [COLLECTION], ["type": "text/html"]),
                weblink("https://schema.org/Dataset",    CONTENT_ORIGIN, [TYPE])
        ]
    }

    // ----------------------------------------------------------------------
    // Routing tests + Level2LinksetView list assertions
    // ----------------------------------------------------------------------

    def "routes landing-page recipe to LandingPageValidator, aggregates its issues, and records LandingPageView"() {
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

        and: "base view contains all non-null links"
        result.signPostingView().webLinks().size() == input.size()

        and: "Level2LinksetView contains exactly one landing page view with the expected origin"
        def v = linksetView(result)
        v != null
        v.landingPages().size() == 1
        v.contentResources().isEmpty()
        v.metadataResources().isEmpty()
        v.missingOriginLinks().isEmpty()

        and:
        def lp = landingView(v, LANDING_ORIGIN)
        lp != null
        lp.webLinks().size() == input.size()
    }

    def "routes metadata recipe to MetadataResourceValidator and records MetadataResourceView"() {
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

        and:
        def v = linksetView(result)
        v != null
        v.landingPages().isEmpty()
        v.contentResources().isEmpty()
        v.metadataResources().size() == 1
        v.missingOriginLinks().isEmpty()

        and:
        def mv = metadataView(v, META_ORIGIN)
        mv != null
        mv.webLinks().size() == input.size()
    }

    def "routes content recipe to ContentResourceValidator and records ContentResourceView"() {
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

        and:
        def v = linksetView(result)
        v != null
        v.landingPages().isEmpty()
        v.metadataResources().isEmpty()
        v.contentResources().size() == 1
        v.missingOriginLinks().isEmpty()

        and:
        def cv = contentView(v, CONTENT_ORIGIN)
        cv != null
        cv.webLinks().size() == input.size()
    }

    // ----------------------------------------------------------------------
    // Mixed input: multiple recipes in one linkset
    // ----------------------------------------------------------------------

    def "records multiple origins: one landing + one content + one metadata are all represented in Level2LinksetView"() {
        given:
        def input = []
        input.addAll(landingRecipeLinks())
        input.addAll(contentRecipeLinks())
        input.addAll(metadataRecipeLinks())

        when:
        def result = parent.validate(input)

        then:
        1 * landingValidator.validate(_) >> { List links ->
            childResult(links as List<WebLink>, [Issue.warning("landing-warning")])
        }
        1 * contentValidator.validate(_) >> { List links ->
            childResult(links as List<WebLink>, [Issue.warning("content-warning")])
        }
        1 * metadataValidator.validate(_) >> { List links ->
            childResult(links as List<WebLink>, [Issue.warning("metadata-warning")])
        }

        and:
        def v = linksetView(result)
        v != null
        println(input)
        landingView(v, LANDING_ORIGIN) != null
        contentView(v, CONTENT_ORIGIN) != null
        metadataView(v, META_ORIGIN) != null
        v.missingOriginLinks().isEmpty()
    }

    // ----------------------------------------------------------------------
    // No-recipe / unknown classification
    // ----------------------------------------------------------------------

    def "records error when no recipe can be determined for an origin and does not create any typed origin view"() {
        given:
        def input = [
                weblink("https://example.org/x", LANDING_ORIGIN, [LICENSE]),
                weblink("https://example.org/y", LANDING_ORIGIN, ["author"])
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

        and: "Level2LinksetView contains no typed entries"
        def v = linksetView(result)
        v != null
        v.landingPages().isEmpty()
        v.contentResources().isEmpty()
        v.metadataResources().isEmpty()

        and: "no missing-origin links (origin exists, recipe just unknown)"
        v.missingOriginLinks().isEmpty()
    }

    // ----------------------------------------------------------------------
    // Missing origin (anchor absent): must be recorded with original index
    // ----------------------------------------------------------------------

    def "records missing origin links (no anchor) including original index and still validates other origins"() {
        given:
        def input = new ArrayList<WebLink>()
        input.addAll(contentRecipeLinks())
        // index 2 will be missing origin (no anchor param)
        input << weblink("https://example.org/no-origin", null, [COLLECTION], ["type": "text/html"])

        when:
        def result = parent.validate(input)

        then:
        1 * contentValidator.validate(_) >> { List links ->
            childResult(links as List<WebLink>, [Issue.warning("content-warning")])
        }
        0 * landingValidator.validate(_)
        0 * metadataValidator.validate(_)

        and:
        hasError(result)
        messages(result).any {
            it.toLowerCase().contains("missing") && (it.toLowerCase().contains("origin") || it.toLowerCase().contains("anchor"))
        }

        and:
        def v = linksetView(result)
        v != null
        contentView(v, CONTENT_ORIGIN) != null
        v.missingOriginLinks().size() == 1

        and: "index is preserved"
        // Adjust accessor names if your MissingOriginLink differs.
        v.missingOriginLinks()[0].index() == 2
        v.missingOriginLinks()[0].webLink().target().toString() == "https://example.org/no-origin"
    }

    // ----------------------------------------------------------------------
    // Null-handling: null element must be reported, skipped, and not appear in views
    // ----------------------------------------------------------------------

    def "filters null elements, records an error with index, and excludes null from SignPostingView and Level2LinksetView"() {
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
        messages(result).any { it.toLowerCase().contains("null") && it.toLowerCase().contains("index") }

        and: "base view excludes nulls"
        result.signPostingView().webLinks().every { it != null }

        and: "Level2LinksetView still classifies content origin"
        def v = linksetView(result)
        v != null
        contentView(v, CONTENT_ORIGIN) != null
    }
}
