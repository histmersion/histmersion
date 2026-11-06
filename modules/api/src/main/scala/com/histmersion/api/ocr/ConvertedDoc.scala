package com.histmersion.api.ocr
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class ConvertedDoc(
    markdown: ConvertedDoc.Markdown,
    pages: ConvertedDoc.Pages = _root_.com.histmersion.api.ocr.ConvertedDoc.Pages(0),
    images: List[ConvertedImage] = Nil,
    filename: Option[ConvertedDoc.Filename] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ConvertedDoc:
  type Markdown = com.histmersion.api.library.Source.OcrMarkdown
  val Markdown: _root_.com.histmersion.api.library.Source.OcrMarkdown.type = _root_.com.histmersion.api.library.Source.OcrMarkdown
  type Pages = Pages.T
  object Pages extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Filename = Filename.T
  object Filename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
