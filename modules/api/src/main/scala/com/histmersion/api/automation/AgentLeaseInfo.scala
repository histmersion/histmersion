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
    (s"$owner${if (thisServer).value then " (this server)" else ""} until $until${
        if (expired).value then " — expired" else ""
      }")

object AgentLeaseInfo:

  type Owner = Owner.T
  object Owner extends Newtype[String, Pure]
  type Until = Until.T
  object Until extends Newtype[String, Pure]
  type Expired = Expired.T
  object Expired extends Newtype[Boolean, Pure]
  type ThisServer = ThisServer.T
  object ThisServer extends Newtype[Boolean, Pure]
