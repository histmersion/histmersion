package com.histmersion.cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

import java.nio.file.Path

/** A Maven repository named by URL: an `https://`, `http://` or `file:` URL, or a local directory as its `file:` URL.
  * What `plugin add --repo` looks in, and what `plugin new --repo` writes into a project.
  */
type PluginRepository = PluginRepository.T

object PluginRepository extends RefinedType[String, Match["(https?|file):.+"]]:
  /** An `https://`, `http://` or `file:` URL as given, or a local directory as its `file:` URL; a directory must exist.
    */
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRaw = ParseRaw.T
  object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseIsDirectoryResult = ParseIsDirectoryResult.T
  object ParseIsDirectoryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(raw: com.histmersion.cli.PluginRepository.ParseRaw, isDirectory: Path => com.histmersion.cli.PluginRepository.ParseIsDirectoryResult): Either[com.histmersion.cli.PluginRepository.ParseResultPart1, PluginRepository] =
    val trimmed = raw.trim
    option(trimmed) match
      case Some(url) => Right(url)
      case None      =>
        val dir = Path.of(trimmed).toAbsolutePath.normalize
        if isDirectory(dir) then either(dir.toUri.toString) else Left(s"no such directory: $dir")
