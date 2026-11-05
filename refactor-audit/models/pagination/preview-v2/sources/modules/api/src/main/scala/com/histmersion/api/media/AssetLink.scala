package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.OffsetPage

final case class AssetLink(
    id: Long,
    citekey: String,
    assetName: String,
    relation: String,
    targetType: String,
    targetId: String,
    xywh: Option[String] = None,
    notes: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object AssetLink:


  final case class Deleted(
      id: Long,
      deleted: Boolean
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema



  final case class List(
      total: com.histmersion.api.RowCount,
      links: scala.List[AssetLink],
      offset: com.histmersion.api.Offset = com.histmersion.api.Offset(0),
      next: Option[com.histmersion.api.Offset] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def page: OffsetPage = OffsetPage(total, offset, com.histmersion.api.RowCount(links.size))

  object List:

    def of(total: Int, rows: scala.List[AssetLink], offset: Int): List =
      List(com.histmersion.api.RowCount(total), rows, com.histmersion.api.Offset(offset), OffsetPage(com.histmersion.api.RowCount(total), com.histmersion.api.Offset(offset), com.histmersion.api.RowCount(rows.size)).next)

  final case class Put(
      citekey: String,
      assetName: String,
      relation: String,
      targetType: String,
      targetId: String,
      xywh: Option[String] = None,
      notes: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
