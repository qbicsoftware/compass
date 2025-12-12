package life.qbic.linksmith.compass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import life.qbic.linksmith.compass.model.SignPostingView;
import life.qbic.linksmith.compass.spi.SignPostingResult;
import life.qbic.linksmith.compass.spi.SignPostingValidator;
import life.qbic.linksmith.compass.validator.Level1SignPostingValidator;
import life.qbic.linksmith.model.WebLink;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public final class SignPostingProcessor {

  private final List<SignPostingValidator> validators;

  private SignPostingProcessor(List<SignPostingValidator> validators) {
    Objects.requireNonNull(validators);
    this.validators = List.copyOf(validators);
  }

  public SignPostingResult process(List<WebLink> webLinks) {
    return new SignPostingResult(new SignPostingView(webLinks), null);
  }

  public static final class Builder {

    private List<SignPostingValidator> validators = new ArrayList<>();

    Builder withValidators(SignPostingValidator... validators) {
      return this.withValidators(Arrays.stream(validators).toList());
    }

    Builder withValidators(List<SignPostingValidator> validators) {
      this.validators.addAll(validators);
      return this;
    }

    SignPostingProcessor build() {
      if (validators.isEmpty()) {
        return new SignPostingProcessor(List.of(Level1SignPostingValidator.create()));
      }
      return new SignPostingProcessor(validators);
    }
  }

}
