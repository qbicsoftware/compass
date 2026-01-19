package life.qbic.compass.processing;

import java.util.List;
import java.util.Objects;
import life.qbic.compass.LinkSetViewAggregationStrategy;
import life.qbic.compass.model.SignPostingResult;
import life.qbic.compass.model.SignPostingView;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Aggregation strategy that deliberately ignores all
 * {@link life.qbic.compass.model.Level2LinksetView} instances.
 *
 * <p>
 * The resulting {@link life.qbic.compass.model.SignPostingResult} will always have
 * {@code level2LinksetView == null}, regardless of how many validators produced a linkset view.
 * </p>
 *
 * <p>
 * This strategy is useful when:
 * </p>
 * <ul>
 *   <li>clients are only interested in issues and {@link life.qbic.compass.model.SignPostingView}, or</li>
 *   <li>Level&nbsp;2 structure is handled externally or in a separate workflow.</li>
 * </ul>
 *
 * <p>
 * This strategy only throws {@link LinkSetViewAggregationStrategy.AggregationStrategyException} in case the provided
 * result list is empty.
 * </p>
 */
public class NoLinkSetViewAggregation implements LinkSetViewAggregationStrategy {

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

    return new SignPostingResult(
        new SignPostingView(results.getFirst().signPostingView().webLinks()),
        new IssueReport(aggregatedIssues), null);
  }
}
