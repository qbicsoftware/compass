package life.qbic.compass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import life.qbic.compass.LinkSetViewAggregationStrategy.AggregationStrategyException;
import life.qbic.compass.model.SignPostingResult;
import life.qbic.compass.model.SignPostingView;
import life.qbic.compass.spi.SignPostingValidator;
import life.qbic.compass.spi.WebLinkModelValidator;
import life.qbic.compass.validation.Level1SignPostingValidator;
import life.qbic.compass.validation.WebLinkModelValidators;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.Issue;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Orchestrates the evaluation of {@link WebLink}s against one or more Signposting profile
 * validators.
 *
 * <p>
 * The {@code SignPostingProcessor} acts as the main entry point for applying Signposting semantics
 * on top of RFC&nbsp;8288–compliant WebLinks produced by Linksmith. It coordinates one or more
 * {@link SignPostingValidator}s and exposes a consolidated {@link SignPostingResult}.
 * </p>
 *
 * <p>
 * The processor itself does not interpret Signposting rules; all profile-specific logic is
 * delegated to the configured validators. This allows:
 * </p>
 * <ul>
 *   <li>validation of different Signposting levels (e.g. Level&nbsp;1, Level&nbsp;2 discovery),</li>
 *   <li>composition of multiple validators in a single processing step, and</li>
 *   <li>extension with custom or domain-specific Signposting profiles.</li>
 * </ul>
 *
 * <p>
 * Processing is strictly <strong>in-memory and side-effect free</strong>:
 * </p>
 * <ul>
 *   <li>No network requests are performed.</li>
 *   <li>Input WebLinks are not modified.</li>
 *   <li>All validation feedback is reported via the returned {@link SignPostingResult}.</li>
 * </ul>
 *
 * <p>
 * If no validators are explicitly configured, the processor defaults to applying
 * the {@link Level1SignPostingValidator}, ensuring basic FAIR Signposting compliance
 * out of the box.
 * </p>
 *
 * <p>
 * The processor is immutable and thread-safe once built.
 * </p>
 *
 * @author Sven Fillinger
 */
public final class SignPostingProcessor {

  private final LinkSetViewAggregationStrategy linkSetViewAggregationStrategy;

  /**
   * Defines how multiple {@link life.qbic.compass.model.Level2LinksetView} instances
   * produced during Signposting processing are aggregated into the final
   * {@link life.qbic.compass.model.SignPostingResult}.
   *
   * <p>
   * In complex Signposting workflows (especially Level&nbsp;2), multiple validators
   * may independently produce a {@code Level2LinksetView}. This enum represents the
   * <em>aggregation policy</em> used by the {@link life.qbic.compass.SignPostingProcessor}
   * to handle such situations.
   * </p>
   *
   * <p>
   * The chosen mode controls whether linkset views are ignored, merged, selected, or
   * treated as an error. The concrete behavior is implemented by
   * {@link life.qbic.compass.LinkSetViewAggregationStrategy} instances and
   * selected via an internal factory.
   * </p>
   *
   * <h2>Mode semantics</h2>
   * <ul>
   *   <li>{@link #NONE} –
   *       No {@code Level2LinksetView} is propagated.
   *       All produced linkset views are discarded and the final
   *       {@code SignPostingResult} will not expose a linkset view.</li>
   *
   *   <li>{@link #FIRST} –
   *       The first non-null {@code Level2LinksetView} encountered is used.
   *       Any subsequent linkset views are ignored.</li>
   *
   *   <li>{@link #MERGE} –
   *       All produced {@code Level2LinksetView} instances are merged into a single
   *       composite view. This mode assumes that merging is semantically meaningful
   *       and may fail if conflicts occur.</li>
   *
   *   <li>{@link #FAIL_ON_MULTIPLE} –
   *       Exactly zero or one {@code Level2LinksetView} is allowed.
   *       If more than one view is produced, processing fails with an aggregation error.</li>
   * </ul>
   *
   * <h2>Recommended usage</h2>
   * <ul>
   *   <li>
   *     Use {@code NONE} when linkset discovery is out of scope and only
   *     validation issues are relevant.
   *   </li>
   *   <li>
   *     Use {@code FIRST} for best-effort discovery pipelines where at most
   *     one linkset is expected but strict enforcement is unnecessary.
   *   </li>
   *   <li>
   *     Use {@code MERGE} when processing heterogeneous or federated linksets
   *     that are expected to describe multiple independent origins.
   *   </li>
   *   <li>
   *     Use {@code FAIL_ON_MULTIPLE} in strict FAIR validation scenarios where
   *     multiple linkset views indicate an ambiguous or invalid state.</li>
   * </ul>
   *
   * <h2>Stability notes</h2>
   * <p>
   * The set of modes is part of the public API. While additional modes may be
   * introduced in future versions, the semantics of existing modes will not change
   * in incompatible ways.
   * </p>
   *
   * @since 1.0.0
   */
  public enum LinkSetViewAggregationMode {
    /** Discard all produced {@code Level2LinksetView} instances. */
    NONE,

    /** Use the first produced {@code Level2LinksetView} and ignore the rest. */
    FIRST,

    /** Merge all produced {@code Level2LinksetView} instances into one. */
    MERGE,

    /** Fail if more than one {@code Level2LinksetView} is produced. */
    FAIL_ON_MULTIPLE
  }

  private final List<SignPostingValidator> validators;
  private final WebLinkModelValidator modelValidator;

  private SignPostingProcessor(
      List<SignPostingValidator> validators,
      WebLinkModelValidator modelValidator,
      LinkSetViewAggregationStrategy aggregationStrategy
  ) {
    Objects.requireNonNull(validators);
    Objects.requireNonNull(modelValidator);
    Objects.requireNonNull(aggregationStrategy);
    this.validators = List.copyOf(validators);
    this.modelValidator = modelValidator;
    this.linkSetViewAggregationStrategy = aggregationStrategy;

  }

  /**
   * Applies all configured {@link SignPostingValidator}s to the provided WebLinks and aggregates
   * their reported issues into a single {@link SignPostingResult}.
   *
   * <p>
   * Each validator is executed independently and receives the <em>same</em> input list. Validators
   * are not allowed to mutate the input.
   * </p>
   *
   * <p><strong>Aggregation semantics</strong></p>
   * <ul>
   *   <li>All validators are executed in the order they were configured.</li>
   *   <li>All {@link life.qbic.linksmith.spi.WebLinkValidator.Issue}s from all
   *       validators are collected and merged into a single {@link IssueReport}.</li>
   *   <li>No short-circuiting occurs: even if one validator reports errors,
   *       subsequent validators are still executed.</li>
   * </ul>
   *
   * <p><strong>View semantics</strong></p>
   * <p>
   * The processor is <strong>non-destructive with respect to non-null links</strong>.
   * It does not reorder or modify WebLinks. However, {@code null} elements are
   * <strong>filtered out</strong> before validation and before creating the returned
   * {@link SignPostingView}, because {@code null} values cannot be represented safely
   * in the view API.
   * </p>
   *
   * <p>
   * As a result, the returned {@link SignPostingView} contains all non-null WebLinks
   * from the input list, in their original order.
   * </p>
   * <p>
   * This processor intentionally does <em>not</em> merge or expose any
   * {@link life.qbic.compass.model.Level2LinksetView} instances returned by
   * individual validators. If Level&nbsp;2 structural views are required,
   * clients should invoke the corresponding validator directly
   * (e.g. {@code Level2RecipeValidator}).
   * </p>
   *
   * <p><strong>Error handling</strong></p>
   * <ul>
   *   <li>{@code webLinks} must not be {@code null}.</li>
   *   <li>{@code webLinks} may contain {@code null} elements. Null elements are skipped.</li>
   * </ul>
   *
   * @param webLinks the WebLinks to be validated
   * @return a {@link SignPostingResult} containing the aggregated issues and a
   * {@link SignPostingView} over the input links
   * @throws NullPointerException         if {@code webLinks} is {@code null}
   * @throws AggregationStrategyException if a policy of the selected linkset view aggregation
   *                                      strategy has been violated
   */
  public SignPostingResult process(List<WebLink> webLinks)
      throws NullPointerException, AggregationStrategyException {
    Objects.requireNonNull(webLinks);
    var issues = new ArrayList<Issue>();
    var safeLinks = webLinks.stream()
        .filter(Objects::nonNull)
        .toList();

    var sanitizedLinks = applyModelValidation(safeLinks, modelValidator, issues);

    var aggregatedResults = new ArrayList<SignPostingResult>(validators.size());
    for (var validator : validators) {
      var result = validator.validate(sanitizedLinks);
      if (result == null) {
        throw new IllegalStateException("Validator returned null SignPostingResult: " + validator.getClass().getName());
      }
      aggregatedResults.add(result);
    }

    if (aggregatedResults.isEmpty()) {
      throw new IllegalStateException("No SignPostingResult available for aggregation.");
    }

    return linkSetViewAggregationStrategy.apply(aggregatedResults);
  }

  private static List<WebLink> applyModelValidation(List<WebLink> webLinks,
      WebLinkModelValidator modelValidator, List<Issue> issues) {
    var result = modelValidator.validate(webLinks);
    var sanitizedLinks = new ArrayList<WebLink>();
    for (int index = 0; index < webLinks.size(); index++) {
      if (!result.blockingLinkByIndex()[index]) {
        sanitizedLinks.add(webLinks.get(index));
      }
    }
    issues.addAll(result.issueReport().issues());
    return sanitizedLinks;
  }

  /**
   * Builder for constructing a {@link SignPostingProcessor} with configurable validation
   * and aggregation behavior.
   *
   * <p>
   * The builder follows a <strong>sensible-defaults</strong> philosophy: if clients do not
   * explicitly configure certain aspects, well-defined default behavior is applied.
   * </p>
   *
   * <h2>Defaults</h2>
   * <ul>
   *   <li><strong>Validators:</strong>
   *       If no {@link SignPostingValidator}s are configured, a single
   *       {@link Level1SignPostingValidator} is applied.</li>
   *   <li><strong>WebLink model validation:</strong>
   *       Defaults to the library-provided RFC&nbsp;8288 model validator
   *       ({@link WebLinkModelValidators#rfc8288()}).</li>
   *   <li><strong>Level&nbsp;2 Linkset View aggregation:</strong>
   *       Defaults to {@link LinkSetViewAggregationMode#FIRST}, meaning that if multiple
   *       {@link life.qbic.compass.model.Level2LinksetView} instances are produced by
   *       validators, only the first one is retained.</li>
   * </ul>
   *
   * <h2>Execution semantics</h2>
   * <ul>
   *   <li>Validators are executed in the order they are added.</li>
   *   <li>All configured validators are always executed; validation does not short-circuit.</li>
   *   <li>Model validation is performed before semantic Signposting validation.</li>
   * </ul>
   *
   * <p>
   * The builder itself is mutable and not thread-safe. The resulting
   * {@link SignPostingProcessor} instance is immutable and thread-safe.
   * </p>
   */
  public static final class Builder {

    private List<SignPostingValidator> validators = new ArrayList<>();

    /**
     * Sensible default for the Weblink model validator is the provided RFC 8288 implementation
     */
    private WebLinkModelValidator modelValidator = WebLinkModelValidators.rfc8288();

    /**
     * Sensible default aggregation strategy in case more than one linkset views is produced from a
     * list of validators.
     */
    private LinkSetViewAggregationStrategy linkSetViewAggregationStrategy =
        LinkSetViewAggregations.forMode(LinkSetViewAggregationMode.FIRST);

    /**
     * Adds one or more validators to this processor.
     *
     * <p>
     * Validators are appended in the order provided.
     * </p>
     *
     * @param validators one or more {@link SignPostingValidator}s
     * @return this builder for fluent chaining
     * @throws NullPointerException if {@code validators} is {@code null}
     */
    public Builder withValidators(SignPostingValidator... validators) {
      return this.withValidators(Arrays.stream(validators).toList());
    }

    /**
     * Adds a list of validators to this processor.
     *
     * <p>
     * The provided list is not defensively copied until {@link #build()} is called.
     * </p>
     *
     * @param validators validators to add
     * @return this builder for fluent chaining
     * @throws NullPointerException if {@code validators} is {@code null}
     */
    public Builder withValidators(List<SignPostingValidator> validators) {
      this.validators.addAll(validators);
      return this;
    }

    /**
     * Adds a validator for the semantic weblink model.
     * <p>
     * If no model validator is provided, it defaults the library's RFC 8288 validation
     * implementation.
     *
     * @param modelValidator a weblink model validator
     * @return this builder for fluent chaining
     */
    public Builder withModelValidator(WebLinkModelValidator modelValidator) {
      this.modelValidator = modelValidator;
      return this;
    }

    /**
     * Configures how multiple {@link life.qbic.compass.model.Level2LinksetView} instances
     * produced during processing are aggregated, using a predefined aggregation mode.
     *
     * <p>
     * This is the recommended configuration entry point for clients. The provided
     * {@link LinkSetViewAggregationMode} is resolved to an internal
     * {@link LinkSetViewAggregationStrategy} via a factory.
     * </p>
     *
     * <p>
     * Calling this method overrides any previously configured linkset aggregation strategy.
     * </p>
     *
     * @param linkSetViewAggregationMode the aggregation mode to apply
     * @return this builder for fluent chaining
     * @throws NullPointerException if {@code linkSetViewAggregationMode} is {@code null}
     */
    public Builder withLinkSetViewStrategy(LinkSetViewAggregationMode linkSetViewAggregationMode) {
      return withLinkSetViewStrategy(LinkSetViewAggregations.forMode(linkSetViewAggregationMode));
    }

    /**
     * Configures a custom {@link LinkSetViewAggregationStrategy} to control how
     * {@link life.qbic.compass.model.Level2LinksetView} instances are aggregated.
     *
     * <p>
     * This method is intended for advanced use cases, such as custom aggregation policies
     * or testing. Most clients should prefer
     * {@link #withLinkSetViewStrategy(LinkSetViewAggregationMode)}.
     * </p>
     *
     * <p>
     * Calling this method overrides any previously configured aggregation strategy.
     * </p>
     *
     * @param aggregationStrategy the aggregation strategy to use
     * @return this builder for fluent chaining
     * @throws NullPointerException if {@code aggregationStrategy} is {@code null}
     */
    public Builder withLinkSetViewStrategy(LinkSetViewAggregationStrategy aggregationStrategy) {
      this.linkSetViewAggregationStrategy = Objects.requireNonNull(aggregationStrategy);
      return this;
    }

    /**
     * Builds a {@link SignPostingProcessor} instance.
     *
     * <p>
     * If no validators were added, the processor is created with a single
     * {@link Level1SignPostingValidator} as a sensible default.
     * </p>
     *
     * @return a configured {@link SignPostingProcessor}
     */
    public SignPostingProcessor build() {
      if (validators.isEmpty()) {
        validators = List.of(Level1SignPostingValidator.create());
      }
      return new SignPostingProcessor(validators, modelValidator, linkSetViewAggregationStrategy);
    }
  }
}
