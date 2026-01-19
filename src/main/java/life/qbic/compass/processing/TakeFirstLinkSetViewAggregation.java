package life.qbic.compass.processing;

import java.util.List;
import java.util.Objects;
import life.qbic.compass.LinkSetViewAggregationStrategy;
import life.qbic.compass.model.SignPostingResult;
import life.qbic.compass.model.SignPostingView;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Aggregation strategy that selects the <em>first</em> available
 * {@link life.qbic.compass.model.Level2LinksetView} and ignores all subsequent ones.
 *
 * <p>
 * The first {@link SignPostingResult} in iteration order that contains a
 * non-null {@code level2LinksetView} wins.
 * </p>
 *
 * <p>
 * This is the default strategy used by {@link life.qbic.compass.SignPostingProcessor}
 * because it provides predictable behavior without failing in multi-validator setups.
 * </p>
 *
 * <p>
 * <strong>Important:</strong> Later validators producing conflicting or more complete
 * linkset views are silently ignored.
 * </p>
 *
 * <p>
 * This strategy only throws {@link LinkSetViewAggregationStrategy.AggregationStrategyException} in case the provided
 * result list is empty.
 * </p>
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
