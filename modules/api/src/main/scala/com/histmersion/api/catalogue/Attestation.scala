package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.OffsetPage
import com.histmersion.api.StrictJson
import io.circe.Codec

final case class Attestation(
    id: Long,
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
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object Attestation:


  final case class Deleted(
      id: Long,
      deleted: Boolean
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema



  final case class List(
      total: Int,
      attestations: scala.List[Attestation],
      offset: Int = 0,
      next: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def page: OffsetPage = OffsetPage(total, offset, attestations.size)

  object List:

    def of(total: Int, rows: scala.List[Attestation], offset: Int): List =
      List(total, rows, offset, OffsetPage(total, offset, rows.size).next)

  final case class Patch(
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
  ) derives KindlingsSchema, com.histmersion.api.json.Schema

  object Patch:


    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  final case class Put(
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
  ) derives KindlingsSchema, com.histmersion.api.json.Schema

  object Put:


    given Codec.AsObject[Put] = StrictJson.codec(KindlingsCodecAsObject.derived[Put])
