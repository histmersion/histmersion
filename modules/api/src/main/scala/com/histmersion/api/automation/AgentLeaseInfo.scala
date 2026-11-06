package com.histmersion.api.automation
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class AgentLeaseInfo(owner: AgentLeaseInfo.Owner, until: AgentLeaseInfo.Until, expired: AgentLeaseInfo.Expired, thisServer: AgentLeaseInfo.ThisServer)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def describe: com.histmersion.api.automation.AgentLeaseInfo.DescribeResult =
    _root_.com.histmersion.api.automation.AgentLeaseInfo.DescribeResult(s"$owner${if (thisServer).value then " (this server)" else ""} until $until${if (expired).value then " — expired" else ""}")

object AgentLeaseInfo:
  type DescribeResult = DescribeResult.T
  object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Owner = Owner.T
  object Owner extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Until = Until.T
  object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Expired = Expired.T
  object Expired extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThisServer = ThisServer.T
  object ThisServer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
