package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.nio.file.Path

/** A Maven repository the dependencies are looked up in: an `https://` or `file:` URL. */
type RepositoryUrl = RepositoryUrl.T
object RepositoryUrl extends RefinedType[String, Match["(https?|file):.+"]]:
  /** A URL as given, or a local directory as its `file:` URL. */
  type FromArgumentResultPart1 = FromArgumentResultPart1.T
  object FromArgumentResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromArgumentRaw = FromArgumentRaw.T
  object FromArgumentRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def fromArgument(raw: com.histmersion.pluginresolver.RepositoryUrl.FromArgumentRaw): Either[com.histmersion.pluginresolver.RepositoryUrl.FromArgumentResultPart1, RepositoryUrl] =
    val trimmed = (raw).value.trim
    if trimmed.matches("(https?|file):.+") then either(trimmed)
    else either(Path.of(trimmed).toAbsolutePath.normalize.toUri.toString)
