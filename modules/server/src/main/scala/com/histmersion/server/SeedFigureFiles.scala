package com.histmersion.server

import com.histmersion.db.Importer
import com.histmersion.domain.media.assets.{AssetService, NewFigure}
import com.histmersion.domain.media.figures.Figures

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The figures of the old file pipeline beside the notes a seed reads (plan §1.8), read where the notes are — a folder
  * the person names, which only the composition root sees as a path — and stored with their source by media.
  */
final class SeedFigureFiles(assets: AssetService) extends Importer.SeedFigures:

  def add(citekey: String, figures: List[NewFigure]): Int = assets.addFigures(citekey, figures)

  /** The figures belonging to one note file, ready to store. */
  def of(note: Path, citekey: String, maxBytes: Long): List[NewFigure] =
    scala.util
      .Try(Files.readString(note))
      .toOption
      .flatMap(text => SeedFigureFiles.dirFor(note, text, citekey))
      .map(SeedFigureFiles.load(_, maxBytes))
      .getOrElse(Nil)

object SeedFigureFiles:

  /** Every image in a figures directory. Silently empty when the directory is absent — most notes have no figures, and
    * that is not an import failure.
    */
  def load(dir: Path, maxBytes: Long): List[NewFigure] =
    if !Files.isDirectory(dir) then Nil
    else
      val files = scala.util.Using.resource(Files.list(dir))(_.iterator.asScala.filter(Files.isRegularFile(_)).toList)
      Figures.of(
        files.flatMap(f => scala.util.Try(Files.readAllBytes(f)).toOption.map(f.getFileName.toString -> _)),
        maxBytes
      )

  /** Where a note's figures live. `wiki/sources/<ck>.md` is three levels below the corpus root, the same arithmetic the
    * frontmatter uses to resolve `md_path`; the figures sit next to the converted body. Falls back to the
    * `md/<citekey>/figures` convention when the note carries no `md_path`.
    */
  def dirFor(notePath: Path, noteText: String, citekey: String): Option[Path] =
    val root = Option(notePath.getParent)
      .flatMap(a => Option(a.getParent))
      .flatMap(a => Option(a.getParent))
    root.map { r =>
      Figures
        .mdPathOf(noteText)
        .map(rel => r.resolve(rel).getParent)
        .map(_.resolve("figures"))
        .getOrElse(r.resolve("md").resolve(citekey).resolve("figures"))
    }
