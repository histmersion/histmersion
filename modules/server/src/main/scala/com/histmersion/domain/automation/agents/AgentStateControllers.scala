package com.histmersion
package domain.automation.agents

import com.histmersion.api.Write
import com.histmersion.api.automation.{AgentStateDeleted, AgentStateEntry, AgentStatePut}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.Api

/** The agents' scratch state as REST, MCP and the CLI reach it; every write holds the maintenance guard. */
final class AgentStateControllers(store: AgentStateService, maintenance: WriteGate):
  private def notFound(key: AgentStateRecord.Key) =
    Api.Error(Api.Error.Message(s"No agent state key '$key'"), Some(Api.Error.Code(Write.CodeNotFound)))

  def list(): List[AgentStateEntry] = store.list()

  def get(key: AgentStateRecord.Key): Either[Api.Error, AgentStateEntry] = store.get(key).toRight(notFound(key))

  def set(key: AgentStateRecord.Key, in: AgentStatePut): Either[Api.Error, AgentStateEntry] =
    maintenance.guard(WriteGate.OperationName("agent_state_set")):
      store.set(key, AgentStateService.SetValue(in.value.value))
      get(key)

  def delete(key: AgentStateRecord.Key): Either[Api.Error, AgentStateDeleted] =
    maintenance.guard(WriteGate.OperationName("agent_state_delete")):
      if store.delete(key) then Right(AgentStateDeleted(Api.AgentState.Key(key.value), AgentStateDeleted.Deleted(true)))
      else Left(notFound(key))
