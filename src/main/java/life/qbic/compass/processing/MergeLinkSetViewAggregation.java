package life.qbic.compass.processing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import life.qbic.compass.LinkSetViewAggregationStrategy;
import life.qbic.compass.model.ContentResourceView;
import life.qbic.compass.model.LandingPageView;
import life.qbic.compass.model.Level2LinksetView;
import life.qbic.compass.model.MetadataResourceView;
import life.qbic.compass.model.MissingOriginLink;
import life.qbic.compass.model.SignPostingResult;
import life.qbic.compass.model.SignPostingView;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Aggregation strategy that merges multiple
 * {@link life.qbic.compass.model.Level2LinksetView} instances into a single view.
 *
 * <p>
 * All landing pages, content resources, metadata resources, and missing-origin
 * links from the individual views are combined into a new aggregated view.
 * </p>
 *
 * <p>
 * This strategy assumes that individual {@code Level2LinksetView}s are
 * <em>compatible</em> and does not attempt to detect semantic conflicts
 * (e.g. duplicate origins with differing semantics).
 * </p>
 *
 * <p>
 * Use this strategy when:
 * </p>
 * <ul>
 *   <li>multiple validators contribute complementary Level&nbsp;2 information, and</li>
 *   <li>the client is prepared to handle potential overlaps or redundancies.</li>
 * </ul>
 *
 * <p>
 * This strategy may throw {@link LinkSetViewAggregationStrategy.AggregationStrategyException}
 * if merging is structurally impossible (e.g. unexpected null invariants) or if the provided
 * result list is empty and no aggregation can be performed.
 * </p>
 */
public class MergeLinkSetViewAggregation implements LinkSetViewAggregationStrategy {

  @Override
  public SignPostingResult apply(List<SignPostingResult> results)
      throws AggregationStrategyException {
    Objects.requireNonNull(results);

    if (results.isEmpty()) {
      throw new AggregationStrategyException("Aggregation strategy was invoked without any results to aggregate");
    }

    var aggregatedIssues = results.stream()
        .map(SignPostingResult::issueReport)
        .flatMap(issueReport -> issueReport.issues().stream())
        .toList();

    var linkSetViews = results.stream()
        .filter(SignPostingResult::hasLinkSetView)
        .map(SignPostingResult::level2LinksetView)
        .toList();

    var mergedView = linkSetViews.isEmpty() ? null : mergeViews(linkSetViews);

    return new SignPostingResult(
        new SignPostingView(results.getFirst().signPostingView().webLinks()),
        new IssueReport(aggregatedIssues), mergedView);

  }

  private static Level2LinksetView mergeViews(List<Level2LinksetView> linkSetViews) {
    Objects.requireNonNull(linkSetViews);
    var landingPages = new ArrayList<LandingPageView>();
    var contentResources = new ArrayList<ContentResourceView>();
    var metadataResources = new ArrayList<MetadataResourceView>();
    var missingOriginLinks = new ArrayList<MissingOriginLink>();
    for (var currentLinkSet : linkSetViews) {
      Objects.requireNonNull(currentLinkSet);
      landingPages.addAll(currentLinkSet.landingPages());
      contentResources.addAll(currentLinkSet.contentResources());
      metadataResources.addAll(currentLinkSet.metadataResources());
      missingOriginLinks.addAll(currentLinkSet.missingOriginLinks());
    }
    return new Level2LinksetView(
        landingPages,
        contentResources,
        metadataResources,
        missingOriginLinks);
  }
}
