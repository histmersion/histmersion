package com.histmersion.domain.automation.agents

import com.histmersion.api.automation.AgentStateEntry

import com.augustnagro.magnum.*

/** The agents' scratch state (`agent_state`): a JSON value under a key, with when it was last set. */
private[agents] object AgentStateRepo:

  type GetKey = GetKey.T
  object GetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetKey = SetKey.T
  object SetKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetValue = SetValue.T
  object SetValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteResult = DeleteResult.T
  object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteKey = DeleteKey.T
  object DeleteKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private given DbCodec[AgentStateEntry] = DbCodec.derived

  def list()(using DbCon): List[AgentStateEntry] =
    sql"SELECT key, value::text, updated_at::text FROM agent_state ORDER BY key".query[AgentStateEntry].run().toList

  def get(key: com.histmersion.domain.automation.agents.AgentStateRepo.GetKey)(using DbCon): Option[AgentStateEntry] =
    sql"SELECT key, value::text, updated_at::text FROM agent_state WHERE key = $key"
      .query[AgentStateEntry]
      .run()
      .headOption

  def set(key: com.histmersion.domain.automation.agents.AgentStateRepo.SetKey, value: com.histmersion.domain.automation.agents.AgentStateRepo.SetValue)(using DbCon): Unit =
    sql"""INSERT INTO agent_state (key, value, updated_at) VALUES ($key, $value::jsonb, now())
          ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()""".update.run()

  def delete(key: com.histmersion.domain.automation.agents.AgentStateRepo.DeleteKey)(using DbCon): com.histmersion.domain.automation.agents.AgentStateRepo.DeleteResult =
    sql"DELETE FROM agent_state WHERE key = $key".update.run() > 0
