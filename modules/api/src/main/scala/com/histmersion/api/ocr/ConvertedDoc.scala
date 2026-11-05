package com.histmersion.api.ocr
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ConvertedDoc(
    markdown: ConvertedDoc.Markdown,
    pages: ConvertedDoc.Pages = 0,
    images: List[ConvertedImage] = Nil,
    filename: Option[ConvertedDoc.Filename] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ConvertedDoc:
  type Markdown = Markdown.T
  object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Pages = Pages.T
  object Pages extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Filename = Filename.T
  object Filename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
