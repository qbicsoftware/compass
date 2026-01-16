package life.qbic.compass;

import java.util.List;
import life.qbic.compass.model.SignPostingResult;

/**
 * <interface short description>
 *
 * @since <version tag>
 */
public interface LinkSetViewAggregationStrategy {

  SignPostingResult apply(List<SignPostingResult> results) throws AggregationStrategyException;

  class AggregationStrategyException extends RuntimeException {
    public AggregationStrategyException(String message) {
      super(message);
    }
  }
}
