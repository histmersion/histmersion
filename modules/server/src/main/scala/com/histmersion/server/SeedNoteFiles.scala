package com.histmersion.server

import com.histmersion.domain.library.ingest.{SeedFolder, SeedNote}
import com.histmersion.domain.media.assets.NewFigure
import com.histmersion.domain.media.figures.Figures
import com.histmersion.domain.library.sources.SourceNote

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The notes of a folder the person names for a seed, read where they are: the frontmatter of each `*.md` (its
  * converted body from `md_path`, confined to `corpus.dir` when that is set) and the figures beside it.
  */
final class SeedNoteFiles(dir: Path, confineTo: Option[SeedNoteFiles.ConfineTo])(using com.histmersion.util.storage.TextFiles) extends SeedFolder:

  // `Files.list` holds an OS directory handle; on a corpus-sized seed, leaking one per call
  // eventually exhausts the process's file descriptors.
  def notes: List[com.histmersion.server.SeedNoteFiles.NotesResult] =
    scala.util.Using.resource(Files.list(dir)) { s =>
      s.iterator.asScala.map(_.getFileName.toString).filter(_.endsWith(".md")).toList.sorted
    }

  def read(name: com.histmersion.server.SeedNoteFiles.ReadName): Option[SeedNote] =
    val f = dir.resolve(name)
    SourceNote.parseFile(f, confineTo).map(s => SeedNote(s, Files.getLastModifiedTime(f).toMillis))

  def figures(name: com.histmersion.server.SeedNoteFiles.FiguresName, citekey: com.histmersion.server.SeedNoteFiles.FiguresCitekey, maxBytes: com.histmersion.server.SeedNoteFiles.FiguresMaxBytes): List[NewFigure] =
    val note = dir.resolve(name)
    scala.util
      .Try(Files.readString(note))
      .toOption
      .flatMap(text => SeedNoteFiles.dirFor(note, text, citekey))
      .map(SeedNoteFiles.load(_, maxBytes))
      .getOrElse(Nil)

object SeedNoteFiles:

  /** Every image in a figures directory. Silently empty when the directory is absent — most notes have no figures, and
    * that is not an import failure.
    */
  type ConfineTo = ConfineTo.T
  object ConfineTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type NotesResult = NotesResult.T
  object NotesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadName = ReadName.T
  object ReadName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FiguresName = FiguresName.T
  object FiguresName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FiguresCitekey = FiguresCitekey.T
  object FiguresCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FiguresMaxBytes = FiguresMaxBytes.T
  object FiguresMaxBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadMaxBytes = LoadMaxBytes.T
  object LoadMaxBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DirForNoteText = DirForNoteText.T
  object DirForNoteText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DirForCitekey = DirForCitekey.T
  object DirForCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def load(dir: Path, maxBytes: com.histmersion.server.SeedNoteFiles.LoadMaxBytes): List[NewFigure] =
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
  def dirFor(notePath: Path, noteText: com.histmersion.server.SeedNoteFiles.DirForNoteText, citekey: com.histmersion.server.SeedNoteFiles.DirForCitekey): Option[Path] =
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
