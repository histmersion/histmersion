package com.histmersion.domain.automation.agents

import com.histmersion.api.AgentStateEntry

import com.augustnagro.magnum.*

/** The agents' scratch state (`agent_state`): a JSON value under a key, with when it was last set. */
private[agents] object AgentStateRepo:

  private given DbCodec[AgentStateEntry] = DbCodec.derived

  def list()(using DbCon): List[AgentStateEntry] =
    sql"SELECT key, value::text, updated_at::text FROM agent_state ORDER BY key".query[AgentStateEntry].run().toList

  def get(key: String)(using DbCon): Option[AgentStateEntry] =
    sql"SELECT key, value::text, updated_at::text FROM agent_state WHERE key = $key"
      .query[AgentStateEntry]
      .run()
      .headOption

  def set(key: String, value: String)(using DbCon): Unit =
    sql"""INSERT INTO agent_state (key, value, updated_at) VALUES ($key, $value::jsonb, now())
          ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()""".update.run()

  def delete(key: String)(using DbCon): Boolean =
    sql"DELETE FROM agent_state WHERE key = $key".update.run() > 0
