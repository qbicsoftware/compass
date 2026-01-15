package life.qbic.compass.validation;

import java.util.List;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * <class short description>
 *
 * @since <version tag>
 */
interface WebLinkModelValidator {

  IssueReport validate(List<WebLink> webLinks);

}
