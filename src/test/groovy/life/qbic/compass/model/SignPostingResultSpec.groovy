package life.qbic.compass.model

import spock.lang.Specification
import spock.lang.Unroll

class SignPostingResultSpec extends Specification {

    @Unroll
    def "stable API: method '#methodName' is available with return type '#returnType.simpleName' and params #paramTypes*.simpleName"() {
        when:
        def method = SignPostingResult.getMethod(methodName, paramTypes as Class[])

        then:
        method != null
        method.returnType == returnType

        where:
        methodName             | returnType        | paramTypes
        "signPostingView"      | SignPostingView   | ([] as Class[])
        "issueReport"          | life.qbic.linksmith.spi.WebLinkValidator.IssueReport | ([] as Class[])
        "level2LinksetView"    | Level2LinksetView | ([] as Class[])
        "hasLinkSetView"       | boolean           | ([] as Class[])
        "hasIssues"            | boolean           | ([] as Class[])
        "hasErrors"            | boolean           | ([] as Class[])
        "hasWarnings"          | boolean           | ([] as Class[])
        "withoutLinksetView"   | SignPostingResult | ([SignPostingView, life.qbic.linksmith.spi.WebLinkValidator.IssueReport] as Class[])
        "withLinksetView"      | SignPostingResult | ([SignPostingView, life.qbic.linksmith.spi.WebLinkValidator.IssueReport, Level2LinksetView] as Class[])
    }

    def "stable API: canonical record constructor is available"() {
        when:
        def ctor = SignPostingResult.getDeclaredConstructor(
                SignPostingView,
                life.qbic.linksmith.spi.WebLinkValidator.IssueReport,
                Level2LinksetView
        )

        then:
        ctor != null
    }

    def "stable API: record fundamental methods exist"() {
        expect:
        SignPostingResult.getMethod("equals", Object) != null
        SignPostingResult.getMethod("hashCode") != null
        SignPostingResult.getMethod("toString") != null
    }

    def "behavior: hasLinkSetView returns false when level2LinksetView is null"() {
        given:
        def view = new SignPostingView([])
        def report = new life.qbic.linksmith.spi.WebLinkValidator.IssueReport([])

        and:
        def result = new SignPostingResult(view, report, null)

        expect:
        !result.hasLinkSetView()
    }

    def "behavior: hasLinkSetView returns true when level2LinksetView is present"() {
        given:
        def view = new SignPostingView([])
        def report = new life.qbic.linksmith.spi.WebLinkValidator.IssueReport([])

        and:
        def nonNullLevel2View = linkSetView()

        and:
        def result = new SignPostingResult(view, report, nonNullLevel2View)

        expect:
        result.hasLinkSetView()
    }

    @Unroll
    def "behavior: hasLinkSetView is consistent with null-check (#caseName)"(String caseName, Level2LinksetView level2View) {
        given:
        def view = new SignPostingView([])
        def report = new life.qbic.linksmith.spi.WebLinkValidator.IssueReport([])

        and:
        def result = new SignPostingResult(view, report, level2View)

        expect:
        result.hasLinkSetView() == (result.level2LinksetView() != null)

        where:
        caseName        | level2View
        "null view"     | null
        "non-null view" | linkSetView()
    }

    def "behavior: hasIssues/hasErrors/hasWarnings are all false when IssueReport is empty"() {
        given:
        def view = new SignPostingView([])
        def report = new life.qbic.linksmith.spi.WebLinkValidator.IssueReport([])

        and:
        def result = new SignPostingResult(view, report, null)

        expect:
        !result.hasIssues()
        !result.hasErrors()
        !result.hasWarnings()
    }

    def "behavior: hasWarnings true implies hasIssues true (warnings are issues)"() {
        given:
        def view = new SignPostingView([])
        def warning = life.qbic.linksmith.spi.WebLinkValidator.Issue.warning("w1")
        def report = new life.qbic.linksmith.spi.WebLinkValidator.IssueReport([warning])

        and:
        def result = new SignPostingResult(view, report, null)

        expect:
        result.hasWarnings()
        !result.hasErrors()
        result.hasIssues()
    }

    def "behavior: hasErrors true implies hasIssues true"() {
        given:
        def view = new SignPostingView([])
        def error = life.qbic.linksmith.spi.WebLinkValidator.Issue.error("e1")
        def report = new life.qbic.linksmith.spi.WebLinkValidator.IssueReport([error])

        and:
        def result = new SignPostingResult(view, report, null)

        expect:
        !result.hasWarnings()
        result.hasErrors()
        result.hasIssues()
    }

    def "behavior: withoutLinksetView creates a result with null level2LinksetView"() {
        given:
        def view = new SignPostingView([])
        def report = new life.qbic.linksmith.spi.WebLinkValidator.IssueReport([])

        when:
        def result = SignPostingResult.withoutLinksetView(view, report)

        then:
        result.level2LinksetView() == null
        !result.hasLinkSetView()
    }

    def "behavior: withLinksetView rejects null level2LinksetView"() {
        given:
        def view = new SignPostingView([])
        def report = new life.qbic.linksmith.spi.WebLinkValidator.IssueReport([])

        when:
        SignPostingResult.withLinksetView(view, report, null)

        then:
        thrown(NullPointerException)
    }

    def "behavior: withLinksetView creates a result with non-null level2LinksetView"() {
        given:
        def view = new SignPostingView([])
        def report = new life.qbic.linksmith.spi.WebLinkValidator.IssueReport([])
        def level2 = linkSetView()

        when:
        def result = SignPostingResult.withLinksetView(view, report, level2)

        then:
        result.level2LinksetView().is(level2)
        result.hasLinkSetView()
    }

    private static Level2LinksetView linkSetView() {
        return new Level2LinksetView([], [], [], [])
    }
}
