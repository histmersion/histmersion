package com.histmersion.domain.automation.approvals

import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState}
import com.histmersion.api.automation.ListFilters

/** What the approvals list is filtered by (core `ListFilters.ApprovalFilter`, validated): `state` None = every state;
  * the lists match any of their values.
  */
final case class ApprovalQuery(
    state: Option[ApprovalState] = None,
    kinds: List[ApprovalKind] = Nil,
    subject: Option[ApprovalQuery.Subject] = None,
    goal: Option[ApprovalQuery.Goal] = None,
    decidedBy: List[ListFilters.DecidedBy] = Nil
)

object ApprovalQuery:
  /** Who decided an approval, in SQL — `ListFilters.DecidedBy.of`, the same rules. The two prefixes are the code's own
    * constants (no quote, `%` or `_` in them — `ListFiltersSuite` holds that), so they are written in, not bound: the
    * expression is used both to filter and to count.
    */

  type Subject = Subject.T
  object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Goal = Goal.T
  object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val decidedByExpr: String =
    import ListFilters.DecidedBy
    s"""(CASE WHEN state = '${ApprovalState.Open.wire}' THEN NULL
              WHEN note LIKE '${DecidedBy.AutopilotNote}%' THEN '${DecidedBy.Autopilot.wire}'
              WHEN note IS NULL AND result LIKE '${DecidedBy.AutoResult}%' THEN '${DecidedBy.Auto.wire}'
              ELSE '${DecidedBy.Person.wire}' END)"""
