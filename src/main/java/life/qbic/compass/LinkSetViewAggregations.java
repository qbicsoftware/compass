package life.qbic.compass;

import life.qbic.compass.SignPostingProcessor.LinkSetViewAggregationMode;
import life.qbic.compass.processing.FailOnMultipleLinkSetViewAggregation;
import life.qbic.compass.processing.MergeLinkSetViewAggregation;
import life.qbic.compass.processing.NoLinkSetViewAggregation;
import life.qbic.compass.processing.TakeFirstLinkSetViewAggregation;

/**
 * Factory and registry for {@link LinkSetViewAggregationStrategy} implementations.
 *
 * <p>
 * This class centralizes the mapping between {@link SignPostingProcessor.LinkSetViewAggregationMode}
 * values and their concrete aggregation strategy implementations. It exists to:
 * </p>
 *
 * <ul>
 *   <li>keep {@link SignPostingProcessor} free of conditional logic,</li>
 *   <li>encapsulate aggregation policy decisions in one place, and</li>
 *   <li>provide a stable extension point for future aggregation modes.</li>
 * </ul>
 *
 * <h2>Design intent</h2>
 * <p>
 * Aggregation of {@code Level2LinksetView} instances is a <em>policy decision</em>, not a validation
 * concern. Different clients may want:
 * </p>
 * <ul>
 *   <li>to ignore linkset views entirely,</li>
 *   <li>to accept only the first valid linkset view,</li>
 *   <li>to merge multiple linkset views into a single composite view, or</li>
 *   <li>to fail fast if more than one linkset view is produced.</li>
 * </ul>
 *
 * <p>
 * This factory ensures that the processor only needs to work with an enum
 * ({@link SignPostingProcessor.LinkSetViewAggregationMode}), while the concrete
 * strategy selection and lifecycle is handled here.
 * </p>
 *
 * <h2>Implementation notes for maintainers</h2>
 * <ul>
 *   <li>
 *     Strategies are held as <strong>singleton instances</strong>.
 *     They must therefore be stateless and thread-safe.
 *   </li>
 *   <li>
 *     If a future strategy requires configuration or state, this design will
 *     need to be revisited (e.g. per-processor instantiation instead of singletons).
 *   </li>
 *   <li>
 *     Adding a new aggregation mode requires:
 *     <ol>
 *       <li>adding a new enum constant to {@code LinkSetViewAggregationMode},</li>
 *       <li>implementing {@link LinkSetViewAggregationStrategy}, and</li>
 *       <li>registering it in this factory.</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <h2>Stability guarantees</h2>
 * <p>
 * This class is package-private and intended for internal use only. The set of
 * available aggregation modes is part of the public API via the enum, but the
 * concrete strategy classes and their internal behavior may evolve.
 * </p>
 *
 * @since 1.0.0
 * @author Sven Fillinger
 */
final class LinkSetViewAggregations {

  private static final LinkSetViewAggregationStrategy NONE =
      new NoLinkSetViewAggregation();
  private static final LinkSetViewAggregationStrategy FIRST =
      new TakeFirstLinkSetViewAggregation();
  private static final LinkSetViewAggregationStrategy MERGE =
      new MergeLinkSetViewAggregation();
  private static final LinkSetViewAggregationStrategy FAIL =
      new FailOnMultipleLinkSetViewAggregation();


  private LinkSetViewAggregations() {
    // utility class
  }

 static LinkSetViewAggregationStrategy forMode(LinkSetViewAggregationMode mode) {
   return switch (mode) {
     case LinkSetViewAggregationMode.NONE -> NONE;
     case LinkSetViewAggregationMode.FIRST -> FIRST;
     case LinkSetViewAggregationMode.MERGE -> MERGE;
     case LinkSetViewAggregationMode.FAIL_ON_MULTIPLE -> FAIL;
   };
 }
}
