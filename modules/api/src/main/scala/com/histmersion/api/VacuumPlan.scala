package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What a vacuum job does, decided when it starts and stored on the job (`jobs.detail.vacuum`). */
final case class VacuumPlan(analyze: Boolean, tables: List[String]) derives KindlingsCodecAsObject, KindlingsSchema
object VacuumPlan:
  given Schema[VacuumPlan] = summon[KindlingsSchema[VacuumPlan]].schema
