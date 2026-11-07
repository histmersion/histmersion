package com.histmersion.domain.automation.agents

import java.time.Instant

/** An agent's scratch value as [[AgentStateService.InMemory]] holds it: its JSON as the database prints it back, and
  * when it was last set.
  */
final case class AgentStateRecord(value: AgentStateRecord.Value, updatedAt: Instant)

object AgentStateRecord:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Value = Value.T
  object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
