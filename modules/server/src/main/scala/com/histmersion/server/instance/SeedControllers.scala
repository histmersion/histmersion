package com.histmersion
package server.instance

import com.histmersion.util.problem.{Legacy, Problem}
import io.scalaland.chimney.dsl.*
import com.histmersion.api.library.{SeedRequest, SeedResult}
import com.histmersion.domain.library.ingest.SeedImport
import com.histmersion.util.storage.SafePath
import com.histmersion.server.{Deps, SeedNoteFiles}

import java.nio.file.Files
import com.histmersion.api.Api
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.domain.library.ingest.SeedFolder
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** `histmersion seed <dir>`: an exported tree of notes on this server's disk read back into the library. The directory
  * is a SERVER-side path the caller chooses, so it is confined to `corpus.dir` when that is configured; when it is not,
  * the old behaviour stands (the host-side workflow points the server at the user's own tree), said once in the log.
  */
final class SeedControllers(deps: Deps, importer: => SeedImport):
  private def cfg = deps.cfg

  /** `corpus.dir` as an `Option`: empty string = not configured. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def corpusRoot: Option[SafePath.Base] =
    Option(cfg.corpus.dir.value).map(_.trim).filter(_.nonEmpty).map(SafePath.Base(_))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def seed(req: SeedRequest)(using Creator.New): Either[Api.Error, SeedResult] =
    deps.maintenance.guard(WriteGate.OperationName("seed")):
      val asked = Option(req.dir).map(_.value.trim).filter(_.nonEmpty).getOrElse(cfg.corpus.dir.value)
      if asked.isEmpty then
        Left(
          Api.Error(
            Problem.Unclassified(Legacy.Text("no directory given and histmersion.corpus.dir is not configured"))
          )
        )
      else
        if !SafePath.configured(corpusRoot) then
          scribe.warn(
            "histmersion.corpus.dir is not configured — `seed` will read any path the caller " +
              "names; set it (HISTMERSION_CORPUS_DIR) to confine server-side reads"
          )
        SafePath
          .confine(corpusRoot, SafePath.Candidate(asked))
          .left
          .map(p => Api.Error(p))
          .flatMap { dir =>
            if !Files.isDirectory(dir) then
              Left(Api.Error(Problem.Unclassified(Legacy.Text(s"not a directory on the server: $dir"))))
            else
              Right(
                importer.run(
                  SeedNoteFiles(dir, corpusRoot)(using deps.textFiles),
                  SeedImport.RunForce(req.force.value),
                  SeedFolder.MaxBytes(cfg.limits.assetBytes.value)
                )
              )
          }
