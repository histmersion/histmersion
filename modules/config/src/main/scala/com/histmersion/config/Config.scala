package com.histmersion
package config

import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}
import com.histmersion.Year
import com.histmersion.lint.{atBoundary, movesTo, BoundaryKind, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Env-var overrides (`port = ${?HISTMERSION_PORT}`) always arrive as HOCON STRINGS; the derived readers below
  * therefore accept either a number or a numeric string. Lexical-scope givens take priority over the companion-scope
  * defaults for every `derives ConfigReader` in this file.
  */
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
@atBoundary(BoundaryKind.Config, "reads each HOCON setting into its typed value, accepting numbers written as strings")
@movesTo(FeaturePlace.Config)
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
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
given ConfigReader[Long] = lenient("long", ConfigReader.longReader, _.toLongOption)
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
given ConfigReader[Double] = lenient("double", ConfigReader.doubleReader, _.toDoubleOption)
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
given ConfigReader[Boolean] = lenient("boolean", ConfigReader.booleanReader, _.toBooleanOption)

given ConfigReader[SeaFloorSource] =
  ConfigLayers.checking(ConfigReader.stringReader)(s => SeaFloorSource.parse(SeaFloorSource.Wire(s)))
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
@tolerated(LintRule.SilentPure, "pending G44 (a Pure newtype's option/either never fails)")
given ConfigReader[Year] = summon[ConfigReader[Int]].emap(Year.either)
