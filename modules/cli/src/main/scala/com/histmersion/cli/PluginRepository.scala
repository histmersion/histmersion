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

  def parse(raw: String, isDirectory: Path => Boolean): Either[String, PluginRepository] =
    val trimmed = raw.trim
    option(trimmed) match
      case Some(url) => Right(url)
      case None      =>
        val dir = Path.of(trimmed).toAbsolutePath.normalize
        if isDirectory(dir) then either(dir.toUri.toString) else Left(s"no such directory: $dir")
