package life.qbic.compass.processing;

import java.util.List;
import life.qbic.compass.LinkSetViewAggregationStrategy;
import life.qbic.compass.model.SignPostingResult;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public class TakeFirstLinkSetViewAggregation implements LinkSetViewAggregationStrategy {

  @Override
  public SignPostingResult apply(List<SignPostingResult> results)
      throws AggregationStrategyException {
    return null;
  }
}
