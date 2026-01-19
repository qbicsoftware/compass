package life.qbic.compass.model;

import life.qbic.linksmith.model.WebLink;

/**
 * Represents a {@link WebLink} that is missing an {@code anchor}/{@code origin} attribute in a Link
 * Set context.
 *
 * <p>
 * In FAIR Signposting Level&nbsp;2, links within a Link Set are expected to be scoped to a common
 * <em>origin</em> (RFC&nbsp;8288 {@code anchor} parameter). When a link lacks this information, it
 * cannot be assigned to a specific Landing Page, Content Resource, or Metadata Resource recipe.
 * </p>
 *
 * <p>
 * Instances of this record are collected during Level&nbsp;2 validation and exposed via
 * {@link Level2LinksetView} so that clients can:
 * </p>
 * <ul>
 *   <li>report incomplete or malformed Link Sets to users,</li>
 *   <li>preserve the original order of links for diagnostics, and</li>
 *   <li>decide whether to ignore, reject, or repair such links.</li>
 * </ul>
 *
 * <p>
 * Missing-origin links are <strong>not</strong> assigned to any typed recipe view.
 * They are reported separately and do not contribute to Landing Page,
 * Content Resource, or Metadata Resource views.
 * </p>
 *
 * @param index   the zero-based index of the link in the original Link Set input
 * @param webLink the {@link WebLink} instance missing an origin
 * @author Sven Fillinger
 * @since 1.0.0
 */
public record MissingOriginLink(int index, WebLink webLink) {

}
