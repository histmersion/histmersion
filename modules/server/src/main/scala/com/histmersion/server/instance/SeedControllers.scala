package com.histmersion.server.instance

import com.histmersion.api.library.{SeedRequest, SeedResult}
import com.histmersion.domain.library.ingest.SeedImport
import com.histmersion.util.storage.SafePath
import com.histmersion.server.{Deps, SeedNoteFiles}

import java.nio.file.Files
import com.histmersion.api.Api

/** `histmersion seed <dir>`: an exported tree of notes on this server's disk read back into the library. The directory
  * is a SERVER-side path the caller chooses, so it is confined to `corpus.dir` when that is configured; when it is not,
  * the old behaviour stands (the host-side workflow points the server at the user's own tree), said once in the log.
  */
final class SeedControllers(deps: Deps, importer: => SeedImport):
  private def cfg = deps.cfg

  /** `corpus.dir` as an `Option`: empty string = not configured. */
  private def corpusRoot: Option[com.histmersion.server.instance.SeedControllers.CorpusRoot] = Option(cfg.corpus.dir).map(_.trim).filter(_.nonEmpty)

  def seed(req: SeedRequest): Either[Api.Error, SeedResult] = deps.maintenance.guard("seed"):
    val asked = Option(req.dir).map(_.trim).filter(_.nonEmpty).getOrElse(cfg.corpus.dir)
    if asked.isEmpty then Left(Api.Error("no directory given and histmersion.corpus.dir is not configured"))
    else
      if !SafePath.configured(corpusRoot) then
        scribe.warn(
          "histmersion.corpus.dir is not configured — `seed` will read any path the caller " +
            "names; set it (HISTMERSION_CORPUS_DIR) to confine server-side reads"
        )
      SafePath.confine(corpusRoot, asked).left.map(Api.Error(_)).flatMap { dir =>
        if !Files.isDirectory(dir) then Left(Api.Error(s"not a directory on the server: $dir"))
        else Right(importer.run(SeedNoteFiles(dir, corpusRoot)(using deps.textFiles), req.force, cfg.limits.assetBytes))
      }

object SeedControllers:
  type CorpusRoot = CorpusRoot.T
  object CorpusRoot extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
