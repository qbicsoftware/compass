package life.qbic.compass.validation

import life.qbic.compass.parsing.LinkSetJsonParser
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class Level2LandingPageValidatorSpec extends Specification implements OfficialSignpostingLevel2Fixture {

    def parser = LinkSetJsonParser.create()
    // --- Official fixture (inline here to keep the spec self-contained) ---
    static String OFFICIAL = '''
{
  "linkset": [
    {
      "anchor": "https://example.org/page/7507",
      "cite-as": [ { "href": "https://doi.org/10.5061/dryad.5d23f" } ],
      "type": [ { "href": "https://schema.org/ScholarlyArticle" }, { "href": "https://schema.org/AboutPage" } ],
      "author": [ { "href": "https://orcid.org/0000-0002-1825-0097" }, { "href": "https://isni.org/isni/0000002251201436" } ],
      "item": [
        { "href": "https://example.org/file/7507/1", "type": "application/pdf" },
        { "href": "https://example.org/file/7507/2", "type": "text/csv" },
        { "href": "https://gitmodo.io/johnd/ct.zip", "type": "application/zip" }
      ],
      "describedby": [
        { "href": "https://example.org/meta/7507/bibtex", "type": "application/x-bibtex" },
        { "href": "https://doi.org/10.5061/dryad.5d23f", "type": "application/vnd.datacite.datacite+json" },
        { "href": "https://example.org/meta/7507/citeproc", "type": "application/vnd.citationstyles.csl+json" }
      ],
      "license": [ { "href": "https://spdx.org/licenses/CC-BY-4.0" } ]
    },
    {
      "anchor": "https://example.org/file/7507/1",
      "collection": [ { "href": "https://example.org/page/7507", "type": "text/html" } ]
    },
    {
      "anchor": "https://example.org/file/7507/2",
      "collection": [ { "href": "https://example.org/page/7507", "type": "text/html" } ],
      "type": [ { "href": "https://schema.org/Dataset" } ]
    },
    {
      "anchor": "https://gitmodo.io/johnd/ct.zip",
      "collection": [ { "href": "https://example.org/page/7507", "type": "text/html" } ],
      "type": [ { "href": "https://schema.org/SoftwareSourceCode" } ]
    },
    {
      "anchor": "https://doi.org/10.5061/dryad.5d23f",
      "describes": [ { "href": "https://example.org/page/7507", "type": "text/html" } ]
    },
    {
      "anchor": "https://example.org/meta/7507/bibtex",
      "describes": [ { "href": "https://example.org/page/7507", "type": "text/html" } ]
    }
  ]
}
'''

    private static InputStream asStream(String json) {
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
    }

    def "happy path: official example has no errors for landing page validation"() {
        given:
        def weblinks = parser.parse(asStream(OFFICIAL))
        def validator = Level2LandingPageValidator.create()

        when:
        def result = validator.validate(weblinks)

        then:
        result != null
        result.issueReport() != null
        !result.issueReport().hasErrors()
    }

    // -------------------------
    // Unhappy paths
    // -------------------------

    @Unroll
    def "unhappy path: missing mandatory landing relation '#missingRel' should raise an error"() {
        given:
        def brokenJson = removeLandingRelation(OFFICIAL, missingRel)
        def weblinks = parser.parse(asStream(brokenJson))
        def validator = Level2LandingPageValidator.create()

        when:
        def result = validator.validate(weblinks)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues()*.message().any { it.toLowerCase().contains(missingRel.toLowerCase()) }

        where:
        missingRel << [
                "cite-as",
                "describedby",
                "item",
                "type"
        ]
    }

    def "unhappy path: landing page has duplicate cite-as entries should raise an error (if cardinality=1 enforced)"() {
        given: "duplicate cite-as in landing context"
        def brokenJson = OFFICIAL.replaceFirst(
                /"cite-as"\s*:\s*\[\s*\{\s*"href"\s*:\s*"https:\/\/doi\.org\/10\.5061\/dryad\.5d23f"\s*\}\s*\]/,
                '"cite-as": [ { "href": "https://doi.org/10.5061/dryad.5d23f" }, { "href": "https://doi.org/10.5061/dryad.5d23f" } ]'
        )
        def weblinks = parser.parse(asStream(brokenJson))
        def validator = Level2LandingPageValidator.create()

        when:
        def result = validator.validate(weblinks)

        then:
        result.issueReport().hasErrors()
        // keep message assertion weak, so you can change wording:
        result.issueReport().issues()*.message().any { it.toLowerCase().contains("cite-as") && it.toLowerCase().contains("multiple") }
    }

    def "unhappy path: insecure http target in a landing relation should raise a warning"() {
        given: "replace one describedby href with insecure http"
        def brokenJson = OFFICIAL.replace(
                '"href": "https://example.org/meta/7507/bibtex"',
                '"href": "http://example.org/meta/7507/bibtex"'
        )
        def weblinks = parser.parse(asStream(brokenJson))
        def validator = Level2LandingPageValidator.create()

        when:
        def result = validator.validate(weblinks)

        then:
        result.issueReport().hasWarnings()
        result.issueReport().issues()*.message().any { it.toLowerCase().contains("http") || it.toLowerCase().contains("https") }
    }

    def "unhappy path: landing anchor missing entirely should raise an error in landing validation"() {
        given: "remove the anchor field from the landing link context object"
        def brokenJson = OFFICIAL.replaceFirst(/"anchor"\s*:\s*"https:\/\/example\.org\/page\/7507"\s*,?/, '')
        def weblinks = parser.parse(asStream(brokenJson))
        def validator = Level2LandingPageValidator.create()

        when:
        def result = validator.validate(weblinks)

        then:
        result.issueReport().hasErrors()
        result.issueReport().issues()*.message().any { it.toLowerCase().contains("anchor") }
    }

    // -------------------------
    // Helper: remove one landing relation array entirely
    // Works by removing a "<rel>": [ ... ] block in the first link context object.
    // -------------------------
    private static String removeLandingRelation(String json, String relation) {
        // remove e.g.  ,"cite-as":[{...}]
        // Keep it intentionally broad; your JSON formatting may differ.
        def pattern = ~/(?s),\s*"${Pattern.quote(relation)}"\s*:\s*\[\s*.*?\s*\]/
        return json.replaceFirst(pattern, '')
    }
}
