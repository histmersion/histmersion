package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.OffsetPage
import com.histmersion.api.json.NewtypeCodecs.given

final case class AssetLink(
    id: AssetLink.Id,
    citekey: com.histmersion.api.library.Citekey,
    assetName: com.histmersion.api.media.AssetInfo.Name,
    relation: AssetLink.Relation,
    targetType: com.histmersion.api.media.MediaTarget.TargetType,
    targetId: com.histmersion.api.media.MediaTarget.TargetId,
    xywh: Option[AssetLink.Xywh] = None,
    notes: Option[AssetLink.Notes] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object AssetLink:


  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Relation = Relation.T
  object Relation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Xywh = Xywh.T
  object Xywh extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Notes = Notes.T
  object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Deleted(
      id: com.histmersion.api.media.AssetLink.Id,
      deleted: Deleted.Value
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Deleted:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class List(
      total: com.histmersion.api.RowCount,
      links: scala.List[AssetLink],
      offset: com.histmersion.api.Offset = com.histmersion.api.Offset(0),
      next: Option[com.histmersion.api.Offset] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def page: OffsetPage = OffsetPage(total, offset, com.histmersion.api.RowCount(links.size))

  object List:

    type OfTotal = OfTotal.T
    object OfTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfOffset = OfOffset.T
    object OfOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(total: com.histmersion.api.media.AssetLink.List.OfTotal, rows: scala.List[AssetLink], offset: com.histmersion.api.media.AssetLink.List.OfOffset): List =
      List(com.histmersion.api.RowCount(total), rows, com.histmersion.api.Offset(offset), OffsetPage(com.histmersion.api.RowCount(total), com.histmersion.api.Offset(offset), com.histmersion.api.RowCount(rows.size)).next)

  final case class Put(
      citekey: com.histmersion.api.library.Citekey,
      assetName: com.histmersion.api.media.AssetInfo.Name,
      relation: com.histmersion.api.media.AssetLink.Relation,
      targetType: com.histmersion.api.media.MediaTarget.TargetType,
      targetId: com.histmersion.api.media.MediaTarget.TargetId,
      xywh: Option[com.histmersion.api.media.AssetLink.Xywh] = None,
      notes: Option[com.histmersion.api.media.AssetLink.Notes] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
