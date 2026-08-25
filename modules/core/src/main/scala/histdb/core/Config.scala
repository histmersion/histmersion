package histdb.core

import org.ekrich.config.ConfigFactory
import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Env-var overrides (`port = ${?HISTDB_PORT}`) always arrive as HOCON STRINGS; the derived readers
  * below therefore accept either a number or a numeric string. Lexical-scope givens take priority over
  * the companion-scope defaults for every `derives ConfigReader` in this file. */
private def lenient[A](name: String, strict: ConfigReader[A], parse: String => Option[A]): ConfigReader[A] =
  new ConfigReader[A]:
    def from(v: org.ekrich.config.ConfigValue) =
      strict.from(v).orElse(
        ConfigReader.stringReader.from(v).flatMap(s =>
          parse(s.trim).toRight(ConfigDecodingError.CannotConvert(Nil, s"'$s' is not a $name", None))))

given ConfigReader[Int]     = lenient("int", ConfigReader.intReader, _.toIntOption)
given ConfigReader[Long]    = lenient("long", ConfigReader.longReader, _.toLongOption)
given ConfigReader[Double]  = lenient("double", ConfigReader.doubleReader, _.toDoubleOption)
given ConfigReader[Boolean] = lenient("boolean", ConfigReader.booleanReader, _.toBooleanOption)

/** Application config, modelled as case classes and read from HOCON (sconfig) via Kindlings
  * derivation. Defaults + env overrides live in `reference.conf`; nothing is hardcoded elsewhere. */
final case class ServerConfig(host: String, port: Int, publicUrl: String) derives ConfigReader
final case class DbConfig(url: String, user: String, password: String) derives ConfigReader
final case class ConverterConfig(url: String, timeoutSeconds: Int, parallelism: Int) derives ConfigReader
final case class CorpusConfig(dir: String) derives ConfigReader
final case class Limits(search: Int, findings: Int, snippetChars: Int) derives ConfigReader
final case class TimeConfig(min: Long, max: Long, defaultFrom: Long, defaultTo: Long)
    derives ConfigReader, KindlingsCodecAsObject
object TimeConfig:
  given Schema[TimeConfig] = KindlingsSchema.derived[TimeConfig].schema

/** Debounce budgets for the filter bar (plan §1.5) — the JS carries no timing literals either. */
final case class DebounceConfig(suggestMs: Int, searchMs: Int, reloadMs: Int)
    derives ConfigReader, KindlingsCodecAsObject
object DebounceConfig:
  given Schema[DebounceConfig] = KindlingsSchema.derived[DebounceConfig].schema

/** The part of the config the web page needs (served at `/api/config`, so JS has no literals). */
final case class UiConfig(
    tiles: String,
    tilesMaxZoom: Int,
    markerRadius: Int,
    viewportDebounceMs: Int,
    pageSize: Int,
    facetSuggestions: Int,
    debounce: DebounceConfig,
    time: TimeConfig
) derives ConfigReader, KindlingsCodecAsObject
object UiConfig:
  given Schema[UiConfig] = KindlingsSchema.derived[UiConfig].schema

final case class CliConfig(serverUrl: String) derives ConfigReader

final case class AppConfig(
    server: ServerConfig,
    db: DbConfig,
    converter: ConverterConfig,
    corpus: CorpusConfig,
    limits: Limits,
    ui: UiConfig,
    cli: CliConfig
) derives ConfigReader

object AppConfig:
  /** Load config. Must run on a platform thread (main), not an Ox virtual thread: the env read uses
    * GraalVM StackValue under native-image. */
  def load(): AppConfig = load(ConfigFactory.systemEnvironment())

  /** Load with an explicit environment (tests). */
  def load(env: org.ekrich.config.Config): AppConfig =
    // Parse reference.conf and resolve ${?ENV} overrides from the given environment only — deliberately
    // NOT ConfigFactory.load(), which folds ALL system properties into HOCON (dotted keys like
    // `io.netty.allocator.type` then collide and blow up sconfig under native-image).
    val root = ConfigFactory
      .parseResources("reference.conf")
      .withFallback(env)
      .resolve()
      .getConfig("histdb")
      .root
    ConfigReader[AppConfig].from(root) match
      case Right(cfg) => cfg
      case Left(err)  => throw new RuntimeException(s"hist-db config error: $err")
