package life.qbic.linksmith.compass.model;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import life.qbic.linksmith.compass.spi.SignPostingResult;
import life.qbic.linksmith.compass.spi.SignPostingValidator;
import life.qbic.linksmith.model.WebLink;

/**
 * A semantic, read-only view over a collection of {@link WebLink}s that provides convenient
 * accessors for Signposting-related link relations.
 *
 * <p>
 * {@code SignPostingView} does not perform validation itself. Instead, it assumes that the supplied
 * WebLinks have already been processed by one or more {@link SignPostingValidator}s and offers a
 * profile-oriented API to inspect the resulting links.
 * </p>
 *
 * <p>
 * The view is intentionally <strong>non-destructive</strong>:
 * </p>
 * <ul>
 *   <li>All WebLinks supplied to the constructor are retained.</li>
 *   <li>No links are added, removed, or modified.</li>
 *   <li>Validation issues are reported separately via {@link SignPostingResult}.</li>
 * </ul>
 *
 * <p>
 * This design allows clients to:
 * </p>
 * <ul>
 *   <li>inspect Signposting relations even in the presence of validation errors,</li>
 *   <li>apply multiple Signposting profiles to the same set of WebLinks, and</li>
 *   <li>defer profile-specific decisions (e.g. dereferencing linksets) to later stages.</li>
 * </ul>
 *
 * <p>
 * The view supports both:
 * </p>
 * <ul>
 *   <li><strong>Level 1 Signposting</strong>, where typed links are provided inline
 *       via HTTP {@code Link} headers, and</li>
 *   <li><strong>Level 2 Signposting discovery</strong>, where inline links may point
 *       to external Link Set resources using {@code rel=linkset}.</li>
 * </ul>
 *
 * <p>
 * {@code SignPostingView} does <em>not</em> dereference link targets or process Link
 * Set contents. Clients are responsible for retrieving and validating any external
 * resources referenced by the returned URIs.
 * </p>
 *
 * @param webLinks the list of WebLinks forming the basis of this view
 */
public record SignPostingView(List<WebLink> webLinks) {

  public SignPostingView {
    Objects.requireNonNull(webLinks);
    webLinks = List.copyOf(webLinks);
  }

  /**
   * Returns all WebLinks that have the relation type parameter with the given value.
   *
   * <pre>
   * {@code
   * // Examples:
   * // for links with 'rel=item'
   * withRelationType("item")
   * // for links with 'rel=author'
   * withRelationType("author")
   * }
   * </pre>
   *
   * @param type the relation type to search for
   * @return a list of WebLinks with the given relation type
   */
  public List<WebLink> withRelationType(String type) {
    return SignPostingView.withRelationType(webLinks, type);
  }

  /**
   * Returns all WebLinks that have the relation type parameter with the given value.
   * <p>
   * The provided WebLink list is not modified but just read. Filtering must rule out potential null
   * items first.
   * <p>
   * The returned list is immutable.
   *
   * @param webLinks the WebLinks to filter
   * @param type     the serialised type of the parameter
   * @return all WebLinks with the provided type parameter
   */
  private static List<WebLink> withRelationType(List<WebLink> webLinks, String type) {
    return webLinks.stream()
        .filter(Objects::nonNull)
        .filter(link -> hasRelationType(link, type))
        .toList();
  }

  /**
   * Returns a boolean flag for the presence of a given relation type in a WebLink.
   * <p>
   * Relation types in WebLinks are serialised with the parameter 'rel': {@code rel=<type>}.
   * <p>
   * The current implementation is <strong>not case-sensitive</strong>.
   *
   * @param webLink the WebLink to investigate
   * @param type    the serialised relation type.
   * @return true, if the WebLink contains a relation with the provided type
   */
  private static boolean hasRelationType(WebLink webLink, String type) {
    return webLink.rel().stream()
        .anyMatch(relation -> relation.equalsIgnoreCase(type));
  }

  /**
   * Returns the targets of all WebLinks with relation type {@code rel=cite-as}.
   *
   * <p>
   * In Signposting Level 1, this relation identifies a persistent identifier for the scholarly
   * object.
   * </p>
   *
   * @return a list of {@link URI}s for {@code cite-as} links
   */
  public List<URI> citeAs() {
    return withRelationType("cite-as").stream()
        .map(WebLink::target)
        .toList();
  }

  /**
   * Returns the targets of all WebLinks with relation type {@code rel=describedby}.
   *
   * <p>
   * This relation typically points to metadata resources describing the scholarly object.
   * </p>
   *
   * @return a list of {@link URI}s for {@code describedby} links
   */
  public List<URI> describedBy() {
    return withRelationType("describedby").stream()
        .map(WebLink::target)
        .toList();
  }

  /**
   * Returns the targets of all WebLinks with relation type {@code rel=linkset}.
   *
   * <p>
   * In Signposting Level 2, these URIs identify Link Set resources that must be retrieved and
   * processed separately by client code.
   * </p>
   *
   * @return a list of {@link URI}s identifying Link Set resources
   */
  public List<URI> linksets() {
    return withRelationType("linkset").stream()
        .map(WebLink::target)
        .toList();
  }
}
