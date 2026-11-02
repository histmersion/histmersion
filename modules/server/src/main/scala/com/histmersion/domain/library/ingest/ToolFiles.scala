package com.histmersion.domain.library.ingest

/** The files the server itself can see, as the MCP `ingest` tool names one (a path a MODEL chose): confined to
  * `corpus.dir` when that is set. The composition root answers it.
  */
trait ToolFiles:
  /** The file at `path`: why it may not be read, or `None` when there is no file there. */
  def file(path: String): Either[String, Option[Upload]]
