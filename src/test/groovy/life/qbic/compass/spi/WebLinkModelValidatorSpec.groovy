package life.qbic.compass.spi

import life.qbic.linksmith.spi.WebLinkValidator
import spock.lang.Specification

class WebLinkModelValidatorSpec extends Specification {
    def "constructor performs defensive copy of IssueReport issues list"() {
        given:
        def originalIssues = new ArrayList<WebLinkValidator.Issue>()
        originalIssues.add(WebLinkValidator.Issue.warning("w1"))
        def originalReport = new WebLinkValidator.IssueReport(originalIssues)

        and:
        def flags = new BitSet()
        flags.set(1)

        when:
        def result = new WebLinkModelValidator.ModelValidationResult(originalReport, flags)

        and: "mutate original list after construction"
        originalIssues.add(WebLinkValidator.Issue.error("e1"))

        then: "result's report is unaffected"
        result.issueReport().issues()*.message() == ["w1"]
    }

    def "constructor performs defensive copy of BitSet"() {
        given:
        def report = new WebLinkValidator.IssueReport([WebLinkValidator.Issue.warning("w1")])

        and:
        def original = new BitSet()
        original.set(0)
        original.set(3)

        when:
        def result = new WebLinkModelValidator.ModelValidationResult(report, original)

        and: "mutate original BitSet after construction"
        original.clear(0)
        original.set(5)

        then: "result's BitSet is unaffected (still has original bits)"
        result.blockingIndices().get(0)
        result.blockingIndices().get(3)
        !result.blockingIndices().get(5)
    }

    def "equals and hashCode consider IssueReport and BitSet content"() {
        given:
        def report1 = new WebLinkValidator.IssueReport([WebLinkValidator.Issue.warning("w1"), WebLinkValidator.Issue.error("e1")])
        def report2 = new WebLinkValidator.IssueReport([WebLinkValidator.Issue.warning("w1"), WebLinkValidator.Issue.error("e1")])

        and:
        def b1 = new BitSet()
        b1.set(2); b1.set(7)

        def b2 = new BitSet()
        b2.set(2); b2.set(7)

        when:
        def r1 = new WebLinkModelValidator.ModelValidationResult(report1, b1)
        def r2 = new WebLinkModelValidator.ModelValidationResult(report2, b2)

        then:
        r1 == r2
        r1.hashCode() == r2.hashCode()
    }

    def "constructor throws NullPointerException for null fields"() {
        given:
        def report = new WebLinkValidator.IssueReport([])
        def bits = new BitSet()

        when:
        new WebLinkModelValidator.ModelValidationResult(null, bits)

        then:
        thrown(NullPointerException)

        when:
        new WebLinkModelValidator.ModelValidationResult(report, null)

        then:
        thrown(NullPointerException)
    }
}
