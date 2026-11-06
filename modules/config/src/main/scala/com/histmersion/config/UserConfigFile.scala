package com.histmersion.config

import scala.annotation.tailrec

import java.nio.file.Path
import scala.jdk.CollectionConverters.*

/** The user config file's part in a load: where it was looked for, whether it was there, and the keys of it that are no
  * setting (a typo is said, never silently dropped).
  */
final case class UserConfigFile(path: Option[Path], loaded: UserConfigFile.Loaded, unknownKeys: List[UserConfigFile.UnknownKeys] = Nil):
  /** One line per ignored key, for the log. */
  def warnings: List[com.histmersion.config.UserConfigFile.Problem] =
    val where = path.fold("the config file")(_.toString)
    unknownKeys.map { k =>
      val hint =
        if k == "histmersion" || k.startsWith("histmersion.") then "" else " (settings go under histmersion { … })"
      s"$where: unknown setting '$k' is ignored$hint"
    }

object UserConfigFile:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Loaded = Loaded.T
  object Loaded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnknownKeys = UnknownKeys.T
  object UnknownKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
