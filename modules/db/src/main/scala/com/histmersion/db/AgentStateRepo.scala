package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

object AgentStateRepo:

  def list(ds: DataSource): List[AgentStateEntry] =
    connect(ds):
      sql"SELECT key, value::text, updated_at::text FROM agent_state ORDER BY key"
        .query[AgentStateEntry]
        .run()
        .toList

  def get(ds: DataSource, key: String): Option[AgentStateEntry] =
    connect(ds):
      sql"SELECT key, value::text, updated_at::text FROM agent_state WHERE key = $key"
        .query[AgentStateEntry]
        .run()
        .headOption

  def set(ds: DataSource, key: String, value: String): Unit =
    transact(ds):
      sql"""INSERT INTO agent_state (key, value, updated_at) VALUES ($key, $value::jsonb, now())
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()""".update.run()

  def delete(ds: DataSource, key: String): Boolean =
    transact(ds):
      sql"DELETE FROM agent_state WHERE key = $key".update.run() > 0
