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
final class SeedNoteFiles(dir: Path, confineTo: Option[com.histmersion.util.storage.SafePath.Base])(using com.histmersion.util.storage.TextFiles) extends SeedFolder:

  // `Files.list` holds an OS directory handle; on a corpus-sized seed, leaking one per call
  // eventually exhausts the process's file descriptors.
  def notes: List[SeedFolder.NotesResult] =
    scala.util.Using.resource(Files.list(dir)) { s =>
      s.iterator.asScala.map(_.getFileName.toString).filter(_.endsWith(".md")).toList.sorted.map(SeedFolder.NotesResult(_))
    }

  def read(name: SeedFolder.Name): Option[SeedNote] =
    val f = dir.resolve(name.value)
    SourceNote.parseFile(f, confineTo).map(s => SeedNote(s, com.histmersion.domain.library.ingest.SeedNote.ModifiedMillis(Files.getLastModifiedTime(f).toMillis)))

  def figures(name: SeedFolder.Name, citekey: com.histmersion.domain.library.Citekey, maxBytes: SeedFolder.MaxBytes): List[NewFigure] =
    val note = dir.resolve(name.value)
    scala.util
      .Try(Files.readString(note))
      .toOption
      .flatMap(text => SeedNoteFiles.dirFor(note, Figures.NoteText(text), citekey))
      .flatMap(d => com.histmersion.ByteCount.option(maxBytes.value).map(SeedNoteFiles.load(d, _)))
      .getOrElse(Nil)

object SeedNoteFiles:

  /** Every image in a figures directory. Silently empty when the directory is absent — most notes have no figures, and
    * that is not an import failure.
    */
  def load(dir: Path, maxBytes: com.histmersion.ByteCount): List[NewFigure] =
    if !Files.isDirectory(dir) then Nil
    else
      val files = scala.util.Using.resource(Files.list(dir))(_.iterator.asScala.filter(Files.isRegularFile(_)).toList)
      Figures.of(
        files.flatMap(f => scala.util.Try(Files.readAllBytes(f)).toOption.map(b => Figures.FileName(f.getFileName.toString) -> NewFigure.Bytes(b))),
        maxBytes
      )

  /** Where a note's figures live. `wiki/sources/<ck>.md` is three levels below the corpus root, the same arithmetic the
    * frontmatter uses to resolve `md_path`; the figures sit next to the converted body. Falls back to the
    * `md/<citekey>/figures` convention when the note carries no `md_path`.
    */
  def dirFor(notePath: Path, noteText: Figures.NoteText, citekey: com.histmersion.domain.library.Citekey): Option[Path] =
    val root = Option(notePath.getParent)
      .flatMap(a => Option(a.getParent))
      .flatMap(a => Option(a.getParent))
    root.map { r =>
      Figures
        .mdPathOf(noteText)
        .map(rel => r.resolve(rel.value).getParent)
        .map(_.resolve("figures"))
        .getOrElse(r.resolve("md").resolve(citekey.value).resolve("figures"))
    }
