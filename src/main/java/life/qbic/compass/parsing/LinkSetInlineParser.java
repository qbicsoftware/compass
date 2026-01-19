package life.qbic.compass.parsing;

import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import life.qbic.compass.spi.LinkSetParser;
import life.qbic.linksmith.model.WebLink;

public final class LinkSetInlineParser implements LinkSetParser {

  private LinkSetInlineParser() {}

  public static LinkSetInlineParser create() {
    return new LinkSetInlineParser();
  }

  @Override
  public List<WebLink> parse(String rawLinkSet) throws ParsingException {
    // TODO implement
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public List<WebLink> parse(InputStream inputStream) throws ParsingException {
    // TODO implement
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  public List<WebLink> parse(Reader reader) throws ParsingException {
    // TODO implement
    throw new RuntimeException("Not yet implemented");
  }
}
