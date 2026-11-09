package com.histmersion
package pluginresolver

import com.histmersion.util.problem.{Legacy, Problem}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.nio.file.Path

/** A Maven repository the dependencies are looked up in: an `https://` or `file:` URL. */
type RepositoryUrl = RepositoryUrl.T
object RepositoryUrl extends Newtype[String, Match["(https?|file):.+"]]:
  /** A URL as given, or a local directory as its `file:` URL. */

  def fromArgument(raw: Resolver.Argument): Either[Problem, RepositoryUrl] =
    val trimmed = (raw).value.trim
    if trimmed.matches("(https?|file):.+") then
      (either(trimmed)).left.map(refactorError2 => Problem.Unclassified(Legacy.Text(refactorError2)))
    else
      (either(Path.of(trimmed).toAbsolutePath.normalize.toUri.toString)).left.map(refactorError3 =>
        Problem.Unclassified(Legacy.Text(refactorError3))
      )
