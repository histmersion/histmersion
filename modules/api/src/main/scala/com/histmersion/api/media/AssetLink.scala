package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.OffsetPage
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.exchange.Bundle
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.RowCount
import com.histmersion.api.Offset
import com.histmersion.api.personal.UnreadPage

final case class AssetLink(
    id: AssetLink.Id,
    citekey: Citekey,
    assetName: Bundle.Asset.FileName,
    relation: AssetLink.Relation,
    targetType: MediaTarget.TargetType,
    targetId: MediaTarget.TargetId,
    xywh: Option[AssetLink.Xywh] = None,
    notes: Option[AssetLink.Notes] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object AssetLink:

  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  export AssetRelation.Wire as Relation
  type Xywh = Xywh.T
  object Xywh extends Newtype[String, Pure]
  type Notes = Notes.T
  object Notes extends Newtype[String, Pure]

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
      links: scala.List[AssetLink],
      offset: Offset = Offset(0),
      next: Option[Offset] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema:
    def page: OffsetPage = OffsetPage(total, offset, RowCount(links.size))

  object List:

    def of(total: RowCount, rows: scala.List[AssetLink], offset: UnreadPage.Offset): List =
      List(
        total,
        rows,
        Offset((offset).value),
        OffsetPage(total, Offset((offset).value), RowCount(rows.size)).next
      )

  final case class Put(
      citekey: Citekey,
      assetName: Bundle.Asset.FileName,
      relation: AssetRelation.Wire,
      targetType: MediaTarget.TargetType,
      targetId: MediaTarget.TargetId,
      xywh: Option[Xywh] = None,
      notes: Option[Notes] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema
