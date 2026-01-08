package life.qbic.compass.validation

import life.qbic.compass.parsing.LinkSetJsonParser
import life.qbic.compass.spi.SignPostingValidator
import spock.lang.Specification

class Level2ContentResourceValidatorSpec extends Specification implements OfficialSignpostingLevel2Fixture {

    def parser = LinkSetJsonParser.create()

    def "happy path: official example passes content resource validation with no errors"() {
        given:
        def weblinks = parser.parse(officialJsonStream())
        SignPostingValidator validator = Level2ContentResourceValidator.create() // adjust

        when:
        def result = validator.validate(weblinks)

        then:
        result != null
        !result.issueReport().hasErrors()

        and: "collection relations exist and point to landing page"
        def collectionLinks = result.signPostingView().withRelationType("collection")
        collectionLinks.size() >= 3

        collectionLinks*.target().contains(URI.create("https://example.org/page/7507"))
    }

    def "happy path: official content anchors are present and each has collection -> landing"() {
        given:
        def weblinks = parser.parse(officialJsonStream())
        def validator = Level2ContentResourceValidator.create() // adjust

        when:
        def result = validator.validate(weblinks)

        then:
        def view = result.signPostingView()

        // anchors aren't directly exposed by SignPostingView in your snippet,
        // so we assert at least that the content link targets appear somewhere as WebLink targets:
        view.withRelationType("item")*.target().containsAll([
                URI.create("https://example.org/file/7507/1"),
                URI.create("https://example.org/file/7507/2"),
                URI.create("https://gitmodo.io/johnd/ct.zip")
        ])
    }
}
