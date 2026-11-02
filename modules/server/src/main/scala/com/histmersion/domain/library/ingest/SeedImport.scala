package com.histmersion.domain.library.ingest

import com.histmersion.api.media.AssetLinks
import com.histmersion.api.library.SeedResult
import com.histmersion.domain.Caller
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.domain.media.assets.NewFigure

import hearth.kindlings.optics.syntax.*

/** Seed the library from a folder of `wiki/sources/` notes. Idempotent (a put by citekey).
  *
  * The database — not the Markdown — is the store of record, so a reseed must never silently wipe agent enrichment: a
  * note whose file is OLDER than the stored source's last write is SKIPPED and reported in [[SeedResult.skipped]].
  * `force` overrides that (the deliberate re-import). A note's figures of the old file pipeline are stored with the
  * source by `addFigures` (media's), and the body's `…/figures/<name>` links normalised to `assets/<name>` in the same
  * write.
  */
final class SeedImport(sources: SourceService, addFigures: (String, List[NewFigure]) => Int):

  def run(folder: SeedFolder, force: Boolean, assetBytes: Long): SeedResult =
    val stored = if force then Map.empty[String, Long] else sources.updatedAtMillis
    val outcomes = folder.notes.map { name =>
      try
        folder.read(name) match
          case None => Left(s"$name: no frontmatter")
          case Some(n) if SeedImport.isStale(stored, n.source.citekey, n.modifiedMillis) =>
            Right(Some(n.source.citekey))
          case Some(n) =>
            val found = folder.figures(name, n.source.citekey, assetBytes)
            val s = n.source.modify(_.ocrMarkdown).using(_.map(AssetLinks.normalizeBody(_, n.source.title)))
            val _ = sources.put(s, Caller.Local)
            if found.nonEmpty then
              val _ = addFigures(s.citekey, found)
            Right(None)
      catch case e: Throwable => Left(s"$name: ${e.getMessage}")
    }
    // unify short tag targets → full citekeys (all sources now present)
    val resolved = sources.resolveEdges(Caller.Local)
    SeedResult(
      outcomes.count(_ == Right(None)),
      outcomes.flatMap(_.left.toOption),
      resolved,
      outcomes.flatMap(_.toOption.flatten)
    )

object SeedImport:
  /** True when the stored source is strictly newer than the file — writing the file would lose work. */
  def isStale(stored: Map[String, Long], citekey: String, fileMillis: Long): Boolean =
    stored.get(citekey).exists(_ > fileMillis)
