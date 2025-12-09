package life.qbic.jsign.validator;

import java.util.List;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public class Level2SignPostingValidator implements SignPostingValidator {

  private Level2SignPostingValidator() {}

  public static Level2SignPostingValidator create() {
    return new Level2SignPostingValidator();
  }

  @Override
  public SignPostingResult validate(List<WebLink> webLinks) {
    return new SignPostingResult(new SignPostingView(webLinks), new IssueReport(List.of()));
  }
}
