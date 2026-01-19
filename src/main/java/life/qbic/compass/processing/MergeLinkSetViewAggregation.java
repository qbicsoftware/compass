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
 * <class short description>
 *
 * @since <version tag>
 */
public class MergeLinkSetViewAggregation implements LinkSetViewAggregationStrategy {

  @Override
  public SignPostingResult apply(List<SignPostingResult> results)
      throws AggregationStrategyException {
    Objects.requireNonNull(results);

    if (results.isEmpty()) {
      throw new AggregationStrategyException("Signposting result list must not be empty");
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
