package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One row of the hierarchy walk (`places` MCP tool / `histmersion places show --tree`): `depth` is the distance from a
  * root, so the caller renders an indented list without re-walking anything.
  */
final case class PlaceTreeRow(
    id: String,
    label: String,
    kind: String,
    depth: Int,
    findings: Int,
    contested: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceTreeRow:
  given Schema[PlaceTreeRow] = summon[KindlingsSchema[PlaceTreeRow]].schema
