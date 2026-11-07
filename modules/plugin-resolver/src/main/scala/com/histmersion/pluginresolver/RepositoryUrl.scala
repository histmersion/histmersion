package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.nio.file.Path

/** A Maven repository the dependencies are looked up in: an `https://` or `file:` URL. */
type RepositoryUrl = RepositoryUrl.T
object RepositoryUrl extends RefinedType[String, Match["(https?|file):.+"]]:
  /** A URL as given, or a local directory as its `file:` URL. */
  type FromArgumentResultPart1 = _root_.com.histmersion.pluginresolver.Resolver.Problem
  val FromArgumentResultPart1: _root_.com.histmersion.pluginresolver.Resolver.Problem.type = _root_.com.histmersion.pluginresolver.Resolver.Problem

  def fromArgument(raw: com.histmersion.pluginresolver.Resolver.Argument): Either[com.histmersion.pluginresolver.RepositoryUrl.FromArgumentResultPart1, RepositoryUrl] =
    val trimmed = (raw).value.trim
    if trimmed.matches("(https?|file):.+") then (either(trimmed)).left.map(refactorError2 => _root_.com.histmersion.pluginresolver.Resolver.Problem(refactorError2))
    else (either(Path.of(trimmed).toAbsolutePath.normalize.toUri.toString)).left.map(refactorError3 => _root_.com.histmersion.pluginresolver.Resolver.Problem(refactorError3))
