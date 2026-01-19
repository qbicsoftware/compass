package life.qbic.compass.validation

import life.qbic.compass.spi.WebLinkModelValidator
import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.model.WebLinkParameter
import life.qbic.linksmith.spi.WebLinkValidator.Issue
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Contract tests for a WebLinkModelValidator that enforces RFC 8288 "Web Linking"
 * constraints at the *object model* level (WebLink + parameters), not at the raw
 * header serialization/ABNF level.
 *
 * These tests intentionally encode strict/normative expectations:
 * - link target MUST be a valid absolute URI
 * - rel parameter MUST be present
 * - parameter names MUST be RFC7230 token
 * - parameters (except hreflang) MUST NOT occur multiple times
 * - rel values MUST be valid relation types (token or absolute URI)
 *
 * If you prefer warnings instead of errors for some policies (e.g. relative URI),
 * adjust assertions accordingly.
 */
class Rfc8288ModelValidatorSpec extends Specification {

    /**
     * Provide your concrete validator under test here.
     *
     * Examples:
     *   def validator = Rfc8288ModelWebLinkValidator.create()
     * or
     *   def validator = new WebLinkModelSanityValidator()
     */
    def validator = Rfc8288ModelValidator.create()

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private static WebLink link(String target, List<WebLinkParameter> params) {
        WebLink.create(URI.create(target), params)
    }

    private static WebLinkParameter p(String name, String value) {
        WebLinkParameter.create(name, value)
    }

    private static WebLinkParameter flag(String name) {
        WebLinkParameter.withoutValue(name)
    }

    private static boolean hasErrors(WebLinkModelValidator.ModelValidationResult r) {
        r != null && r.issueReport().hasErrors()
    }

    private static boolean hasWarnings(WebLinkModelValidator.ModelValidationResult r) {
        r != null && r.issueReport().hasWarnings()
    }

    private static List<Issue> issues(IssueReport r) {
        r?.issues() ?: []
    }

    private static List<String> messages(IssueReport r) {
        issues(r)*.message()
    }

    private static boolean anyMsg(WebLinkModelValidator.ModelValidationResult r, String fragment) {
        messages(r.issueReport()).any { it.toLowerCase().contains(fragment.toLowerCase()) }
    }

    // ----------------------------------------------------------------------
    // Input handling
    // ----------------------------------------------------------------------

    def "null list input throws NPE (contract)"() {
        when:
        validator.validate(null)

        then:
        thrown(NullPointerException)
    }

    def "null element in list is reported as ERROR with index and skipped"() {
        given:
        def links = [
                link("https://example.org/a", [p("rel", "cite-as")]),
                null,
                link("https://example.org/b", [p("rel", "describedby")])
        ]

        when:
        def report = validator.validate(links)

        then:
        hasErrors(report)
        anyMsg(report, "null")
        anyMsg(report, "index 1")
    }

    // ----------------------------------------------------------------------
    // Target URI requirements (model-level strictness)
    // ----------------------------------------------------------------------

    def "target must be absolute URI: relative target is ERROR"() {
        given:
        def links = [
                // URI.create("/rel") is valid java.net.URI but relative
                link("/rel", [p("rel", "cite-as")])
        ]

        when:
        def report = validator.validate(links)

        then:
        hasErrors(report)
        anyMsg(report, "relative")
        anyMsg(report, "target")
        anyMsg(report, "index 0")
    }

    def "target must have scheme and authority: 'mailto:' is allowed by URI but treated as non-http (WARNING or ERROR) - choose policy"() {
        given:
        def links = [
                link("mailto:info@example.org", [p("rel", "author")])
        ]

        when:
        def report = validator.validate(links)

        then:
        // If you want strict RFC8288-header/web policy => ERROR; if you want soft => WARNING.
        // Pick one and keep consistent in implementation.
        hasWarnings(report) || hasErrors(report)
        anyMsg(report, "index 0")
    }

    // ----------------------------------------------------------------------
    // rel parameter presence and validity
    // ----------------------------------------------------------------------

    def "rel parameter MUST be present: missing rel is ERROR"() {
        given:
        def links = [
                link("https://example.org/a", [p("type", "text/html")])
        ]

        when:
        def report = validator.validate(links)

        then:
        hasErrors(report)
        anyMsg(report, "rel")
        anyMsg(report, "missing")
        anyMsg(report, "index 0")
    }

    @Unroll
    def "rel value must be valid relation type token or absolute URI: '#relValue' -> #expected"() {
        given:
        def links = [
                link("https://example.org/a", [p("rel", relValue)])
        ]

        when:
        def report = validator.validate(links)

        then:
        expected == "ok" ? !hasErrors(report) : hasErrors(report)

        where:
        relValue                         || expected
        "cite-as"                        || "ok"        // registered token
        "describedby"                    || "ok"
        "item"                           || "ok"
        "collection"                     || "ok"
        "describes"                      || "ok"
        "CITE-AS"                        || "ok"        // case-insensitive tokens are ok at model level
        "good rel"                       || "ok"        // spaces are allowed in a single token value
        ""                               || "error"     // empty not allowed
        "   "                            || "error"     // blank not allowed
        "cite_as"                        || "error"     // underscore not allowed in RFC8288 token (token is ALPHA/DIGIT/.-)
        "urn:example:relation"           || "ok"        // URI relation type allowed
        "https://example.org/rel/custom" || "ok"        // URI rel allowed
        "example.org/rel/custom"         || "error"     // missing scheme, not a valid absolute URI rel
        "http://[::1"                    || "error"     // invalid URI
    }

    def "multiple rel tokens in one rel parameter (space-separated) are allowed and must be split/recognized"() {
        given:
        def links = [
                link("https://example.org/a", [p("rel", "cite-as describedby")])
        ]

        when:
        def report = validator.validate(links)

        then:
        // should be valid: rel can contain multiple relation types separated by SP
        !hasErrors(report)
    }

    def "rel MUST be present and MUST NOT occur more than once; duplicates are ERROR and must be reported"() {
        given:
        def links = [
                link("https://example.org/a", [
                        p("rel", "cite-as"),
                        p("rel", "describedby") // second rel parameter => forbidden by RFC8288
                ])
        ]

        when:
        def report = validator.validate(links)

        then:
        hasErrors(report)
        anyMsg(report, "rel")
        anyMsg(report, "must not") || anyMsg(report, "more than once") || anyMsg(report, "ignored")
        anyMsg(report, "index 0")
    }

    // ----------------------------------------------------------------------
    // Parameter name token validity (RFC7230 token)
    // ----------------------------------------------------------------------

    @Unroll
    def "parameter name must be RFC7230 token: '#paramName' -> #expected"() {
        given:
        def links = [
                link("https://example.org/a", [
                        p(paramName, "x"),
                        p("rel", "cite-as")
                ])
        ]

        when:
        def report = validator.validate(links)

        then:
        expected == "ok" ? !hasErrors(report) : hasErrors(report)

        where:
        paramName  || expected
        "type"     || "ok"
        "title"    || "ok"
        "hreflang" || "ok"
        "x-custom" || "ok"
        "x_custom" || "ok"
        "bad name" || "error"     // spaces not allowed
        "na(me)"   || "error"     // parentheses not allowed
        ""         || "error"
    }

    // ----------------------------------------------------------------------
    // Parameter multiplicity rules
    // ----------------------------------------------------------------------

    @Unroll
    def "duplicate non-repeatable parameter '#paramName' is WARNING or ERROR, but MUST be reported and duplicates ignored"() {
        given:
        def links = [
                link("https://example.org/a", [
                        p("rel", "cite-as"),
                        p(paramName, firstValue),
                        p(paramName, secondValue)
                ])
        ]

        when:
        def report = validator.validate(links)

        then:
        // MUST be reported (your strictness decides warning vs error)
        hasWarnings(report) || hasErrors(report)
        anyMsg(report, paramName)
        anyMsg(report, "multiple") || anyMsg(report, "duplicate") || anyMsg(report, "not allowed")
        anyMsg(report, "index 0")

        and:
        // Optional but highly recommended contract test:
        // validator should deterministically keep one value (e.g. first) and ignore the rest.
        // If your validator returns a normalized/filtered link list, assert it here.
        // Example (adapt to your API):
        // def normalized = report.normalizedLinks()
        // normalized[0].param(paramName) == firstValue

        where:
        paramName  | firstValue            | secondValue
        "type"     | "text/html"           | "application/json"
        "title"    | "Landing page"        | "Other title"
        "title*"   | "UTF-8'en'Hello"      | "UTF-8'en'World"
        "media"    | "screen"              | "print"
        "anchor"   | "https://ex.org/a"    | "https://ex.org/b"
    }

    def "hreflang may occur multiple times (allowed repeatable parameter) and should not produce error or warning"() {
        given:
        def links = [
                link("https://example.org/a", [
                        p("rel", "describedby"),
                        p("hreflang", "en"),
                        p("hreflang", "de")
                ])
        ]

        when:
        def report = validator.validate(links)

        then:
        !hasErrors(report)
        !hasWarnings(report)
    }

    // ----------------------------------------------------------------------
    // Anchor parameter is extension in RFC8288 (but used in RFC9264/linksets).
    // We treat it as allowed parameter name; value is URI-ish but may be string.
    // ----------------------------------------------------------------------

    def "anchor parameter is allowed; anchor value may be absolute URI string"() {
        given:
        def links = [
                link("https://example.org/a", [
                        p("rel", "cite-as"),
                        p("anchor", "https://example.org/origin/1")
                ])
        ]

        when:
        def report = validator.validate(links)

        then:
        !hasErrors(report)
    }

    def "anchor parameter with invalid URI string may be ERROR or WARNING - but MUST be reported"() {
        given:
        def links = [
                link("https://example.org/a", [
                        p("rel", "cite-as"),
                        p("anchor", "http://[::1") // invalid
                ])
        ]

        when:
        def report = validator.validate(links)

        then:
        (hasWarnings(report) || hasErrors(report))
        anyMsg(report, "anchor")
        anyMsg(report, "invalid")
        anyMsg(report, "index 0")
    }

    def "relative anchor URI-reference is ERROR because parser MUST resolve to absolute in the model (Compass policy)"() {
        given:
        def links = [
                link("https://example.org/a", [
                        p("rel", "cite-as"),
                        p("anchor", "/relative/origin") // valid URI-reference, but not absolute
                ])
        ]

        when:
        def report = validator.validate(links)

        then:
        hasErrors(report)
        anyMsg(report, "anchor")
        anyMsg(report, "absolute") || anyMsg(report, "relative") || anyMsg(report, "resolve")
        anyMsg(report, "index 0")
    }

    def "relative target URI is ERROR because parser MUST resolve to absolute in the model (Compass policy)"() {
        given:
        def links = [
                link("/content/file", [
                        p("rel", "cite-as")
                ])
        ]

        when:
        def report = validator.validate(links)

        then:
        hasErrors(report)
        anyMsg(report, "target")
        anyMsg(report, "absolute") || anyMsg(report, "relative") || anyMsg(report, "resolve")
        anyMsg(report, "index 0")
    }

    def "fragment-only anchor is ERROR because it is not absolute (Compass policy)"() {
        given:
        def links = [
                link("https://example.org/a", [
                        p("rel", "cite-as"),
                        p("anchor", "#section") // URI-reference but not absolute
                ])
        ]

        when:
        def report = validator.validate(links)

        then:
        hasErrors(report)
        anyMsg(report, "anchor")
        anyMsg(report, "absolute") || anyMsg(report, "fragment") || anyMsg(report, "resolve")
        anyMsg(report, "index 0")
    }

    // ----------------------------------------------------------------------
    // Flag / valueless parameters (allowed in RFC8288)
    // ----------------------------------------------------------------------

    def "valueless parameter is allowed (flag param) as long as name is token"() {
        given:
        def links = [
                link("https://example.org/a", [
                        p("rel", "cite-as"),
                        flag("templated")  // or any extension parameter
                ])
        ]

        when:
        def report = validator.validate(links)

        then:
        !hasErrors(report)
    }

    // ----------------------------------------------------------------------
    // Mixed issues: validator should accumulate findings (non-fatal)
    // ----------------------------------------------------------------------

    def "accumulates issues across multiple links (non-fatal) and reports all violations"() {
        given:
        def links = [
                // Missing rel
                link("https://example.org/a", [p("type", "text/html")]),
                // Bad param name
                link("https://example.org/b", [p("rel", "cite-as"), p("bad name", "x")]),
                // Relative target
                link("/rel", [p("rel", "describedby")]),
        ]

        when:
        def report = validator.validate(links)

        then:
        hasErrors(report)
        issues(report.issueReport()).size() >= 3
        anyMsg(report, "missing")
        anyMsg(report, "bad name")
        anyMsg(report, "relative")
        anyMsg(report, "index 0")
        anyMsg(report, "index 1")
        anyMsg(report, "index 2")
    }
}
