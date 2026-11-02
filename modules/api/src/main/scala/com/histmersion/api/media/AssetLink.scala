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
      KindlingsSchema

object AssetLink:
  given Schema[AssetLink] = summon[KindlingsSchema[AssetLink]].schema

  final case class Deleted(
      id: Long,
      deleted: Boolean
  ) derives KindlingsCodecAsObject,
        KindlingsSchema

  object Deleted:
    given Schema[Deleted] = summon[KindlingsSchema[Deleted]].schema

  final case class List(
      total: Int,
      links: scala.List[AssetLink],
      offset: Int = 0,
      next: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def page: OffsetPage = OffsetPage(total, offset, links.size)

  object List:
    given Schema[List] = summon[KindlingsSchema[List]].schema
    def of(total: Int, rows: scala.List[AssetLink], offset: Int): List =
      List(total, rows, offset, OffsetPage(total, offset, rows.size).next)

  final case class Put(
      citekey: String,
      assetName: String,
      relation: String,
      targetType: String,
      targetId: String,
      xywh: Option[String] = None,
      notes: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema

  object Put:
    given Schema[Put] = summon[KindlingsSchema[Put]].schema
