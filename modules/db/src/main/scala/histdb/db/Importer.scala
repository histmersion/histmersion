package histdb.db

import histdb.core.*

import javax.sql.DataSource
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Seed the DB from a `wiki/sources/` directory of Markdown notes. Idempotent (upsert by citekey).
  * `chunkChars` (config `histdb.limits.chunk-chars`) sizes the FTS body chunks written per source. */
object Importer:
  def importDir(ds: DataSource, dir: Path, chunkChars: Int): SeedResult =
    val files = Files.list(dir).iterator.asScala
      .filter(_.getFileName.toString.endsWith(".md"))
      .toList
      .sortBy(_.getFileName.toString)
    var ok       = 0
    val failures = scala.collection.mutable.ListBuffer.empty[String]
    files.foreach { f =>
      try
        Frontmatter.parseFile(f) match
          case Some(s) => SourceRepo.upsert(ds, s, chunkChars); ok += 1
          case None    => failures += s"${f.getFileName}: no frontmatter"
      catch case e: Throwable => failures += s"${f.getFileName}: ${e.getMessage}"
    }
    val resolved = SourceRepo.resolveEdges(ds) // unify short tag targets → full citekeys (all sources now present)
    SeedResult(ok, failures.toList, resolved)
