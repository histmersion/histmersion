package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.OffsetPage
import com.histmersion.api.StrictJson
import io.circe.Codec
import com.histmersion.api.json.NewtypeCodecs.given

final case class Attestation(
    id: Attestation.Id,
    citekey: com.histmersion.api.library.Citekey,
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
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetType = TargetType.T
  object TargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetId = TargetId.T
  object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Quote = Quote.T
  object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Prefix = Prefix.T
  object Prefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Suffix = Suffix.T
  object Suffix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CharStart = CharStart.T
  object CharStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CharEnd = CharEnd.T
  object CharEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChunkOrd = ChunkOrd.T
  object ChunkOrd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Page = Page.T
  object Page extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Canvas = Canvas.T
  object Canvas extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Xywh = Xywh.T
  object Xywh extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CtsUrn = CtsUrn.T
  object CtsUrn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Deleted(
      id: com.histmersion.api.catalogue.Attestation.Id,
      deleted: Deleted.Value
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Deleted:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class List(
      total: com.histmersion.api.RowCount,
      attestations: scala.List[Attestation],
      offset: com.histmersion.api.Offset = com.histmersion.api.Offset(0),
      next: Option[com.histmersion.api.Offset] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def page: OffsetPage = OffsetPage(total, offset, com.histmersion.api.RowCount(attestations.size))

  object List:


    def of(total: com.histmersion.api.RowCount, rows: scala.List[Attestation], offset: com.histmersion.api.personal.UnreadPage.Offset): List =
      List(com.histmersion.api.RowCount((total).value), rows, com.histmersion.api.Offset((offset).value), OffsetPage(com.histmersion.api.RowCount((total).value), com.histmersion.api.Offset((offset).value), com.histmersion.api.RowCount(rows.size)).next)

  final case class Patch(
      quote: Option[com.histmersion.api.catalogue.Attestation.Quote] = None,
      prefix: Option[com.histmersion.api.catalogue.Attestation.Prefix] = None,
      suffix: Option[com.histmersion.api.catalogue.Attestation.Suffix] = None,
      charStart: Option[com.histmersion.api.catalogue.Attestation.CharStart] = None,
      charEnd: Option[com.histmersion.api.catalogue.Attestation.CharEnd] = None,
      chunkOrd: Option[com.histmersion.api.catalogue.Attestation.ChunkOrd] = None,
      page: Option[com.histmersion.api.catalogue.Attestation.Page] = None,
      canvas: Option[com.histmersion.api.catalogue.Attestation.Canvas] = None,
      xywh: Option[com.histmersion.api.catalogue.Attestation.Xywh] = None,
      ctsUrn: Option[com.histmersion.api.catalogue.Attestation.CtsUrn] = None,
      note: Option[com.histmersion.api.catalogue.Attestation.Note] = None
  ) derives KindlingsSchema, com.histmersion.api.json.Schema

  object Patch:


    given Codec.AsObject[Patch] = StrictJson.codec(KindlingsCodecAsObject.derived[Patch])

  final case class Put(
      citekey: com.histmersion.api.library.Citekey,
      targetType: com.histmersion.api.catalogue.Attestation.TargetType,
      targetId: com.histmersion.api.catalogue.Attestation.TargetId,
      quote: Option[com.histmersion.api.catalogue.Attestation.Quote] = None,
      prefix: Option[com.histmersion.api.catalogue.Attestation.Prefix] = None,
      suffix: Option[com.histmersion.api.catalogue.Attestation.Suffix] = None,
      charStart: Option[com.histmersion.api.catalogue.Attestation.CharStart] = None,
      charEnd: Option[com.histmersion.api.catalogue.Attestation.CharEnd] = None,
      chunkOrd: Option[com.histmersion.api.catalogue.Attestation.ChunkOrd] = None,
      page: Option[com.histmersion.api.catalogue.Attestation.Page] = None,
      canvas: Option[com.histmersion.api.catalogue.Attestation.Canvas] = None,
      xywh: Option[com.histmersion.api.catalogue.Attestation.Xywh] = None,
      ctsUrn: Option[com.histmersion.api.catalogue.Attestation.CtsUrn] = None,
      note: Option[com.histmersion.api.catalogue.Attestation.Note] = None
  ) derives KindlingsSchema, com.histmersion.api.json.Schema

  object Put:


    given Codec.AsObject[Put] = StrictJson.codec(KindlingsCodecAsObject.derived[Put])
