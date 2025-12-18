package life.qbic.compass.parsing

import life.qbic.compass.spi.LinkSetParser
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

/**
 * Unit tests for parsing JSON Link Sets (media type application/linkset+json).
 *
 * Based on RFC 9264, Section 4.2 and examples in Section 7.2.
 */
class LinkSetJsonParserSpec extends Specification {

    def parser = LinkSetJsonParser.create()

    def "happy path: parses minimal linkset+json with one anchor and one relation entry"() {
        given:
        def raw = '''
        {
          "linkset": [
            {
              "anchor": "https://example.org/resource1",
              "author": [
                { "href": "https://authors.example.net/johndoe", "type": "application/rdf+xml" }
              ]
            }
          ]
        }
        '''

        when:
        def links = parser.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)))

        then:
        links != null
        links.size() == 1

        and: "target + rel + type are mapped"
        links[0].target() == URI.create("https://authors.example.net/johndoe")
        links[0].rel().contains("author")
        links[0].type().orElse(null) == "application/rdf+xml"

        and: "anchor is available on the produced weblink (choose the assertion matching your model)"
        // If anchor() returns Optional<String>:
        // links[0].anchor().orElse(null) == "https://example.org/resource1"
        // If anchor() returns Optional<URI>:
        // links[0].anchor().orElse(null) == URI.create("https://example.org/resource1")
    }

    def "happy path: parses multiple anchors and multiple relations into multiple weblinks"() {
        given:
        def raw = '''
        {
          "linkset": [
            {
              "anchor": "https://example.org/landing",
              "cite-as": [ { "href": "https://doi.org/10.1234/example" } ],
              "describedby": [
                { "href": "https://example.org/metadata", "type": "application/json" }
              ]
            },
            {
              "anchor": "https://example.org/content",
              "item": [
                { "href": "https://example.org/file1", "type": "application/pdf" },
                { "href": "https://example.org/file2", "type": "application/pdf" }
              ]
            }
          ]
        }
        '''

        when:
        def links = parser.parse(raw)

        then:
        links*.target() as Set == [
                URI.create("https://doi.org/10.1234/example"),
                URI.create("https://example.org/metadata"),
                URI.create("https://example.org/file1"),
                URI.create("https://example.org/file2")
        ] as Set

        and:
        links.find { it.target() == URI.create("https://doi.org/10.1234/example") }.rel().contains("cite-as")
        links.find { it.target() == URI.create("https://example.org/metadata") }.rel().contains("describedby")
        links.findAll { it.rel().contains("item") }.size() == 2
    }

    def "happy path: ignores unknown relation types and unknown target attributes without failing"() {
        given:
        def raw = '''
        {
          "linkset": [
            {
              "anchor": "https://example.org/resource1",
              "custom-rel": [
                { "href": "https://example.org/x", "foo": "bar", "type": "text/plain" }
              ]
            }
          ]
        }
        '''

        when:
        def links = parser.parse(raw)

        then:
        links.size() == 1
        links[0].target() == URI.create("https://example.org/x")
        links[0].rel().contains("custom-rel")
        links[0].type().orElse(null) == "text/plain"
    }

    @Unroll
    def "invalid: rejects malformed / semantically invalid linkset+json (#caseName)"() {
        when:
        parser.parse((String) raw)

        then:
        thrown(LinkSetParser.ParsingException)

        where:
        caseName                      | raw
        "null input"                  | null
        "empty string"                | ""
        "not json"                    | "<not-json>"
        "missing top-level linkset"   | '{"x": []}'
        "linkset not an array"        | '{"linkset": {}}'
        "entry missing anchor"        | '{"linkset":[{"author":[{"href":"https://example.org/a"}]}]}'
        "relation value not an array" | '{"linkset":[{"anchor":"https://example.org/r","author":{"href":"https://example.org/a"}}]}'
        "target missing href"         | '{"linkset":[{"anchor":"https://example.org/r","author":[{"type":"text/plain"}]}]}'
        "href not a uri"              | '{"linkset":[{"anchor":"https://example.org/r","author":[{"href":"::::"}]}]}'
        "anchor not a uri"            | '{"linkset":[{"anchor":"::::","author":[{"href":"https://example.org/a"}]}]}'
    }

    def "invariant: returned list is immutable or at least defensively copied"() {
        given:
        def raw = '''
        {
          "linkset": [
            { "anchor": "https://example.org/resource1",
              "author": [ { "href": "https://authors.example.net/johndoe" } ]
            }
          ]
        }
        '''

        when:
        def links = parser.parse(raw)
        links.add(null)

        then:
        thrown(UnsupportedOperationException)
    }
}
