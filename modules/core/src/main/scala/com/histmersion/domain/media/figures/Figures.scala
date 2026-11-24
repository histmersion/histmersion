package com.histmersion
package domain.media.figures

import com.histmersion.api.media.{AssetLinks, ImageSize}
import com.histmersion.domain.media.assets.NewFigure
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.exchange.Bundle
import com.histmersion.lint.{LintRule, tolerated}

/** The figures the OLD file pipeline already produced (plan §1.8, last bullet).
  *
  * `tools/convert.py` wrote `md/<citekey>/paper.md` plus `md/<citekey>/figures/…`, and the note's `md_path` frontmatter
  * key points at the former. Those figures are on disk for much of the corpus, so the seed importer picks them up
  * rather than making everyone re-OCR: the files become `assets` rows and the body's `figures/<name>` links are
  * normalised to the canonical `assets/<name>` by [[com.histmersion.api.media.AssetLinks.fromFigures]]. Pure: the files
  * are read where the folder is (the composition root's seed reading).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object Figures:

  type NoteText = NoteText.T
  object NoteText extends Newtype[String, Pure]
  type MdPath = MdPath.T
  object MdPath extends Newtype[String, Pure]
  type FileName = FileName.T
  object FileName extends Newtype[String, Pure]

  private val MdPathLine = """(?m)^md_path:\s*"?([^"\n\r]+)"?\s*$""".r

  /** The `md_path:` value of a note's frontmatter, if it has one. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mdPathOf(noteText: NoteText): Option[MdPath] =
    MdPathLine.findFirstMatchIn(noteText.value.take(8192)).map(_.group(1).trim).filter(_.nonEmpty).map(MdPath(_))

  /** The images among a figures folder's files (by name, in name order), each at most `maxBytes`, with the size read
    * off its header.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def of(files: List[(FileName, NewFigure.Bytes)], maxBytes: ByteCount): List[NewFigure] =
    files.sortBy(_._1.value).flatMap { (file, b) =>
      val name = AssetLinks.safeName(file.value)
      val mime = AssetLinks.mimeOf(Bundle.Asset.Extension(name.value))
      Option
        .when(mime.value.startsWith("image/") && b.value.nonEmpty && b.value.length <= maxBytes.value)(b)
        .map { b =>
          // measured from the header: the converter reports sizes, a file on disk cannot
          val size = ImageSize.of(ImageSize.Bytes(b.value))
          NewFigure(
            NewFigure.Name(name.value),
            NewFigure.Mime(mime.value),
            b,
            size.map(s => NewFigure.Width(s._1.value)),
            size.map(s => NewFigure.Height(s._2.value))
          )
        }
    }
