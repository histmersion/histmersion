package com.histmersion.config

import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}

/** Env-var overrides (`port = ${?HISTMERSION_PORT}`) always arrive as HOCON STRINGS; the derived readers below
  * therefore accept either a number or a numeric string. Lexical-scope givens take priority over the companion-scope
  * defaults for every `derives ConfigReader` in this file.
  */
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

given ConfigReader[SeaFloorSource] = ConfigReader.stringReader.emap(SeaFloorSource.parse)
