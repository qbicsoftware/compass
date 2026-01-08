package life.qbic.compass.validation

import life.qbic.compass.parsing.LinkSetJsonParser
import life.qbic.compass.spi.SignPostingValidator
import spock.lang.Specification

class Level2MetadataResourceValidatorSpec extends Specification implements OfficialSignpostingLevel2Fixture {

    def parser = LinkSetJsonParser.create()

    def "happy path: official example passes metadata resource validation with no errors"() {
        given:
        def weblinks = parser.parse(officialJsonStream())
        SignPostingValidator validator = Level2MetadataResourceValidator.create() // adjust

        when:
        def result = validator.validate(weblinks)

        then:
        result != null
        !result.issueReport().hasErrors()

        and:
        def describesTargets = result.signPostingView().withRelationType("describes")*.target()
        describesTargets.contains(URI.create("https://example.org/page/7507"))
    }

    def "happy path: at least two metadata resources describe the landing page in official example"() {
        given:
        def weblinks = parser.parse(officialJsonStream())
        def validator = Level2MetadataResourceValidator.create() // adjust

        when:
        def result = validator.validate(weblinks)

        then:
        result.signPostingView().withRelationType("describes").size() >= 2
    }
}
