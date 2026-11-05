package com.histmersion.domain.automation.workflows

import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Agents.{Pipeline, ProbeAnswer, Role, RunRequest}
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.api.checks.Lint.LintFixResult
import com.histmersion.domain.PlanGoal
import com.histmersion.api.Api
import com.histmersion.api.backup.Backup

/** What the engine asks of the rest of the server (a port the engine owns): its goals, a run's target and options, a
  * link's reference and the probe's subject in the domains' terms, the plan's works and vocabulary, and the
  * housekeeping. The composition root implements it over its wiring (`server/agents/ServerAutomationHost`).
  */
trait AutomationHost:
  /** Is there an article with this slug? */
  def articleExists(slug: String): Boolean

  /** A goal by its slug, a renamed goal's old slug answering as the goal it was renamed to. */
  def goal(slug: String): Either[Api.Error, Goal]

  /** A goal written as a person would (`PUT`), an old slug writing to the goal it was renamed to. */
  def putGoal(slug: String, in: Goal.Put): Either[Api.Error, Goal]

  /** A goal changed as a person would (`PATCH`). */
  def patchGoal(slug: String, patch: Goal.Patch): Either[Api.Error, Goal]

  // ── a run's request, checked by the workflow it names ─────────────────────────────────────────

  /** The target as pipeline `p` names it: an existing source or article, a goal — or, for compile-article, any valid
    * slug (a new article is created, an existing one extended). None: a target kind the engine resolves itself.
    */
  def runTarget(p: Pipeline, raw: String): Option[Either[Api.Error, String]]

  /** The options pipeline `p` understands, checked before a run is queued (`roundsCap`: the review's round cap). */
  def runOptions(p: Pipeline, req: RunRequest, target: String, roundsCap: Int): Either[Api.Error, Unit]

  /** The roles a run of `p` may give a model of its own: its own, and those of the run it chains. */
  def runRoles(p: Pipeline): List[Role]

  /** A source or an article a goal links, as its canonical key (a citekey, a slug). */
  def linkRef(kind: LinkKind, raw: String): Either[Api.Error, String]

  /** What the probe asks a model about one source, and the answer it must give: (citekey, title, year). */
  def probeSubject(citekey: String): Either[Api.Error, ProbeAnswer]

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
  def backup(): Either[Api.Error, Backup.Status]

  /** The backup's status now. */
  def backupStatus: Either[Api.Error, Backup.Status]

  /** The safe `lint --fix` repairs, under the maintenance lock. */
  def lintFix(): Either[Api.Error, LintFixResult]
