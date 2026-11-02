package com.histmersion.server

import com.histmersion.config.AppConfig
import com.histmersion.domain.library.ingest.{ToolFiles, Upload}
import com.histmersion.files.SafePath

import java.nio.file.Files

/** The files this server can see, for the MCP `ingest` tool's `path`: confined to `corpus.dir` when that is set; when
  * it is not, the old behaviour stands and the operator is told why.
  */
final class ServerToolFiles(cfg: () => AppConfig) extends ToolFiles:
  def file(path: String): Either[String, Option[Upload]] =
    val root = Option(cfg().corpus.dir).map(_.trim).filter(_.nonEmpty)
    if !SafePath.configured(root) then
      scribe.warn(
        "histmersion.corpus.dir is not configured — the `ingest` tool's `path` is " +
          "unconfined; set it (HISTMERSION_CORPUS_DIR) to bound server-side reads"
      )
    SafePath.confine(root, path).map(f => Option.when(Files.isRegularFile(f))(Upload.handed(f.toFile)))
