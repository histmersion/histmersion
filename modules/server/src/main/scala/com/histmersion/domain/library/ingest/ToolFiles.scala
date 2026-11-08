package com.histmersion
package domain.library.ingest

import io.github.iltotore.iron.constraint.any.Pure

/** The files the server itself can see, as the MCP `ingest` tool names one (a path a MODEL chose): confined to
  * `corpus.dir` when that is set. The composition root answers it.
  */
trait ToolFiles:
  /** The file at `path`: why it may not be read, or `None` when there is no file there. */
  def file(path: ToolFiles.Path): Either[ToolFiles.Problem, Option[Upload]]

object ToolFiles:
  type Path = Path.T
  object Path extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
