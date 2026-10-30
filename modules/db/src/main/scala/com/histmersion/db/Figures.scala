package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The figures the OLD file pipeline already produced (plan §1.8, last bullet).
  *
  * `tools/convert.py` wrote `md/<citekey>/paper.md` plus `md/<citekey>/figures/…`, and the note's `md_path` frontmatter
  * key points at the former. Those figures are on disk for much of the corpus, so the seed importer picks them up
  * rather than making everyone re-OCR: the files become `assets` rows and the body's `figures/<name>` links are
  * normalised to the canonical `assets/<name>` by [[com.histmersion.api.AssetLinks.fromFigures]].
  *
  * Pure except for the directory read; the path arithmetic is a separate, testable function.
  */
object Figures:

  private val MdPath = """(?m)^md_path:\s*"?([^"\n\r]+)"?\s*$""".r

  /** The `md_path:` value of a note's frontmatter, if it has one. */
  def mdPathOf(noteText: String): Option[String] =
    MdPath.findFirstMatchIn(noteText.take(8192)).map(_.group(1).trim).filter(_.nonEmpty)

  /** Where a note's figures live. `wiki/sources/<ck>.md` is three levels below the corpus root, the same arithmetic
    * [[com.histmersion.domain.Frontmatter]] uses to resolve `md_path`; the figures sit next to the converted body.
    * Falls back to the `md/<citekey>/figures` convention when the note carries no `md_path`.
    */
  def dirFor(notePath: Path, noteText: String, citekey: String): Option[Path] =
    val root = Option(notePath.getParent)
      .flatMap(a => Option(a.getParent))
      .flatMap(a => Option(a.getParent))
    root.map { r =>
      mdPathOf(noteText)
        .map(rel => r.resolve(rel).getParent)
        .map(_.resolve("figures"))
        .getOrElse(r.resolve("md").resolve(citekey).resolve("figures"))
    }

  /** Read every image in a figures directory. Silently empty when the directory is absent — most notes have no figures,
    * and that is not an import failure.
    */
  def load(dir: Path, maxBytes: Long): List[AssetRepo.Stored] =
    if !Files.isDirectory(dir) then Nil
    else
      val files = Files
        .list(dir)
        .iterator
        .asScala
        .filter(Files.isRegularFile(_))
        .toList
        .sortBy(_.getFileName.toString)
      files.flatMap { f =>
        val name = AssetLinks.safeName(f.getFileName.toString)
        val mime = AssetLinks.mimeOf(name)
        if !mime.startsWith("image/") then None
        else
          scala.util
            .Try(Files.readAllBytes(f))
            .toOption
            .filter(b => b.nonEmpty && b.length <= maxBytes)
            .map { b =>
              // measured from the header: the converter reports sizes, a file on disk cannot
              val size = ImageSize.of(b)
              AssetRepo.Stored(name, mime, b, size.map(_._1), size.map(_._2))
            }
      }

  /** The figures belonging to one note file, ready to store. */
  def forNote(notePath: Path, citekey: String, maxBytes: Long): List[AssetRepo.Stored] =
    scala.util
      .Try(Files.readString(notePath))
      .toOption
      .flatMap(text => dirFor(notePath, text, citekey))
      .map(load(_, maxBytes))
      .getOrElse(Nil)
