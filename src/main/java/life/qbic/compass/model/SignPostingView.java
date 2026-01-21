package life.qbic.compass.model;

import java.util.List;
import java.util.Objects;
import life.qbic.compass.spi.SignPostingValidator;
import life.qbic.linksmith.model.WebLink;

/**
 * A semantic, read-only view over a collection of {@link WebLink}s.
 *
 * <p>
 * {@code SignPostingView} is a convenience facade for working with typed Web Links in the context
 * of FAIR Signposting. It provides relation-based accessors (e.g. {@link #citeAs()},
 * {@link #describedBy()}, {@link #linkSet()}) on top of the underlying WebLink model.
 * </p>
 *
 * <h2>Validation and responsibility</h2>
 * <p>
 * This view does <em>not</em> perform validation. It is typically created from the output of one or
 * more {@link SignPostingValidator}s (see {@link SignPostingResult}), but it can also be used with
 * WebLinks originating from other sources.
 * </p>
 *
 * <h2>Immutability and non-destructive behavior</h2>
 * <ul>
 *   <li>The input list is defensively copied.</li>
 *   <li>The list returned by {@link #webLinks()} is unmodifiable.</li>
 *   <li>All accessor methods return unmodifiable lists.</li>
 *   <li>No WebLinks are modified, added, removed, or reordered.</li>
 * </ul>
 *
 * <h2>Null handling contract</h2>
 * <p>
 * The provided {@code webLinks} list must not contain {@code null} elements.
 * This is a strict contract: construction fails fast if any element is {@code null}.
 * Clients are responsible for sanitizing inputs before creating this view.
 * </p>
 *
 * <h2>Level 1 and Level 2 discovery</h2>
 * <p>
 * The view supports:
 * </p>
 * <ul>
 *   <li><strong>Level 1</strong>: typed links conveyed inline (e.g. in HTTP {@code Link} headers).</li>
 *   <li><strong>Level 2 discovery</strong>: inline links advertising Link Set resources using
 *       {@code rel=linkset}.</li>
 * </ul>
 *
 * <p>
 * The view does not dereference link targets and does not parse Link Set resources. Clients are
 * responsible for retrieving Link Sets and parsing them (e.g. via
 * {@link life.qbic.compass.spi.LinkSetParser} implementations).
 * </p>
 *
 * @param webLinks the WebLinks forming the basis of this view (must not be {@code null} and must
 *                 not contain {@code null} elements)
 * @author Sven Fillinger
 * @since 1.0.0
 */
public record SignPostingView(List<WebLink> webLinks) {

  /**
   * SignPostingView constructor.
   *
   * @param webLinks the WebLinks forming the basis of this view (must not be {@code null} and must
   *                 not contain {@code null} elements)
   * @throws NullPointerException in case the webLinks list or one element is {@code null}
   */
  public SignPostingView {
    Objects.requireNonNull(webLinks);
    for (int index = 0; index < webLinks.size(); index++) {
      if (webLinks.get(index) == null) {
        throw new NullPointerException("webLinks contains null element at index " + index);
      }
    }
    webLinks = List.copyOf(webLinks);
  }

  /**
   * Returns all {@link WebLink}s whose relation type list contains the given relation token.
   *
   * <p>
   * Matching is performed case-insensitively ({@code equalsIgnoreCase}) for interoperability. The
   * returned list is unmodifiable.
   * </p>
   *
   * <pre>{@code
   * // Examples:
   * view.withRelationType("item");
   * view.withRelationType("author");
   * }</pre>
   *
   * @param type the relation type token to search for (e.g. {@code "cite-as"})
   * @return unmodifiable list of WebLinks containing {@code type} in their {@code rel} tokens
   * @since 1.0.0
   */
  public List<WebLink> withRelationType(String type) {
    return SignPostingView.withRelationType(webLinks, type);
  }

  /**
   * Filters the given links for those containing the provided relation type token.
   *
   * <p>
   * The input list is not modified. {@code null} entries are ignored. The returned list is
   * unmodifiable.
   * </p>
   *
   * @param webLinks the WebLinks to filter
   * @param type     the relation type token to match
   * @return unmodifiable list of matching WebLinks
   */
  private static List<WebLink> withRelationType(List<WebLink> webLinks, String type) {
    return webLinks.stream()
        .filter(link -> hasRelationType(link, type))
        .toList();
  }

  /**
   * Returns whether the given WebLink contains the provided relation type token.
   *
   * <p>
   * Relation types are represented by the {@code rel} parameter. Matching is case-insensitive.
   * </p>
   *
   * @param webLink the WebLink to inspect
   * @param type    the relation type token to match
   * @return {@code true} if {@code webLink} contains {@code type} as relation token
   */
  private static boolean hasRelationType(WebLink webLink, String type) {
    return webLink.rel().stream()
        .anyMatch(relation -> relation.equalsIgnoreCase(type));
  }

  /**
   * Returns all WebLinks with relation type {@code rel=author}.
   *
   * <p>
   * In FAIR Signposting, {@code author} commonly identifies an agent related to the scholarly
   * object (often by persistent identifier, e.g. ORCID or ROR). The returned list is unmodifiable.
   * </p>
   *
   * @return unmodifiable list of {@code author}-typed WebLinks
   * @since 1.0.0
   */
  public List<WebLink> author() {
    return withRelationType("author");
  }

  /**
   * Returns all WebLinks with relation type {@code rel=cite-as}.
   *
   * <p>
   * In FAIR Signposting, {@code cite-as} identifies the preferred persistent identifier for
   * citation of the scholarly object. The returned list is unmodifiable.
   * </p>
   *
   * @return unmodifiable list of {@code cite-as}-typed WebLinks
   * @since 1.0.0
   */
  public List<WebLink> citeAs() {
    return withRelationType("cite-as");
  }

  /**
   * Returns all WebLinks with relation type {@code rel=collection}.
   *
   * <p>
   * Used to relate a resource to a collection/landing context. The returned list is unmodifiable.
   * </p>
   *
   * @return unmodifiable list of {@code collection}-typed WebLinks
   * @since 1.0.0
   */
  public List<WebLink> collection() {
    return withRelationType("collection");
  }

  /**
   * Returns all WebLinks with relation type {@code rel=describedby}.
   *
   * <p>
   * Typically points to metadata resources describing the scholarly object. The returned list is
   * unmodifiable.
   * </p>
   *
   * @return unmodifiable list of {@code describedby}-typed WebLinks
   * @since 1.0.0
   */
  public List<WebLink> describedBy() {
    return withRelationType("describedby");
  }

  /**
   * Returns all WebLinks with relation type {@code rel=describes}.
   *
   * <p>
   * Often used by metadata resources to indicate which resource they describe. The returned list is
   * unmodifiable.
   * </p>
   *
   * @return unmodifiable list of {@code describes}-typed WebLinks
   * @since 1.0.0
   */
  public List<WebLink> describes() {
    return withRelationType("describes");
  }

  /**
   * Returns all WebLinks with relation type {@code rel=item}.
   *
   * <p>
   * In FAIR Signposting, {@code item} typically points to content resources (e.g. files). The
   * returned list is unmodifiable.
   * </p>
   *
   * @return unmodifiable list of {@code item}-typed WebLinks
   * @since 1.0.0
   */
  public List<WebLink> item() {
    return withRelationType("item");
  }

  /**
   * Returns all WebLinks with relation type {@code rel=license}.
   *
   * <p>
   * Points to licensing information for the scholarly object or one of its resources. The returned
   * list is unmodifiable.
   * </p>
   *
   * @return unmodifiable list of {@code license}-typed WebLinks
   * @since 1.0.0
   */
  public List<WebLink> license() {
    return withRelationType("license");
  }

  /**
   * Returns all WebLinks with relation type {@code rel=linkset}.
   *
   * <p>
   * In Level 2 discovery, {@code linkset} advertises an external Link Set resource (RFC 9264).
   * Compass does not fetch or parse this resource automatically. Clients can retrieve the target
   * and parse it using implementations of {@link life.qbic.compass.spi.LinkSetParser}, such as
   * {@link life.qbic.compass.parsing.LinkSetInlineParser} or
   * {@link life.qbic.compass.parsing.LinkSetJsonParser}. The returned list is unmodifiable.
   * </p>
   *
   * @return unmodifiable list of {@code linkset}-typed WebLinks
   * @since 1.0.0
   */
  public List<WebLink> linkSet() {
    return withRelationType("linkset");
  }

  /**
   * Returns all WebLinks with relation type {@code rel=type}.
   *
   * <p>
   * {@code type} is used to express semantic typing (often schema.org types) for the current
   * resource. The returned list is unmodifiable.
   * </p>
   *
   * @return unmodifiable list of {@code type}-typed WebLinks
   * @since 1.0.0
   */
  public List<WebLink> type() {
    return withRelationType("type");
  }
}
