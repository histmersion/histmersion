package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** `value` is JSON carried as a string, so Kindlings/tapir derive its Schema without a custom one for `io.circe.Json`.
  */
final case class AgentStateEntry(
    key: AgentStateEntry.Key,
    value: AgentStateEntry.Value,
    updatedAt: Option[AgentStateEntry.UpdatedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object AgentStateEntry:
  type Value = Value.T
  object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
