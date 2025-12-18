package life.qbic.compass.parsing;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.List;
import life.qbic.compass.spi.LinkSetParser;
import life.qbic.linksmith.model.WebLink;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public class LinkSetInlineParser implements LinkSetParser {

  @Override
  public List<WebLink> parse(String rawLinkSet) throws ParsingException {
    return List.of();
  }

  @Override
  public List<WebLink> parse(InputStream inputStream) throws ParsingException {
    return List.of();
  }

  @Override
  public List<WebLink> parse(Reader reader) throws ParsingException {
    return List.of();
  }
}
