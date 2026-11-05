package com.histmersion.config

import scala.annotation.tailrec

import java.nio.file.Path
import scala.jdk.CollectionConverters.*

/** The user config file's part in a load: where it was looked for, whether it was there, and the keys of it that are no
  * setting (a typo is said, never silently dropped).
  */
final case class UserConfigFile(path: Option[Path], loaded: Boolean, unknownKeys: List[String] = Nil):
  /** One line per ignored key, for the log. */
  def warnings: List[String] =
    val where = path.fold("the config file")(_.toString)
    unknownKeys.map { k =>
      val hint =
        if k == "histmersion" || k.startsWith("histmersion.") then "" else " (settings go under histmersion { … })"
      s"$where: unknown setting '$k' is ignored$hint"
    }
