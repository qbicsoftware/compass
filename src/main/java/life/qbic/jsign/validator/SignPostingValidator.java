package life.qbic.jsign.validator;

import java.util.List;
import life.qbic.linksmith.model.WebLink;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public interface SignPostingValidator {

  SignPostingResult validate(List<WebLink> webLinks);

}
