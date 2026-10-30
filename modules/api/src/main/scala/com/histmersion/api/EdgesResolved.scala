package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EdgesResolved(resolved: Int) derives KindlingsCodecAsObject, KindlingsSchema
object EdgesResolved:
  given Schema[EdgesResolved] = summon[KindlingsSchema[EdgesResolved]].schema
