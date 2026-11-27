package com.histmersion
package web
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{Plural, Wording}
import com.histmersion.api.automation.Manager
import com.histmersion.api.automation.Managers.*
import com.raquo.laminar.api.L.*
import com.histmersion.api.WireEnumValues
import com.histmersion.web.ui.Choices
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.automation.{
  Agents,
  AgentScheduler,
  AgentSchedulerMarkdown,
  Autopilot,
  ManagersApi,
  ManagersMarkdown,
  Stalls
}
import com.histmersion.api.views.AutopilotStatus
import com.histmersion.api.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Paging
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Manager
import com.histmersion.api.research.Article
import com.histmersion.api.automation.AgentMode
import com.histmersion.api.automation.Activity
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.library.Facet
import com.histmersion.api.UiLabels
import com.histmersion.api.Offset
import com.histmersion.lint.{tolerated, LintRule}

/** The process managers (docs/research-process-managers.md §5): the managers window (each one's state — with what the
  * supervisor derived: starved, at capacity, idle — its items, runs in flight and last event) and one window per
  * manager: pause (with or without its runs) / resume / stop, the items in flight with their runs' progress, the items
  * with hold / release / priority / skip / re-open, the hint box, the live hints, the events and the settings. A
  * manager window polls while it has runs in flight (the runs window's schedule); every change is behind the write
  * gate. Also the article window's Review button.
  */
object ManagersView:
  type Badge = Badge.T
  object Badge extends Newtype[String, Pure]
  type ButtonLabel = ButtonLabel.T
  object ButtonLabel extends Newtype[String, Pure]
  type ConfigKey = ConfigKey.T
  object ConfigKey extends Newtype[String, Pure]
  type ConfigText = ConfigText.T
  object ConfigText extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type CountsText = CountsText.T
  object CountsText extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type DrainLine = DrainLine.T
  object DrainLine extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type EnqueuedNote = EnqueuedNote.T
  object EnqueuedNote extends Newtype[String, Pure]
  type FocusHead = FocusHead.T
  object FocusHead extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type FocusPace = FocusPace.T
  object FocusPace extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type GoalLine = GoalLine.T
  object GoalLine extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Hint = Hint.T
  object Hint extends Newtype[String, Pure]
  type ItemAction = ItemAction.T
  object ItemAction extends Newtype[String, Pure]
  type JobTypeMeaning = JobTypeMeaning.T
  object JobTypeMeaning extends Newtype[String, Pure]
  type ManagerMeaning = ManagerMeaning.T
  object ManagerMeaning extends Newtype[String, Pure]
  type SchedulerHead = SchedulerHead.T
  object SchedulerHead extends Newtype[String, Pure]
  type Stages = Stages.T
  object Stages extends Newtype[String, Pure]
  type StatusMeaning = StatusMeaning.T
  object StatusMeaning extends Newtype[String, Pure]
  type StrategyLabel = StrategyLabel.T
  object StrategyLabel extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type WaitLabel = WaitLabel.T
  object WaitLabel extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type WaitMeaning = WaitMeaning.T
  object WaitMeaning extends Newtype[String, Pure]
  type WaitingSummary = WaitingSummary.T
  object WaitingSummary extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type WriteLabel = WriteLabel.T
  object WriteLabel extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  /** Items per page, events per page. */
  val ItemLimit = Paging.Limit(25)
  val EventLimit = Paging.Limit(30)

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The badge of a manager: its person-set state, or — active — what the supervisor derived. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def badge(m: ManagerInfo): Badge =
    if m.state != State.Active.wire then Badge(m.state.value)
    else
      m.statusNote.map(_.is) match
        case Some(Manager.Status.Kind.Starved)    => Badge("starved")
        case Some(Manager.Status.Kind.Budget)     => Badge("budget")
        case Some(Manager.Status.Kind.AtCapacity) => Badge("at capacity")
        case Some(Manager.Status.Kind.Idle)       => Badge("idle")
        case Some(Manager.Status.Kind.Queued)     => Badge("queued")
        case _                                    => Badge("active")

  /** What a manager does, from its id (`review`, `enrich`, `seed:<goal>`, `watch:<goal>`); a tooltip on the runs
    * window's manager menu.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def managerMeaning(id: Managers.ManagerInfo.Id): Option[ManagerMeaning] =
    val goal = id.value.dropWhile(_ != ':').drop(1)
    Kind.parse(Kind.Wire(id.value.takeWhile(_ != ':'))).map {
      case Kind.Review =>
        ManagerMeaning("The review manager: reviews the library's articles until their reviews converge.")
      case Kind.Enrich =>
        ManagerMeaning("The enrich manager: enriches the library's sources until each one is enriched.")
      case Kind.Seed =>
        ManagerMeaning(
          s"Seeds the research area $goal: plans it, proposes its new articles and, once approved, compiles them."
        )
      case Kind.Watch =>
        ManagerMeaning(s"Watches the research area $goal: an acquisition sweep and a currency check on its schedule.")
    }

  /** What each status of a manager's item means; a tooltip on the items' filter buttons. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def itemStatusMeaning(s: ItemStatus): StatusMeaning = s match
    case ItemStatus.Pending => StatusMeaning("Waiting to start; the scheduler picks the order.")
    case ItemStatus.Held    =>
      StatusMeaning("Held: it does not start until released, its approval is decided, or its hold ends.")
    case ItemStatus.Active  => StatusMeaning("A run is working on it now.")
    case ItemStatus.Done    => StatusMeaning("Settled: its runs got it where the manager wanted it.")
    case ItemStatus.Stopped =>
      StatusMeaning("Settled without being done: skipped, or the manager gave up on it. Re-open brings it back.")
    case ItemStatus.Failed    => StatusMeaning("Settled because its run failed. Re-open brings it back.")
    case ItemStatus.Cancelled =>
      StatusMeaning("Settled because the work is moot: its target is gone, or its sweep was removed.")

  /** What a person can do to this item: by its status — but an item the autopilot's focus holds is not released by hand
    * (pin its goal instead).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def itemActionsFor(i: ManagerItem): List[ItemAction] =
    if i.hold.exists(_.focus.isDefined) then itemActions(i.status).filterNot(_ == ItemAction("release"))
    else itemActions(i.status)

  /** What a person can do to an item in this status. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def itemActions(status: api.automation.ItemStatus.Wire): List[ItemAction] =
    ItemStatus.parse(status) match
      case Some(ItemStatus.Pending) => List(ItemAction("hold"), ItemAction("priority"), ItemAction("skip"))
      case Some(ItemStatus.Held)    => List(ItemAction("release"), ItemAction("priority"), ItemAction("skip"))
      case Some(ItemStatus.Active)  => List(ItemAction("priority"), ItemAction("skip"))
      case Some(s) if s.isFinal     => List(ItemAction("reopen"))
      case _                        => Nil

  /** A whole number the person typed, or nothing. */
  private def whole(s: String): Option[Int] = s.trim.toIntOption.filter(_ >= 0)

  private def list(s: String, sep: String): Option[List[String]] =
    Option(s.split(sep).toList.map(_.trim).filter(_.nonEmpty)).filter(_.nonEmpty)

  /** The settings form → a config (blank fields are the server's defaults). Left = what does not parse. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def config(f: Map[ConfigKey, ConfigText]): Either[ConfigText, ManagerConfig] =
    def g(k: String): String = f.getOrElse(ConfigKey(k), ConfigText("")).value.trim
    val bad = List(
      "maxRuns",
      "maxOpen",
      "rounds",
      "maxRunsPerArticle",
      "noProgressRuns",
      "reopenLimit",
      "cooldownMinutes",
      "lowWater",
      "refillBatch",
      "maxFailures",
      "staleAfterDays",
      "minSources",
      "maxArticles",
      "autoIngestPerDay",
      "extendMinNew"
    )
      .filter(k => g(k).nonEmpty && whole(g(k)).isEmpty)
    def onOff(k: String, what: String): Either[String, Option[Boolean]] = g(k).toLowerCase match
      case ""                     => Right(None)
      case "true" | "on" | "yes"  => Right(Some(true))
      case "false" | "off" | "no" => Right(Some(false))
      case other                  => Left(s"$what: '$other' is not on or off")
    val sweeps = list(g("sweeps"), ",").map(ws => ws.map(w => Sweep.parse(Sweep.Wire(w)).toRight(w)))
    val infer = for
      i <- onOff("inferFilter", "infer filter")
      ai <- onOff("autoIngest", "ingest legal open access")
      aa <- onOff("autoApprove", "decide its approvals by code checks")
      _ <- sweeps.toList.flatten
        .collectFirst { case Left(w) => w }
        .fold[Either[String, Unit]](Right(()))(w => Left(s"sweeps: '$w' is not acquisition or currency"))
    yield (i, ai, aa)
    val usd = g("budgetUsd")
    val daily = g("dailyUsd")
    val models =
      list(g("models"), ";").getOrElse(Nil).map(w => Agents.ModelOverride.parse(Agents.ModelOverride.Wire(w)))
    val result: Either[String, ManagerConfig] =
      if bad.nonEmpty then Left(s"not a whole number: ${bad.mkString(", ")}")
      else if usd.nonEmpty && usd.toDoubleOption.forall(_ <= 0) then Left("budget USD is not a positive number")
      else if daily.nonEmpty && daily.toDoubleOption.forall(_ < 0) then
        Left("daily cap USD is not a number (0 = no cap)")
      else
        infer.flatMap((inf, ai, aa) =>
          models.collectFirst { case Left(e) => e } match
            case Some(e) => Left(s"models: ${e.sentence}")
            case None    =>
              Right(
                ManagerConfig(
                  whole(g("maxRuns")).map(ManagerConfig.MaxRuns(_)),
                  whole(g("maxOpen")).map(ManagerConfig.MaxOpen(_)),
                  whole(g("rounds")).map(ManagerConfig.Rounds(_)),
                  whole(g("maxRunsPerArticle")).map(ManagerConfig.MaxRunsPerArticle(_)),
                  whole(g("noProgressRuns")).map(ManagerConfig.NoProgressRuns(_)),
                  whole(g("reopenLimit")).map(ManagerConfig.ReopenLimit(_)),
                  whole(g("cooldownMinutes")).map(ManagerConfig.CooldownMinutes(_)),
                  whole(g("lowWater")).map(ManagerConfig.LowWater(_)),
                  whole(g("refillBatch")).map(ManagerConfig.RefillBatch(_)),
                  whole(g("maxFailures")).map(ManagerConfig.MaxFailures(_)),
                  usd.toDoubleOption.map(ManagerConfig.BudgetUsd(_)),
                  list(g("includeTags"), ",").map(_.map(ManagerConfig.IncludeTags(_))),
                  Option(g("includeGoal")).filter(_.nonEmpty).map(ManagerConfig.IncludeGoal(_)),
                  Option(models.collect { case Right(m) => m }).filter(_.nonEmpty),
                  whole(g("staleAfterDays")).map(ManagerConfig.StaleAfterDays(_)),
                  inf.map(ManagerConfig.InferFilter(_)),
                  whole(g("minSources")).map(ManagerConfig.MinSources(_)),
                  whole(g("maxArticles")).map(ManagerConfig.MaxArticles(_)),
                  sweeps.map(_.collect { case Right(s) => s.wire }.distinct),
                  ai.map(ManagerConfig.AutoIngest(_)),
                  whole(g("autoIngestPerDay")).map(ManagerConfig.AutoIngestPerDay(_)),
                  daily.toDoubleOption.map(ManagerConfig.DailyUsd(_)),
                  aa.map(ManagerConfig.AutoApprove(_)),
                  whole(g("extendMinNew")).map(ManagerConfig.ExtendMinNew(_))
                )
              )
        )
    result.left.map(ConfigText(_))

  /** A config as the form shows it (blank = the default). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fields(c: ManagerConfig): Map[ConfigKey, ConfigText] =
    List(
      "maxRuns" -> c.maxRuns.fold("")(_.toString),
      "maxOpen" -> c.maxOpen.fold("")(_.toString),
      "rounds" -> c.rounds.fold("")(_.toString),
      "maxRunsPerArticle" -> c.maxRunsPerArticle.fold("")(_.toString),
      "noProgressRuns" -> c.noProgressRuns.fold("")(_.toString),
      "reopenLimit" -> c.reopenLimit.fold("")(_.toString),
      "cooldownMinutes" -> c.cooldownMinutes.fold("")(_.toString),
      "lowWater" -> c.lowWater.fold("")(_.toString),
      "refillBatch" -> c.refillBatch.fold("")(_.toString),
      "maxFailures" -> c.maxFailures.fold("")(_.toString),
      "staleAfterDays" -> c.staleAfterDays.fold("")(_.toString),
      "minSources" -> c.minSources.fold("")(_.toString),
      "maxArticles" -> c.maxArticles.fold("")(_.toString),
      "autoIngestPerDay" -> c.autoIngestPerDay.fold("")(_.toString),
      "extendMinNew" -> c.extendMinNew.fold("")(_.toString),
      "inferFilter" -> c.inferFilter.fold("")(b => if b.value then "on" else "off"),
      "autoApprove" -> c.autoApprove.fold("")(b => if b.value then "on" else "off"),
      "autoIngest" -> c.autoIngest.fold("")(b => if b.value then "on" else "off"),
      "sweeps" -> c.sweeps.fold("")(_.mkString(", ")),
      "budgetUsd" -> c.budgetUsd.fold("")(_.toString),
      "dailyUsd" -> c.dailyUsd.fold("")(_.toString),
      "includeTags" -> c.includeTags.getOrElse(Nil).mkString(", "),
      "includeGoal" -> c.includeGoal.fold("")(_.value),
      "models" -> c.models.getOrElse(Nil).map(_.text).mkString("; ")
    ).map((k, v) => ConfigKey(k) -> ConfigText(v)).toMap

  /** The settings the form offers, with their labels. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val Settings: List[(ConfigKey, ConfigText)] = List(
    "maxRuns" -> "runs at once",
    "maxOpen" -> "open runs (incl. paused)",
    "rounds" -> "rounds per run",
    "maxRunsPerArticle" -> "runs per article",
    "noProgressRuns" -> "no-progress window (runs)",
    "reopenLimit" -> "reopen limit",
    "cooldownMinutes" -> "cooldown (minutes)",
    "lowWater" -> "refill below",
    "refillBatch" -> "refill batch",
    "maxFailures" -> "failures before stop",
    "budgetUsd" -> "budget per run (USD)",
    "dailyUsd" -> "daily cap (USD of metered spend; plan-billed runs count 0; 0 = none)",
    "includeTags" -> "only articles tagged (comma-separated)",
    "includeGoal" -> "only a goal's articles",
    "models" -> "models (role=provider:model; …)",
    "staleAfterDays" -> "review again after (days; 0 never)",
    "inferFilter" -> "infer a missing filter first (on/off)",
    "minSources" -> "seed: sources per article at least",
    "maxArticles" -> "seed: articles per plan at most",
    "sweeps" -> "watch: sweeps (acquisition, currency)",
    "autoIngest" -> "watch: ingest legal open-access finds (on/off; legal open access only)",
    "autoIngestPerDay" -> "watch: ingests a day at most",
    "autoApprove" -> "decide its runs' plans and drafts by code checks (on/off; blank = agents.approvals)",
    "extendMinNew" -> "extend an article at this many new sources (0 never)"
  ).map((k, label) => ConfigKey(k) -> ConfigText(label))

  /** What a manager's settings form offers: every setting, but a watcher's only to a watcher (and the review settings
    * not to one).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def settingsFor(kind: Manager.Kind.Wire): List[(ConfigKey, ConfigText)] =
    val watchOnly = Set("sweeps", "autoIngest", "autoIngestPerDay")
    val reviewOnly = Set(
      "rounds",
      "maxRunsPerArticle",
      "noProgressRuns",
      "reopenLimit",
      "lowWater",
      "refillBatch",
      "includeTags",
      "includeGoal",
      "staleAfterDays",
      "inferFilter",
      "extendMinNew"
    )
    if kind == Kind.Watch.wire then Settings.filterNot((k, _) => reviewOnly(k.value))
    else Settings.filterNot((k, _) => watchOnly(k.value))

  /** A watcher's sweeps as its events tell them: each settled sweep and each work it ingested, newest first. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sweepEvents(es: List[ManagerEvent]): List[ManagerEvent] =
    es.filter(e => e.kind.value == Feed.Swept || e.kind.value == Feed.Ingested)

  /** The hint box → a hint. `action` none/prioritise/hold/skip; `targets` comma-separated slugs; `suggest`
    * = the server's model proposes the action (none chosen here; a person approves what it proposes).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def hint(
      text: Managers.HintAdd.Text,
      item: Managers.HintAdd.Item,
      once: Managers.HintAdd.Once,
      action: Managers.HintEffect.Action,
      targets: Managers.HintEffect.Targets,
      tag: Managers.HintEffect.Tag,
      until: Managers.HintEffect.Until,
      untilSource: Managers.HintEffect.UntilSource,
      suggest: Managers.HintAdd.Suggest = Managers.HintAdd.Suggest(false)
  ): Either[Managers.HintAdd.Text, HintAdd] =
    val t = text.value.trim
    val none = HintEffect.Action.none(action)
    val itemText = Option(item.value.trim).filter(_.nonEmpty).map(HintAdd.Item(_))
    if t.isEmpty then Left(Managers.HintAdd.Text("a hint needs text"))
    else if suggest.value && !none then
      Left(Managers.HintAdd.Text("choose an action, or let the model suggest one — not both"))
    else if suggest.value then Right(HintAdd(HintAdd.Text(t), itemText, Some(once), suggest = Some(suggest)))
    else if none then Right(HintAdd(HintAdd.Text(t), itemText, Some(once)))
    else
      HintEffect.Action.verb(action).filter(HintEffect.Verb.Actions.contains) match
        case None       => Left(Managers.HintAdd.Text(s"unknown action $action"))
        case Some(verb) =>
          val e = HintEffect(
            verb.word,
            list(targets.value, ",").getOrElse(Nil).map(HintEffect.Targets(_)),
            Option(tag.value.trim).filter(_.nonEmpty).map(HintEffect.Tag(_)),
            None,
            None,
            Option(until.value.trim).filter(_.nonEmpty).map(HintEffect.Until(_)),
            Option(untilSource.value.trim).filter(_.nonEmpty).map(HintEffect.UntilSource(_))
          )
          if verb == HintEffect.Verb.Hold && e.until.isEmpty && e.untilSource.isEmpty then
            Left(Managers.HintAdd.Text("hold: give a date or a source"))
          else if e.targets.isEmpty && e.tag.isEmpty && item.value.trim.isEmpty then
            Left(Managers.HintAdd.Text(s"${verb.word}: name the articles or a tag"))
          else Right(HintAdd(HintAdd.Text(t), itemText, Some(once), Some(e)))

  // ── the page buttons: Review, Extend, Enrich ────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def counted(n: Int, noun: String): String =
    Plural.count(Plural.CountN(n.toLong), Plural.CountSingular(noun)).value

  /** What an enqueue did, in one line: the items queued (and how many were skipped as enriched). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def enqueuedNote(kind: EnqueueKind, r: EnqueueResult): EnqueuedNote =
    val what = kind match
      case EnqueueKind.Review => "queued for review"
      case EnqueueKind.Extend => "queued to be extended, then reviewed"
      case EnqueueKind.Enrich => "queued for enrichment"
      case EnqueueKind.Seed   => "queued: its seeding manager proposes the next articles"
    val skipped = if r.skipped.isEmpty then "" else s"; ${counted(r.skipped.size, "source")} enriched already"
    val line = r.items match
      case Nil if kind == EnqueueKind.Enrich => s"nothing to enrich$skipped"
      case Nil                               => "nothing queued"
      case List(i)                           => s"$what (item #${i.id}, ${i.status})$skipped"
      case is                                => s"${counted(is.size, "item")} $what$skipped"
    EnqueuedNote(line)

  /** Buttons that hand a target to a manager (only where the server runs agents), with the line saying what was queued
    * and a link to the manager.
    */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def enqueueButtons(
      target: Managers.EnqueueRequest.Target,
      buttons: List[(EnqueueKind, Icon, ButtonLabel, Hint)]
  ): HtmlElement =
    val note = Var(Option.empty[(String, String)])
    def go(kind: EnqueueKind): Unit =
      ApiClient
        .enqueue(kind, target)
        .onComplete(r =>
          note.set(
            Some(
              r.fold(e => (s"not queued: ${e.getMessage}", ""), x => (enqueuedNote(kind, x).value, x.managerId.value))
            )
          )
        )
    div(
      cls := "wm-actions",
      child.maybe <-- RunsView.enabled.signal.map(on =>
        Option.when(on.enabled)(
          div(
            cls := "btn-group",
            buttons.map((kind, icon, label, tip) =>
              WriteButton(
                icon,
                label.value,
                tip.value,
                () => go(kind)
              )
            )
          )
        )
      ),
      child.maybe <-- note.signal.map(
        _.map((n, id) =>
          span(
            cls := "muted wm-note",
            n,
            Option.when(id.nonEmpty)(WinLink(WindowKey.Manager(WindowKey.Manager.Id(id)), " open the manager"))
          )
        )
      )
    )

  /** The article window: Review (the review manager, first in line), Extend (compile the new sources of its filter into
    * it, then review it) and Enrich (the sources it cites that are not enriched yet).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def articleActions(slug: Article.Slug): HtmlElement =
    enqueueButtons(
      EnqueueRequest.Target(slug.value),
      List(
        (
          EnqueueKind.Review,
          Icon.Managers,
          ButtonLabel("review"),
          Hint("queue this article for the review manager (first in line)")
        ),
        (
          EnqueueKind.Extend,
          Icon.Edit,
          ButtonLabel("extend"),
          Hint("compile the new sources of its filter into this article, then review it")
        ),
        (
          EnqueueKind.Enrich,
          Icon.Runs,
          ButtonLabel("enrich"),
          Hint("enrich the sources this article cites that are not enriched yet")
        )
      )
    )

  /** The goal window: Extend (its seeding manager proposes its next articles — a person approves the plan) and Enrich
    * (the sources linked to it that are not enriched yet).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def goalActions(slug: Goal.Slug): HtmlElement =
    enqueueButtons(
      EnqueueRequest.Target(slug.value),
      List(
        (
          EnqueueKind.Seed,
          Icon.Edit,
          ButtonLabel("extend"),
          Hint("propose the next articles of this research area (you approve the plan)")
        ),
        (
          EnqueueKind.Enrich,
          Icon.Runs,
          ButtonLabel("enrich"),
          Hint("enrich the sources linked to this goal that are not enriched yet")
        )
      )
    )

  // ── the autopilot ───────────────────────────────────────────────────────────────────────────────

  /** One goal's line in the autopilot's table: its counts and its pace. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def goalLine(g: Autopilot.GoalProgress): GoalLine =
    GoalLine(
      s"${counted(g.sources.value, "source")} · ${counted(g.articles.value, "article")}" +
        (if g.openApprovals.value > 0 then s" · ${counted(g.openApprovals.value, "open approval")}" else "") +
        s" · ${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s" (~$d d)")
    )

  /** An active or queued goal's pace, in a line. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def focusPace(g: Autopilot.FocusGoal): FocusPace =
    FocusPace(s"${g.doneLast7} done in 7 days, ${g.open} open" + g.etaDays.fold("")(d => s" (~$d d)"))

  /** The goals that cannot use a focus slot now, by why, the commonest first. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def waitingSummary(ws: List[Autopilot.FocusWaiting]): WaitingSummary =
    WaitingSummary(
      ws.groupBy(_.code).toList.sortBy((c, xs) => (-xs.size, c.value)).map((c, xs) => s"${xs.size} $c").mkString(" · ")
    )

  /** The focus's heading: the cap and where it comes from, or why it is not in force. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def focusHead(f: Autopilot.FocusInfo): FocusHead =
    FocusHead(
      (if f.on.value then s"Focus: at most ${f.max} active goals" else "Focus: not in force") +
        (if f.maxFrom.value == "person" then " (cap set by a person)" else "") +
        (if f.held.value > 0 then s" · ${counted(f.held.value, "item")} held" else "")
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def errorLine(message: Signal[Option[String]]): Modifier[HtmlElement] =
    ErrorLine(message.map(_.map((text => Problem.Unclassified(Legacy.Text(text))))))

  /** The focus: the active goals (why each was chosen, what it does or waits for, its pace), the queue in the order the
    * next become active, and why the others cannot use a slot; pin / unpin a goal and set the cap (each behind the
    * write gate).
    */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def focusPanel(
      f: Autopilot.FocusInfo,
      changed: Autopilot.FocusInfo => Unit,
      failure: Var[Option[String]]
  ): HtmlElement =
    val cap = Var(f.max.toString)
    def go(what: WriteLabel)(call: => scala.concurrent.Future[Autopilot.FocusInfo]): Unit =
      if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
      else
        call.onComplete(
          _.fold(e => failure.set(Some(shown"focus $what: ${e.getMessage}")), x => { failure.set(None); changed(x) })
        )
    def pinButton(g: Autopilot.FocusGoal) =
      val (verb, tip) = if g.pinned.value then ("unpin", "Let the focus choose again")
      else ("pin", "Keep this goal active (it takes a slot now)")
      button(
        typ := "button",
        cls := "btn btn--sm",
        Wording.capitalize(verb),
        title := tip,
        onClick --> (_ =>
          go(WriteLabel(shown"$verb ${g.slug}"))(
            ApiClient.setFocus(Autopilot.FocusSet.Action(verb), Some(Goal.Slug(g.slug.value)))
          )
        )
      )
    def goalLink(slug: Autopilot.FocusGoal.Slug) = WinLink(WindowKey.Goal(WindowKey.Goal.Slug(slug.value)), slug.value)
    div(
      cls := "autopilot-focus",
      div(
        cls := "toolbar",
        div(cls := "btn-group", span(cls := "autopilot-state", focusHead(f))),
        div(
          cls := "btn-group",
          input(
            typ := "number",
            cls := "field manager-prio",
            aria.label := "the cap of active goals",
            minAttr := "0",
            controlled(value <-- cap.signal, onInput.mapToValue --> cap.writer)
          ),
          button(
            typ := "button",
            cls := "btn btn--sm",
            "Set cap",
            title := "At most this many goals active for model work (0 = no cap)",
            onClick --> (_ =>
              whole(cap.now()).foreach(n =>
                go(WriteLabel("cap"))(
                  ApiClient.setFocus(Autopilot.FocusSet.Action("max"), value = Some(Autopilot.FocusSet.Value(n)))
                )
              )
            )
          ),
          Option.when(f.maxFrom.value == "person")(
            button(
              typ := "button",
              cls := "btn btn--sm",
              "default",
              title := "back to agents.autopilot.max-active-goals",
              onClick --> (_ => go(WriteLabel("cap"))(ApiClient.setFocus(Autopilot.FocusSet.Action("max"))))
            )
          )
        )
      ),
      f.note.map(n => div(cls := "muted", n)),
      Option.when(f.changes.nonEmpty)(div(cls := "form-ok", f.changes.mkString("; ").take(400))),
      ul(
        cls := "job-list focus-active",
        f.active.map(g =>
          li(
            cls := "job-row",
            GoalsView.badge(GoalsView.BadgeStatus(g.status.value), GoalsView.BadgeText(g.stage.value)),
            div(
              cls := "job-main",
              div(
                cls := "job-subject",
                goalLink(g.slug),
                Option.when(g.pinned.value)(span(cls := "muted", " · pinned")),
                span(cls := "muted", shown" · ${g.title}")
              ),
              div(cls := "job-progress", shown"${g.why}"),
              g.doing.map(d => div(cls := "muted", s"now: $d")),
              div(cls := "job-meta muted", focusPace(g)),
              child.maybe <-- Auth.writable.map(w => Option.when(w)(div(cls := "form-row", pinButton(g))))
            )
          )
        )
      ),
      Option.when(f.queue.nonEmpty)(
        div(
          div(cls := "muted focus-heading", s"next (${f.queued} waiting for a slot):"),
          ol(
            cls := "job-list focus-queue",
            f.queue.map(g =>
              li(
                cls := "job-row",
                GoalsView.badge(GoalsView.BadgeStatus(g.status.value), GoalsView.BadgeText(g.stage.value)),
                div(
                  cls := "job-main",
                  div(cls := "job-subject", goalLink(g.slug)),
                  div(cls := "muted", shown"${g.why}"),
                  child.maybe <-- Auth.writable.map(w => Option.when(w)(div(cls := "form-row", pinButton(g))))
                )
              )
            )
          )
        )
      ),
      Option.when(f.waiting.nonEmpty)(
        div(cls := "muted focus-heading", shown"cannot use a slot now: ${waitingSummary(f.waiting)}")
      )
    )

  /** The drain in a line: draining (runs still finishing) or drained (safe to stop the server). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def drainLine(d: Autopilot.DrainInfo): DrainLine =
    val line =
      if d.drained.value then
        s"Paused since ${d.drainedAt.getOrElse(d.since)}: nothing runs, nothing new starts — safe to stop the server"
      else
        s"Finishing current work, then pausing (since ${d.since}${
            if d.by.value.nonEmpty then s", by ${d.by}" else ""
          }): nothing new starts; " +
          s"${counted(d.inFlight.value, "run")} in flight still finishing"
    DrainLine(line)

  /** The goals by stage, in the order a goal moves through them: how many in each. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stages(gs: List[Autopilot.GoalProgress]): Stages =
    Stages(
      Goal.Status.all
        .map(st => st -> gs.count(_.status.value == st.value))
        .filter(_._2 > 0)
        .map((st, n) => s"$n $st")
        .mkString(" · ")
    )

  /** The switch and the status: on/off (the click shows the new state at once; the server's answer follows), the
    * approval rules, today's ingests, and every goal's stage, counts and pace.
    */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def autopilotSection: HtmlElement =
    val st = Var(Option.empty[AutopilotStatus])
    val pending = Var(Option.empty[Boolean])
    val failure = Var(Option.empty[String])
    def load(): Unit = ApiClient.autopilot.onComplete(
      _.fold(e => failure.set(Some(e.getMessage)), s => { failure.set(None); st.set(Some(s)) })
    )
    def drain(on: Autopilot.DrainSet.On): Unit =
      ApiClient
        .drain(on)
        .onComplete(
          _.fold(e => failure.set(Some(s"drain: ${e.getMessage}")), s => { failure.set(None); st.set(Some(s)) })
        )
    def flip(on: Autopilot.AutopilotSet.On): Unit =
      pending.set(Some(on.value))
      ApiClient.setAutopilot(on).onComplete { r =>
        pending.set(None)
        r.fold(e => failure.set(Some(s"autopilot: ${e.getMessage}")), s => { failure.set(None); st.set(Some(s)) })
      }
    val shownOn = st.signal.combineWith(pending.signal).map((s, p) => p.orElse(s.map(_.on.value)).getOrElse(false))
    div(
      cls := "autopilot",
      onMountCallback(_ => load()),
      div(
        cls := "toolbar",
        div(
          cls := "btn-group",
          span(
            cls := "autopilot-state",
            cls("is-on") <-- shownOn,
            child.text <-- shownOn
              .combineWith(st.signal.map(_.flatMap(_.drain).isDefined))
              .map((on, draining) =>
                Wording.agentsState(
                  AgentMode.Step.Autopilot.On(on),
                  AgentMode.Step.Drain.On(draining)
                )
              )
          ),
          child <-- shownOn.map(on =>
            if on then
              WriteButton(
                Icon.Managers,
                "Pause",
                "Pause the seeding managers and watchers the autopilot runs",
                () => flip(Autopilot.AutopilotSet.On(false))
              )
            else
              WriteButton(
                Icon.Managers,
                "Start working",
                "every research goal gets an active seeding manager and watcher and moves from planned to reviewed by itself",
                () => flip(Autopilot.AutopilotSet.On(true))
              )
          )
        ),
        div(
          cls := "btn-group",
          child <-- st.signal.map(_.flatMap(_.drain)).map {
            case Some(_) =>
              WriteButton(
                Icon.Managers,
                Wording.Undrain,
                "The scheduler admits work again (undrain)",
                () => drain(Autopilot.DrainSet.On(false))
              )
            case None =>
              WriteButton(
                Icon.Managers,
                Wording.Drain,
                "Before a deploy: nothing new starts while the runs in flight finish; no manager is paused (drain)",
                () => drain(Autopilot.DrainSet.On(true))
              )
          }
        )
      ),
      child.maybe <-- st.signal.map(_.flatMap(_.drain).map(d => div(cls := "form-ok", drainLine(d)))),
      errorLine(failure.signal),
      child.maybe <-- st.signal.map(_.map { s =>
        div(
          cls := "autopilot-status",
          div(
            cls := "muted",
            s.since.fold("")(t => s"since $t") + s.by.fold("")(b => s" by $b") +
              s" · ${stages(s.goals)} · ingests today ${s.ingestedToday}${
                  if s.ingestCap.value > 0 then s" of ${s.ingestCap}" else ""
                }" +
              s" · at most ${s.maxConcurrentRuns} runs at once"
          ),
          Option.when(s.backlog.value > 0)(
            div(
              cls := "muted",
              ManagersMarkdown.backlog(
                Managers.ManagerInfo.Backlog(s.backlog.value),
                s.backlogDays.map(d => Managers.ManagerInfo.BacklogDays(d.value))
              )
            )
          ),
          div(
            cls := "muted",
            "Approvals: " + s.approvals
              .map(r => s"${ApprovalsView.kindLabel(r.kind).toLowerCase}: ${r.mode}")
              .mkString(" · ")
          ),
          Option.when(!s.cronsEnabled.value)(p(cls := "form-error", role := "alert", Autopilot.EngineStatus.CronsOff)),
          Option.when(s.changes.nonEmpty)(div(cls := "form-ok", s.changes.mkString("; ").take(400))),
          s.focus.map(f => focusPanel(f, x => st.update(_.map(_.modify(_.focus).setTo(Some(x)))), failure)),
          ul(
            cls := "job-list autopilot-goals",
            s.goals.map(g =>
              li(
                cls := "job-row",
                GoalsView.badge(GoalsView.BadgeStatus(g.status.value)),
                div(
                  cls := "job-main",
                  div(
                    cls := "job-subject",
                    WinLink(
                      WindowKey.Goal(WindowKey.Goal.Slug(g.slug.value)),
                      title := g.slug,
                      Option(g.title.value)
                        .filter(_.trim.nonEmpty)
                        .getOrElse(Wording.humanize(Wording.Id(g.slug.value)))
                    )
                  ),
                  div(cls := "job-meta muted", goalLine(g)),
                  Option.when(g.managers.nonEmpty)(
                    div(
                      cls := "muted",
                      g.managers
                        .map(m => s"${Wording.manager(Wording.Id(m.id.value)).takeWhile(_ != '·').trim} ${m.state}")
                        .mkString(" · ")
                    )
                  )
                )
              )
            )
          )
        )
      })
    )

  // ── the central scheduler ───────────────────────────────────────────────────────────────────────

  /** The waiting items a person's filters leave: any of the job types, any of the reasons, the target text. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def visibleWaiting(
      ws: List[AgentScheduler.Waiting],
      types: List[AgentScheduler.Waiting.JobType],
      codes: List[AgentScheduler.Waiting.Code],
      subject: Option[Activity.Subject]
  ): List[AgentScheduler.Waiting] =
    ws.filter(w =>
      (types.isEmpty || types.contains(w.jobType)) && (codes.isEmpty || codes.contains(w.code)) &&
        subject.forall(t =>
          (w.target.value + " " + w.goal.fold("")(_.value) + " " + w.manager.value).toLowerCase
            .contains(t.value.trim.toLowerCase)
        )
    )

  /** What each job type of the scheduler's queue is; a tooltip on its filter chip. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def jobTypeMeaning(jobType: JobKinds.Wire): Option[JobTypeMeaning] = jobType.value match
    case "goal-plan" =>
      Some(JobTypeMeaning("Planning a research goal: its sub-goals, search queries and works (plan-goal runs)."))
    case "propose" =>
      Some(JobTypeMeaning("Proposing the articles a research area should have (propose-articles runs)."))
    case "sweep" =>
      Some(
        JobTypeMeaning(
          "Acquisition sweeps: discovering new works for a goal and ranking them (acquisition-sweep runs)."
        )
      )
    case "currency" =>
      Some(
        JobTypeMeaning("Currency checks: retractions and newer critiques of a goal's sources (currency-check runs).")
      )
    case "enrich" =>
      Some(JobTypeMeaning("Enriching a source: summary, findings, dates, places, status (enrich-source runs)."))
    case "compile" => Some(JobTypeMeaning("Compiling a new article from the corpus (compile-article runs)."))
    case "extend"  =>
      Some(JobTypeMeaning("Appending a section to an article from its new sources (compile-article runs)."))
    case "filter" => Some(JobTypeMeaning("Inferring an article's coverage filter (infer-filter runs)."))
    case "review" => Some(JobTypeMeaning("Reviewing articles and revising them (review-article runs)."))
    case _        => None

  /** Why an item waits, by its code (the queue's, and an item's own); a tooltip on the "why" chips. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def waitMeaning(code: AgentScheduler.Waiting.Code): Option[WaitMeaning] = code.value match
    case "capacity" =>
      Some(
        WaitMeaning(
          "No place free: the most runs at once are running. It starts when one finishes, in the order shown."
        )
      )
    case "pool" =>
      Some(WaitMeaning("The subscription plan its models draw on already carries as many runs as it takes."))
    case "manager-cap" =>
      Some(WaitMeaning("Its manager already has as many runs in flight as its own runs-at-once setting allows."))
    case "blocked" =>
      Some(
        WaitMeaning(
          "Cannot start yet; its row says why (a spending cap, a model's allowance, or work that must come first)."
        )
      )
    case "busy" =>
      Some(
        WaitMeaning(
          "Could not start in the last pass (another run is writing its target, say); the next pass tries again."
        )
      )
    case "budget" => Some(WaitMeaning("Nothing starts: today's or this month's agent spending cap is reached."))
    case "drain"  =>
      Some(WaitMeaning("Nothing new starts: draining, so the runs in flight finish first, until a person lifts it."))
    case AgentScheduler.Code.Person => Some(WaitMeaning("Waits for a person to decide an approval; it holds no place."))
    case AgentScheduler.Code.Held   => Some(WaitMeaning("Held by a person; it holds no place."))
    case AgentScheduler.Code.NotDue => Some(WaitMeaning("Not due yet: a cooldown, or a failed run's backoff."))
    case AgentScheduler.Code.Running => Some(WaitMeaning("Started: its run is in flight."))
    case AgentScheduler.Code.Settled => Some(WaitMeaning("Settled: done, stopped, failed or cancelled."))
    case AgentScheduler.Code.Paused  => Some(WaitMeaning("Its manager or its run is paused."))
    case _                           => None

  /** A facet of the queue as the filter chips count it: each value with its items, the most first. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def facet(
      ws: List[AgentScheduler.Waiting],
      of: AgentScheduler.Waiting => Facet.Value
  ): List[(Facet.Value, Facet.Count)] =
    ws.groupMapReduce(of)(_ => 1)(_ + _)
      .toList
      .sortBy((v, n) => (-n, v.value))
      .map((v, n) => (v, Facet.Count(n)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def strategyLabel(name: AgentScheduler.StrategyLine.Name): StrategyLabel =
    StrategyLabel(Wording.strategies.getOrElse(name, Wording.humanize(Wording.Id(name.value))))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def jobTypeLabel(t: JobKinds.Wire): UiLabels.Text =
    UiLabels.Text(Wording.schedulerJobTypes.getOrElse(t, Wording.humanize(Wording.Id(t.value))))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def waitLabel(code: AgentScheduler.Waiting.Code): WaitLabel =
    WaitLabel(AgentScheduler.waitCodes.getOrElse(code, Wording.humanize(Wording.Id(code.value))))

  /** The scheduler's heading: the strategy (and who chose it), the runs at once, the pools, what waits for a person. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def schedulerHead(s: AgentScheduler.SchedulerStatus): SchedulerHead =
    SchedulerHead(
      shown"Scheduler: ${strategyLabel(s.strategy)}" + (if s.strategyFrom.value == "person"
                                                        then " (set by a person)"
                                                        else "") +
        s" · ${s.occupied} of ${s.maxRuns} runs at once" +
        (if s.pools.isEmpty then ""
         else " · " + s.pools.map(p => s"${Wording.pool(p.pool)} ${p.used}/${p.cap}").mkString(", ")) +
        (if s.forPerson.value > 0 then s" · ${counted(s.forPerson.value, "item")} wait for a person (no place held)"
         else "")
    )

  /** The central scheduler: the strategy switch (behind the write gate), the resources, what the last pass started
    * (each opening its manager's runs in the runs window), and the queue — filtered by job type, reason and text like
    * the runs, jobs and approvals windows — with why each item waits and behind what.
    */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def schedulerSection: HtmlElement =
    val st = Var(Option.empty[AgentScheduler.SchedulerStatus])
    val failure = Var(Option.empty[String])
    def load(): Unit = ApiClient
      .scheduler(Some(Paging.Limit(300)))
      .onComplete(_.fold(e => failure.set(Some(e.getMessage)), s => { failure.set(None); st.set(Some(s)) }))
    def switch(name: AgentScheduler.StrategyLine.Name): Unit =
      if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
      else
        ApiClient
          .setScheduler(AgentScheduler.SchedulerSet.Strategy(name.value))
          .onComplete(
            _.fold(e => failure.set(Some(s"scheduler: ${e.getMessage}")), s => { failure.set(None); st.set(Some(s)) })
          )
    val filters = List(UiState.schedTypes, UiState.schedCodes, UiState.schedSubject)
    val stalls = Var(Option.empty[Stalls.StallReport])
    val waiting = st.signal.map(_.fold(List.empty[AgentScheduler.Waiting])(_.waiting))
    def openRuns(manager: AgentScheduler.Started.Manager): Unit =
      UiState.set(UiState.runManager := Some(manager.value), UiState.runStatus := None)()
      WindowManager.open(WindowKey.Runs)
    div(
      cls := "scheduler",
      onMountCallback(_ => load()),
      div(
        cls := "toolbar",
        div(
          cls := "btn-group",
          span(cls := "autopilot-state", child.text <-- st.signal.map(_.fold("Scheduler")(s => schedulerHead(s).value)))
        ),
        div(
          cls := "btn-group",
          role := "group",
          aria.label := "strategy",
          children <-- st.signal.map(
            _.toList.flatMap(s =>
              s.strategies.map(x =>
                button(
                  typ := "button",
                  cls := "btn btn--toggle btn--sm",
                  strategyLabel(x.name),
                  title := s"${x.describe} (${x.name})",
                  aria.pressed := (x.name == s.strategy).toString,
                  onClick --> (_ => switch(x.name))
                )
              )
            )
          )
        ),
        div(
          cls := "btn-group",
          button(typ := "button", cls := "btn btn--sm", "Refresh", onClick --> (_ => load())),
          button(
            typ := "button",
            cls := "btn btn--sm",
            "Stalls",
            title := "The goals with no progress, why, and what unblocks them",
            onClick --> (_ =>
              ApiClient
                .stalls()
                .onComplete(_.fold(e => failure.set(Some(s"stalls: ${e.getMessage}")), r => stalls.set(Some(r))))
            )
          ),
          button(
            typ := "button",
            cls := "btn btn--sm",
            "Approvals",
            title := "What waits for a person (the approvals window)",
            onClick --> (_ => { UiState.set(UiState.approvalState := None)(); WindowManager.open(WindowKey.Approvals) })
          )
        )
      ),
      errorLine(failure.signal),
      child.maybe <-- stalls.signal.map(_.map { r =>
        div(
          cls := "scheduler-stalls",
          div(
            cls := "muted",
            s"${r.stalled.size} of ${r.goals} unfinished goals with no progress in ${r.days} day(s)" +
              (if r.byReason.isEmpty then "" else ": " + r.byReason.map(x => s"${x.code} ${x.count}").mkString(" · "))
          ),
          ul(
            cls := "job-list",
            r.stalled
              .take(100)
              .map(x =>
                li(
                  cls := "job-row",
                  span(
                    cls := s"job-state state-${x.code}",
                    title := x.code,
                    Wording.humanize(Wording.Id(x.code.value))
                  ),
                  div(
                    cls := "job-main",
                    div(
                      cls := "job-subject",
                      WinLink(
                        WindowKey.Goal(WindowKey.Goal.Slug(x.slug.value)),
                        title := x.slug,
                        Wording.humanize(Wording.Id(x.slug.value))
                      ),
                      span(cls := "muted", x.idleDays.fold(" · never progressed")(d => s" · idle $d d"))
                    ),
                    div(cls := "job-progress", x.why),
                    div(cls := "muted", s"unblock: ${x.action}")
                  )
                )
              )
          )
        )
      }),
      child.maybe <-- st.signal.map(_.map { s =>
        div(
          s.stop.map(x => p(cls := "form-error", role := "alert", s"nothing starts: $x")),
          div(
            cls := "muted",
            (if s.dry.value then "what a pass would do now (nothing started) · "
             else s.at.fold("")(t => s"last pass $t · ")) +
              s.types
                .filter(t => t.waiting.value > 0 || t.recent.value > 0)
                .map(t => s"${t.jobType} ${t.recent} recent, ${t.waiting} waiting")
                .mkString(" · ")
          ),
          Option.when(s.started.nonEmpty)(
            ul(
              cls := "job-list scheduler-started",
              s.started.map(x =>
                li(
                  cls := "job-row",
                  span(cls := "job-state state-running", title := x.jobType, jobTypeLabel(x.jobType)),
                  div(
                    cls := "job-main",
                    div(
                      cls := "job-subject",
                      button(
                        typ := "button",
                        cls := "link-btn",
                        x.target,
                        title := "its manager's runs",
                        onClick --> (_ => openRuns(x.manager))
                      ),
                      span(
                        cls := "muted",
                        title := x.manager,
                        s" · ${Wording.manager(Wording.Id(x.manager.value))}"
                      )
                    ),
                    div(cls := "muted", shown"${x.why}")
                  )
                )
              )
            )
          )
        )
      }),
      div(
        cls := "list-filters",
        ListFilterBar.chips(
          "type",
          waiting.map(facet(_, w => Facet.Value(w.jobType.value))),
          UiState.schedTypes,
          show = t => jobTypeLabel(JobKinds.Wire(t)).value,
          hint = t => jobTypeMeaning(JobKinds.Wire(t)).map(_.value),
          about = "Show only waiting items of the job types picked (any of them): what kind of work they are."
        ),
        ListFilterBar.chips(
          "why",
          waiting.map(facet(_, w => Facet.Value(w.code.value))),
          UiState.schedCodes,
          show = c => waitLabel(AgentScheduler.Waiting.Code(c)).value,
          hint = c => waitMeaning(AgentScheduler.Waiting.Code(c)).map(_.value),
          about = "Show only waiting items held back for the reasons picked (any of them)."
        ),
        div(
          cls := "toolbar",
          ListFilterBar.text("target, goal or manager", UiState.schedSubject),
          ListFilterBar.clearButton(filters)
        )
      ),
      child <-- waiting
        .combineWith(UiState.schedTypes.signal, UiState.schedCodes.signal, UiState.schedSubject.signal)
        .map { (ws, ty, co, su) =>
          val shown = visibleWaiting(
            ws,
            ty.map(AgentScheduler.Waiting.JobType(_)),
            co.map(AgentScheduler.Waiting.Code(_)),
            su.map(Activity.Subject(_))
          )
          if ws.isEmpty then p(cls := "muted", "Nothing waits.")
          else
            ol(
              cls := "job-list scheduler-queue",
              shown.map(w =>
                li(
                  cls := "job-row",
                  span(cls := s"job-state state-${w.code}", title := w.code, waitLabel(w.code)),
                  div(
                    cls := "job-main",
                    div(
                      cls := "job-subject",
                      WinLink(WindowKey.Manager(WindowKey.Manager.Id(w.manager.value)), w.target.value),
                      span(
                        cls := "muted",
                        shown" · ${jobTypeLabel(w.jobType)} · ${Wording.manager(Wording.Id(w.manager.value))}" + w.goal
                          .fold("")(g => s" · goal ${Wording.humanize(Wording.Id(g.value))}") +
                          (if w.position.value > 0 then s" · #${w.position} in line" else "")
                      )
                    ),
                    div(cls := "job-progress", shown"${w.why}"),
                    Option.when(w.behind.nonEmpty)(div(cls := "muted", s"behind ${w.behind.mkString(", ")}")),
                    div(
                      cls := "job-meta muted",
                      s"priority ${w.priority} · waited ${AgentSchedulerMarkdown.minutes(w.waitedMinutes)}"
                    )
                  )
                )
              )
            )
        }
    )

  // ── the managers window ─────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def stateBadge(m: ManagerInfo): HtmlElement =
    val b = badge(m).value
    span(
      cls := s"job-state state-${b.replace(' ', '-')}",
      title := m.statusNote.fold(m.state.value)(_.render),
      Wording.capitalize(b)
    )

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pane: HtmlElement =
    val ms = Var(List.empty[ManagerInfo])
    val loaded = Var(false)
    val failure = Var(Option.empty[String])
    def refresh(): Unit =
      ApiClient.managers.onComplete(
        _.fold(e => failure.set(Some(e.getMessage)), xs => { failure.set(None); ms.set(xs); loaded.set(true) })
      )
    div(
      cls := "tool-pane managers-pane",
      onMountCallback(_ => refresh()),
      div(cls := "pane-bar", button(typ := "button", cls := "btn", "Refresh", onClick --> (_ => refresh()))),
      child.maybe <-- RunsView.enabled.signal.map(on =>
        Option.when(!on.enabled)(
          p(cls := "muted", "Embedded agents are off on this server (agents.enabled): no manager starts a run.")
        )
      ),
      autopilotSection,
      schedulerSection,
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child <-- ms.signal.combineWith(loaded.signal).map { (xs, isLoaded) =>
        if !isLoaded then p(cls := "muted", "Loading…")
        else if xs.isEmpty then
          p(cls := "muted", "No managers yet. The review manager is created (paused) when the server's agents start.")
        else
          ul(
            cls := "job-list manager-list",
            xs.map(m =>
              li(
                cls := "job-row manager-row",
                stateBadge(m),
                div(
                  cls := "job-main",
                  div(
                    cls := "job-subject",
                    WinLink(
                      WindowKey.Manager(WindowKey.Manager.Id(m.id.value)),
                      title := m.id,
                      Wording.manager(Wording.Id(m.id.value))
                    )
                  ),
                  m.statusNote.map(_.render).filter(_.nonEmpty).map(n => div(cls := "job-progress", n)),
                  div(
                    cls := "job-meta muted",
                    counts(m.counts),
                    s" · ${counted(m.inFlight.value, "run")} in flight",
                    s" · ${ManagersMarkdown.today(m)}"
                  ),
                  Option
                    .when(m.backlog.value > 0)(div(cls := "muted", ManagersMarkdown.backlog(m.backlog, m.backlogDays))),
                  m.lastEvent.map(e => div(cls := "muted", s"${e.at} · ${e.kind}: ${e.text.value.take(160)}"))
                )
              )
            )
          )
      }
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def counts(c: ItemCounts): CountsText =
    val text = List(
      "pending" -> c.pending.value,
      "held" -> c.held.value,
      "active" -> c.active.value,
      "done" -> c.done.value,
      "stopped" -> c.stopped.value,
      "failed" -> c.failed.value
    ).filter(_._2 > 0).map((k, n) => s"$n $k").mkString(", ")
    CountsText(if text.isEmpty then "no items" else text)

  // ── one manager ─────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def managerPane(id: Managers.ManagerInfo.Id): HtmlElement =
    val detail = Var(Option.empty[ManagerDetail])
    val page = Var(Option.empty[ItemPage])
    val offset = Var(Offset(0))
    val status = Var(Option.empty[ItemStatus.Wire])
    val events = Var(List.empty[ManagerEvent])
    val older = Var(Option.empty[EventPage.Before])
    val loadingOlder = Var(false)
    val failure = Var(Option.empty[String])
    val message = Var(Option.empty[String])
    val busy = Var(false)
    // `var`s local to this window: its pending poll, the delay, whether it is mounted, whether older
    // events were loaded (a poll then keeps them)
    var timer: Int = 0
    var delay: Option[Poll.Revision] = None
    var mounted: Boolean = false
    var extended: Boolean = false

    def items(): Unit =
      ApiClient.managerItems(id, status.now(), offset.now(), ItemLimit).foreach(p => page.set(Some(p)))

    def refresh(changed: Poll.DataChanged): Unit =
      dom.window.clearTimeout(timer)
      ApiClient.manager(id).onComplete { r =>
        if mounted then
          r.fold(
            e => failure.set(Some(e.getMessage)),
            d => {
              val moved = changed.changed || !detail.now().contains(d)
              failure.set(None)
              detail.set(Some(d))
              if extended then events.update(ListPaging.keepOlder(_, d.events, _.id < _.id))
              else
                events.set(d.events)
                older.set(Option.when(d.events.nonEmpty)(EventPage.Before(d.events.last.id.value)))
              delay = Poll.next(
                delay,
                (if d.manager.inFlight.value > 0 || d.current.nonEmpty then Poll.LiveMode.Live
                 else Poll.LiveMode.Paused),
                (if moved then Poll.DataChanged.Changed else Poll.DataChanged.Unchanged)
              )
              delay.foreach(ms => timer = dom.window.setTimeout(() => refresh(Poll.DataChanged.Unchanged), ms.value))
            }
          )
      }
      items()

    def write[A](what: WriteLabel)(f: => scala.concurrent.Future[A]): Unit =
      busy.set(true)
      f.onComplete { r =>
        busy.set(false)
        r.fold(
          e => failure.set(Some(shown"$what: ${e.getMessage}")),
          _ => { failure.set(None); message.set(Some(shown"$what: done")) }
        )
        refresh(changed = Poll.DataChanged.Changed)
      }

    def showOlder(): Unit =
      older.now().foreach { b =>
        loadingOlder.set(true)
        ApiClient.managerEvents(id, Some(b), EventLimit).onComplete { r =>
          loadingOlder.set(false)
          r.foreach { p =>
            val shown = events.now().map(_.id).toSet
            events.update(_ ++ p.events.filterNot(e => shown(e.id)))
            older.set(p.before)
            extended = true
          }
        }
      }

    div(
      cls := "tool-pane manager-pane",
      onMountCallback { _ =>
        mounted = true; extended = false; refresh(changed = Poll.DataChanged.Changed)
      },
      onUnmountCallback { _ => mounted = false; dom.window.clearTimeout(timer) },
      offset.signal.changes --> (_ => items()),
      status.signal.changes --> { _ => offset.set(Offset(0)); items() },
      errorLine(failure.signal),
      child.maybe <-- message.signal.map(_.map(m => p(cls := "form-ok", m))),
      child <-- detail.signal.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(d) => head(d, busy.signal, write)
      },
      div(
        cls := "goal-section",
        h4("In flight"),
        child <-- detail.signal.map(_.toList.flatMap(_.current)).map { cur =>
          if cur.isEmpty then p(cls := "muted", "Nothing in flight.")
          else ul(cls := "job-list", cur.map(i => itemRow(id, i, busy.signal, write)))
        }
      ),
      child.maybe <-- detail.signal.map(
        _.filter(_.manager.kind == Kind.Watch.wire).map(d =>
          div(
            cls := "goal-section manager-sweeps",
            h4("Sweeps"),
            p(
              cls := "muted",
              d.manager.cron.fold("No cron: its sweeps run only when re-opened by hand.")(c =>
                s"Cron `$c` (UTC)" + d.manager.nextTickAt.fold("")(t => s", next $t") +
                  (if d.manager.state == State.Active.wire then "" else " — fires once it is resumed") + ". " +
                  (if d.manager.effective.autoIngest.exists(_.value)
                   then
                     s"Legal open-access picks are ingested, at most ${d.manager.effective.autoIngestPerDay.fold(0)(_.value)} a day (legal open access only)."
                   else "Its picks go to the acquisition list (auto-ingest off).")
              )
            ),
            child <-- events.signal
              .map(sweepEvents)
              .map(es =>
                if es.isEmpty then p(cls := "muted", "No sweep has settled yet.")
                else
                  ul(
                    cls := "manager-events",
                    es.map(e => li(span(cls := "muted", s"${e.at} · "), strong(e.kind), shown" ${e.text}"))
                  )
              )
          )
        )
      ),
      div(
        cls := "goal-section",
        h4("Items"),
        div(
          cls := "toolbar",
          div(
            cls := "btn-group",
            (None :: ItemStatus.values.toList.map(s => Some(s.wire))).map(s =>
              button(
                typ := "button",
                cls := "btn btn--toggle btn--sm",
                aria.pressed <-- status.signal.map(v => (v == s).toString),
                title := s
                  .flatMap(ItemStatus.parse)
                  .fold("Every item, whatever its status.")(st => itemStatusMeaning(st).value),
                s.fold("all")(_.value),
                onClick --> (_ => status.set(s))
              )
            )
          )
        ),
        child <-- page.signal.map {
          case None                         => p(cls := "muted", "Loading…")
          case Some(pg) if pg.items.isEmpty => p(cls := "muted", "No items.")
          case Some(pg) => ul(cls := "job-list manager-items", pg.items.map(i => itemRow(id, i, busy.signal, write)))
        },
        Pager(
          Pager.Label("items"),
          offset,
          ItemLimit,
          page.signal.map(p => Paging.Shown(p.fold(0)(_.items.size))),
          page.signal.map(p => Paging.Page.Total(p.fold(0)(_.total.value)))
        )
      ),
      hintBox(id, busy.signal, write),
      div(
        cls := "goal-section",
        h4("Hints"),
        child <-- detail.signal.map(_.toList.flatMap(_.hints)).map { hs =>
          if hs.isEmpty then p(cls := "muted", "No live hints.")
          else
            ul(
              cls := "manager-hints",
              hs.map(h =>
                li(
                  span(ManagersMarkdown.hint(h)),
                  child.maybe <-- Auth.writable.map(w =>
                    Option.when(w)(
                      button(
                        typ := "button",
                        cls := "link-btn",
                        " retire",
                        disabled <-- busy,
                        onClick --> (_ => write(WriteLabel("retire hint"))(ApiClient.managerRemoveHint(id, h.id)))
                      )
                    )
                  )
                )
              )
            )
        }
      ),
      div(
        cls := "goal-section",
        h4("Events"),
        child <-- events.signal.map(es =>
          if es.isEmpty then p(cls := "muted", "No events yet.")
          else
            ul(
              cls := "manager-events",
              es.map(e =>
                li(
                  span(cls := "muted", s"${e.at} · "),
                  strong(e.kind),
                  shown" ${e.text}",
                  e.runId.map(r =>
                    span(" ", WinLink(WindowKey.Run(WindowKey.Run.Id(r.value)), s"run ${r.value.take(8)}"))
                  )
                )
              )
            )
        ),
        // newest first with a `before` cursor: the list-paging branch's "Show older"
        ListPaging.olderButton(older.signal.map(_.isDefined), loadingOlder.signal, () => showOlder())
      ),
      child <-- detail.signal.map(_.map(_.manager)).map {
        case None    => emptyNode
        case Some(m) => settings(m, busy.signal, write)
      }
    )

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def head(
      d: ManagerDetail,
      busy: Signal[Boolean],
      write: WriteLabel => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    val m = d.manager
    div(
      cls := "goal-detail",
      div(
        cls := "run-head",
        stateBadge(m),
        span(cls := "run-title", title := m.id, s" ${Wording.manager(Wording.Id(m.id.value))}")
      ),
      m.statusNote.map(_.render).filter(_.nonEmpty).map(n => p(cls := "job-progress", n)),
      Option.when(m.state == State.Paused.wire && m.inFlight.value == 0)(
        p(cls := "muted", "Paused: it starts nothing (nothing is spent) until you resume it.")
      ),
      dl(
        cls := "run-facts",
        dt("items"),
        dd(counts(m.counts)),
        m.cron.toList.flatMap(c => List(dt("cron"), dd(s"$c (UTC)" + m.nextTickAt.fold("")(t => s", next $t")))),
        dt("in flight"),
        dd(counted(m.inFlight.value, "run")),
        Option
          .when(m.backlog.value > 0)(List(dt("ingest backlog"), dd(ManagersMarkdown.backlog(m.backlog, m.backlogDays))))
          .toList
          .flatten,
        dt("today"),
        dd(
          span(ManagersMarkdown.today(m)),
          span(
            cls := "muted",
            " — the daily cap counts metered spend; a plan-billed run (Claude or ChatGPT plan) costs 0 USD, its tokens are shown"
          )
        ),
        dt("settings"),
        dd(ManagersHandlersText.describe(m.effective))
      ),
      div(
        cls := "form-row",
        child <-- Auth.writable.map { w =>
          if !w then Auth.keyNeeded("steer this manager")
          else
            val st = State.parse(m.state)
            div(
              cls := "toolbar",
              div(
                cls := "btn-group",
                Option
                  .when(st.contains(State.Active))(
                    List(
                      button(
                        typ := "button",
                        cls := "btn",
                        "pause",
                        disabled <-- busy,
                        title := "start no new runs; its runs in flight go on",
                        onClick --> (_ =>
                          write(WriteLabel("pause"))(
                            ApiClient.managerAction(m.id, ManagersApi.Action("pause"))
                          )
                        )
                      ),
                      button(
                        typ := "button",
                        cls := "btn",
                        "pause + runs",
                        disabled <-- busy,
                        title := "also pause its runs in flight",
                        onClick --> (_ =>
                          write(WriteLabel("pause with its runs"))(
                            ApiClient.managerAction(m.id, ManagersApi.Action("pause"), runs = true)
                          )
                        )
                      )
                    )
                  )
                  .toList
                  .flatten,
                Option.when(!st.contains(State.Active))(
                  button(
                    typ := "button",
                    cls := "btn btn--primary",
                    "resume",
                    disabled <-- busy,
                    onClick --> (_ =>
                      write(WriteLabel("resume"))(ApiClient.managerAction(m.id, ManagersApi.Action("resume")))
                    )
                  )
                ),
                Option.when(!st.contains(State.Stopped))(
                  button(
                    typ := "button",
                    cls := "btn btn--danger",
                    "stop",
                    disabled <-- busy,
                    title := "cancel its runs in flight and its open items",
                    onClick --> (_ =>
                      if dom.window.confirm(shown"Stop ${m.id}: cancel its runs in flight and its open items?") then
                        write(WriteLabel("stop"))(ApiClient.managerAction(m.id, ManagersApi.Action("stop")))
                    )
                  )
                )
              )
            )
        }
      )
    )

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def itemRow(
      mid: Managers.ManagerInfo.Id,
      i: ManagerItem,
      busy: Signal[Boolean],
      write: WriteLabel => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    val prio = Var(i.priority.toString)
    val until = Var("")
    li(
      cls := s"job-row manager-item item-${i.status}",
      span(cls := s"job-state state-${i.status}", i.status),
      div(
        cls := "job-main",
        div(
          cls := "job-subject",
          ArticleLink(Article.Slug(i.target.value), i.target.value),
          span(
            cls := "muted",
            s" · priority ${i.priority} · ${counted(i.attempts.value, "run")}" +
              (if i.failures.value > 0 then s", ${i.failures} failed" else "")
          )
        ),
        i.run.map(r =>
          div(
            cls := "job-progress",
            WinLink(WindowKey.Run(WindowKey.Run.Id(r.id.value)), s"run ${r.id.value.take(8)} ${r.status}"),
            r.progress.filter(_.value.nonEmpty).map(p => span(cls := "muted", shown" · $p"))
          )
        ),
        i.hold.map(h =>
          div(
            cls := "muted",
            "held" + h.until.fold("")(u => s" until $u") + h.untilSource.fold("")(s => s" until $s exists") +
              h.focus.fold("")(g => s" for the focus (goal $g; pin it in the managers window to run it now)") +
              Option(h.reason).filter(w => shown"$w".nonEmpty).fold("")(r => shown" — $r")
          )
        ),
        i.notBefore.map(t => div(cls := "muted", s"not before $t")),
        i.outcome.filter(_.value.nonEmpty).map(o => div(cls := "muted", o)),
        child.maybe <-- Auth.writable.map(w =>
          Option.when(w && itemActionsFor(i).nonEmpty)(
            div(
              cls := "form-row manager-item-actions",
              itemActionsFor(i).map(action =>
                action.value match
                  case "hold" =>
                    span(
                      input(
                        typ := "text",
                        cls := "field",
                        aria.label := "hold until",
                        placeholder := "until 2026-11-01 or a DOI",
                        controlled(value <-- until.signal, onInput.mapToValue --> until.writer)
                      ),
                      button(
                        typ := "button",
                        cls := "btn btn--sm",
                        "hold",
                        disabled <-- busy,
                        onClick --> { _ =>
                          val u = until.now().trim
                          val patch = if u.isEmpty then ItemPatch(Managers.ItemPatch.Action("hold"))
                          else if ManagerPolicyText.isDate(u) then
                            ItemPatch(Managers.ItemPatch.Action("hold"), until = Some(Managers.ItemPatch.Until(u)))
                          else
                            ItemPatch(
                              Managers.ItemPatch.Action("hold"),
                              untilSource = Some(Managers.ItemPatch.UntilSource(u))
                            )
                          write(WriteLabel(s"hold ${i.target}"))(ApiClient.managerPatchItem(mid, i.id, patch))
                        }
                      )
                    )
                  case "priority" =>
                    span(
                      input(
                        typ := "number",
                        cls := "field manager-prio",
                        aria.label := "priority",
                        controlled(value <-- prio.signal, onInput.mapToValue --> prio.writer)
                      ),
                      button(
                        typ := "button",
                        cls := "btn btn--sm",
                        "set priority",
                        disabled <-- busy,
                        onClick --> (_ =>
                          whole(prio.now())
                            .orElse(prio.now().trim.toIntOption)
                            .foreach(p =>
                              write(WriteLabel(s"priority of ${i.target}"))(
                                ApiClient.managerPatchItem(
                                  mid,
                                  i.id,
                                  ItemPatch(
                                    Managers.ItemPatch.Action("priority"),
                                    priority = Some(Managers.ItemPatch.Priority(p))
                                  )
                                )
                              )
                            )
                        )
                      )
                    )
                  case a =>
                    button(
                      typ := "button",
                      cls := (if a == "skip" then "btn btn--sm btn--danger" else "btn btn--sm"),
                      a,
                      disabled <-- busy,
                      onClick --> (_ =>
                        write(WriteLabel(s"$a ${i.target}"))(
                          ApiClient.managerPatchItem(mid, i.id, ItemPatch(Managers.ItemPatch.Action(a)))
                        )
                      )
                    )
              )
            )
          )
        )
      )
    )

  /** The hint box's action: none, or one of the verbs a person's hint may ask for. */
  private val HintChoices: Choices[Option[HintEffect.Verb]] =
    Choices.optional(WireEnumValues.Wire.of(HintEffect.Verb.NoAction), HintEffect.Verb.Actions)

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def hintBox(
      id: Managers.ManagerInfo.Id,
      busy: Signal[Boolean],
      write: WriteLabel => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    val text = Var("")
    val item = Var("")
    val once = Var(false)
    val action = Var(Option.empty[HintEffect.Verb])
    val targets = Var("")
    val tag = Var("")
    val until = Var("")
    val source = Var("")
    val suggest = Var(false)
    val error = Var(Option.empty[String])
    div(
      cls := "goal-section manager-hint-box",
      h4("Hint"),
      p(
        cls := "muted",
        "A note the fixer and the skeptic of its next runs read (subordinate to the record). " +
          "Optionally, an action applied now: put articles first, hold them, or skip them — or let the server's model " +
          "read the note and suggest the action (it waits under Approvals until you approve it)."
      ),
      child <-- Auth.writable.map { w =>
        if !w then Auth.keyNeeded("give this manager a hint")
        else
          div(
            textArea(
              cls := "field manager-hint-text",
              aria.label := "hint",
              placeholder := "e.g. prefer the 2019 dating; the skeptic's Kristiansen critique is covered in [[x]]",
              controlled(value <-- text.signal, onInput.mapToValue --> text.writer)
            ),
            div(
              cls := "form-row",
              input(
                typ := "text",
                cls := "field",
                aria.label := "only about this article",
                placeholder := "only about (article slug, optional)",
                controlled(value <-- item.signal, onInput.mapToValue --> item.writer)
              ),
              label(
                input(typ := "checkbox", controlled(checked <-- once.signal, onClick.mapToChecked --> once.writer)),
                " next run only"
              ),
              child.maybe <-- RunsView.enabled.signal.map(on =>
                Option.when(on.enabled)(
                  label(
                    title := "the server's planner model proposes the action the note asks for; nothing changes until you approve it",
                    input(
                      typ := "checkbox",
                      controlled(checked <-- suggest.signal, onClick.mapToChecked --> suggest.writer)
                    ),
                    " suggest an action"
                  )
                )
              ),
              select(
                cls := "field",
                aria.label := "action",
                HintChoices.options,
                HintChoices.bound(action)
              )
            ),
            child.maybe <-- action.signal.map(a =>
              Option.when(a.isDefined)(
                div(
                  cls := "form-row",
                  input(
                    typ := "text",
                    cls := "field",
                    aria.label := "articles",
                    placeholder := "articles (slugs, comma-separated)",
                    controlled(value <-- targets.signal, onInput.mapToValue --> targets.writer)
                  ),
                  input(
                    typ := "text",
                    cls := "field",
                    aria.label := "or a tag",
                    placeholder := "or a tag",
                    controlled(value <-- tag.signal, onInput.mapToValue --> tag.writer)
                  ),
                  Option
                    .when(a.contains(HintEffect.Verb.Hold))(
                      List(
                        input(
                          typ := "text",
                          cls := "field",
                          aria.label := "until a date",
                          placeholder := "until 2026-11-01",
                          controlled(value <-- until.signal, onInput.mapToValue --> until.writer)
                        ),
                        input(
                          typ := "text",
                          cls := "field",
                          aria.label := "until a source exists",
                          placeholder := "or until a DOI/citekey exists",
                          controlled(value <-- source.signal, onInput.mapToValue --> source.writer)
                        )
                      )
                    )
                    .toList
                    .flatten
                )
              )
            ),
            errorLine(error.signal),
            button(
              typ := "button",
              cls := "btn btn--primary",
              "add hint",
              disabled <-- busy,
              onClick --> { _ =>
                hint(
                  HintAdd.Text(text.now()),
                  HintAdd.Item(item.now()),
                  HintAdd.Once(once.now()),
                  action.now().fold(HintEffect.Verb.NoAction)(_.word),
                  HintEffect.Targets(targets.now()),
                  HintEffect.Tag(tag.now()),
                  HintEffect.Until(until.now()),
                  HintEffect.UntilSource(source.now()),
                  HintAdd.Suggest(suggest.now())
                ) match
                  case Left(e)  => error.set(Some(e.value))
                  case Right(h) =>
                    error.set(None)
                    write(if h.suggest.exists(_.value) then
                      WriteLabel("hint (a model suggests its action: see Approvals)")
                    else WriteLabel("hint"))(ApiClient.managerAddHint(id, h))
                    text.set(""); targets.set(""); tag.set(""); until.set(""); source.set(""); suggest.set(false)
              }
            )
          )
      }
    )

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def settings(
      m: ManagerInfo,
      busy: Signal[Boolean],
      write: WriteLabel => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    val form = Var(fields(m.config))
    val cron = Var(m.cron.fold("")(_.value))
    val error = Var(Option.empty[String])
    val eff = fields(m.effective)
    htmlTag("details")(
      cls := "goal-section manager-settings",
      htmlTag("summary")("Settings"),
      p(cls := "muted", "Blank = the server's default (shown as the placeholder)."),
      Option.when(m.kind != Kind.Enrich.wire)(
        label(
          cls := "manager-setting",
          span("cron (UTC: five fields, or @daily/@weekly/@monthly; blank = none)"),
          input(
            typ := "text",
            cls := "field",
            aria.label := "cron",
            placeholder := (if m.kind == Kind.Watch.wire then "@weekly" else "none"),
            controlled(value <-- cron.signal, onInput.mapToValue --> cron.writer)
          )
        )
      ),
      div(
        cls := "manager-settings-grid",
        settingsFor(m.kind).map { (k, lbl) =>
          label(
            cls := "manager-setting",
            span(lbl),
            input(
              typ := "text",
              cls := "field",
              aria.label := lbl.value,
              placeholder := eff.getOrElse(k, ConfigText("")).value,
              controlled(
                value <-- form.signal.map(_.getOrElse(k, ConfigText("")).value),
                onInput.mapToValue --> (v => form.update(_ + (k -> ConfigText(v))))
              )
            )
          )
        }
      ),
      errorLine(error.signal),
      child <-- Auth.writable.map { w =>
        if !w then Auth.keyNeeded("change these settings")
        else
          button(
            typ := "button",
            cls := "btn btn--primary",
            "save settings",
            disabled <-- busy,
            onClick --> { _ =>
              config(form.now()) match
                case Left(e)  => error.set(Some(e.value))
                case Right(c) =>
                  error.set(None)
                  // the cron goes only when it changed (sending it again would move its next firing)
                  val cr = Option(cron.now().trim).filter(_ != m.cron.fold("")(_.value)).map(ManagerUpdate.Cron(_))
                  write(WriteLabel("settings"))(ApiClient.managerUpdate(m.id, ManagerUpdate(Some(c), cr)))
            }
          )
      }
    )
