package histdb.core

import javax.sql.DataSource
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Seed the DB from a `wiki/sources/` directory of Markdown notes. Idempotent (upsert by citekey). */
object Importer:
  final case class Result(imported: Int, failed: List[String], edgesResolved: Int)

  def importDir(ds: DataSource, dir: Path): Result =
    val files = Files.list(dir).iterator.asScala
      .filter(_.getFileName.toString.endsWith(".md"))
      .toList
      .sortBy(_.getFileName.toString)
    var ok       = 0
    val failures = scala.collection.mutable.ListBuffer.empty[String]
    files.foreach { f =>
      try
        Frontmatter.parseFile(f) match
          case Some(s) => SourceRepo.upsert(ds, s); ok += 1
          case None    => failures += s"${f.getFileName}: no frontmatter"
      catch case e: Throwable => failures += s"${f.getFileName}: ${e.getMessage}"
    }
    val resolved = SourceRepo.resolveEdges(ds) // unify short tag targets → full citekeys (all sources now present)
    Result(ok, failures.toList, resolved)
