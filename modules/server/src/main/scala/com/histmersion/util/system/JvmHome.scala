package com.histmersion.util.system

import java.nio.file.Path

final case class JvmHome(path: Path):
  def expand(value: com.histmersion.util.system.JvmHome.Path): com.histmersion.util.system.JvmHome.Path = com.histmersion.util.system.JvmHome.Path(value.value.replaceFirst("^~", java.util.regex.Matcher.quoteReplacement(path.toString)))

object JvmHome:
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
