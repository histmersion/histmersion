package com.histmersion.cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

import java.nio.file.Path

/** A Maven repository named by URL: an `https://`, `http://` or `file:` URL, or a local directory as its `file:` URL.
  * What `plugin add --repo` looks in, and what `plugin new --repo` writes into a project.
  */
type PluginRepository = PluginRepository.T

object PluginRepository extends RefinedType[String, Match["(https?|file):.+"]]:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** An `https://`, `http://` or `file:` URL as given, or a local directory as its `file:` URL; a directory must exist.
    */

  def parse(raw: com.histmersion.cli.PluginRepository.Text, isDirectory: Path => Boolean): Either[com.histmersion.cli.PluginRepository.Problem, PluginRepository] =
    val trimmed = raw.trim
    option(trimmed) match
      case Some(url) => Right(url)
      case None      =>
        val dir = Path.of(trimmed).toAbsolutePath.normalize
        if isDirectory(dir) then either(dir.toUri.toString) else Left(s"no such directory: $dir")
