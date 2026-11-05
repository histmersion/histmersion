package com.histmersion.api.ocr
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ConvertedDoc(
    markdown: String,
    pages: Int = 0,
    images: List[ConvertedImage] = Nil,
    filename: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
