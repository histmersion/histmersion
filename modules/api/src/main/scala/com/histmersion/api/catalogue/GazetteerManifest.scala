package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class GazetteerManifest(extracts: List[GazetteerExtract]) derives KindlingsCodecAsObject, KindlingsSchema
object GazetteerManifest:
  given Schema[GazetteerManifest] = summon[KindlingsSchema[GazetteerManifest]].schema
