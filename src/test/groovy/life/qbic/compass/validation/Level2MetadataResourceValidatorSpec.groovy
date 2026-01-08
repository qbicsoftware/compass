package life.qbic.compass.validation


import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.model.WebLinkParameter
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests for Level 2 Metadata Resource recipe validation.
 *
 * Notes:
 * - These tests assume the validator consumes a flat list of WebLinks and MUST enforce a single anchor context.
 */
class Level2MetadataResourceValidatorSpec extends Specification {

    // Example anchors and targets used in tests
    private static final String LANDING = "https://example.org/page/7507"
    private static final String META_1  = "https://example.org/meta/7507/bibtex"
    private static final String META_2  = "https://example.org/meta/7507/citeproc"

    /**
     * Helper: create a WebLink with anchor + rel.
     * The validator counts relations using WebLink.rel().
     */
    private static WebLink link(String target, String anchor, String... rels) {
        def params = []
        params << new WebLinkParameter("anchor", anchor)
        params << new WebLinkParameter("rel", rels.join(" "))
        return WebLink.create(URI.create(target), params)
    }

    private static WebLink linkMissingAnchor(String target, String... rels) {
        def params = []
        params << new WebLinkParameter("rel", rels.join(" "))
        return WebLink.create(URI.create(target), params)
    }

    def "happy path: metadata resource list with single anchor and one describes passes"() {
        given:
        def validator = Level2MetadataResourceValidator.create()

        and: "a metadata resource context (anchor is the metadata resource URI)"
        // In the official linkset example, metadata resource anchors have rel=describes pointing to landing
        def webLinks = [
                link(LANDING, META_1, "describes")
        ]

        when:
        def result = validator.validate(webLinks)

        then:
        !result.issueReport().hasErrors()
    }

    def "happy path: metadata resource may contain describes plus other relations but still passes"() {
        given:
        def validator = Level2MetadataResourceValidator.create()

        and:
        def webLinks = [
                link(LANDING, META_1, "describes"),
                link("https://example.org/extra", META_1, "something-else")
        ]

        when:
        def result = validator.validate(webLinks)

        then:
        // If the validator *warns* about unknown relations, change to:
        // !result.issueReport().hasErrors() && result.issueReport().hasWarnings()
        !result.issueReport().hasErrors()
    }

    def "unhappy path: missing describes relation is an error"() {
        given:
        def validator = Level2MetadataResourceValidator.create()

        and:
        def webLinks = [
                link("https://example.org/whatever", META_1, "item") // no describes
        ]

        when:
        def result = validator.validate(webLinks)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues()*.message().any { msg ->
            def m = msg.toLowerCase()
            m.contains("describes") && (m.contains("missing") || m.contains("mandatory"))
        }
    }

    def "unhappy path: multiple describes links in same metadata context is an error (if cardinality=1)"() {
        given:
        def validator = Level2MetadataResourceValidator.create()

        and:
        def webLinks = [
                link(LANDING, META_1, "describes"),
                link("https://example.org/page/other", META_1, "describes")
        ]

        when:
        def result = validator.validate(webLinks)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues()*.message().any { msg ->
            def m = msg.toLowerCase()
            m.contains("describes") && (m.contains("multiple") || m.contains("cardinality") || m.contains("too many"))
        }
    }

    def "unhappy path: weblink with missing anchor is reported as error"() {
        given:
        def validator = Level2MetadataResourceValidator.create()

        and:
        def webLinks = [
                linkMissingAnchor(LANDING, "describes")
        ]

        when:
        def result = validator.validate(webLinks)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues()*.message().any { msg ->
            def m = msg.toLowerCase()
            m.contains("anchor") && (m.contains("missing") || m.contains("without") || m.contains("null"))
        }
    }

    def "unhappy path: multiple anchors in list makes metadata recipe ambiguous and aborts recipe validation"() {
        given:
        def validator = Level2MetadataResourceValidator.create()

        and: "two different metadata anchors mixed"
        def webLinks = [
                link(LANDING, META_1, "describes"),
                link(LANDING, META_2, "describes")
        ]

        when:
        def result = validator.validate(webLinks)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues()*.message().any { msg ->
            def m = msg.toLowerCase()
            m.contains("anchor") && (m.contains("multiple") || m.contains("not allowed") || m.contains("expected"))
        }

        and: "optional: ensure it did not also report missing describes for the second anchor context"
        !result.issueReport().issues()*.message().any { it.toLowerCase().contains("missing") && it.toLowerCase().contains("describes") }
    }

    @Unroll
    def "unhappy path: null handling - #caseName"(String caseName, List<WebLink> webLinks) {
        given:
        def validator = Level2MetadataResourceValidator.create()

        when:
        def result = validator.validate(webLinks)

        then:
        // Depending on your design, you might throw NPE instead of reporting.
        // If you throw, change this test accordingly.
        result.issueReport().hasErrors()

        where:
        caseName                     | webLinks
        "null element in list"       | [null]
        "contains null among links"  | [link(LANDING, META_1, "describes"), null]
    }
}
