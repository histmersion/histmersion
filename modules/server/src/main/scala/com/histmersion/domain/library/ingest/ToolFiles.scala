package com.histmersion.domain.library.ingest

/** The files the server itself can see, as the MCP `ingest` tool names one (a path a MODEL chose): confined to
  * `corpus.dir` when that is set. The composition root answers it.
  */
trait ToolFiles:
  /** The file at `path`: why it may not be read, or `None` when there is no file there. */
  def file(path: com.histmersion.domain.library.ingest.ToolFiles.Path): Either[com.histmersion.domain.library.ingest.ToolFiles.Problem, Option[Upload]]

object ToolFiles:
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
