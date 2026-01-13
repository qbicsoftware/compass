package life.qbic.compass.model;

import java.net.URI;
import java.util.List;
import life.qbic.linksmith.model.WebLink;

/**
 * <record short description>
 *
 * @since <version tag>
 */
public record MetadataResourceView(URI origin, List<WebLink> webLinks) {

}
