package life.qbic.compass.validation;

import life.qbic.compass.spi.WebLinkModelValidator;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public final class WebLinkModelValidators {

  private WebLinkModelValidators() {}

  public static WebLinkModelValidator rfc8288() {
    return Rfc8288ModelValidator.create();
  }

}
