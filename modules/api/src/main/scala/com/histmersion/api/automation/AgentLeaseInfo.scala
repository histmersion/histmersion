package com.histmersion
package api.automation
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class AgentLeaseInfo(owner: AgentLeaseInfo.Owner, until: AgentLeaseInfo.Until, expired: AgentLeaseInfo.Expired, thisServer: AgentLeaseInfo.ThisServer)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def describe: String =
    (s"$owner${if (thisServer).value then " (this server)" else ""} until $until${if (expired).value then " — expired" else ""}")

object AgentLeaseInfo:

  type Owner = Owner.T
  object Owner extends Newtype[String, Pure]
  type Until = Until.T
  object Until extends Newtype[String, Pure]
  type Expired = Expired.T
  object Expired extends Newtype[Boolean, Pure]
  type ThisServer = ThisServer.T
  object ThisServer extends Newtype[Boolean, Pure]
