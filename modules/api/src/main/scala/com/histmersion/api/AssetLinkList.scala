package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class AssetLinkList(
    total: Int,
    links: List[AssetLink],
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def page: OffsetPage = OffsetPage(total, offset, links.size)

object AssetLinkList:
  given Schema[AssetLinkList] = summon[KindlingsSchema[AssetLinkList]].schema
  def of(total: Int, rows: List[AssetLink], offset: Int): AssetLinkList =
    AssetLinkList(total, rows, offset, OffsetPage(total, offset, rows.size).next)
