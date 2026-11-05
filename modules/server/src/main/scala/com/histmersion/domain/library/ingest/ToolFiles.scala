package com.histmersion.domain.library.ingest

/** The files the server itself can see, as the MCP `ingest` tool names one (a path a MODEL chose): confined to
  * `corpus.dir` when that is set. The composition root answers it.
  */
trait ToolFiles:
  /** The file at `path`: why it may not be read, or `None` when there is no file there. */
  def file(path: com.histmersion.domain.library.ingest.ToolFiles.FilePath): Either[com.histmersion.domain.library.ingest.ToolFiles.FileResultPart1, Option[Upload]]

object ToolFiles:
  type FileResultPart1 = FileResultPart1.T
  object FileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilePath = FilePath.T
  object FilePath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
