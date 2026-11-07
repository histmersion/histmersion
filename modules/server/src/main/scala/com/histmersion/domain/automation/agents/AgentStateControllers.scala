package com.histmersion.domain.automation.agents

import com.histmersion.api.Write
import com.histmersion.api.automation.{AgentStateDeleted, AgentStateEntry, AgentStatePut}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.Api

/** The agents' scratch state as REST, MCP and the CLI reach it; every write holds the maintenance guard. */
final class AgentStateControllers(store: AgentStateService, maintenance: WriteGate):
  private def notFound(key: com.histmersion.domain.automation.agents.AgentStateRecord.Key) = Api.Error(s"No agent state key '$key'", Some(Write.CodeNotFound))

  def list(): List[AgentStateEntry] = store.list()

  def get(key: com.histmersion.domain.automation.agents.AgentStateRecord.Key): Either[Api.Error, AgentStateEntry] = store.get(key).toRight(notFound(key))

  def set(key: com.histmersion.domain.automation.agents.AgentStateRecord.Key, in: AgentStatePut): Either[Api.Error, AgentStateEntry] =
    maintenance.guard("agent_state_set"):
      store.set(key, in.value)
      get(key)

  def delete(key: com.histmersion.domain.automation.agents.AgentStateRecord.Key): Either[Api.Error, AgentStateDeleted] =
    maintenance.guard("agent_state_delete"):
      if store.delete(key) then Right(AgentStateDeleted(key, true)) else Left(notFound(key))

object AgentStateControllers:
