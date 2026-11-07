package com.histmersion.domain.library.ingest

import com.histmersion.api.media.AssetLinks
import com.histmersion.api.library.SeedResult
import com.histmersion.domain.Caller
import com.histmersion.domain.library.sources.{CitekeyMillis, SourceService}
import com.histmersion.domain.media.assets.{AssetService, NewFigure}

import hearth.kindlings.optics.syntax.*

/** Seed the library from a folder of `wiki/sources/` notes. Idempotent (a put by citekey).
  *
  * The database — not the Markdown — is the store of record, so a reseed must never silently wipe agent enrichment: a
  * note whose file is OLDER than the stored source's last write is SKIPPED and reported in [[SeedResult.skipped]].
  * `force` overrides that (the deliberate re-import). A note's figures of the old file pipeline are stored with the
  * source by `addFigures` (media's), and the body's `…/figures/<name>` links normalised to `assets/<name>` in the same
  * write.
  */
final class SeedImport(
    sources: SourceService,
    addFigures: (com.histmersion.domain.library.Citekey, List[NewFigure]) => AssetService.AddedFigureCount
):

  def run(folder: SeedFolder, force: SeedImport.RunForce, assetBytes: SeedFolder.MaxBytes): SeedResult =
    val stored =
      if force.value then Map.empty[com.histmersion.domain.library.Citekey, CitekeyMillis.Millis] else sources.updatedAtMillis
    val outcomes = folder.notes.map { listed =>
      val name = SeedFolder.Name(listed.value)
      try
        folder.read(name) match
          case None => Left(s"$name: no frontmatter")
          case Some(n) =>
            com.histmersion.domain.library.Citekey.parse(n.source.citekey.value) match
              case Left(problem) => Left(s"$name: ${problem.value}")
              case Right(ck) if SeedImport.isStale(stored, ck, n.modifiedMillis) =>
                Right(Some(n.source.citekey))
              case Right(ck) =>
                val found = folder.figures(name, ck, assetBytes)
                val s = n.source.modify(_.ocrMarkdown).using(_.map(AssetLinks.normalizeBody(_, n.source.title.value)))
                val _ = sources.put(s, Caller.Local)
                if found.nonEmpty then
                  val _ = addFigures(ck, found)
                Right(None)
      catch case e: Throwable => Left(s"$name: ${e.getMessage}")
    }
    // unify short tag targets → full citekeys (all sources now present)
    val resolved = sources.resolveEdges(Caller.Local)
    SeedResult(
      SeedResult.Imported(outcomes.count(_ == Right(None))),
      outcomes.flatMap(_.left.toOption).map(why => SeedResult.Failed(why)),
      SeedResult.EdgesResolved(resolved.value),
      outcomes.flatMap(_.toOption.flatten).map(ck => SeedResult.Skipped(ck.value))
    )

object SeedImport:
  type RunForce = RunForce.T
  object RunForce extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** True when the stored source is strictly newer than the file — writing the file would lose work. */
  def isStale(
      stored: Map[com.histmersion.domain.library.Citekey, CitekeyMillis.Millis],
      citekey: com.histmersion.domain.library.Citekey,
      fileMillis: com.histmersion.domain.library.ingest.SeedNote.ModifiedMillis
  ): Boolean =
    stored.get(citekey).exists(_.value > fileMillis.value)
