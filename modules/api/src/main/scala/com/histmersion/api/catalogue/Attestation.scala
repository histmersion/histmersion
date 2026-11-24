package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.OffsetPage
import com.histmersion.api.StrictJson
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.RowCount
import com.histmersion.api.Offset
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.automation.Creation

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
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Attestation:

  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  type TargetType = TargetType.T
  object TargetType extends Newtype[String, Pure]
  type TargetId = TargetId.T
  object TargetId extends Newtype[String, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]
  type Prefix = Prefix.T
  object Prefix extends Newtype[String, Pure]
  type Suffix = Suffix.T
  object Suffix extends Newtype[String, Pure]
  type CharStart = CharStart.T
  object CharStart extends Newtype[Int, Pure]
  type CharEnd = CharEnd.T
  object CharEnd extends Newtype[Int, Pure]
  type ChunkOrd = ChunkOrd.T
  object ChunkOrd extends Newtype[Int, Pure]
  type Page = Page.T
  object Page extends Newtype[String, Pure]
  type Canvas = Canvas.T
  object Canvas extends Newtype[String, Pure]
  type Xywh = Xywh.T
  object Xywh extends Newtype[String, Pure]
  type CtsUrn = CtsUrn.T
  object CtsUrn extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]

  final case class Deleted(
      id: Id,
      deleted: Deleted.Value
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Deleted:
    type Value = Value.T
    object Value extends Newtype[Boolean, Pure]

  final case class List(
      total: RowCount,
      attestations: scala.List[Attestation],
      offset: Offset = Offset(0),
      next: Option[Offset] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    def page: OffsetPage = OffsetPage(total, offset, RowCount(attestations.size))

  object List:

    def of(total: RowCount, rows: scala.List[Attestation], offset: UnreadPage.Offset): List =
      List(
        RowCount((total).value),
        rows,
        Offset((offset).value),
        OffsetPage(RowCount((total).value), Offset((offset).value), RowCount(rows.size)).next
      )

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
  ) derives StrictJson,
        KindlingsSchema,
        api.json.Schema

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
      note: Option[Note] = None,
      createdBy: Option[Creation.By] = None,
      via: Option[Creation.Via] = None
  ) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema
