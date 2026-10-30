package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The manifest file itself. */
final case class GazetteerManifest(extracts: List[GazetteerExtract]) derives KindlingsCodecAsObject, KindlingsSchema
object GazetteerManifest:
  given Schema[GazetteerManifest] = summon[KindlingsSchema[GazetteerManifest]].schema
