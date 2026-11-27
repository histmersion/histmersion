package com.histmersion
package server
import com.histmersion.util.problem.Problem

import com.histmersion.config.AppConfig
import com.histmersion.domain.library.ingest.{ToolFiles, Upload}
import com.histmersion.util.storage.SafePath

import java.nio.file.Files
import com.histmersion.util.storage.HandedFile
import com.histmersion.lint.{atBoundary, movesTo, BoundaryKind, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The files this server can see, for the MCP `ingest` tool's `path`: confined to `corpus.dir` when that is set; when
  * it is not, the old behaviour stands and the operator is told why.
  */
@atBoundary(BoundaryKind.Process, "reads the file named by the MCP ingest tool, inside the allowed directory")
@movesTo(FeaturePlace.Server)
final class ServerToolFiles(cfg: () => AppConfig) extends ToolFiles:
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def file(path: ToolFiles.Path): Either[Problem, Option[Upload]] =
    val root = Option(cfg().corpus.dir.value).map(_.trim).filter(_.nonEmpty).map(SafePath.Base(_))
    if !SafePath.configured(root) then
      scribe.warn(
        "histmersion.corpus.dir is not configured — the `ingest` tool's `path` is " +
          "unconfined; set it (HISTMERSION_CORPUS_DIR) to bound server-side reads"
      )
    SafePath
      .confine(root, SafePath.Candidate(path.value))
      .map(f => Option.when(Files.isRegularFile(f))(Upload.handed(HandedFile.snapshot(f.toFile))))
