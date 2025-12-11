package life.qbic.jsign.validator

import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.model.WebLinkParameter
import spock.lang.Specification

/**
 * Specification for Level 2 (linkset) discovery from inline WebLinks.
 *
 * Assumed semantics for Level2DiscoverySignPostingValidator:
 *
 *  - It looks for links with rel="linkset".
 *  - A "supported" linkset is one with:
 *      type = "application/linkset+json" OR "application/linkset".
 *  - If no linkset links at all are present:
 *      → ERROR: "No linkset discovered" (or similar)
 *  - If linkset links exist but none have a supported type:
 *      → ERROR about "no supported linkset"
 *      → WARNING about unsupported/missing type on each linkset link
 *  - If at least one supported linkset exists:
 *      → no ERROR for discovery
 *      → WARNINGS only for odd situations (e.g. multiple different URIs/types)
 *  - The SignPostingView in the result always contains exactly the input WebLinks.
 */
class Level2DiscoverySignPostingValidatorSpec extends Specification {

    def validator = Level2DiscoveryValidator.create()

    // ------------------------------------------------------------------------
    // Happy paths
    // ------------------------------------------------------------------------

    def "single linkset with application/linkset+json is accepted without errors"() {
        given:
        def links = [
                weblink("https://example.org/object", [rel("cite-as")]),
                weblink("https://example.org/object/linkset.json", [
                        rel("linkset"),
                        type("application/linkset+json")
                ])
        ]

        when:
        SignPostingResult result = validator.validate(links)

        then:
        result != null
        result.signPostingView() != null
        result.signPostingView().webLinks() == links

        and: "no discovery errors"
        !result.issueReport().hasErrors()
        // optional: no warnings either
        !result.issueReport().hasWarnings()
    }

    def "single linkset with application/linkset is accepted without errors"() {
        given:
        def links = [
                weblink("https://example.org/object", [rel("cite-as")]),
                weblink("https://example.org/object/linkset", [
                        rel("linkset"),
                        type("application/linkset")
                ])
        ]

        when:
        def result = validator.validate(links)

        then:
        !result.issueReport().hasErrors()
    }

    def "multiple linkset links with same URI and supported type do not cause errors"() {
        given:
        def links = [
                weblink("https://example.org/object", [rel("cite-as")]),
                weblink("https://example.org/object/linkset", [
                        rel("linkset"),
                        type("application/linkset+json")
                ]),
                // duplicate of the same linkset (e.g. via different headers)
                weblink("https://example.org/object/linkset", [
                        rel("linkset"),
                        type("application/linkset+json")
                ])
        ]

        when:
        def result = validator.validate(links)

        then:
        !result.issueReport().hasErrors()
        // You may decide to warn or not; here we assume no warning:
        !result.issueReport().hasWarnings()
    }

    // ------------------------------------------------------------------------
    // Missing / unsupported linkset
    // ------------------------------------------------------------------------

    def "no linkset links at all produces an error"() {
        given:
        def links = [
                weblink("https://example.org/object", [rel("cite-as")]),
                weblink("https://example.org/object/metadata", [rel("describedby")])
        ]

        when:
        def result = validator.validate(links)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("linkset")
        }
    }

    def "linkset rel present but without type produces error and warning"() {
        given:
        def links = [
                weblink("https://example.org/object", [rel("cite-as")]),
                // rel=linkset but no type parameter
                weblink("https://example.org/object/linkset", [rel("linkset")])
        ]

        when:
        def result = validator.validate(links)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("no supported linkset")
        }

        and: "warning about missing type on the linkset link"
        result.issueReport().hasWarnings()
        result.issueReport().issues().any {
            it.isWarning() &&
                    it.message().toLowerCase().contains("type") &&
                    it.message().toLowerCase().contains("linkset")
        }
    }

    def "linkset with unsupported type is reported as unsupported"() {
        given:
        def links = [
                weblink("https://example.org/object", [rel("cite-as")]),
                weblink("https://example.org/object/linkset-weird", [
                        rel("linkset"),
                        type("text/plain")
                ])
        ]

        when:
        def result = validator.validate(links)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("no supported linkset")
        }

        and:
        result.issueReport().hasWarnings()
        result.issueReport().issues().any {
            it.isWarning() &&
                    it.message().toLowerCase().contains("unsupported") &&
                    it.message().toLowerCase().contains("text/plain")
        }
    }

    def "mix of supported and unsupported linkset links still counts as success but warns about unsupported ones"() {
        given:
        def links = [
                weblink("https://example.org/object", [rel("cite-as")]),
                weblink("https://example.org/object/linkset.json", [
                        rel("linkset"),
                        type("application/linkset+json")
                ]),
                weblink("https://example.org/object/linkset.txt", [
                        rel("linkset"),
                        type("text/plain")
                ])
        ]

        when:
        def result = validator.validate(links)

        then: "discovery succeeds thanks to the supported linkset+json"
        !result.issueReport().hasErrors()

        and: "unsupported linkset is still mentioned as warning"
        result.issueReport().hasWarnings()
        result.issueReport().issues().any {
            it.isWarning() &&
                    it.message().toLowerCase().contains("unsupported") &&
                    it.message().toLowerCase().contains("text/plain")
        }
    }

    def "multiple supported linkset URIs with different targets yield a warning"() {
        given:
        def links = [
                weblink("https://example.org/object", [rel("cite-as")]),
                weblink("https://example.org/object/linkset-a.json", [
                        rel("linkset"),
                        type("application/linkset+json")
                ]),
                weblink("https://example.org/object/linkset-b.json", [
                        rel("linkset"),
                        type("application/linkset+json")
                ])
        ]

        when:
        def result = validator.validate(links)

        then:
        !result.issueReport().hasErrors()

        and:
        result.issueReport().hasWarnings()
        result.issueReport().issues().any {
            it.isWarning() &&
                    it.message().toLowerCase().contains("multiple") &&
                    it.message().toLowerCase().contains("linkset")
        }
    }

    // ------------------------------------------------------------------------
    // Robustness / side-effect tests
    // ------------------------------------------------------------------------

    def "discovery validator does not modify the input WebLink list"() {
        given:
        def original = [
                weblink("https://example.org/object", [rel("cite-as")]),
                weblink("https://example.org/object/linkset", [
                        rel("linkset"), type("application/linkset+json")
                ])
        ]
        def snapshot = new ArrayList<>(original)

        when:
        def result = validator.validate(original)

        then:
        original == snapshot
        result.signPostingView().webLinks() == original
    }

    def "discovery validator tolerates empty list but reports error"() {
        when:
        def result = validator.validate([])

        then:
        result != null
        result.signPostingView() != null
        result.signPostingView().webLinks().isEmpty()

        and:
        result.issueReport().hasErrors()
        result.issueReport().issues().any {
            it.isError() && it.message().toLowerCase().contains("linkset")
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static WebLink weblink(String uri, List<WebLinkParameter> params) {
        new WebLink(URI.create(uri), List.copyOf(params))
    }

    private static WebLinkParameter rel(String relValue) {
        new WebLinkParameter("rel", relValue)
    }

    private static WebLinkParameter type(String typeValue) {
        new WebLinkParameter("type", typeValue)
    }
}
