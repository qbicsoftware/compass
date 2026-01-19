package life.qbic.compass.spi;

import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import life.qbic.linksmith.model.WebLink;

/**
 * Service Provider Interface (SPI) for parsing RFC&nbsp;9264 Link Sets into
 * {@link WebLink} model objects.
 *
 * <p>
 * A {@code LinkSetParser} converts a serialized Link Set representation
 * (inline, JSON, or other supported media types) into an in-memory list of
 * {@link WebLink}s that can be processed by Compass validators.
 * </p>
 *
 * <h2>Scope and responsibilities</h2>
 * <ul>
 *   <li>Parse a complete Link Set document.</li>
 *   <li>Return a list of {@link WebLink} objects representing all links found.</li>
 *   <li>Fail fast if the input cannot be parsed.</li>
 * </ul>
 *
 * <h2>Non-goals</h2>
 * <ul>
 *   <li>This interface does <em>not</em> perform Signposting validation.</li>
 *   <li>This interface does <em>not</em> dereference link targets.</li>
 *   <li>This interface does <em>not</em> validate semantic correctness of relations.</li>
 * </ul>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>All {@code parse} methods must return a non-null list.</li>
 *   <li>The returned list must not contain {@code null} elements.</li>
 *   <li>Implementations may assume UTF-8 unless otherwise documented.</li>
 *   <li>Parsing errors must be reported via {@link ParsingException}.</li>
 * </ul>
 *
 * <p>
 * Implementations are expected to be stateless and reusable.
 * </p>
 *
 * @since 1.0.0
 */
public interface LinkSetParser {

  /**
   * Parses a Link Set from its raw textual representation.
   *
   * @param rawLinkSet the raw Link Set document
   * @return a list of parsed {@link WebLink}s
   * @throws ParsingException if parsing fails
   */
  List<WebLink> parse(String rawLinkSet) throws ParsingException;

  /**
   * Parses a Link Set from an {@link InputStream}.
   *
   * <p>
   * Implementations are responsible for consuming the stream fully.
   * The stream is not closed by this method.
   * </p>
   *
   * @param inputStream the input stream containing the Link Set
   * @return a list of parsed {@link WebLink}s
   * @throws ParsingException if parsing fails
   */
  List<WebLink> parse(InputStream inputStream) throws ParsingException;

  /**
   * Parses a Link Set from a {@link Reader}.
   *
   * <p>
   * Implementations are responsible for consuming the reader fully.
   * The reader is not closed by this method.
   * </p>
   *
   * @param reader the reader supplying the Link Set content
   * @return a list of parsed {@link WebLink}s
   * @throws ParsingException if parsing fails
   */
  List<WebLink> parse(Reader reader) throws ParsingException;

  /**
   * Signals a failure during Link Set parsing.
   *
   * <p>
   * This exception indicates syntactic or structural errors in the
   * Link Set representation. It is intentionally unchecked to simplify
   * usage in streaming and validation pipelines.
   * </p>
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
