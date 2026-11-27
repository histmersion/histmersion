package com.histmersion
package domain.automation.approvals
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor

import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState}
import com.histmersion.api.automation.ListFilters
import io.github.iltotore.iron.constraint.any.Pure
import com.augustnagro.magnum.{sql, Frag}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.lint.{tolerated, LintRule}

/** What the approvals list is filtered by (core `ListFilters.ApprovalFilter`, validated): `state` None = every state;
  * the lists match any of their values.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class ApprovalQuery(
    state: Option[ApprovalState] = None,
    kinds: List[ApprovalKind] = Nil,
    subject: Option[ApprovalQuery.Subject] = None,
    goal: Option[ApprovalQuery.Goal] = None,
    decidedBy: List[ListFilters.DecidedBy] = Nil
)

object ApprovalQuery:
  type Cursor = Cursor.T
  object Cursor extends Newtype[String, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type State = State.T
  object State extends Newtype[String, Pure]
  type UpTo = UpTo.T
  object UpTo extends Newtype[String, Pure]

  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]
  type Goal = Goal.T
  object Goal extends Newtype[String, Pure]

  /** Who decided an approval, in SQL — `ListFilters.DecidedBy.of`, the same rules. The state and the answers are bound
    * through their codecs, the two prefixes as LIKE patterns (the code's own constants: no quote, `%` or `_` in them —
    * `ListFiltersSuite` holds that); the expression is used both to filter and to count (`GROUP BY 1`).
    */
  val decidedByExpr: Frag =
    import ListFilters.DecidedBy
    sql"""(CASE WHEN state = ${ApprovalState.Open.wire} THEN NULL
              WHEN note LIKE ${DecidedBy.AutopilotNote + "%"} THEN ${DecidedBy.Autopilot.wire}
              WHEN note IS NULL AND result LIKE ${DecidedBy.AutoResult + "%"} THEN ${DecidedBy.Auto.wire}
              ELSE ${DecidedBy.Person.wire} END)"""
