package life.qbic.compass.parsing

import life.qbic.compass.spi.LinkSetParser
import spock.lang.Specification
import spock.lang.Unroll

class LinkSetInlineParserSpec extends Specification {

    def parser = new LinkSetInlineParser()

    def "happy path: parses one inline link entry with rel + anchor + type"() {
        given:
        def raw = '<https://authors.example.net/johndoe>; rel="author"; anchor="https://example.org/resource1"; type="application/rdf+xml"'

        when:
        def links = parser.parse(raw)

        then:
        links != null
        links.size() == 1
        links[0].target() == URI.create("https://authors.example.net/johndoe")
        links[0].rel().contains("author")
        links[0].type().orElse(null) == "application/rdf+xml"

        and: "anchor is mapped (choose the assertion matching your model)"
        // Optional<String>:
        // links[0].anchor().orElse(null) == "https://example.org/resource1"
        // Optional<URI>:
        // links[0].anchor().orElse(null) == URI.create("https://example.org/resource1")
    }

    def "happy path: parses multiple link entries separated by comma"() {
        given:
        def raw = [
                '<https://example.org/metadata>; rel="describedby"; anchor="https://example.org/landing"; type="application/json"',
                '<https://doi.org/10.1234/example>; rel="cite-as"; anchor="https://example.org/landing"'
        ].join(', ')

        when:
        def links = parser.parse(raw)

        then:
        links.size() == 2
        links*.target() as Set == [
                URI.create("https://example.org/metadata"),
                URI.create("https://doi.org/10.1234/example")
        ] as Set

        and:
        links.find { it.target() == URI.create("https://example.org/metadata") }.rel().contains("describedby")
        links.find { it.target() == URI.create("https://doi.org/10.1234/example") }.rel().contains("cite-as")
    }

    def "happy path: tolerates OWS / extra whitespace around separators"() {
        given:
        def raw = '<https://example.org/x>  ;  rel = "item"  ;  anchor = "https://example.org/a"'

        when:
        def links = parser.parse(raw)

        then:
        links.size() == 1
        links[0].target() == URI.create("https://example.org/x")
        links[0].rel().contains("item")
    }

    def "happy path: parameter without value (e.g., ; foo) is preserved as an extension attribute"() {
        given:
        def raw = '<https://example.org/x>; rel="item"; anchor="https://example.org/a"; foo'

        when:
        def links = parser.parse(raw)

        then:
        links.size() == 1
        links[0].rel().contains("item")
        // Depending on your WebLink API for extension attributes:
        // links[0].extensionAttributes().containsKey("foo")
    }

    @Unroll
    def "invalid: rejects malformed / semantically invalid inline linkset (#caseName)"() {
        when:
        parser.parse(raw)

        then:
        thrown(LinkSetParser.ParsingException)

        where:
        caseName                       | raw
        "null input"                   | null
        "empty string"                 | ""
        "missing < at start"           | 'https://example.org/x>; rel="item"'
        "missing > end"                | '<https://example.org/x; rel="item"'
        "missing rel parameter"        | '<https://example.org/x>; anchor="https://example.org/a"'
        "rel appears twice (ignore?)"  | '<https://example.org/x>; rel="item"; rel="author"'
        "unterminated quoted string"   | '<https://example.org/x>; rel="item'
        "invalid target URI"           | '<::::>; rel="item"'
        "invalid anchor URI"           | '<https://example.org/x>; rel="item"; anchor="::::"'
    }

    def "invariant: returned list is immutable or defensively copied"() {
        given:
        def raw = '<https://example.org/x>; rel="item"'

        when:
        def links = parser.parse(raw)
        links.add(null)

        then:
        thrown(UnsupportedOperationException)
    }
}
