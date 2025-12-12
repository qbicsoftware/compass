package life.qbic.linksmith.compass

import life.qbic.linksmith.compass.model.SignPostingView
import life.qbic.linksmith.compass.spi.SignPostingResult
import life.qbic.linksmith.compass.spi.SignPostingValidator
import life.qbic.linksmith.compass.validator.Level1SignPostingValidator
import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.spi.WebLinkValidator
import spock.lang.Specification

/**
 * Specification for {@link SignPostingProcessor}.
 *
 * Focus:
 *  - happy paths (default validator and custom validators)
 *  - invariants / side effects (no input mutation, defensive copies)
 *  - builder semantics (varargs/list overloads, empty validator config)
 */
class SignPostingProcessorSpec extends Specification {

    // ------------------------------------------------------------------------
    // Happy paths
    // ------------------------------------------------------------------------

    def "processor calls all configured validators exactly once with the provided WebLinks"() {
        given:
        def webLinks = [
                weblink("https://example.org/object"),
                weblink("https://example.org/meta")
        ]

        def v1 = Mock(SignPostingValidator)
        def v2 = Mock(SignPostingValidator)

        and: "each validator returns some result"
        def r1 = new SignPostingResult(new SignPostingView(webLinks), new WebLinkValidator.IssueReport([WebLinkValidator.Issue.warning("v1")]))
        def r2 = new SignPostingResult(new SignPostingView(webLinks), new WebLinkValidator.IssueReport([WebLinkValidator.Issue.warning("v2")]))

        and:
        def processor = new SignPostingProcessor.Builder()
                .withValidators(v1, v2)
                .build()

        when:
        def result = processor.process(webLinks)

        then: "both validators are invoked"
        1 * v1.validate(webLinks) >> r1
        1 * v2.validate(webLinks) >> r2
        0 * _

        and: "result contains the original view"
        result != null
        result.signPostingView() != null
        result.signPostingView().webLinks() == webLinks

        and: "processor returns a combined report (adjust expectations to your aggregation rules)"
        result.issueReport() != null
        result.issueReport().issues()*.message().containsAll(["v1", "v2"])
    }

    def "builder without validators falls back to Level1SignPostingValidator by default"() {
        when:
        def processor = new SignPostingProcessor.Builder().build()

        then:
        // verify internal state via reflection to avoid coupling to implementation changes elsewhere
        def field = SignPostingProcessor.getDeclaredField("validators")
        field.setAccessible(true)
        def validators = (List<?>) field.get(processor)

        validators.size() == 1
        validators.first().class == Level1SignPostingValidator
    }

    def "builder accepts validators via varargs overload"() {
        given:
        def v1 = Stub(SignPostingValidator)
        def v2 = Stub(SignPostingValidator)

        when:
        def processor = new SignPostingProcessor.Builder()
                .withValidators(v1, v2)
                .build()

        then:
        def validators = readValidators(processor)
        validators.size() == 2
        validators[0].is(v1)
        validators[1].is(v2)
    }

    def "builder accepts validators via list overload and preserves order"() {
        given:
        def v1 = Stub(SignPostingValidator)
        def v2 = Stub(SignPostingValidator)
        def v3 = Stub(SignPostingValidator)

        when:
        def processor = new SignPostingProcessor.Builder()
                .withValidators([v1, v2, v3])
                .build()

        then:
        def validators = readValidators(processor)
        validators == [v1, v2, v3]
    }

    // ------------------------------------------------------------------------
    // Side effects / invariants
    // ------------------------------------------------------------------------

    def "processor does not mutate the provided WebLinks list"() {
        given:
        def webLinks = new ArrayList<WebLink>([
                weblink("https://example.org/object")
        ])
        def snapshot = new ArrayList<>(webLinks)

        and:
        def v = Mock(SignPostingValidator)
        def processor = new SignPostingProcessor.Builder()
                .withValidators(v)
                .build()

        and:
        v.validate(_ as List<WebLink>) >> new SignPostingResult(
                new SignPostingView(webLinks),
                new WebLinkValidator.IssueReport([])
        )

        when:
        processor.process(webLinks)

        then:
        webLinks == snapshot
    }

    def "SignPostingView performs defensive copy: modifying input list after processing does not affect the view"() {
        given:
        def inputLinks = new ArrayList<WebLink>([
                weblink("https://example.org/object")
        ])

        and: "a validator that returns a real result"
        def validator = Stub(SignPostingValidator) {
            validate(_ as List<WebLink>) >> { List<WebLink> passed ->
                // IMPORTANT: return a real SignPostingResult, not a mock
                new SignPostingResult(new SignPostingView(passed), new WebLinkValidator.IssueReport([]))
            }
        }

        and:
        def processor = new SignPostingProcessor.Builder()
                .withValidators(validator)
                .build()

        when:
        def result = processor.process(inputLinks)
        inputLinks.add(weblink("https://example.org/other"))  // mutate after processing

        then:
        result.signPostingView().webLinks().size() == 1
        result.signPostingView().webLinks().get(0).target() == URI.create("https://example.org/object")
    }

    def "builder does not retain a mutable reference to the validators list passed in"() {
        given:
        def v1 = Stub(SignPostingValidator)
        def v2 = Stub(SignPostingValidator)
        def passed = new ArrayList<SignPostingValidator>([v1])

        when:
        def processor = new SignPostingProcessor.Builder()
                .withValidators(passed)
                .build()

        and: "mutate the original list after build"
        passed.add(v2)

        then: "processor's internal validators remain unchanged"
        readValidators(processor) == [v1]
    }

    def "builder accumulates validators when withValidators is called multiple times"() {
        given:
        def v1 = Stub(SignPostingValidator)
        def v2 = Stub(SignPostingValidator)

        when:
        def processor = new SignPostingProcessor.Builder()
                .withValidators(v1)
                .withValidators([v2])
                .build()

        then:
        readValidators(processor) == [v1, v2]
    }

    def "process rejects null input list"() {
        given:
        def processor = new SignPostingProcessor.Builder().build()

        when:
        processor.process(null)

        then:
        thrown(NullPointerException)
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static WebLink weblink(String target) {
        // Adjust if your WebLink constructor differs.
        // Many implementations model target/reference; here we assume a single URI target is enough for tests.
        new WebLink(URI.create(target), List.of())
    }

    private static List<SignPostingValidator> readValidators(SignPostingProcessor processor) {
        def field = SignPostingProcessor.getDeclaredField("validators")
        field.accessible = true
        return (List<SignPostingValidator>) field.get(processor)
    }
}
