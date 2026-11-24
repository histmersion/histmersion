package com.histmersion
package config

import com.histmersion.util.problem.Problem
import scala.annotation.tailrec

import java.nio.file.Path
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** The user config file's part in a load: where it was looked for, whether it was there, and the keys of it that are no
  * setting (a typo is said, never silently dropped).
  */
@tolerated(LintRule.FileSystem, "pending G23 (files only through util.storage)")
@movesTo(FeaturePlace.UtilSettings)
final case class UserConfigFile(
    path: Option[Path],
    loaded: UserConfigFile.Loaded,
    unknownKeys: List[UserConfigFile.UnknownKeys] = Nil
):
  /** One problem per ignored key, for the log. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def warnings: List[Problem] =
    unknownKeys.map { k =>
      val fault =
        if k.value == "histmersion" || k.value.startsWith("histmersion.") then Problem.Config.Fault.Undeclared
        else Problem.Config.Fault.OutsideRoot
      Problem.Config.Setting(Problem.SettingKey(s"$k"), fault)
    }

object UserConfigFile:

  type Loaded = Loaded.T
  object Loaded extends Newtype[Boolean, Pure]
  type UnknownKeys = UnknownKeys.T
  object UnknownKeys extends Newtype[String, Pure]
