package life.qbic.compass.model


import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.model.WebLinkParameter
import spock.lang.Specification

/**
 * Specification for {@link SignPostingView}.
 *
 * Focus:
 *  - structural invariants (immutability, defensive copying, no side effects)
 *  - semantic helpers around rel-based access (Level 1 & Level 2)
 *  - typical Level 1 and Level 2 Signposting scenarios
 */
class SignPostingViewSpec extends Specification {

    // ------------------------------------------------------------------------
    // Construction & structural invariants
    // ------------------------------------------------------------------------

    def "cannot be constructed with null list of WebLinks"() {
        when:
        new SignPostingView(null)

        then:
        thrown(NullPointerException)
    }

    def "webLinks list in view is a defensive copy and not the same instance as passed in"() {
        given:
        def original = [
                weblink("https://example.org/object", []),
                weblink("https://example.org/object/meta", [])
        ]

        def originalCopy = List.copyOf(original)

        when:
        def view = new SignPostingView(original)
        original.removeFirst()

        then:
        view.webLinks() != original        // different instance
        view.webLinks() == originalCopy    // but equal contents
    }

    def "modifying original list after creation does not affect SignPostingView"() {
        given:
        def original = [weblink("https://example.org/object", [])]
        def view = new SignPostingView(original)

        when:
        original.add(weblink("https://example.org/other", []))

        then:
        view.webLinks().size() == 1
        view.webLinks()*.target() == [URI.create("https://example.org/object")]
    }

    def "webLinks list returned by view is unmodifiable"() {
        given:
        def view = new SignPostingView([weblink("https://example.org/object", [])])

        when:
        view.webLinks().add(weblink("https://example.org/other", []))

        then:
        thrown(UnsupportedOperationException)
    }

    def "empty list of WebLinks is allowed and exposed as empty immutable list"() {
        given:
        def view = new SignPostingView([])

        expect:
        view.webLinks().isEmpty()
        view.webLinks().getClass().simpleName.contains("Immutable")
                || view.webLinks().getClass().name.toLowerCase().contains("immutable")
    }

    // ------------------------------------------------------------------------
    // rel-based semantic helper: withRel
    // ------------------------------------------------------------------------

    def "withRel returns all WebLinks whose rel parameter contains the given token"() {
        given:
        def links = [
                weblink("https://example.org/object", [rel("cite-as")]),
                weblink("https://example.org/meta", [rel("describedby")]),
                weblink("https://example.org/file1", [rel("item")]),
                // multiple rel tokens in one parameter
                weblink("https://example.org/file2", [rel("item describedby")])
        ]
        def view = new SignPostingView(links)

        when:
        def describedByLinks = view.withRelationType("describedby")
        def itemLinks = view.withRelationType("item")

        then: "describedby matches both dedicated and combined rel param"
        describedByLinks*.target() as Set ==
                [URI.create("https://example.org/meta"),
                 URI.create("https://example.org/file2")] as Set

        and: "item matches both dedicated and combined rel param"
        itemLinks*.target() as Set ==
                [URI.create("https://example.org/file1"),
                 URI.create("https://example.org/file2")] as Set
    }

    def "withRel on unknown relation returns an empty list"() {
        given:
        def view = new SignPostingView([
                weblink("https://example.org/object", [rel("cite-as")])
        ])

        expect:
        view.withRelationType("author").isEmpty()
    }

    // ------------------------------------------------------------------------
    // Level 1 semantics helpers: cite-as, describedby, items, etc.
    // ------------------------------------------------------------------------

    def "Level 1: citeAs returns all URIs with rel=cite-as"() {
        given:
        def view = new SignPostingView([
                weblink("https://example.org/landing", []),
                weblink("https://doi.org/10.1234/xyz", [rel("cite-as")]),
                // Some other unrelated link
                weblink("https://example.org/file1", [rel("item")])
        ])

        when:
        def citeAsUris = view.citeAs()

        then:
        citeAsUris == [URI.create("https://doi.org/10.1234/xyz")]
    }

    def "Level 1: describedBy returns all URIs with rel=describedby"() {
        given:
        def view = new SignPostingView([
                weblink("https://example.org/landing", []),
                weblink("https://example.org/meta/datacite.xml", [rel("describedby")]),
                weblink("https://example.org/meta/schemaorg.jsonld", [rel("describedby")]),
                weblink("https://example.org/file1", [rel("item")])
        ])

        when:
        def described = view.describedBy()

        then:
        described as Set == [
                URI.create("https://example.org/meta/datacite.xml"),
                URI.create("https://example.org/meta/schemaorg.jsonld")
        ] as Set
    }

    def "Level 1: citeAs and describedBy are empty when no such rels exist"() {
        given:
        def view = new SignPostingView([
                weblink("https://example.org/landing", []),
                weblink("https://example.org/file1", [rel("item")])
        ])

        expect:
        view.citeAs().isEmpty()
        view.describedBy().isEmpty()
    }

    // ------------------------------------------------------------------------
    // Level 2 discovery semantics: linksets()
    // ------------------------------------------------------------------------

    def "Level 2: linksets returns all URIs with rel=linkset"() {
        given:
        def view = new SignPostingView([
                weblink("https://example.org/landing", []),
                weblink("https://example.org/linkset.json", [
                        rel("linkset"),
                        type("application/linkset+json")
                ]),
                weblink("https://example.org/linkset-alt", [
                        rel("linkset"),
                        type("application/linkset")
                ]),
                weblink("https://example.org/file1", [rel("item")])
        ])

        when:
        def linksetUris = view.linksets()

        then:
        linksetUris as Set == [
                URI.create("https://example.org/linkset.json"),
                URI.create("https://example.org/linkset-alt")
        ] as Set
    }

    def "Level 2: linksets is empty if no rel=linkset exists"() {
        given:
        def view = new SignPostingView([
                weblink("https://example.org/landing", []),
                weblink("https://example.org/file1", [rel("item")])
        ])

        expect:
        view.linksets().isEmpty()
    }

    // ------------------------------------------------------------------------
    // Mixed scenario: Level 1 + Level 2 in the same view
    // ------------------------------------------------------------------------

    def "mixed profile: view exposes Level 1 and Level 2 semantics consistently"() {
        given:
        def view = new SignPostingView([
                // landing page related links
                weblink("https://doi.org/10.9999/foo", [rel("cite-as")]),
                weblink("https://example.org/meta/datacite.xml", [rel("describedby")]),
                weblink("https://example.org/file1", [rel("item")]),
                // level 2 linkset advertisement
                weblink("https://example.org/linkset.json", [
                        rel("linkset"), type("application/linkset+json")
                ])
        ])

        expect: "Level 1 helpers"
        view.citeAs() == [URI.create("https://doi.org/10.9999/foo")]
        view.describedBy() == [URI.create("https://example.org/meta/datacite.xml")]

        and: "Level 2 discovery helper"
        view.linksets() == [URI.create("https://example.org/linkset.json")]

        and: "rel-based helper is consistent"
        view.withRelationType("item")*.target() == [URI.create("https://example.org/file1")]
        view.withRelationType("linkset")*.target() == [URI.create("https://example.org/linkset.json")]
    }

    // ------------------------------------------------------------------------
    // Helper methods
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
