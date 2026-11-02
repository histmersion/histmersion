package com.histmersion.domain.automation.agents

import java.time.Instant

/** An agent's scratch value as [[AgentStateService.InMemory]] holds it: its JSON as the database prints it back, and
  * when it was last set.
  */
final case class AgentStateRecord(value: String, updatedAt: Instant)
