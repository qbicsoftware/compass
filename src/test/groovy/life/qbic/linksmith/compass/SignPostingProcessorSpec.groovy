package life.qbic.linksmith.compass

import life.qbic.linksmith.compass.spi.SignPostingResult
import life.qbic.linksmith.compass.spi.SignPostingValidator
import life.qbic.linksmith.compass.validator.Level1SignPostingValidator
import life.qbic.linksmith.model.WebLink
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

    def "process returns SignPostingResult with SignPostingView wrapping the provided WebLinks"() {
        given:
        def inputLinks = [
                weblink("https://example.org/object"),
                weblink("https://doi.org/10.1234/xyz")
        ]
        def processor = new SignPostingProcessor.Builder()
                .withValidators(Stub(SignPostingValidator)) // presence irrelevant for current implementation
                .build()

        when:
        SignPostingResult result = processor.process(inputLinks)

        then:
        result != null
        result.signPostingView() != null
        result.signPostingView().webLinks() == inputLinks
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

    def "process does not mutate the provided WebLinks list"() {
        given:
        def inputLinks = new ArrayList<WebLink>([
                weblink("https://example.org/object"),
                weblink("https://example.org/meta")
        ])
        def snapshot = new ArrayList<>(inputLinks)

        def processor = new SignPostingProcessor.Builder()
                .withValidators(Stub(SignPostingValidator))
                .build()

        when:
        processor.process(inputLinks)

        then:
        inputLinks == snapshot
    }

    def "SignPostingView performs defensive copy: modifying input list after processing does not affect the view"() {
        given:
        def inputLinks = new ArrayList<WebLink>([
                weblink("https://example.org/object")
        ])

        def processor = new SignPostingProcessor.Builder()
                .withValidators(Stub(SignPostingValidator))
                .build()

        when:
        def result = processor.process(inputLinks)
        inputLinks.add(weblink("https://example.org/other"))  // mutate after processing

        then:
        result.signPostingView().webLinks().size() == 1
        result.signPostingView().webLinks()*.target == [URI.create("https://example.org/object")]
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
