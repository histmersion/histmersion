package com.histmersion
package util.system

import java.nio.file.Path
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.FileSystem, "pending G23 (files only through util.storage)")
final case class JvmHome(path: Path):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def expand(value: JvmHome.Path): JvmHome.Path =
    JvmHome.Path(value.value.replaceFirst("^~", java.util.regex.Matcher.quoteReplacement(path.toString)))

object JvmHome:
  type Path = Path.T
  object Path extends Newtype[String, Pure]
