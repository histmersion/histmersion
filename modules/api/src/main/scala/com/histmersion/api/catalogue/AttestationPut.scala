package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class AttestationPut(
    citekey: String,
    targetType: String,
    targetId: String,
    quote: Option[String] = None,
    prefix: Option[String] = None,
    suffix: Option[String] = None,
    charStart: Option[Int] = None,
    charEnd: Option[Int] = None,
    chunkOrd: Option[Int] = None,
    page: Option[String] = None,
    canvas: Option[String] = None,
    xywh: Option[String] = None,
    ctsUrn: Option[String] = None,
    note: Option[String] = None
) derives KindlingsSchema

object AttestationPut:
  given Schema[AttestationPut] = summon[KindlingsSchema[AttestationPut]].schema

  given Codec.AsObject[AttestationPut] = StrictJson.codec(KindlingsCodecAsObject.derived[AttestationPut])
