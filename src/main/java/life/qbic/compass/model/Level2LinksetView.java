package life.qbic.compass.model;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * <record short description>
 *
 * @since <version tag>
 */
public record Level2LinksetView(
    Map<URI, LandingPageView> landingPages,
    Map<URI, ContentResourceView> contentResources,
    Map<URI, MetadataResourceView> metadataResources,
    List<MissingOriginLink> missingOriginLinks
) {

}
