package com.histmersion.domain.automation.workflows

import com.histmersion.api.{ApiError, BackupStatus, Goal, GoalPatch, GoalPut}
import com.histmersion.api.Agents.{Pipeline, ProbeAnswer, Role, RunRequest}
import com.histmersion.api.GoalPlans.LinkKind
import com.histmersion.api.Lint.LintFixResult
import com.histmersion.domain.PlanGoal

/** What the engine asks of the rest of the server (a port the engine owns): its goals, a run's target and options, a
  * link's reference and the probe's subject in the domains' terms, the plan's works and vocabulary, and the
  * housekeeping. The composition root implements it over its wiring (`server/agents/ServerAutomationHost`).
  */
trait AutomationHost:
  /** Is there an article with this slug? */
  def articleExists(slug: String): Boolean

  /** A goal by its slug, a renamed goal's old slug answering as the goal it was renamed to. */
  def goal(slug: String): Either[ApiError, Goal]

  /** A goal written as a person would (`PUT`), an old slug writing to the goal it was renamed to. */
  def putGoal(slug: String, in: GoalPut): Either[ApiError, Goal]

  /** A goal changed as a person would (`PATCH`). */
  def patchGoal(slug: String, patch: GoalPatch): Either[ApiError, Goal]

  // ── a run's request, checked by the workflow it names ─────────────────────────────────────────

  /** The target as pipeline `p` names it: an existing source or article, a goal — or, for compile-article, any valid
    * slug (a new article is created, an existing one extended). None: a target kind the engine resolves itself.
    */
  def runTarget(p: Pipeline, raw: String): Option[Either[ApiError, String]]

  /** The options pipeline `p` understands, checked before a run is queued (`roundsCap`: the review's round cap). */
  def runOptions(p: Pipeline, req: RunRequest, target: String, roundsCap: Int): Either[ApiError, Unit]

  /** The roles a run of `p` may give a model of its own: its own, and those of the run it chains. */
  def runRoles(p: Pipeline): List[Role]

  /** A source or an article a goal links, as its canonical key (a citekey, a slug). */
  def linkRef(kind: LinkKind, raw: String): Either[ApiError, String]

  /** What the probe asks a model about one source, and the answer it must give: (citekey, title, year). */
  def probeSubject(citekey: String): Either[ApiError, ProbeAnswer]

  /** Whether a model's title names the record's (after normalisation). */
  def sameTitle(answered: String, record: String): Boolean

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
