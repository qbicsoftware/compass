package life.qbic.compass.validation;

import java.util.List;
import life.qbic.compass.spi.SignPostingResult;
import life.qbic.compass.spi.SignPostingValidator;
import life.qbic.linksmith.model.WebLink;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public class Level2MetadataResourceValidator implements SignPostingValidator {

  private Level2MetadataResourceValidator() {}

  public static SignPostingValidator create() {
    return new Level2MetadataResourceValidator();
  }

  @Override
  public SignPostingResult validate(List<WebLink> webLinks) {
    // TODO implement
    throw new RuntimeException("Not yet implemented");
  }
}
