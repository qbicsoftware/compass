package life.qbic.compass.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import life.qbic.compass.model.SignPostingView;
import life.qbic.compass.spi.SignPostingResult;
import life.qbic.compass.spi.SignPostingValidator;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator;
import life.qbic.linksmith.spi.WebLinkValidator.Issue;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public class Level2MetadataResourceValidator implements SignPostingValidator {

  private Level2MetadataResourceValidator() {
  }

  public static SignPostingValidator create() {
    return new Level2MetadataResourceValidator();
  }

  @Override
  public SignPostingResult validate(List<WebLink> webLinks) {
    var issues = new ArrayList<Issue>();
    validateForMetadataResource(webLinks, issues);

    return new SignPostingResult(new SignPostingView(webLinks), new IssueReport(issues));
  }

  private static void validateForMetadataResource(List<WebLink> webLinks, List<Issue> issues) {
    var linksWithoutAnchor = new ArrayList<WebLink>();
    var recordedRelations = new HashMap<String, Integer>();

    var isContextUnique = Level2Util.validateForSingleAnchor(webLinks, issues, linksWithoutAnchor,
        recordedRelations);

    if (!isContextUnique) {
      // Validate and record issues for links without anchors
      // Missing anchors violate Level 2 FAIR Signposting recipes, since the origin of the link
      // cannot be determined
      linksWithoutAnchor.forEach(link -> issues.add(Issue.error(
          "Found weblink with missing value for 'anchor'. Link target was '%s'".formatted(
              link.target()))));
      // Early abort without further cardinality evaluation
      return;
    }

    //validateCiteAs(recordedRelations, issues);
  }
}
