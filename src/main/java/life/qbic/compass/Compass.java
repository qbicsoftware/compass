package life.qbic.compass;

import life.qbic.compass.model.SignPostingResult;
import life.qbic.compass.parsing.LinkSetInlineParser;
import life.qbic.compass.parsing.LinkSetJsonParser;
import life.qbic.compass.spi.LinkSetParser;
import life.qbic.compass.spi.SignPostingValidator;
import life.qbic.compass.validation.Level1SignPostingValidator;
import life.qbic.compass.validation.Level2ContentResourceValidator;
import life.qbic.compass.validation.Level2LandingPageValidator;
import life.qbic.compass.validation.Level2MetadataResourceValidator;
import life.qbic.compass.validation.Level2RecipeValidator;
import life.qbic.linksmith.model.WebLink;

/**
 * Primary entry point and facade for the Compass FAIR Signposting library.
 *
 * <p>
 * {@code Compass} provides a single, discoverable access point to the library’s
 * <strong>default parsers</strong> and <strong>default validators</strong>.
 * It is intentionally lightweight and stateless, exposing only factory-style
 * accessors grouped by concern.
 * </p>
 *
 * <h2>Design goals</h2>
 * <ul>
 *   <li>
 *     <strong>Discoverability</strong> – clients can find all supported parsing
 *     and validation capabilities starting from one class.
 *   </li>
 *   <li>
 *     <strong>Separation of concerns</strong> – parsing and validation are exposed
 *     independently and are not coupled to each other.
 *   </li>
 *   <li>
 *     <strong>Non-opinionated usage</strong> – clients may use parsers without validators,
 *     validators without parsers, or supply their own implementations via the SPI.
 *   </li>
 *   <li>
 *     <strong>Stable defaults</strong> – this class exposes ready-to-use default
 *     implementations that follow the FAIR Signposting specification.
 *   </li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * // Parse a Level 2 Link Set
 * var parser = Compass.parsers().linksetJson();
 * List<WebLink> links = parser.parse(inputStream);
 *
 * // Validate using Level 2 recipe routing
 * var validator = Compass.validators().level2Recipes();
 * SignPostingResult result = validator.validate(links);
 * }</pre>
 *
 * <p>
 * {@code Compass} itself performs no parsing or validation logic. All stateful
 * behavior is delegated to the returned parser and validator instances.
 * </p>
 *
 * <h2>Extensibility</h2>
 * <p>
 * Advanced users may bypass this facade entirely and work directly with:
 * </p>
 * <ul>
 *   <li>{@link LinkSetParser} implementations</li>
 *   <li>{@link SignPostingValidator} implementations</li>
 * </ul>
 *
 * <p>
 * This class exists solely as a convenience and does not restrict extensibility
 * or customization.
 * </p>
 *
 * @since 1.0.0
 * @author Sven Fillinger
 */
public final class Compass {

  private Compass() {}

  /**
   * Provides access to the default FAIR Signposting parsers.
   *
   * <p>
   * The returned object acts as a logical namespace grouping all supported
   * {@link LinkSetParser} implementations shipped with this library.
   * </p>
   *
   * @return a singleton accessor for parser factories
   */
  public static Parsers parsers() {
    return Parsers.INSTANCE;
  }

  /**
   * Provides access to the default FAIR Signposting validators.
   *
   * <p>
   * The returned object acts as a logical namespace grouping all supported
   * {@link SignPostingValidator} implementations shipped with this library.
   * </p>
   *
   * @return a singleton accessor for validator factories
   */
  public static Validators validators() {
    return Validators.INSTANCE;
  }

  /**
   * Namespace for FAIR Signposting parser factories.
   *
   * <p>
   * Parsers are responsible for transforming serialized representations
   * (e.g. HTTP {@code Link} headers, Link Set JSON) into {@link WebLink}
   * objects suitable for validation and inspection.
   * </p>
   *
   * <p>
   * All parsers returned by this class are stateless and may be reused
   * across multiple parsing operations.
   * </p>
   */
  public static final class Parsers {

    private static final Parsers INSTANCE = new Parsers();

    private Parsers() {}

    /**
     * Creates a parser for RFC&nbsp;9264 JSON Link Sets.
     *
     * <p>
     * This parser is intended for FAIR Signposting Level&nbsp;2 discovery,
     * where links for multiple resource contexts are provided together
     * in a Link Set document.
     * </p>
     *
     * @return a {@link LinkSetParser} for JSON Link Sets
     */
    public LinkSetParser linksetJson() {
      return LinkSetJsonParser.create();
    }

    /**
     * Creates a parser for inline {@code Link} headers and HTML {@code <link>} elements.
     *
     * <p>
     * This parser is typically used for FAIR Signposting Level&nbsp;1,
     * where typed links are provided directly with the resource response.
     * </p>
     *
     * @return a {@link LinkSetParser} for inline link representations
     */
    public LinkSetParser linksetInline() {
      return LinkSetInlineParser.create();
    }
  }

  /**
   * Namespace for FAIR Signposting validator factories.
   *
   * <p>
   * Validators operate on parsed {@link WebLink} collections and produce
   * {@link SignPostingResult} instances
   * describing validation outcomes.
   * </p>
   *
   * <p>
   * Validators returned by this class are independent and stateless;
   * clients may freely compose or replace them.
   * </p>
   */
  public static final class Validators {

    private static final Validators INSTANCE = new Validators();

    private Validators() {}

    /**
     * Creates a validator for FAIR Signposting Level&nbsp;1.
     *
     * <p>
     * This validator enforces the <em>mandatory</em> Level&nbsp;1 landing page
     * recipe and emits warnings for recommended relations.
     * </p>
     *
     * @return a Level&nbsp;1 {@link SignPostingValidator}
     */
    public SignPostingValidator level1() {
      return Level1SignPostingValidator.create();
    }

    /**
     * Creates a validator that routes FAIR Signposting Level&nbsp;2 recipes
     * automatically per origin (anchor).
     *
     * <p>
     * This validator detects landing page, metadata resource, and content
     * resource recipes using heuristics and delegates validation to the
     * appropriate specialized validator.
     * </p>
     *
     * @return a Level&nbsp;2 recipe-routing {@link SignPostingValidator}
     */
    public SignPostingValidator level2Recipes() {
      return Level2RecipeValidator.create();
    }

    /**
     * Creates a validator for the FAIR Signposting Level&nbsp;2 landing page recipe.
     *
     * <p>
     * This validator should be used when the landing page context is known
     * a priori and no heuristic routing is required.
     * </p>
     *
     * @return a landing page {@link SignPostingValidator}
     */
    public SignPostingValidator level2LandingPage() {
      return Level2LandingPageValidator.create();
    }

    /**
     * Creates a validator for the FAIR Signposting Level&nbsp;2 metadata resource recipe.
     *
     * @return a metadata resource {@link SignPostingValidator}
     */
    public SignPostingValidator level2MetadataResource() {
      return Level2MetadataResourceValidator.create();
    }

    /**
     * Creates a validator for the FAIR Signposting Level&nbsp;2 content resource recipe.
     *
     * @return a content resource {@link SignPostingValidator}
     */
    public SignPostingValidator level2ContentResource() {
      return Level2ContentResourceValidator.create();
    }
  }
}
