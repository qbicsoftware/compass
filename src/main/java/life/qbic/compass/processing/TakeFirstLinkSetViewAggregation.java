package life.qbic.compass.processing;

import java.util.List;
import java.util.Objects;
import life.qbic.compass.LinkSetViewAggregationStrategy;
import life.qbic.compass.model.SignPostingResult;
import life.qbic.compass.model.SignPostingView;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public class TakeFirstLinkSetViewAggregation implements LinkSetViewAggregationStrategy {

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

    var firstLinkSetView = results.stream()
        .filter(SignPostingResult::hasLinkSetView)
        .findFirst()
        .map(SignPostingResult::level2LinksetView);

    return new SignPostingResult(
        new SignPostingView(results.getFirst().signPostingView().webLinks()),
        new IssueReport(aggregatedIssues), firstLinkSetView.orElse(null));

  }
}
