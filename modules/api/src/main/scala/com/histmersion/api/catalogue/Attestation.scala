package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.OffsetPage
import com.histmersion.api.StrictJson
import io.circe.Codec
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.RowCount
import com.histmersion.api.Offset
import com.histmersion.api.personal.UnreadPage

final case class Attestation(
    id: Attestation.Id,
    citekey: Citekey,
    targetType: Attestation.TargetType,
    targetId: Attestation.TargetId,
    quote: Option[Attestation.Quote] = None,
    prefix: Option[Attestation.Prefix] = None,
    suffix: Option[Attestation.Suffix] = None,
    charStart: Option[Attestation.CharStart] = None,
    charEnd: Option[Attestation.CharEnd] = None,
    chunkOrd: Option[Attestation.ChunkOrd] = None,
    page: Option[Attestation.Page] = None,
    canvas: Option[Attestation.Canvas] = None,
    xywh: Option[Attestation.Xywh] = None,
    ctsUrn: Option[Attestation.CtsUrn] = None,
    note: Option[Attestation.Note] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object Attestation:


  type Id = Id.T
  object Id extends RefinedType[Long, Pure]
  type TargetType = TargetType.T
  object TargetType extends RefinedType[String, Pure]
  type TargetId = TargetId.T
  object TargetId extends RefinedType[String, Pure]
  type Quote = Quote.T
  object Quote extends RefinedType[String, Pure]
  type Prefix = Prefix.T
  object Prefix extends RefinedType[String, Pure]
  type Suffix = Suffix.T
  object Suffix extends RefinedType[String, Pure]
  type CharStart = CharStart.T
  object CharStart extends RefinedType[Int, Pure]
  type CharEnd = CharEnd.T
  object CharEnd extends RefinedType[Int, Pure]
  type ChunkOrd = ChunkOrd.T
  object ChunkOrd extends RefinedType[Int, Pure]
  type Page = Page.T
  object Page extends RefinedType[String, Pure]
  type Canvas = Canvas.T
  object Canvas extends RefinedType[String, Pure]
  type Xywh = Xywh.T
  object Xywh extends RefinedType[String, Pure]
  type CtsUrn = CtsUrn.T
  object CtsUrn extends RefinedType[String, Pure]
  type Note = Note.T
  object Note extends RefinedType[String, Pure]

  final case class Deleted(
      id: Id,
      deleted: Deleted.Value
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Deleted:
    type Value = Value.T
    object Value extends RefinedType[Boolean, Pure]



  final case class List(
      total: RowCount,
      attestations: scala.List[Attestation],
      offset: Offset = Offset(0),
      next: Option[Offset] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def page: OffsetPage = OffsetPage(total, offset, RowCount(attestations.size))

  object List:


    def of(total: RowCount, rows: scala.List[Attestation], offset: UnreadPage.Offset): List =
      List(RowCount((total).value), rows, Offset((offset).value), OffsetPage(RowCount((total).value), Offset((offset).value), RowCount(rows.size)).next)

  final case class Patch(
      quote: Option[Quote] = None,
      prefix: Option[Prefix] = None,
      suffix: Option[Suffix] = None,
      charStart: Option[CharStart] = None,
      charEnd: Option[CharEnd] = None,
      chunkOrd: Option[ChunkOrd] = None,
      page: Option[Page] = None,
      canvas: Option[Canvas] = None,
      xywh: Option[Xywh] = None,
      ctsUrn: Option[CtsUrn] = None,
      note: Option[Note] = None
  ) derives KindlingsSchema, com.histmersion.api.json.Schema

  object Patch:


    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  final case class Put(
      citekey: Citekey,
      targetType: TargetType,
      targetId: TargetId,
      quote: Option[Quote] = None,
      prefix: Option[Prefix] = None,
      suffix: Option[Suffix] = None,
      charStart: Option[CharStart] = None,
      charEnd: Option[CharEnd] = None,
      chunkOrd: Option[ChunkOrd] = None,
      page: Option[Page] = None,
      canvas: Option[Canvas] = None,
      xywh: Option[Xywh] = None,
      ctsUrn: Option[CtsUrn] = None,
      note: Option[Note] = None
  ) derives KindlingsSchema, com.histmersion.api.json.Schema

  object Put:


    given Codec.AsObject[Put] = StrictJson.codec(KindlingsCodecAsObject.derived[Put])
