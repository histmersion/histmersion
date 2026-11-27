package com.histmersion
package domain.automation.agents

import com.histmersion.api.automation.AgentStateEntry

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The agents' scratch state (`agent_state`): a JSON value under a key, with when it was last set. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the agent state tables and their row codecs")
private[agents] object AgentStateRepo:
  type StateKey = StateKey.T
  object StateKey extends Newtype[String, Pure]

  type SetValue = SetValue.T
  object SetValue extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[AgentStateEntry] = DbCodec.derived

  def list()(using DbCon): List[AgentStateEntry] =
    sql"SELECT key, value::text, updated_at::text FROM agent_state ORDER BY key".query[AgentStateEntry].run().toList

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def get(key: StateKey)(using DbCon): Option[AgentStateEntry] =
    sql"SELECT key, value::text, updated_at::text FROM agent_state WHERE key = $key"
      .query[AgentStateEntry]
      .run()
      .headOption

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def set(key: StateKey, value: SetValue)(using DbCon): Unit =
    sql"""INSERT INTO agent_state (key, value, updated_at) VALUES ($key, $value::jsonb, now())
          ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()""".update.run()

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def delete(key: StateKey)(using DbCon): Boolean =
    sql"DELETE FROM agent_state WHERE key = $key".update.run() > 0
