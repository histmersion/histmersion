package com.histmersion.domain.media.figures

import com.histmersion.api.media.{AssetLinks, ImageSize}
import com.histmersion.domain.media.assets.NewFigure

/** The figures the OLD file pipeline already produced (plan §1.8, last bullet).
  *
  * `tools/convert.py` wrote `md/<citekey>/paper.md` plus `md/<citekey>/figures/…`, and the note's `md_path` frontmatter
  * key points at the former. Those figures are on disk for much of the corpus, so the seed importer picks them up
  * rather than making everyone re-OCR: the files become `assets` rows and the body's `figures/<name>` links are
  * normalised to the canonical `assets/<name>` by [[com.histmersion.api.media.AssetLinks.fromFigures]]. Pure: the files
  * are read where the folder is (the composition root's seed reading).
  */
object Figures:

  type MdPathOfResult = MdPathOfResult.T
  object MdPathOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MdPathOfNoteText = MdPathOfNoteText.T
  object MdPathOfNoteText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfFilesPart1 = OfFilesPart1.T
  object OfFilesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfFilesPart2 = OfFilesPart2.T
  object OfFilesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMaxBytes = OfMaxBytes.T
  object OfMaxBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val MdPath = """(?m)^md_path:\s*"?([^"\n\r]+)"?\s*$""".r

  /** The `md_path:` value of a note's frontmatter, if it has one. */
  def mdPathOf(noteText: com.histmersion.domain.media.figures.Figures.MdPathOfNoteText): Option[com.histmersion.domain.media.figures.Figures.MdPathOfResult] =
    MdPath.findFirstMatchIn(noteText.take(8192)).map(_.group(1).trim).filter(_.nonEmpty)

  /** The images among a figures folder's files (by name, in name order), each at most `maxBytes`, with the size read
    * off its header.
    */
  def of(files: List[(com.histmersion.domain.media.figures.Figures.OfFilesPart1, com.histmersion.domain.media.figures.Figures.OfFilesPart2)], maxBytes: com.histmersion.domain.media.figures.Figures.OfMaxBytes): List[NewFigure] =
    files.sortBy(_._1).flatMap { (file, b) =>
      val name = AssetLinks.safeName(file)
      val mime = AssetLinks.mimeOf(name)
      Option
        .when(mime.startsWith("image/") && b.nonEmpty && b.length <= maxBytes)(b)
        .map { b =>
          // measured from the header: the converter reports sizes, a file on disk cannot
          val size = ImageSize.of(b)
          NewFigure(name, mime, b, size.map(_._1), size.map(_._2))
        }
    }
