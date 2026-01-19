package life.qbic.compass.processing;

import java.util.List;
import java.util.Objects;
import life.qbic.compass.LinkSetViewAggregationStrategy;
import life.qbic.compass.model.Level2LinksetView;
import life.qbic.compass.model.SignPostingResult;
import life.qbic.compass.model.SignPostingView;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public class FailOnMultipleLinkSetViewAggregation implements LinkSetViewAggregationStrategy {

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

    var allLinkSetViews = results.stream()
        .filter(SignPostingResult::hasLinkSetView)
        .map(SignPostingResult::level2LinksetView)
        .toList();
    
    if (allLinkSetViews.size() > 1 ) {
      throw new AggregationStrategyException("More than one linkset view available");
    }

    var selectedLinkSetView = allLinkSetViews.isEmpty() ? null : allLinkSetViews.getFirst();

    return new SignPostingResult(
        new SignPostingView(results.getFirst().signPostingView().webLinks()),
        new IssueReport(aggregatedIssues), selectedLinkSetView);

  }
}
