package life.qbic.compass.processing

import life.qbic.compass.LinkSetViewAggregationStrategy
import life.qbic.compass.model.*
import life.qbic.linksmith.model.WebLink
import life.qbic.linksmith.spi.WebLinkValidator.Issue
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport
import spock.lang.Specification

class LinkSetViewAggregationStrategiesSpec extends Specification {

    // --------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------

    private static SignPostingView viewWith(int marker) {
        // We only need a stable instance; content is irrelevant for these tests.
        new SignPostingView([] as List<WebLink>)
    }

    private static IssueReport issues(int n, String prefix = "i") {
        def list = (1..n).collect { Issue.warning("${prefix}${it}") }
        new IssueReport(list)
    }

    private static SignPostingResult result(SignPostingView view, IssueReport report, Level2LinksetView linksetView) {
        new SignPostingResult(view, report, linksetView)
    }

    private static Level2LinksetView linkset(int landing, int content, int metadata, int missing) {
        def mkLinks = { [] as List<WebLink> }
        def mkLanding = { int i -> new LandingPageView(URI.create("https://example.org/landing/$i"), mkLinks()) }
        def mkContent = { int i -> new ContentResourceView(URI.create("https://example.org/content/$i"), mkLinks()) }
        def mkMeta    = { int i -> new MetadataResourceView(URI.create("https://example.org/meta/$i"), mkLinks()) }

        // We avoid constructing MissingOriginLink with fields we don't know.
        // If your MissingOriginLink record has a public ctor, feel free to populate this list.
        def missingLinks = [] as List<MissingOriginLink>

        new Level2LinksetView(
                (0..<landing).collect { mkLanding(it) },
                (0..<content).collect { mkContent(it) },
                (0..<metadata).collect { mkMeta(it) },
                missingLinks
        )
    }

    private static int issueCount(SignPostingResult r) {
        r.issueReport()?.issues()?.size() ?: 0
    }

    // --------------------------------------------------------------------------
    // NoLinkSetViewAggregation
    // --------------------------------------------------------------------------

    def "NoLinkSetViewAggregation drops any Level2LinksetView but preserves aggregated issues"() {
        given:
        def s = new NoLinkSetViewAggregation()
        def sv = viewWith(1)
        def v1 = linkset(1, 0, 0, 0)

        def results = [
                result(sv, issues(1, "a"), v1),
                result(sv, issues(2, "b"), null)
        ]

        when:
        def out = s.apply(results)

        then:
        out != null
        out.level2LinksetView() == null
        issueCount(out) == 3
    }

    def "NoLinkSetViewAggregation throws on empty input"() {
        given:
        def s = new NoLinkSetViewAggregation()

        when:
        s.apply([])

        then:
        thrown(LinkSetViewAggregationStrategy.AggregationStrategyException)
    }

    // --------------------------------------------------------------------------
    // TakeFirstLinkSetViewAggregation
    // --------------------------------------------------------------------------

    def "TakeFirstLinkSetViewAggregation takes the first non-null Level2LinksetView"() {
        given:
        def s = new TakeFirstLinkSetViewAggregation()
        def sv = viewWith(1)
        def first = linkset(1, 0, 0, 0)
        def second = linkset(0, 2, 0, 0)

        def results = [
                result(sv, issues(1, "a"), null),
                result(sv, issues(1, "b"), first),
                result(sv, issues(1, "c"), second)
        ]

        when:
        def out = s.apply(results)

        then:
        out.level2LinksetView() == first
        issueCount(out) == 3
    }

    def "TakeFirstLinkSetViewAggregation returns null linkset view if none are present"() {
        given:
        def s = new TakeFirstLinkSetViewAggregation()
        def sv = viewWith(1)

        def results = [
                result(sv, issues(1, "a"), null),
                result(sv, issues(2, "b"), null)
        ]

        when:
        def out = s.apply(results)

        then:
        out.level2LinksetView() == null
        issueCount(out) == 3
    }

    // --------------------------------------------------------------------------
    // FailOnMultipleLinkSetViewAggregation
    // --------------------------------------------------------------------------

    def "FailOnMultipleLinkSetViewAggregation returns the only non-null Level2LinksetView"() {
        given:
        def s = new FailOnMultipleLinkSetViewAggregation()
        def sv = viewWith(1)
        def only = linkset(0, 1, 0, 0)

        def results = [
                result(sv, issues(1, "a"), null),
                result(sv, issues(1, "b"), only),
                result(sv, issues(1, "c"), null)
        ]

        when:
        def out = s.apply(results)

        then:
        out.level2LinksetView() == only
        issueCount(out) == 3
    }

    def "FailOnMultipleLinkSetViewAggregation throws if multiple non-null Level2LinksetViews exist"() {
        given:
        def s = new FailOnMultipleLinkSetViewAggregation()
        def sv = viewWith(1)

        def results = [
                result(sv, issues(1, "a"), linkset(1, 0, 0, 0)),
                result(sv, issues(1, "b"), linkset(0, 1, 0, 0))
        ]

        when:
        s.apply(results)

        then:
        thrown(LinkSetViewAggregationStrategy.AggregationStrategyException)
    }

    def "FailOnMultipleLinkSetViewAggregation returns null if none are present"() {
        given:
        def s = new FailOnMultipleLinkSetViewAggregation()
        def sv = viewWith(1)

        def results = [
                result(sv, issues(1, "a"), null),
                result(sv, issues(1, "b"), null)
        ]

        when:
        def out = s.apply(results)

        then:
        out.level2LinksetView() == null
        issueCount(out) == 2
    }

    // --------------------------------------------------------------------------
    // MergeLinkSetViewAggregation
    // --------------------------------------------------------------------------

    def "MergeLinkSetViewAggregation merges all non-null Level2LinksetViews by concatenating lists in order"() {
        given:
        def s = new MergeLinkSetViewAggregation()
        def sv = viewWith(1)

        def v1 = linkset(1, 0, 2, 0) // landing=1, content=0, meta=2
        def v2 = linkset(0, 3, 0, 0) // landing=0, content=3, meta=0

        def results = [
                result(sv, issues(1, "a"), v1),
                result(sv, issues(2, "b"), null),
                result(sv, issues(3, "c"), v2)
        ]

        when:
        def out = s.apply(results)

        then:
        issueCount(out) == 6

        and:
        out.level2LinksetView() != null
        out.level2LinksetView().landingPages().size() == 1
        out.level2LinksetView().contentResources().size() == 3
        out.level2LinksetView().metadataResources().size() == 2

        and: "missing-origin list is merged too (here empty in both fixtures)"
        out.level2LinksetView().missingOriginLinks().isEmpty()
    }

    def "MergeLinkSetViewAggregation returns null if no Level2LinksetView exists in any result"() {
        given:
        def s = new MergeLinkSetViewAggregation()
        def sv = viewWith(1)

        def results = [
                result(sv, issues(1, "a"), null),
                result(sv, issues(1, "b"), null)
        ]

        when:
        def out = s.apply(results)

        then:
        out.level2LinksetView() == null
        issueCount(out) == 2
    }

    def "MergeLinkSetViewAggregation throws on empty input"() {
        given:
        def s = new MergeLinkSetViewAggregation()

        when:
        s.apply([])

        then:
        thrown(LinkSetViewAggregationStrategy.AggregationStrategyException)
    }
}
