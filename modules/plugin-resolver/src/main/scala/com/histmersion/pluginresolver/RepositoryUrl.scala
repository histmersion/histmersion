package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.nio.file.Path

/** A Maven repository the dependencies are looked up in: an `https://` or `file:` URL. */
type RepositoryUrl = RepositoryUrl.T
object RepositoryUrl extends RefinedType[String, Match["(https?|file):.+"]]:
  /** A URL as given, or a local directory as its `file:` URL. */
  def fromArgument(raw: String): Either[String, RepositoryUrl] =
    val trimmed = raw.trim
    if trimmed.matches("(https?|file):.+") then either(trimmed)
    else either(Path.of(trimmed).toAbsolutePath.normalize.toUri.toString)
