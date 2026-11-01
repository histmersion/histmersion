package com.histmersion.db

import com.histmersion.domain.catalogue.places.FindingPlacement
import com.histmersion.domain.media.assets.NewFigure

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*

import javax.sql.DataSource
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Seed the DB from a `wiki/sources/` directory of Markdown notes. Idempotent (upsert by citekey).
  *
  * The DB — not the Markdown — is the store of record, so a reseed must never silently wipe agent enrichment: a file
  * whose mtime is OLDER than the stored row's `updated_at` is SKIPPED and reported in [[SeedResult.skipped]]. `force`
  * overrides that (the deliberate re-import).
  */
object Importer:

  /** The figures a note brings (media's: read where the note is, stored with the source). */
  trait SeedFigures:
    /** The figures of the old file pipeline beside a note, at most `maxBytes` each. */
    def of(note: Path, citekey: String, maxBytes: Long): List[NewFigure]

    /** A source's figures added, leaving any stored under the same name alone; how many were written. */
    def add(citekey: String, figures: List[NewFigure]): Int

  def importDir(ds: DataSource, dir: Path, chunkChars: Int, figures: SeedFigures): SeedResult =
    importDir(ds, dir, chunkChars, force = false, figures)

  /** `chunkChars` (config `histmersion.limits.chunk-chars`) sizes the FTS body chunks written per source; `assetBytes`
    * (`histmersion.limits.asset-bytes`) caps one figure picked up from `md/<ck>/figures/…`.
    */
  def importDir(
      ds: DataSource,
      dir: Path,
      chunkChars: Int,
      force: Boolean,
      figures: SeedFigures,
      assetBytes: Long = Long.MaxValue,
      toleranceKm: Double = FindingPlacement.DefaultToleranceKm,
      confineTo: Option[String] = None
  ): SeedResult =
    // `Files.list` holds an OS directory handle; on a corpus-sized seed, leaking one per call
    // eventually exhausts the process's file descriptors.
    val files = scala.util.Using.resource(Files.list(dir)) { s =>
      s.iterator.asScala
        .filter(_.getFileName.toString.endsWith(".md"))
        .toList
        .sortBy(_.getFileName.toString)
    }
    val stored = if force then Map.empty[String, Long] else SourceRepo.updatedAtMillis(ds)
    var ok = 0
    val failures = scala.collection.mutable.ListBuffer.empty[String]
    val skipped = scala.collection.mutable.ListBuffer.empty[String]
    files.foreach { f =>
      try
        Frontmatter.parseFile(f, confineTo) match
          case Some(parsed) =>
            if isStale(stored, parsed.citekey, Files.getLastModifiedTime(f).toMillis) then skipped += parsed.citekey
            else
              // figures of the old file pipeline (plan §1.8): stored as assets, and the body's
              // `…/figures/<name>` links (absolute paths included) normalised to the canonical
              // `assets/<name>` in the same write, along with the OCR's picture-text dumps
              val found = figures.of(f, parsed.citekey, assetBytes)
              val s = parsed.copy(ocrMarkdown = parsed.ocrMarkdown.map(AssetLinks.normalizeBody(_, parsed.title)))
              SourceRepo.upsert(ds, s, chunkChars, toleranceKm)
              if found.nonEmpty then
                val _ = figures.add(s.citekey, found)
              ok += 1
          case None => failures += s"${f.getFileName}: no frontmatter"
      catch case e: Throwable => failures += s"${f.getFileName}: ${e.getMessage}"
    }
    val resolved = SourceRepo.resolveEdges(ds) // unify short tag targets → full citekeys (all sources now present)
    SeedResult(ok, failures.toList, resolved, skipped.toList)

  /** True when the stored row is strictly newer than the file — writing the file would lose work. */
  def isStale(stored: Map[String, Long], citekey: String, fileMillis: Long): Boolean =
    stored.get(citekey).exists(_ > fileMillis)
