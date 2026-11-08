package com.histmersion
package domain.automation.agents

import java.time.Instant
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** An agent's scratch value as [[AgentStateService.InMemory]] holds it: its JSON as the database prints it back, and
  * when it was last set.
  */
final case class AgentStateRecord(value: AgentStateRecord.Value, updatedAt: Instant)

object AgentStateRecord:
  type Key = Key.T
  object Key extends RefinedType[String, Pure]
  type Value = Value.T
  object Value extends RefinedType[String, Pure]
