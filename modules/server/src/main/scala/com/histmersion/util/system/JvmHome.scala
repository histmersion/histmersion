package com.histmersion.util.system

import java.nio.file.Path

final case class JvmHome(path: Path):
  def expand(value: com.histmersion.util.system.JvmHome.ExpandValue): com.histmersion.util.system.JvmHome.ExpandResult = value.replaceFirst("^~", java.util.regex.Matcher.quoteReplacement(path.toString))

object JvmHome:
  type ExpandResult = ExpandResult.T
  object ExpandResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandValue = ExpandValue.T
  object ExpandValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
