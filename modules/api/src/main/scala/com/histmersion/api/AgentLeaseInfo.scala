package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Who holds the agent lease (docs/research-process-managers.md §2): only that server runs the agent engine, the
  * schedules and the managers on this database.
  */
final case class AgentLeaseInfo(owner: String, until: String, expired: Boolean, thisServer: Boolean)
    derives KindlingsCodecAsObject,
      KindlingsSchema:
  def describe: String =
    s"$owner${if thisServer then " (this server)" else ""} until $until${if expired then " — expired" else ""}"
object AgentLeaseInfo:
  given Schema[AgentLeaseInfo] = summon[KindlingsSchema[AgentLeaseInfo]].schema
