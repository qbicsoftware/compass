package life.qbic.jsign.validator

import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.model.WebLinkParameter
import spock.lang.Specification

/**
 * Specification for Signposting Level 2 profile validation.
 *
 * Assumptions (adapt as needed):
 *  - Inherits all Level 1 constraints (cite-as + describedby required).
 *  - Requires at least one author link with a PID (e.g. ORCID).
 *  - Requires at least one type parameter on the resource or on describedby links.
 *  - Requires describedby links to have specific media types.
 */
class Level2SignPostingValidatorSpec extends Specification {

    def validator = new Level2SignPostingValidator()

    // --- Happy paths -------------------------------------------------------

    def "valid Level 2 profile with cite-as, describedby, author PID and type passes without issues"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"], null),
                weblink("https://example.org/metadata", ["describedby"], "application/ld+json"),
                weblink("https://orcid.org/0000-0001-2345-6789", ["author"], "application/vnd.orcid+json")
        ]

        when:
        def result = validator.validate(links)

        then:
        !result.issueReport().hasErrors()
        !result.issueReport().hasWarnings()
        result.signPostingView().webLinks().size() == 3
    }

    def "Level 2 accepts multiple describedby links with valid metadata types"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"], null),
                weblink("https://example.org/metadata/json", ["describedby"], "application/json"),
                weblink("https://example.org/metadata/html", ["describedby"], "text/html"),
                weblink("https://orcid.org/0000-0001-2345-6789", ["author"], "application/vnd.orcid+json")
        ]

        when:
        def result = validator.validate(links)

        then:
        !result.issueReport().hasErrors()
        result.signPostingView().webLinks().size() == 4
    }

    // --- Profile violations ------------------------------------------------

    def "Level 2 fails if author relation is missing entirely"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"], null),
                weblink("https://example.org/metadata", ["describedby"], "application/ld+json")
        ]

        when:
        def result = validator.validate(links)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("author")
        }
    }

    def "Level 2 fails if author is present without a PID-like URI"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"], null),
                weblink("https://example.org/metadata", ["describedby"], "application/ld+json"),
                weblink("https://example.org/persons/john-doe", ["author"], "text/html")
        ]

        when:
        def result = validator.validate(links)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("author") &&
                    it.message().toLowerCase().contains("pid")
        }
    }

    def "Level 2 fails if required type information is missing"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"], null),
                // describedby without type
                weblink("https://example.org/metadata", ["describedby"], null),
                weblink("https://orcid.org/0000-0001-2345-6789", ["author"], "application/vnd.orcid+json")
        ]

        when:
        def result = validator.validate(links)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("type")
        }
    }

    def "Level 2 fails if no Level 1 compliance (missing cite-as)"() {
        given:
        def links = [
                weblink("https://example.org/metadata", ["describedby"], "application/ld+json"),
                weblink("https://orcid.org/0000-0001-2345-6789", ["author"], "application/vnd.orcid+json")
        ]

        when:
        def result = validator.validate(links)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("cite-as")
        }
    }

    def "Level 2 validator returns a SignPostingView even on profile violations"() {
        given:
        def links = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"], null),
                weblink("https://example.org/metadata", ["describedby"], "text/plain"), // wrong type?
                weblink("https://example.org/person/jane", ["author"], null)           // not a PID?
        ]

        when:
        def result = validator.validate(links)

        then:
        result.signPostingView() != null
        result.signPostingView().webLinks() == links

        and:
        result.issueReport().hasErrors()
    }

    // --- Edge cases & side effects ----------------------------------------

    def "Level 2 validation does not mutate input links"() {
        given:
        def original = [
                weblink("https://doi.org/10.1234/foo", ["cite-as"], null),
                weblink("https://example.org/metadata", ["describedby"], "application/ld+json"),
                weblink("https://orcid.org/0000-0001-2345-6789", ["author"], "application/vnd.orcid+json")
        ]
        def snapshot = new ArrayList<>(original)

        when:
        def result = validator.validate(original)

        then:
        original == snapshot
        !result.issueReport().hasErrors()
    }

    def "Level 2 validator is robust against empty list and reports multiple missing features"() {
        when:
        def result = validator.validate([])

        then:
        result.signPostingView().webLinks().isEmpty()
        result.issueReport().hasErrors()
        result.issueReport().issues().any { it.message().toLowerCase().contains("cite-as") }
        result.issueReport().issues().any { it.message().toLowerCase().contains("describedby") }
        result.issueReport().issues().any { it.message().toLowerCase().contains("author") }
    }

    // --- Helpers -----------------------------------------------------------

    private static WebLink weblink(String uri, List<String> rels, String type) {
        def params = []
        rels.each { rel ->
            params << WebLinkParameter.create("rel", rel)
        }
        if (type != null) {
            params << WebLinkParameter.create("type", type)
        }
        new WebLink(URI.create(uri), params)
    }
}
