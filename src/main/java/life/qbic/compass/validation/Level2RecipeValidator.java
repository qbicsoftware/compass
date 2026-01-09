package life.qbic.compass.validation;

import java.util.List;
import java.util.Objects;
import life.qbic.compass.spi.SignPostingResult;
import life.qbic.compass.spi.SignPostingValidator;
import life.qbic.linksmith.model.WebLink;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public class Level2RecipeValidator implements SignPostingValidator {

  private final SignPostingValidator landingPageValidator;
  private final SignPostingValidator metadataResourceValidator;
  private final SignPostingValidator contentResourceValidator;

  private Level2RecipeValidator(
      SignPostingValidator landingPageValidator,
      SignPostingValidator metadataResourceValidator,
      SignPostingValidator contentResourceValidator
  ) {
    this.landingPageValidator = Objects.requireNonNull(landingPageValidator);
    this.metadataResourceValidator = Objects.requireNonNull(metadataResourceValidator);
    this.contentResourceValidator = Objects.requireNonNull(contentResourceValidator);
  }

  public static Level2RecipeValidator create() {
    return new Level2RecipeValidator(
        Level2LandingPageValidator.create(),
        Level2MetadataResourceValidator.create(),
        Level2ContentResourceValidator.create()
    );
  }

  static Level2RecipeValidator create(
      SignPostingValidator landingPageValidator,
      SignPostingValidator metadataResourceValidator,
      SignPostingValidator contentResourceValidator
      ) {
    return new Level2RecipeValidator(
        landingPageValidator,
        metadataResourceValidator,
        contentResourceValidator);
  }


  @Override
  public SignPostingResult validate(List<WebLink> webLinks) {
    // TODO implement
    throw new RuntimeException("Not yet implemented");
  }
}
