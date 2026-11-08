package com.histmersion
package api.ocr
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ConvertedDoc(
    markdown: ConvertedDoc.Markdown,
    pages: ConvertedDoc.Pages = ConvertedDoc.Pages(0),
    images: List[ConvertedImage] = Nil,
    filename: Option[ConvertedDoc.Filename] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ConvertedDoc:
  export com.histmersion.api.library.Source.{OcrMarkdown as Markdown}
  type Pages = Pages.T
  object Pages extends Newtype[Int, Pure]
  type Filename = Filename.T
  object Filename extends Newtype[String, Pure]
