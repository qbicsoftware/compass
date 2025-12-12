package life.qbic.linksmith.compass.validator


import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.model.WebLinkParameter
import spock.lang.Specification


/**
 * Specification for Signposting Level 1 profile validation.
 *
 * Assumptions (adapt to your profile interpretation):
 *  - Level 1 requires at least one "cite-as" link.
 *  - Level 1 requires at least one "describedby" link.
 *  - "author" is recommended (warning if missing).
 *  - Multiple "cite-as" with different targets is an error.
 *  - Unknown rels are ignored but allowed.
 */
class Level1SignPostingValidatorSpec extends Specification {

    def validator = Level1SignPostingValidator.create()

    // --- Happy paths -------------------------------------------------------

    def "valid Level 1 profile with cite-as, describedby and author passes without errors"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"]),
                weblink("https://example.org/metadata", ["describedby"]),
                weblink("https://orcid.org/0000-0001-2345-6789", ["author"])
        ]

        when:
        def result = validator.validate(links)

        then:
        result != null
        result.signPostingView() != null
        result.signPostingView().webLinks().size() == 3

        and: "no profile errors or warnings"
        !result.issueReport().hasErrors()
        !result.issueReport().hasWarnings()
    }

    def "valid Level 1 profile without author but with cite-as and describedby yields warning only"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"]),
                weblink("https://example.org/metadata", ["describedby"])
        ]

        when:
        def result = validator.validate(links)

        then:
        result != null
        result.signPostingView().webLinks().size() == 2

        and: "no errors, but at least one warning about missing author"
        !result.issueReport().hasErrors()
        result.issueReport().hasWarnings()
        result.issueReport().issues().any { it.message().toLowerCase().contains("author") }
    }

    def "Level 1 accepts extra unrelated rels without failing validation"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"]),
                weblink("https://example.org/metadata", ["describedby"]),
                weblink("https://example.org/something", ["item", "collection"]), // other rels
        ]

        when:
        def result = validator.validate(links)

        then:
        !result.issueReport().hasErrors()
        // might or might not warn about extra rels depending on your design
    }

    // --- Profile violations ------------------------------------------------

    def "Level 1 fails if cite-as is completely missing"() {
        given:
        def links = [
                weblink("https://example.org/metadata", ["describedby"])
        ]

        when:
        def result = validator.validate(links)

        then:
        result != null
        result.signPostingView().webLinks().size() == 1

        and:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("cite-as")
        }
    }

    def "Level 1 fails if describedby is missing even when cite-as is present"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"])
        ]

        when:
        def result = validator.validate(links)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("describedby")
        }
    }

    def "Level 1 flags multiple conflicting cite-as targets as error"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"]),
                weblink("https://doi.org/10.9999/bar", ["cite-as"]),
                weblink("https://example.org/metadata", ["describedby"])
        ]

        when:
        def result = validator.validate(links)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("multiple") && it.message().toLowerCase().contains("cite-as")
        }
    }

    def "Level 1 warns on non-http(s) cite-as or describedby targets"() {
        given:
        def links = [
                weblink("ftp://example.org/thing", ["cite-as"]),
                weblink("mailto:someone@example.org", ["describedby"])
        ]

        when:
        def result = validator.validate(links)

        then:
        // you decide: error or warning – here we expect warnings
        !result.issueReport().hasErrors()
        result.issueReport().hasWarnings()
        result.issueReport().issues().any { it.message().toLowerCase().contains("cite-as") }
        result.issueReport().issues().any { it.message().toLowerCase().contains("describedby") }
    }

    def "Level 1 keeps SignPostingView consistent even in presence of profile errors"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"]),
                weblink("https://example.org/other-cite", ["cite-as"]), // conflicting
                weblink("https://example.org/metadata", ["describedby"])
        ]

        when:
        def result = validator.validate(links)

        then:
        result.signPostingView() != null
        result.signPostingView().webLinks() == links

        and: "errors reflect conflicts, but view still provides all raw links"
        result.issueReport().hasErrors()
    }

    // --- Edge cases & side-effect checks ----------------------------------

    def "Level 1 validator does not mutate input WebLink list"() {
        given:
        def original = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"]),
                weblink("https://example.org/metadata", ["describedby"])
        ]
        def snapshot = new ArrayList<>(original)

        when:
        def result = validator.validate(original)

        then:
        original == snapshot   // no mutation of list order or size
        !result.issueReport().hasErrors()
    }

    def "Level 1 validator is robust to empty input list"() {
        when:
        def result = validator.validate([])

        then:
        result != null
        result.signPostingView() != null
        result.signPostingView().webLinks().isEmpty()

        and:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("cite-as")
        }
    }

    // --- Helpers -----------------------------------------------------------

    private static WebLink weblink(String uri, List<String> rels) {
        def params = rels.collect { r -> WebLinkParameter.create("rel", r) }
        new WebLink(URI.create(uri), params)
    }
}
