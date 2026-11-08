package com.histmersion
package server.instance

import com.histmersion.api.library.{SeedRequest, SeedResult}
import com.histmersion.domain.library.ingest.SeedImport
import com.histmersion.util.storage.SafePath
import com.histmersion.server.{Deps, SeedNoteFiles}

import java.nio.file.Files
import com.histmersion.api.Api
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.domain.library.ingest.SeedFolder

/** `histmersion seed <dir>`: an exported tree of notes on this server's disk read back into the library. The directory
  * is a SERVER-side path the caller chooses, so it is confined to `corpus.dir` when that is configured; when it is not,
  * the old behaviour stands (the host-side workflow points the server at the user's own tree), said once in the log.
  */
final class SeedControllers(deps: Deps, importer: => SeedImport):
  private def cfg = deps.cfg

  /** `corpus.dir` as an `Option`: empty string = not configured. */
  private def corpusRoot: Option[SafePath.Base] =
    Option(cfg.corpus.dir.value).map(_.trim).filter(_.nonEmpty).map(SafePath.Base(_))

  def seed(req: SeedRequest): Either[Api.Error, SeedResult] = deps.maintenance.guard(WriteGate.OperationName("seed")):
    val asked = Option(req.dir).map(_.value.trim).filter(_.nonEmpty).getOrElse(cfg.corpus.dir.value)
    if asked.isEmpty then Left(Api.Error(Api.Error.Message("no directory given and histmersion.corpus.dir is not configured")))
    else
      if !SafePath.configured(corpusRoot) then
        scribe.warn(
          "histmersion.corpus.dir is not configured — `seed` will read any path the caller " +
            "names; set it (HISTMERSION_CORPUS_DIR) to confine server-side reads"
        )
      SafePath.confine(corpusRoot, SafePath.Candidate(asked)).left.map(p => Api.Error(Api.Error.Message(p.value))).flatMap { dir =>
        if !Files.isDirectory(dir) then Left(Api.Error(Api.Error.Message(s"not a directory on the server: $dir")))
        else Right(importer.run(SeedNoteFiles(dir, corpusRoot)(using deps.textFiles), SeedImport.RunForce(req.force.value), SeedFolder.MaxBytes(cfg.limits.assetBytes.value)))
      }
