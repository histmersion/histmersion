package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `PUT /api/instance` — name this collection ("work", "home"); blank clears the label. */
final case class InstanceLabel(label: Option[String]) derives KindlingsCodecAsObject, KindlingsSchema
object InstanceLabel:
  given Schema[InstanceLabel] = summon[KindlingsSchema[InstanceLabel]].schema
