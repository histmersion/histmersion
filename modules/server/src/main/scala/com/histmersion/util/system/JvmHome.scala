package com.histmersion
package util.system

import java.nio.file.Path
import io.github.iltotore.iron.constraint.any.Pure

final case class JvmHome(path: Path):
  def expand(value: JvmHome.Path): JvmHome.Path = JvmHome.Path(value.value.replaceFirst("^~", java.util.regex.Matcher.quoteReplacement(path.toString)))

object JvmHome:
  type Path = Path.T
  object Path extends Newtype[String, Pure]
