package com.histmersion
package config

import scala.annotation.tailrec

import java.nio.file.Path
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** The user config file's part in a load: where it was looked for, whether it was there, and the keys of it that are no
  * setting (a typo is said, never silently dropped).
  */
final case class UserConfigFile(
    path: Option[Path],
    loaded: UserConfigFile.Loaded,
    unknownKeys: List[UserConfigFile.UnknownKeys] = Nil
):
  /** One line per ignored key, for the log. */
  def warnings: List[UserConfigFile.Problem] =
    val where = path.fold("the config file")(_.toString)
    unknownKeys.map { k =>
      val hint =
        if k.value == "histmersion" || k.value.startsWith("histmersion.") then ""
        else " (settings go under histmersion { … })"
      UserConfigFile.Problem(s"$where: unknown setting '${k.value}' is ignored$hint")
    }

object UserConfigFile:
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]

  type Loaded = Loaded.T
  object Loaded extends Newtype[Boolean, Pure]
  type UnknownKeys = UnknownKeys.T
  object UnknownKeys extends Newtype[String, Pure]
