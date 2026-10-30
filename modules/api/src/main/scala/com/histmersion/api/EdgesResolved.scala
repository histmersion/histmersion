package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Result of unifying short edge targets to full citekeys after a client-driven seed. */
final case class EdgesResolved(resolved: Int) derives KindlingsCodecAsObject, KindlingsSchema
object EdgesResolved:
  given Schema[EdgesResolved] = summon[KindlingsSchema[EdgesResolved]].schema
