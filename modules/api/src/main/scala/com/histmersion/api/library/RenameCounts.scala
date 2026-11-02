package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class RenameCounts(
    findings: Int,
    articles: List[String],
    sources: List[String],
    links: Int,
    columns: List[RenameCount],
    edgesPromoted: Int
) derives KindlingsCodecAsObject,
      KindlingsSchema
object RenameCounts:
  given Schema[RenameCounts] = summon[KindlingsSchema[RenameCounts]].schema
