package life.qbic.compass.processing;

import java.util.List;
import java.util.Objects;
import life.qbic.compass.LinkSetViewAggregationStrategy;
import life.qbic.compass.model.SignPostingResult;
import life.qbic.compass.model.SignPostingView;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Strict aggregation strategy that fails if more than one
 * {@link life.qbic.compass.model.Level2LinksetView} is present.
 *
 * <p>
 * If zero or one linkset view is encountered, aggregation succeeds.
 * If two or more validators produce a linkset view, aggregation fails
 * with an {@link LinkSetViewAggregationStrategy.AggregationStrategyException}.
 * </p>
 *
 * <p>
 * This strategy enforces a strong invariant:
 * <em>at most one</em> Level&nbsp;2 linkset view may exist.
 * </p>
 *
 * <p>
 * It is recommended for:
 * </p>
 * <ul>
 *   <li>strict validation pipelines,</li>
 *   <li>testing and debugging validator composition, or</li>
 *   <li>environments where multiple Level&nbsp;2 producers indicate a configuration error.</li>
 * </ul>
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
