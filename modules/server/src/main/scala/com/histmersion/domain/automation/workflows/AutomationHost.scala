package com.histmersion.domain.automation.workflows

import com.histmersion.api.{ApiError, BackupStatus, Goal, GoalPatch, GoalPut}
import com.histmersion.api.Lint.LintFixResult
import com.histmersion.domain.PlanGoal

/** What the engine asks of the rest of the server while the domains still keep it in the composition's handlers: the
  * composition root implements it over them (`server/agents/TransitionHost`), and each operation moves to the owning
  * service as its slice comes.
  */
trait AutomationHost:
  /** A goal by its slug, a renamed goal's old slug answering as the goal it was renamed to. */
  def goal(slug: String): Either[ApiError, Goal]

  /** A goal written as a person would (`PUT`), an old slug writing to the goal it was renamed to. */
  def putGoal(slug: String, in: GoalPut): Either[ApiError, Goal]

  /** A goal changed as a person would (`PATCH`). */
  def patchGoal(slug: String, patch: GoalPatch): Either[ApiError, Goal]

  /** A goal plan's works looked up by DOI (the library's metadata: title, year, authors, whether the corpus holds it,
    * whether it is open access or retracted).
    */
  def workLookup: PlanGoal.Lookup

  /** The values of a search field the corpus uses, the most used first (a plan's vocabulary). */
  def vocabulary(field: String): List[String]

  // ── the autopilot's housekeeping (util.data) ─────────────────────────────────────────────────

  /** Why the housekeeping may not run now (a maintenance operation holds the database), if it may not. */
  def housekeepingBlock: Option[String]

  /** Start a backup; its status at once. */
  def backup(): Either[ApiError, BackupStatus]

  /** The backup's status now. */
  def backupStatus: Either[ApiError, BackupStatus]

  /** The safe `lint --fix` repairs, under the maintenance lock. */
  def lintFix(): Either[ApiError, LintFixResult]
