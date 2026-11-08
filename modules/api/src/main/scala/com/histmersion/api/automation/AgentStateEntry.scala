package com.histmersion
package api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `value` is JSON carried as a string, so Kindlings/tapir derive its Schema without a custom one for `io.circe.Json`.
  */
final case class AgentStateEntry(
    key: AgentStateEntry.Key,
    value: AgentStateEntry.Value,
    updatedAt: Option[AgentStateEntry.UpdatedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object AgentStateEntry:
  export com.histmersion.api.Api.AgentState.Key
  type Value = Value.T
  object Value extends RefinedType[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends RefinedType[String, Pure]
