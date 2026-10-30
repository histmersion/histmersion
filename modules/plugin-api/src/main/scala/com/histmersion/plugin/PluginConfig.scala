package com.histmersion.plugin

import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}

/** The readers a plugin's config derivation must see: an `${?ENV}` override always arrives as a HOCON STRING, so a
  * number or boolean setting has to accept a numeric or boolean string too — the same givens `core/Config.scala`
  * declares for Histmersion's own settings. A plugin imports them where it derives (`import
  * com.histmersion.plugin.PluginConfig.given`); lexical-scope givens win over the companion defaults. Without them an
  * env override of a number fails with "Expected NUMBER but got STRING".
  */
object PluginConfig:
  private def lenient[A](name: String, strict: ConfigReader[A], parse: String => Option[A]): ConfigReader[A] =
    new ConfigReader[A]:
      def from(v: org.ekrich.config.ConfigValue) =
        strict
          .from(v)
          .orElse(
            ConfigReader.stringReader
              .from(v)
              .flatMap(s => parse(s.trim).toRight(ConfigDecodingError.CannotConvert(Nil, s"'$s' is not a $name", None)))
          )

  given ConfigReader[Int] = lenient("int", ConfigReader.intReader, _.toIntOption)
  given ConfigReader[Long] = lenient("long", ConfigReader.longReader, _.toLongOption)
  given ConfigReader[Double] = lenient("double", ConfigReader.doubleReader, _.toDoubleOption)
  given ConfigReader[Boolean] = lenient("boolean", ConfigReader.booleanReader, _.toBooleanOption)
