package com.histmersion.util.system

import java.nio.file.Path

final case class JvmHome(path: Path):
  def expand(value: String): String = value.replaceFirst("^~", java.util.regex.Matcher.quoteReplacement(path.toString))
