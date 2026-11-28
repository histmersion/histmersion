package com.histmersion
package api.automation
import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

final case class AgentLeaseInfo(
    owner: AgentLeaseInfo.Owner,
    until: AgentLeaseInfo.Until,
    expired: AgentLeaseInfo.Expired,
    thisServer: AgentLeaseInfo.ThisServer
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe: String =
    (s"$owner${if (thisServer).thisServer then " (this server)" else ""} until $until${
        if (expired).expired then " — expired" else ""
      }")

object AgentLeaseInfo:

  type Owner = Owner.T
  object Owner extends Newtype[String, Pure]
  type Until = Until.T
  object Until extends Newtype[String, Pure]
  enum Expired derives CanEqual:
    case Expired, Live
  object Expired extends TwoStates[Expired]:
    extension (expired: Expired) def expired: Boolean = expired == Expired
  enum ThisServer derives CanEqual:
    case ThisServer, OtherServer
  object ThisServer extends TwoStates[ThisServer]:
    extension (thisServer: ThisServer) def thisServer: Boolean = thisServer == ThisServer
