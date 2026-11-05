package com.histmersion.api.automation
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class AgentLeaseInfo(owner: AgentLeaseInfo.Owner, until: AgentLeaseInfo.Until, expired: AgentLeaseInfo.Expired, thisServer: AgentLeaseInfo.ThisServer)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def describe: String =
    s"$owner${if thisServer then " (this server)" else ""} until $until${if expired then " — expired" else ""}"

object AgentLeaseInfo:
  type Owner = Owner.T
  object Owner extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Until = Until.T
  object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Expired = Expired.T
  object Expired extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThisServer = ThisServer.T
  object ThisServer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
