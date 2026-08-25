package histdb.db

import histdb.core.*

import javax.sql.DataSource
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Seed the DB from a `wiki/sources/` directory of Markdown notes. Idempotent (upsert by citekey).
  *
  * The DB — not the Markdown — is the store of record, so a reseed must never silently wipe agent
  * enrichment: a file whose mtime is OLDER than the stored row's `updated_at` is SKIPPED and reported
  * in [[SeedResult.skipped]]. `force` overrides that (the deliberate re-import). */
object Importer:

  def importDir(ds: DataSource, dir: Path, chunkChars: Int): SeedResult = importDir(ds, dir, chunkChars, force = false)

  /** `chunkChars` (config `histdb.limits.chunk-chars`) sizes the FTS body chunks written per source;
    * `assetBytes` (`histdb.limits.asset-bytes`) caps one figure picked up from `md/<ck>/figures/…`. */
  def importDir(ds: DataSource, dir: Path, chunkChars: Int, force: Boolean,
      assetBytes: Long = Long.MaxValue,
      toleranceKm: Double = PlaceRepo.DefaultToleranceKm): SeedResult =
    val files = Files.list(dir).iterator.asScala
      .filter(_.getFileName.toString.endsWith(".md"))
      .toList
      .sortBy(_.getFileName.toString)
    val stored   = if force then Map.empty[String, Long] else SourceRepo.updatedAtMillis(ds)
    var ok       = 0
    val failures = scala.collection.mutable.ListBuffer.empty[String]
    val skipped  = scala.collection.mutable.ListBuffer.empty[String]
    files.foreach { f =>
      try
        Frontmatter.parseFile(f) match
          case Some(parsed) =>
            if isStale(stored, parsed.citekey, Files.getLastModifiedTime(f).toMillis) then skipped += parsed.citekey
            else
              // figures of the old file pipeline (plan §1.8): stored as assets, and the body's
              // `figures/<name>` links normalised to the canonical `assets/<name>` in the same write
              val figures = Figures.forNote(f, parsed.citekey, assetBytes)
              val s = parsed.copy(ocrMarkdown = parsed.ocrMarkdown.map(AssetLinks.fromFigures))
              SourceRepo.upsert(ds, s, chunkChars, toleranceKm)
              if figures.nonEmpty then
                val _ = AssetRepo.add(ds, s.citekey, figures)
              ok += 1
          case None => failures += s"${f.getFileName}: no frontmatter"
      catch case e: Throwable => failures += s"${f.getFileName}: ${e.getMessage}"
    }
    val resolved = SourceRepo.resolveEdges(ds) // unify short tag targets → full citekeys (all sources now present)
    SeedResult(ok, failures.toList, resolved, skipped.toList)

  /** True when the stored row is strictly newer than the file — writing the file would lose work. */
  def isStale(stored: Map[String, Long], citekey: String, fileMillis: Long): Boolean =
    stored.get(citekey).exists(_ > fileMillis)
