package histdb.core

import org.ekrich.config.ConfigFactory
import hearth.kindlings.sconfigderivation.ConfigReader

/** Application config, modelled as case classes and read from HOCON (sconfig) via Kindlings
  * derivation. Defaults + env overrides live in `reference.conf`. */
final case class DbConfig(url: String, user: String, password: String) derives ConfigReader

final case class AppConfig(db: DbConfig) derives ConfigReader

object AppConfig:
  def load(): AppConfig =
    val root = ConfigFactory.load().getConfig("histdb").root
    ConfigReader[AppConfig].from(root) match
      case Right(cfg) => cfg
      case Left(err)  => throw new RuntimeException(s"hist-db config error: $err")
