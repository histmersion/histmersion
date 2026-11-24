package com.histmersion
package domain.automation.workflows

import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Agents.{Pipeline, ProbeAnswer, Role, RunRequest}
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.api.checks.Lint.LintFixResult
import com.histmersion.domain.PlanGoal
import com.histmersion.api.Api
import com.histmersion.api.backup.Backup
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Title
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

/** What the engine asks of the rest of the server (a port the engine owns): its goals, a run's target and options, a
  * link's reference and the probe's subject in the domains' terms, the plan's works and vocabulary, and the
  * housekeeping. The composition root implements it over its wiring (`server/agents/ServerAutomationHost`).
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait AutomationHost:
  /** Is there an article with this slug? */
  def articleExists(slug: Slug): Boolean

  /** A goal by its slug, a renamed goal's old slug answering as the goal it was renamed to. */
  def goal(slug: domain.automation.Goal.Slug): Either[Api.Error, Goal]

  /** A goal written as a person would (`PUT`), an old slug writing to the goal it was renamed to. */
  def putGoal(slug: domain.automation.Goal.Slug, in: Goal.Put)(using Creator.New): Either[Api.Error, Goal]

  /** A goal changed as a person would (`PATCH`). */
  def patchGoal(slug: domain.automation.Goal.Slug, patch: Goal.Patch): Either[Api.Error, Goal]

  // ── a run's request, checked by the workflow it names ─────────────────────────────────────────

  /** The target as pipeline `p` names it: an existing source or article, a goal — or, for compile-article, any valid
    * slug (a new article is created, an existing one extended). None: a target kind the engine resolves itself.
    */
  def runTarget(
      p: Pipeline,
      raw: AutomationHost.RunTargetRaw
  ): Option[Either[Api.Error, AutomationHost.CanonicalKey]]

  /** The options pipeline `p` understands, checked before a run is queued (`roundsCap`: the review's round cap). */
  def runOptions(
      p: Pipeline,
      req: RunRequest,
      target: ManagerContext.ItemTarget,
      roundsCap: AutomationHost.RunOptionsRoundsCap
  ): Either[Api.Error, Unit]

  /** The roles a run of `p` may give a model of its own: its own, and those of the run it chains. */
  def runRoles(p: Pipeline): List[Role]

  /** A source or an article a goal links, as its canonical key (a citekey, a slug). */
  def linkRef(kind: LinkKind, raw: AutomationHost.LinkRefRaw): Either[Api.Error, AutomationHost.CanonicalKey]

  /** What the probe asks a model about one source, and the answer it must give: (citekey, title, year). */
  def probeSubject(citekey: Citekey): Either[Api.Error, ProbeAnswer]

  /** Whether a model's title names the record's (after normalisation). */
  def sameTitle(answered: Title, record: Title): Boolean

  /** A goal plan's works looked up by DOI (the library's metadata: title, year, authors, whether the corpus holds it,
    * whether it is open access or retracted).
    */
  def workLookup: PlanGoal.Lookup

  /** The values of a search field the corpus uses, the most used first (a plan's vocabulary). */
  def vocabulary(field: AutomationHost.VocabularyField): List[AutomationHost.Term]

  // ── the autopilot's housekeeping (util.data) ─────────────────────────────────────────────────

  /** Why the housekeeping may not run now (a maintenance operation holds the database), if it may not. */
  def housekeepingBlock: Option[AutomationHost.HousekeepingBlock]

  /** Start a backup; its status at once. */
  def backup(): Either[Api.Error, Backup.Status]

  /** The backup's status now. */
  def backupStatus: Either[Api.Error, Backup.Status]

  /** The safe `lint --fix` repairs, under the maintenance lock. */
  def lintFix(): Either[Api.Error, LintFixResult]

object AutomationHost:
  type CanonicalKey = CanonicalKey.T
  object CanonicalKey extends Newtype[String, Pure]
  type RunTargetRaw = RunTargetRaw.T
  object RunTargetRaw extends Newtype[String, Pure]
  type RunOptionsRoundsCap = RunOptionsRoundsCap.T
  object RunOptionsRoundsCap extends Newtype[Int, Pure]
  type LinkRefRaw = LinkRefRaw.T
  object LinkRefRaw extends Newtype[String, Pure]
  type Term = Term.T
  object Term extends Newtype[String, Pure]
  type VocabularyField = VocabularyField.T
  object VocabularyField extends Newtype[String, Pure]
  type HousekeepingBlock = HousekeepingBlock.T
  object HousekeepingBlock extends Newtype[String, Pure]
