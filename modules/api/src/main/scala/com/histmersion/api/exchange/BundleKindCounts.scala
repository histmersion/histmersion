package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.given

final case class BundleKindCounts(
    kind: com.histmersion.util.data.exchange.Bundle.Kind,
    inserted: Int = 0,
    updated: Int = 0,
    unchanged: Int = 0,
    keptLocal: Int = 0,
    conflicts: Int = 0,
    deleted: Int = 0,
    skippedDeleted: Int = 0,
    held: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def changed: Int = inserted + updated + deleted
object BundleKindCounts:
  given Schema[BundleKindCounts] = summon[KindlingsSchema[BundleKindCounts]].schema
