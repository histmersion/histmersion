package com.histmersion
package domain.automation.agents

import com.histmersion.api.automation.AgentStateEntry

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The agents' scratch state (`agent_state`): a JSON value under a key, with when it was last set. */
private[agents] object AgentStateRepo:
  type StateKey = StateKey.T
  object StateKey extends RefinedType[String, Pure]

  type SetValue = SetValue.T
  object SetValue extends RefinedType[String, Pure]

  private given DbCodec[AgentStateEntry] = DbCodec.derived

  def list()(using DbCon): List[AgentStateEntry] =
    sql"SELECT key, value::text, updated_at::text FROM agent_state ORDER BY key".query[AgentStateEntry].run().toList

  def get(key: StateKey)(using DbCon): Option[AgentStateEntry] =
    sql"SELECT key, value::text, updated_at::text FROM agent_state WHERE key = $key"
      .query[AgentStateEntry]
      .run()
      .headOption

  def set(key: StateKey, value: SetValue)(using DbCon): Unit =
    sql"""INSERT INTO agent_state (key, value, updated_at) VALUES ($key, $value::jsonb, now())
          ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()""".update.run()

  def delete(key: StateKey)(using DbCon): Boolean =
    sql"DELETE FROM agent_state WHERE key = $key".update.run() > 0
