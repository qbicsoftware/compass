package life.qbic.compass;

import java.util.List;
import life.qbic.compass.model.SignPostingResult;

/**
 * Strategy interface for aggregating {@link SignPostingResult} instances produced by
 * multiple {@link life.qbic.compass.spi.SignPostingValidator}s into a single result.
 *
 * <p>
 * Aggregation strategies define how (and whether) multiple
 * {@link life.qbic.compass.model.Level2LinksetView} instances are combined, selected,
 * ignored, or rejected when more than one validator produces such a view.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Inspect the list of {@link SignPostingResult}s returned by validators.</li>
 *   <li>Decide how to handle zero, one, or multiple Level&nbsp;2 Linkset Views.</li>
 *   <li>Return a single {@link SignPostingResult} that represents the aggregated outcome.</li>
 * </ul>
 *
 * <h2>Non-responsibilities</h2>
 * <ul>
 *   <li>Strategies must <em>not</em> execute validators.</li>
 *   <li>Strategies must <em>not</em> modify individual {@link SignPostingResult} instances.</li>
 *   <li>Strategies must <em>not</em> perform model or profile validation.</li>
 * </ul>
 *
 * <h2>Error handling</h2>
 * <p>
 * Implementations may throw {@link AggregationStrategyException} if the aggregation
 * policy cannot be satisfied (e.g. when multiple Linkset Views are present but the
 * strategy requires exactly one).
 * </p>
 *
 * <p>
 * This interface is primarily intended for internal use by
 * {@link life.qbic.compass.SignPostingProcessor}, but is exposed to allow advanced
 * clients to supply custom aggregation behavior.
 * </p>
 *
 * @since 1.0.0
 */
public interface LinkSetViewAggregationStrategy {

  /**
   * Aggregates the provided validation results into a single {@link SignPostingResult}.
   *
   * <p>
   * The input list represents the results of all configured validators, in execution order.
   * Implementations may inspect, select, merge, or ignore individual results according to
   * their aggregation policy.
   * </p>
   *
   * @param results the results produced by all executed validators
   * @return a single aggregated {@link SignPostingResult}
   * @throws AggregationStrategyException if aggregation fails according to the strategy rules
   */
  SignPostingResult apply(List<SignPostingResult> results)
      throws AggregationStrategyException;

  /**
   * Exception thrown when a {@link LinkSetViewAggregationStrategy} cannot successfully
   * aggregate the provided results.
   *
   * <p>
   * This is a runtime exception because aggregation failures indicate a configuration
   * or policy violation rather than a recoverable validation error.
   * </p>
   */
  class AggregationStrategyException extends RuntimeException {

    public AggregationStrategyException(String message) {
      super(message);
    }
  }
}
