package com.histmersion.domain.automation.approvals

import com.histmersion.api.Agents.{ApprovalKind, ApprovalState}
import com.histmersion.api.ListFilters

/** What the approvals list is filtered by (core `ListFilters.ApprovalFilter`, validated): `state` None = every state;
  * the lists match any of their values.
  */
final case class ApprovalQuery(
    state: Option[ApprovalState] = None,
    kinds: List[ApprovalKind] = Nil,
    subject: Option[String] = None,
    goal: Option[String] = None,
    decidedBy: List[ListFilters.DecidedBy] = Nil
)

object ApprovalQuery:
  /** Who decided an approval, in SQL — `ListFilters.DecidedBy.of`, the same rules. The two prefixes are the code's own
    * constants (no quote, `%` or `_` in them — `ListFiltersSuite` holds that), so they are written in, not bound: the
    * expression is used both to filter and to count.
    */
  val decidedByExpr: String =
    import ListFilters.DecidedBy
    s"""(CASE WHEN state = '${ApprovalState.Open.wire}' THEN NULL
              WHEN note LIKE '${DecidedBy.AutopilotNote}%' THEN '${DecidedBy.Autopilot.wire}'
              WHEN note IS NULL AND result LIKE '${DecidedBy.AutoResult}%' THEN '${DecidedBy.Auto.wire}'
              ELSE '${DecidedBy.Person.wire}' END)"""
