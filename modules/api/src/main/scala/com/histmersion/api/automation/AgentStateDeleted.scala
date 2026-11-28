package com.histmersion
package api.automation
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api
import com.histmersion.api.TwoStates

final case class AgentStateDeleted(
    key: AgentStateDeleted.Key,
    deleted: AgentStateDeleted.Deleted
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object AgentStateDeleted:
  object Key:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
  type Key = Api.AgentState.Key
  enum Deleted derives CanEqual:
    case Deleted, Absent
  object Deleted extends TwoStates[Deleted]:
    extension (deleted: Deleted) def deleted: Boolean = deleted == Deleted
