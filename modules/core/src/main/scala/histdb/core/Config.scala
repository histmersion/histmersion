package histdb.core

import org.ekrich.config.ConfigFactory
import hearth.kindlings.sconfigderivation.ConfigReader

/** Application config, modelled as case classes and read from HOCON (sconfig) via Kindlings
  * derivation. Defaults + env overrides live in `reference.conf`. */
final case class DbConfig(url: String, user: String, password: String) derives ConfigReader

final case class AppConfig(db: DbConfig) derives ConfigReader

object AppConfig:
  def load(): AppConfig =
    // Parse reference.conf and resolve ${?ENV} overrides from the environment only — deliberately NOT
    // ConfigFactory.load(), which folds ALL system properties into HOCON (dotted keys like
    // `io.netty.allocator.type` then collide and blow up sconfig under native-image).
    val root = ConfigFactory
      .parseResources("reference.conf")
      .withFallback(ConfigFactory.systemEnvironment()) // env available for ${?HISTDB_*} overrides
      .resolve()
      .getConfig("histdb")
      .root
    ConfigReader[AppConfig].from(root) match
      case Right(cfg) => cfg
      case Left(err)  => throw new RuntimeException(s"hist-db config error: $err")
