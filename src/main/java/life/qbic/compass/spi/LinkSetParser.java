package life.qbic.compass.spi;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.List;
import life.qbic.linksmith.model.WebLink;

/**
 * <class short description>
 *
 */
public interface LinkSetParser {

  List<WebLink> parse(String rawLinkSet) throws ParsingException;

  List<WebLink> parse(InputStream inputStream) throws ParsingException;

  List<WebLink> parse(Reader reader) throws ParsingException;

  /**
   *
   */
  class ParsingException extends RuntimeException {
    public ParsingException(String message) {
      super(message);
    }

    public ParsingException(String message, Throwable cause) {
      super(message, cause);
    }
  }

}
