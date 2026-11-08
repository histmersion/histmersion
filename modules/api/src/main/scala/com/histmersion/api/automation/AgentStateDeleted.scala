package com.histmersion
package api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api

final case class AgentStateDeleted(
    key: AgentStateDeleted.Key,
    deleted: AgentStateDeleted.Deleted
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object AgentStateDeleted:
  object Key:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
  type Key = Api.AgentState.Key
  type Deleted = Deleted.T
  object Deleted extends Newtype[Boolean, Pure]
