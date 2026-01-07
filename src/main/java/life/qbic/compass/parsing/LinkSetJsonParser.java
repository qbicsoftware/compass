package life.qbic.compass.parsing;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import life.qbic.compass.spi.LinkSetParser;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.model.WebLinkParameter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses Linkset documents in JSON representation (media types {@code application/linkset+json} and
 * {@code application/linkset}) into a flat list of {@link WebLink} instances.
 * <p>
 * This parser implements the JSON Linkset representation defined by RFC 9264. A Linkset document is
 * a JSON object with a single top-level {@code "linkset"} member whose value is an array of
 * <i>link context objects</i>. Each link context object has a shared {@code "anchor"} and one or
 * more additional members whose names represent link relation types (e.g. {@code "author"},
 * {@code "cite-as"}). The values of such relation members are arrays of <i>link target objects</i>
 * that contain at least {@code "href"} and optionally additional target attributes.
 * <p>
 * The parser converts each link target object into a {@link WebLink} by:
 * <ul>
 *   <li>Using {@code href} as {@link WebLink#target()}.</li>
 *   <li>Adding a {@code rel} parameter with the relation type name (member name in JSON).</li>
 *   <li>Adding an {@code anchor} parameter with the parsed anchor value.</li>
 *   <li>Adding all remaining members of the target object as {@link WebLinkParameter} entries
 *       (uninterpreted), to be validated by downstream RFC/profile validators.</li>
 * </ul>
 * <p>
 * This class deliberately keeps semantic validation minimal. Structural violations of the JSON
 * representation (e.g. missing {@code linkset}, unexpected token types, malformed JSON) result in
 * {@link ParsingException}. Deeper semantic checks (e.g. required relations, parameter constraints,
 * Signposting profile rules) are expected to be handled by validators downstream.
 *
 * @author Sven Fillinger
 */
public class LinkSetJsonParser implements LinkSetParser {

  /**
   * Creates a new {@link LinkSetJsonParser}.
   * <p>
   * Prefer {@link #create()} for a conventional factory-style instantiation.
   */
  private LinkSetJsonParser() {
  }

  /**
   * Factory method to create a new {@link LinkSetJsonParser} instance.
   *
   * @return a new parser instance
   */
  public static LinkSetJsonParser create() {
    return new LinkSetJsonParser();
  }

  /**
   * Parses a raw JSON Linkset string into a list of {@link WebLink} objects.
   * <p>
   * The input string is interpreted as UTF-8 and passed to {@link #parse(InputStream)}.
   *
   * @param rawLinkSet the raw JSON Linkset document
   * @return parsed web links
   * @throws ParsingException if the input is not valid JSON Linkset representation
   */
  @Override
  public List<WebLink> parse(String rawLinkSet) throws ParsingException {
    if (rawLinkSet == null) {
      throw new ParsingException("raw link set must not be null");
    }
    return parse(new ByteArrayInputStream(rawLinkSet.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Parses a JSON Linkset from an {@link InputStream} into a list of {@link WebLink} objects.
   * <p>
   * The parser uses Jackson's streaming API to iterate over tokens and expects the following
   * structural shape:
   * <pre>
   * {@code
   * { "linkset": [ { ...link context object... }, ... ] }
   * }
   * </pre>
   * For malformed JSON, the thrown {@link ParsingException} contains line/column information
   * derived from the underlying Jackson exception location.
   *
   * @param inputStream the input stream containing JSON Linkset data
   * @return parsed web links
   * @throws ParsingException if the document is malformed or violates the expected top-level shape
   */
  @Override
  public List<WebLink> parse(InputStream inputStream) throws ParsingException {
    if (inputStream == null) {
      throw new ParsingException("input stream must not be null");
    }
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectReader reader = mapper.reader();

    try (JsonParser parser = reader.createParser(inputStream)) {
      JsonToken token = parser.nextToken();
      // Set of links in JSON is represented as an object
      // https://www.rfc-editor.org/rfc/rfc9264.html#section-4.2.1
      if (token != JsonToken.START_OBJECT) {
        throw new ParsingException("Linkset JSON must be an object, but was: " + token);
      }
      // We need to verify the linkset property is present to conform to RFC 9264
      // The set of links in JSON must have a single 'linkset' property
      // https://www.rfc-editor.org/rfc/rfc9264.html#section-4.2.1
      if (parser.nextToken() != JsonToken.PROPERTY_NAME) {
        throw new ParsingException(
            "Linkset JSON must contains 'linkset', but was: %s:%s".formatted(token,
                parser.currentName()));
      }
      // Ensure the property name is 'linkset' and
      if (parser.currentName() == null || !parser.currentName().equals("linkset")) {
        throw new ParsingException(
            "Expected 'linkset' property, but was: %s".formatted(parser.currentName()));
      }

      // Links must be wrapped in an JSON array
      // https://www.rfc-editor.org/rfc/rfc9264.html#section-4.2.1
      // and
      // https://www.rfc-editor.org/rfc/rfc9264.html#section-4.2.2
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        throw new ParsingException(
            "Linkset JSON must be an array, but was %s".formatted(parser.currentName()));
      }

      var collectedWebLinks = new ArrayList<WebLink>();
      while ((token = parser.nextToken()) != null && token != JsonToken.END_ARRAY) {
        tryParseLinkContext(token, parser, collectedWebLinks);
      }
      return List.copyOf(collectedWebLinks);
    } catch (JacksonException e) {
      var location = e.getLocation();
      throw new ParsingException(
          "Invalid linkset JSON at line " + location.getLineNr() +
              ", column " + location.getColumnNr() +
              ": " + e.getOriginalMessage(), e
      );
    }
  }

  private static void tryParseLinkContext(JsonToken token, JsonParser parser,
      ArrayList<WebLink> collectedWebLinks) {
    try {
      parseLinkContext(token, parser, collectedWebLinks);
    } catch (IllegalArgumentException | ParsingException e) {
      // Ensure the client friendly information about the location is passed with the exception
      var location = parser.currentLocation();
      throw new ParsingException(
          "Invalid linkset JSON at line " + location.getLineNr() +
              ", column " + location.getColumnNr(), e
      );
    }
  }

  /**
   * Parses a single <i>link context object</i> (RFC 9264) and appends the corresponding
   * {@link WebLink} objects to {@code collectedLinks}.
   * <p>
   * A link context object contains:
   * <ul>
   *   <li>an {@code "anchor"} member (context/origin for the contained links), and</li>
   *   <li>one or more additional members whose names represent link relation types; their values
   *       are arrays of link target objects.</li>
   * </ul>
   * <p>
   * This method collects all link target objects for all relation members, then creates web links
   * by injecting {@code rel=<relationType>} and {@code anchor=<anchorValue>} as parameters.
   *
   * @param currentToken   the current token at the beginning of the context object
   * @param parser         active JSON parser positioned within the linkset array
   * @param collectedLinks accumulator list to append parsed {@link WebLink} instances to
   * @throws ParsingException if required members are missing or the token stream is inconsistent
   */
  private static void parseLinkContext(JsonToken currentToken, JsonParser parser,
      List<WebLink> collectedLinks) throws ParsingException {
    List<LinkTargetObject> relationTypeEntries = new ArrayList<>();
    String anchorValue = null;

    while (currentToken != null && currentToken != JsonToken.END_OBJECT) {
      if (parser.currentName() == null || currentToken != JsonToken.PROPERTY_NAME ) {
        currentToken = parser.nextToken();
        continue;
      }
      // Only the "anchor" member has a scalar value. Link target objects are grouped by relation
      // type in JSON arrays
      switch (parser.currentName()) {
        case "anchor" -> anchorValue = parser.nextStringValue();
        default -> {
          // Forward to the next token which is expected to the start of an array
          parser.nextToken();
          parseLinkTargets(parser, relationTypeEntries);
        }
      }
      currentToken = parser.nextToken();
    }
    collectedLinks.addAll(createWebLinks(anchorValue, relationTypeEntries));
  }

  /**
   * Creates {@link WebLink} instances for all parsed link target objects within the same context.
   *
   * @param anchorValue         the anchor/context value from the link context object
   * @param relationTypeEntries collected link target objects (each representing one resulting
   *                            link)
   * @return a list of created {@link WebLink} objects
   */
  private static List<WebLink> createWebLinks(String anchorValue,
      List<LinkTargetObject> relationTypeEntries) {
    return relationTypeEntries.stream()
        .map(entry -> createWebLink(anchorValue, entry))
        .toList();
  }

  /**
   * Creates a single {@link WebLink} from a parsed link target object and its context anchor.
   * <p>
   * The resulting {@link WebLink} will use {@code href} as its target URI and encode:
   * <ul>
   *   <li>{@code rel} (derived from the relation member name),</li>
   *   <li>{@code anchor} (derived from the containing context object), and</li>
   *   <li>all remaining target attributes as raw {@link WebLinkParameter} entries.</li>
   * </ul>
   * <p>
   * Semantic validation of parameter values (e.g., MIME type correctness, permitted attributes,
   * Signposting profile constraints) is intentionally left to downstream validators.
   *
   * @param anchorValue         the anchor value (context/origin)
   * @param relationTypeEntries parsed link target object
   * @return a new {@link WebLink} instance
   * @throws ParsingException
   */
  private static WebLink createWebLink(String anchorValue, LinkTargetObject relationTypeEntries)
      throws ParsingException {
    var parameters = new ArrayList<WebLinkParameter>();
    parameters.add(new WebLinkParameter("rel", relationTypeEntries.relationType()));
    // an anchor value MAY be provided according to RFC 9264.
    if (anchorValue != null) {
      // in case a value for the anchor is provided, ist MUST be a URI
      if (!isUri(anchorValue)) {
        throw new IllegalArgumentException("Anchor value must be a URI: " + anchorValue);
      }
      parameters.add(new WebLinkParameter("anchor", anchorValue));
    }

    parameters.addAll(
        relationTypeEntries.parameters().entrySet().stream()
            .map(entry -> new WebLinkParameter(entry.getKey(), entry.getValue()))
            .toList());
    return new WebLink(URI.create(relationTypeEntries.href()), parameters);
  }

  private static boolean isUri(String value) {
    try {
      URI.create(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Parses an array of link targets and adds them as {@link LinkTargetObject}.
   * <p>
   * In JSON-serialized set of links, link targets are expressed in link context objects.
   * <p>
   * A link context object contains all link targets with the same context, which is expressed in
   * its "anchor" member. For every relation type of its link targets it must contain an additional
   * member.
   * <p>
   * Ever value of this member has a value which is an array with distinct JSON objects - the link
   * target object.
   *
   * <pre>
   * {@code
   * { "linkset":
   *   [
   *     { "anchor": "https://example.net/bar",
   *       "item": [
   *         {"href": "https://example.com/foo1"},
   *         {"href": "https://example.com/foo2"}
   *       ]
   *     }
   *   ]
   * }}
   * </pre>
   * <p>
   * In the previous example, the linkset contains one link context object, with
   * {@code https://example.net/bar} as the shared link target objects context. The link context
   * object contains one member that represents the relation type {@code item}.
   * <p>
   * The value of the {@code item} member is an array with two link target objects, pointing to two
   * different locations, {@code https://example.com/foo1} and {@code https://example.com/foo2}.
   *
   * @param parser            the current active JSON parser to use to iterate though the tokens
   * @param linkTargetObjects the current list of parsed link target objects
   */
  private static void parseLinkTargets(JsonParser parser,
      List<LinkTargetObject> linkTargetObjects) {
    var relationType = parser.currentName();
    if (parser.currentToken() != JsonToken.START_ARRAY) {
      throw new ParsingException("Link targets must we provided in an array");
    }
    JsonToken token = parser.nextToken();
    var relationParameters = new HashMap<String, String>();
    // for each relation in a linkset JSON object, the link targets are JSON objects themselves
    // with the 'href' parameter as the link target and additional attributes
    while (token != null && token != JsonToken.END_ARRAY) {
      relationParameters.clear();
      // every web link is an own JSON object in the relation type array
      // for every link target, all parameters are collected and finally
      // a relation type entry is created
      while (token != null && token != JsonToken.END_OBJECT) {
        if (token != JsonToken.PROPERTY_NAME) {
          token = parser.nextToken();
          continue;
        }
        // every relation parameter is a property of its parent relation
        var propertyName = parser.getString();
        // the next token is the value of the property
        var propertyValue = parser.nextStringValue();
        relationParameters.put(propertyName, propertyValue);
        token = parser.nextToken();
      }
      linkTargetObjects.add(
          createLinkTarget(relationType, relationParameters)
      );
      token = parser.nextToken();
    }
  }

  /**
   * Link target objects encode a target link with a URI encoded in its "href" attribute and a list
   * of additional target attributes.
   * <p>
   * The {@link LinkTargetObject} provides a convenient API to access its relation type, reference
   * and additional attributes.
   *
   * @param relationType     the relation type of the link target
   * @param targetAttributes the target attributes
   * @return a link target object
   */
  private static LinkTargetObject createLinkTarget(
      String relationType,
      Map<String, String> targetAttributes) {
    if (!targetAttributes.containsKey("href")) {
      throw new ParsingException("Missing 'href' parameter for link target");
    }
    return new LinkTargetObject(relationType,
        targetAttributes.get("href"),
        targetAttributes.entrySet().stream()
            .filter(entrySet -> !Objects.equals(entrySet.getKey(), "href"))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue))
    );
  }

  /**
   * Parses a Linkset JSON document from a {@link Reader}.
   * <p>
   * This method is currently not implemented. Callers should use {@link #parse(InputStream)} to
   * ensure consistent handling of encodings and streaming parsing.
   *
   * @param reader the reader providing the JSON Linkset document
   * @return parsed web links
   * @throws ParsingException always, until implemented
   */
  @Override
  public List<WebLink> parse(Reader reader) throws ParsingException {
    return List.of();
  }

  /**
   * Internal representation of a link target object (RFC 9264) that is ready to be transformed into
   * a {@link WebLink}.
   * <p>
   * The relation type is derived from the member name of the surrounding link context object. The
   * {@code href} field represents the link target. All remaining target attributes are kept as raw,
   * uninterpreted name/value pairs.
   *
   * @param relationType the relation type (e.g. {@code "author"}, {@code "cite-as"})
   * @param href         the target URI encoded in the {@code href} attribute
   * @param parameters   remaining target attributes (excluding {@code href})
   */
  record LinkTargetObject(String relationType, String href, Map<String, String> parameters) {

  }
}
