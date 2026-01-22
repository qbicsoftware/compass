package life.qbic.compass

import life.qbic.compass.SignPostingProcessor.LinkSetViewAggregationMode
import life.qbic.compass.processing.FailOnMultipleLinkSetViewAggregation
import life.qbic.compass.processing.MergeLinkSetViewAggregation
import life.qbic.compass.processing.NoLinkSetViewAggregation
import life.qbic.compass.processing.TakeFirstLinkSetViewAggregation
import spock.lang.Specification
import spock.lang.Unroll

class LinkSetViewAggregationsSpec extends Specification {

    @Unroll
    def "routes mode #mode to strategy type #expectedType.simpleName"() {
        when:
        def strategy = LinkSetViewAggregations.forMode(mode)

        then:
        strategy != null
        strategy.class == expectedType

        where:
        mode                                        || expectedType
        LinkSetViewAggregationMode.NONE             || NoLinkSetViewAggregation
        LinkSetViewAggregationMode.FIRST            || TakeFirstLinkSetViewAggregation
        LinkSetViewAggregationMode.MERGE            || MergeLinkSetViewAggregation
        LinkSetViewAggregationMode.FAIL_ON_MULTIPLE || FailOnMultipleLinkSetViewAggregation
    }

    @Unroll
    def "returns singleton instance for mode #mode (same object on repeated calls)"() {
        when:
        def s1 = LinkSetViewAggregations.forMode(mode as LinkSetViewAggregationMode)
        def s2 = LinkSetViewAggregations.forMode(mode as LinkSetViewAggregationMode)
        def s3 = LinkSetViewAggregations.forMode(mode as LinkSetViewAggregationMode)

        then: "Groovy identity check"
        s1.is(s2)
        s2.is(s3)

        where:
        mode << LinkSetViewAggregationMode.values()
    }

    def "different modes return different instances (no accidental aliasing)"() {
        when:
        def strategies = LinkSetViewAggregationMode.values()
                .collect { LinkSetViewAggregations.forMode(it) }

        then: "all strategies are pairwise distinct by identity"
        def identities = strategies.collect { System.identityHashCode(it) }
        identities.size() == identities.toSet().size()

        and: "sanity: exactly one instance per mode"
        strategies.size() == LinkSetViewAggregationMode.values().length
    }

    def "null mode throws NullPointerException"() {
        when:
        LinkSetViewAggregations.forMode(null)

        then:
        thrown(NullPointerException)
    }
}
