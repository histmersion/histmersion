package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Persistent process managers (docs/research-process-managers.md): the vocabulary and the wire DTOs of
  * `/api/agents/managers/…`. A MANAGER owns WORK ITEMS ("article X until its review converges"), decides the next run
  * of each, evaluates what the run left behind, and stops an item with a reason a person can act on. It takes HINTS
  * from the person and records EVENTS. Wire types only; the policy is pure in `core` ([[ManagerPolicy]]), the
  * supervisor in the server.
  */
object Managers:

  export com.histmersion.values.{ItemStatus, ManagerKind as Kind, ManagerState as State}

  /** Why an item exists: the manager's own selection, a person, a run that chained it, a hint, an ingest job
    * (`agents.auto-enrich`).
    */
  enum ItemOrigin(val wire: String):
    case Selection extends ItemOrigin("selection")
    case Person extends ItemOrigin("person")
    case Chain extends ItemOrigin("chain")
    case Hint extends ItemOrigin("hint")
    case Ingest extends ItemOrigin("ingest")

    /** The manager's cron fired (a watcher's weekly sweeps, a refill on schedule). */
    case Cron extends ItemOrigin("cron")

  object ItemOrigin:
    def parse(s: String): Option[ItemOrigin] = values.find(_.wire == s.trim.toLowerCase)

  /** The kinds of a manager event. */
  object Feed:
    val Started = "started"
    val Settled = "settled"
    val Evaluated = "evaluated"
    val Stopped = "stopped"
    val Escalated = "escalated"
    val Held = "held"
    val Hint = "hint"
    val Paused = "paused"
    val Stall = "stall"
    val Starved = "starved"

    /** A watcher's sweep settled: what it found, queued and ingested. */
    val Swept = "swept"

    /** A watcher's sweep ingested one legal open-access work (what its daily cap counts). */
    val Ingested = "ingested"

    /** The manager's cron fired. */
    val Cron = "cron"

    /** A schedule was migrated into the manager. */
    val Migrated = "migrated"

    /** The supervisor decided (or left for a person) an approval of the manager's runs by its code checks (the
      * autopilot's auto approvals).
      */
    val Approval = "approval"

    /** The autopilot changed the manager or its goal (created, resumed, paused, the goal's status). */
    val Autopilot = "autopilot"

  /** The item kinds. The review manager's: one article until its review converges; the coverage filter an article
    * lacks, inferred before its review; an article extended from its filter's new sources (then reviewed). The enrich
    * manager's: one source enriched. The seeding manager's: the articles a research area should have, proposed for a
    * person's approval; one approved article compiled.
    */
  val ArticleReview = "article-review"
  val InferFilter = "infer-filter"
  val ArticleExtend = "article-extend"
  val SourceEnrich = "source-enrich"
  val ProposeArticles = "propose-articles"
  val ArticleCompile = "article-compile"

  /** The watcher's: one research area's acquisition sweep and its currency check (target: the goal). */
  val AcquisitionSweep = "acquisition-sweep"
  val CurrencyCheck = "currency-check"

  /** The seeding manager's first item for a goal with no plan (the autopilot): a `plan-goal` run — sub-goals, queries
    * and works — before its articles are proposed (target: the goal).
    */
  val PlanGoalItem = "plan-goal"

  /** The watcher's INGEST BACKLOG: one legal open-access work its sweep picked past the day's ingest allowance (target:
    * its DOI, state: [[OaWork]]). Not a run: the supervisor ingests it through the legal chain on a later day, within
    * the caps, the oldest (highest priority) first; one with no legal file goes to the acquisition list then.
    */
  val OaIngest = "oa-ingest"

  /** The item kinds a manager of kind `k` drives (its first is its default). */
  def itemKinds(k: Kind): List[String] = k match
    case Kind.Review => List(ArticleReview, InferFilter, ArticleExtend)
    case Kind.Enrich => List(SourceEnrich)
    case Kind.Seed   => List(ProposeArticles, ArticleCompile, PlanGoalItem)
    case Kind.Watch  => List(AcquisitionSweep, CurrencyCheck, OaIngest)

  /** The item kind a person's word names (`review`, `extend`, `enrich`, `propose`, `compile`, or the item kind itself),
    * among those of manager kind `k`; blank = its default.
    */
  def itemKindOf(k: Kind, word: String): Option[String] =
    val w = word.trim.toLowerCase
    val kinds = itemKinds(k)
    if w.isEmpty then kinds.headOption
    else
      val alias = w match
        case "review" | "article"        => ArticleReview
        case "filter" | "infer"          => InferFilter
        case "extend"                    => ArticleExtend
        case "enrich" | "source"         => SourceEnrich
        case "propose" | "plan" | "seed" => ProposeArticles
        case "compile"                   => ArticleCompile
        case "sweep" | "acquisition"     => AcquisitionSweep
        case "currency"                  => CurrencyCheck
        case "plan-goal" | "goal-plan"   => PlanGoalItem
        case "ingest" | "backlog"        => OaIngest
        case other                       => other
      Option(alias).filter(kinds.contains)

  /** The library-wide review manager's id. */
  val ReviewId = "review"

  /** The library-wide enrich manager's id. */
  val EnrichId = "enrich"

  /** The seeding manager of research area `goal`. */
  def seedId(goal: String): String = s"seed:${goal.trim}"

  /** The watcher of research area `goal`. */
  def watchId(goal: String): String = s"watch:${goal.trim}"

  /** What a watcher sweeps (`ManagerConfig.sweeps`, the words a goal schedule used): `acquisition` — search the area's
    * queries, a model picks what to acquire (and, opted in, legal open-access works are ingested) — and `currency` —
    * assess and followups over its sources. Each is one item kind.
    */
  enum Sweep(val wire: String, val itemKind: String):
    case Acquisition extends Sweep("acquisition", AcquisitionSweep)
    case Currency extends Sweep("currency", CurrencyCheck)

  object Sweep:
    def parse(s: String): Option[Sweep] =
      values.find(x => x.wire == s.trim.toLowerCase || x.itemKind == s.trim.toLowerCase)
    def ofItem(kind: String): Option[Sweep] = values.find(_.itemKind == kind)

    /** The sweeps a config names (none = both), in their order. */
    def of(c: ManagerConfig): List[Sweep] =
      c.sweeps.fold(values.toList)(ws => values.toList.filter(s => ws.exists(w => parse(w).contains(s))))

  /** What `POST /api/agents/enqueue` takes (a page button): `review` an article, `extend` an article (compile its
    * filter's new sources, then review), `enrich` a source — or every unenriched source an article cites or a goal
    * links —, `seed` a research area (propose its next articles).
    */
  enum EnqueueKind(val wire: String):
    case Review extends EnqueueKind("review")
    case Extend extends EnqueueKind("extend")
    case Enrich extends EnqueueKind("enrich")
    case Seed extends EnqueueKind("seed")

  object EnqueueKind:
    def parse(s: String): Option[EnqueueKind] = s.trim.toLowerCase match
      case "" | "article" | ArticleReview       => Some(Review)
      case ArticleExtend                        => Some(Extend)
      case SourceEnrich | "source"              => Some(Enrich)
      case ProposeArticles | "propose" | "plan" => Some(Seed)
      case other                                => values.find(_.wire == other)

  /** One article the seeding manager compiles: what its approved proposal said. */
  final case class ArticleSpec(title: String, kind: String, filter: ArticleFilter, why: String = "")
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object ArticleSpec:
    given Schema[ArticleSpec] = summon[KindlingsSchema[ArticleSpec]].schema

  /** A manager's saved settings (`agent_managers.config`). Every field is optional: an absent one is the server's
    * default (`agents.managers.<kind>.*`). `models` = a model per role for its runs, over the role's chain.
    * `includeTags`/`includeGoal` narrow what the selection picks.
    */
  final case class ManagerConfig(
      maxRuns: Option[Int] = None,
      maxOpen: Option[Int] = None,
      rounds: Option[Int] = None,
      maxRunsPerArticle: Option[Int] = None,
      noProgressRuns: Option[Int] = None,
      reopenLimit: Option[Int] = None,
      cooldownMinutes: Option[Int] = None,
      lowWater: Option[Int] = None,
      refillBatch: Option[Int] = None,
      maxFailures: Option[Int] = None,
      budgetUsd: Option[Double] = None,
      includeTags: Option[List[String]] = None,
      includeGoal: Option[String] = None,
      models: Option[List[Agents.ModelOverride]] = None,
      /** review: a converged article is due again after this many days, or when its filter gained sources (0 = never).
        */
      staleAfterDays: Option[Int] = None,
      /** review: an article that records no coverage filter gets an `infer-filter` item before its review. */
      inferFilter: Option[Boolean] = None,
      /** seed: a proposed article's filter must return at least this many sources. */
      minSources: Option[Int] = None,
      /** seed: at most this many articles proposed per plan. */
      maxArticles: Option[Int] = None,
      /** watch: what it sweeps — `acquisition`, `currency` (None = both). */
      sweeps: Option[List[String]] = None,
      /** watch: ingest the legal open-access works its acquisition sweep picks (OPT-IN, default off; the legal chain
        * only); past the daily cap they wait in its ingest backlog (`oa-ingest` items) for a later day; off, they go to
        * the acquisition list.
        */
      autoIngest: Option[Boolean] = None,
      /** watch: at most this many such ingests per UTC day. */
      autoIngestPerDay: Option[Int] = None,
      /** No run is started once the manager's METERED spend this UTC day (its runs' step costs) reaches this many USD.
        * Plan-billed calls cost 0 USD, so they never count — their tokens are shown beside it. None (or 0) = no cap of
        * its own (`agents.managers.daily-usd`; the global caps still apply).
        */
      dailyUsd: Option[Double] = None,
      /** The approvals its runs open (article-plan, goal-plan, article-publish): true = decided by the supervisor's
        * code checks whatever `agents.approvals.*` says, false = always a person's; None = `agents.approvals.*`.
        */
      autoApprove: Option[Boolean] = None,
      /** review: an article whose filter returns at least this many sources it neither cites nor gives a reason for
        * gets an `article-extend` item (0 = never; None = `agents.managers.review.extend-min-new`).
        */
      extendMinNew: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    /** `other`'s values where it has one, else these. */
    def merge(other: ManagerConfig): ManagerConfig = ManagerConfig(
      other.maxRuns.orElse(maxRuns),
      other.maxOpen.orElse(maxOpen),
      other.rounds.orElse(rounds),
      other.maxRunsPerArticle.orElse(maxRunsPerArticle),
      other.noProgressRuns.orElse(noProgressRuns),
      other.reopenLimit.orElse(reopenLimit),
      other.cooldownMinutes.orElse(cooldownMinutes),
      other.lowWater.orElse(lowWater),
      other.refillBatch.orElse(refillBatch),
      other.maxFailures.orElse(maxFailures),
      other.budgetUsd.orElse(budgetUsd),
      other.includeTags.orElse(includeTags),
      other.includeGoal.orElse(includeGoal),
      other.models.orElse(models),
      other.staleAfterDays.orElse(staleAfterDays),
      other.inferFilter.orElse(inferFilter),
      other.minSources.orElse(minSources),
      other.maxArticles.orElse(maxArticles),
      other.sweeps.orElse(sweeps),
      other.autoIngest.orElse(autoIngest),
      other.autoIngestPerDay.orElse(autoIngestPerDay),
      other.dailyUsd.orElse(dailyUsd),
      other.autoApprove.orElse(autoApprove),
      other.extendMinNew.orElse(extendMinNew)
    )
  object ManagerConfig:
    given Schema[ManagerConfig] = summon[KindlingsSchema[ManagerConfig]].schema

    /** `k=v` pairs (the CLI's `--config`) → a config; a key it does not know, or a value that does not parse, is an
      * error. `models` takes `role=provider:model` entries separated by `;`; tags by `,`.
      */
    def parse(kvs: List[String]): Either[String, ManagerConfig] =
      kvs.foldLeft[Either[String, ManagerConfig]](Right(ManagerConfig())) { (acc, kv) =>
        acc.flatMap { c =>
          kv.split("=", 2) match
            case Array(k0, v0) =>
              val (k, v) = (k0.trim, v0.trim)
              def int = v.toIntOption.filter(_ >= 0).toRight(s"$k: '$v' is not a whole number")
              k match
                case "maxRuns" | "max-runs"                       => int.map(n => c.copy(maxRuns = Some(n)))
                case "maxOpen" | "max-open"                       => int.map(n => c.copy(maxOpen = Some(n)))
                case "rounds"                                     => int.map(n => c.copy(rounds = Some(n)))
                case "maxRunsPerArticle" | "max-runs-per-article" => int.map(n => c.copy(maxRunsPerArticle = Some(n)))
                case "noProgressRuns" | "no-progress-runs"        => int.map(n => c.copy(noProgressRuns = Some(n)))
                case "reopenLimit" | "reopen-limit"               => int.map(n => c.copy(reopenLimit = Some(n)))
                case "cooldownMinutes" | "cooldown-minutes"       => int.map(n => c.copy(cooldownMinutes = Some(n)))
                case "lowWater" | "low-water"                     => int.map(n => c.copy(lowWater = Some(n)))
                case "refillBatch" | "refill-batch"               => int.map(n => c.copy(refillBatch = Some(n)))
                case "maxFailures" | "max-failures"               => int.map(n => c.copy(maxFailures = Some(n)))
                case "staleAfterDays" | "stale-after-days"        => int.map(n => c.copy(staleAfterDays = Some(n)))
                case "minSources" | "min-sources"                 => int.map(n => c.copy(minSources = Some(n)))
                case "maxArticles" | "max-articles"               => int.map(n => c.copy(maxArticles = Some(n)))
                case "autoIngestPerDay" | "auto-ingest-per-day"   => int.map(n => c.copy(autoIngestPerDay = Some(n)))
                case "inferFilter" | "infer-filter"               => bool(k, v).map(b => c.copy(inferFilter = Some(b)))
                case "autoIngest" | "auto-ingest"                 => bool(k, v).map(b => c.copy(autoIngest = Some(b)))
                case "autoApprove" | "auto-approve"               => bool(k, v).map(b => c.copy(autoApprove = Some(b)))
                case "extendMinNew" | "extend-min-new"            => int.map(n => c.copy(extendMinNew = Some(n)))
                case "sweeps"                                     =>
                  val ws = v.split(",").toList.map(_.trim).filter(_.nonEmpty)
                  ws.find(w => Sweep.parse(w).isEmpty) match
                    case Some(w) =>
                      Left(s"$k: '$w' is not a sweep (one of: ${Sweep.values.map(_.wire).mkString(", ")})")
                    case None => Right(c.copy(sweeps = Some(ws.flatMap(Sweep.parse).distinct.map(_.wire))))
                case "dailyUsd" | "daily-usd" =>
                  v.toDoubleOption
                    .filter(_ >= 0)
                    .toRight(s"$k: '$v' is not a number of USD (0 = no cap)")
                    .map(d => c.copy(dailyUsd = Some(d)))
                case "budgetUsd" | "budget-usd" =>
                  v.toDoubleOption
                    .filter(_ > 0)
                    .toRight(s"$k: '$v' is not a positive number")
                    .map(d => c.copy(budgetUsd = Some(d)))
                case "includeTags" | "include-tags" | "tags" =>
                  Right(c.copy(includeTags = Some(v.split(",").toList.map(_.trim).filter(_.nonEmpty))))
                case "includeGoal" | "include-goal" | "goal" =>
                  Right(c.copy(includeGoal = Option(v).filter(_.nonEmpty)))
                case "models" =>
                  v.split(";")
                    .toList
                    .map(_.trim)
                    .filter(_.nonEmpty)
                    .map(Agents.ModelOverride.parse)
                    .partitionMap(identity) match
                    case (Nil, ms) => Right(c.copy(models = Some(ms)))
                    case (errs, _) => Left(errs.mkString("; "))
                case other => Left(s"unknown manager setting '$other'")
            case _ => Left(s"'$kv' is not key=value")
        }
      }

    private def bool(k: String, v: String): Either[String, Boolean] = v.toLowerCase match
      case "true" | "on" | "yes"  => Right(true)
      case "false" | "off" | "no" => Right(false)
      case _                      => Left(s"$k: '$v' is not true or false")

  /** Counts of a manager's items by status. */
  final case class ItemCounts(
      pending: Int = 0,
      held: Int = 0,
      active: Int = 0,
      done: Int = 0,
      stopped: Int = 0,
      failed: Int = 0,
      cancelled: Int = 0
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def open: Int = pending + held + active
  object ItemCounts:
    given Schema[ItemCounts] = summon[KindlingsSchema[ItemCounts]].schema

    def of(rows: List[(String, Int)]): ItemCounts =
      @tailrec def loop(
          rest: List[(String, Int)],
          pending: Int,
          held: Int,
          active: Int,
          done: Int,
          stopped: Int,
          failed: Int,
          cancelled: Int
      ): ItemCounts =
        rest match
          case Nil            => ItemCounts(pending, held, active, done, stopped, failed, cancelled)
          case (s, n) :: tail =>
            ItemStatus.parse(s) match
              case Some(ItemStatus.Pending)   => loop(tail, pending + n, held, active, done, stopped, failed, cancelled)
              case Some(ItemStatus.Held)      => loop(tail, pending, held + n, active, done, stopped, failed, cancelled)
              case Some(ItemStatus.Active)    => loop(tail, pending, held, active + n, done, stopped, failed, cancelled)
              case Some(ItemStatus.Done)      => loop(tail, pending, held, active, done + n, stopped, failed, cancelled)
              case Some(ItemStatus.Stopped)   => loop(tail, pending, held, active, done, stopped + n, failed, cancelled)
              case Some(ItemStatus.Failed)    => loop(tail, pending, held, active, done, stopped, failed + n, cancelled)
              case Some(ItemStatus.Cancelled) =>
                loop(tail, pending, held, active, done, stopped, failed, cancelled + n)
              case None => loop(tail, pending, held, active, done, stopped, failed, cancelled)
      loop(rows, 0, 0, 0, 0, 0, 0, 0)

  /** One manager event. */
  final case class ManagerEvent(
      id: Long,
      managerId: String,
      itemId: Option[Long] = None,
      runId: Option[String] = None,
      at: String = "",
      kind: String = "",
      text: String = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerEvent:
    given Schema[ManagerEvent] = summon[KindlingsSchema[ManagerEvent]].schema

  /** One manager, as listed. `statusNote` is derived by the supervisor ("starved: …", "at capacity", "idle", "3
    * pending"); `inFlight` = its runs not settled yet.
    */
  final case class ManagerInfo(
      id: String,
      kind: String,
      goal: Option[String] = None,
      state: String = State.Active.wire,
      pauseRuns: Boolean = false,
      config: ManagerConfig = ManagerConfig(),
      /** The settings in effect: `config` over the server's defaults. */
      effective: ManagerConfig = ManagerConfig(),
      statusNote: Option[String] = None,
      cron: Option[String] = None,
      nextTickAt: Option[String] = None,
      createdAt: String = "",
      updatedAt: String = "",
      counts: ItemCounts = ItemCounts(),
      inFlight: Int = 0,
      lastEvent: Option[ManagerEvent] = None,
      /** What its runs spent this UTC day: metered USD (what `dailyUsd` caps; a plan-billed call costs 0) and tokens
        * (every call's, plan-billed ones too).
        */
      spentTodayUsd: Double = 0,
      tokensToday: Long = 0,
      /** A watcher's ingest backlog: the legal open-access works waiting for a day's allowance (pending or held
        * `oa-ingest` items), and the days it takes to drain at the current allowance (0 = today; None = it does not
        * drain: auto-ingest off, a cap of 0, or the manager not active).
        */
      backlog: Int = 0,
      backlogDays: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerInfo:
    given Schema[ManagerInfo] = summon[KindlingsSchema[ManagerInfo]].schema

  /** Why an item is held and until when: a date, a source (DOI or citekey) entering the corpus, or a person deciding
    * approval `untilApproval` (the proposal its run left: the item is then settled with the decision, not run again) —
    * or, `focus` (the goal it belongs to), until that goal is one of the autopilot's active goals (the focus releases
    * it; a person pins the goal to run it now).
    */
  final case class Hold(
      until: Option[String] = None,
      untilSource: Option[String] = None,
      reason: String = "",
      hint: Option[Long] = None,
      untilApproval: Option[Long] = None,
      focus: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Hold:
    given Schema[Hold] = summon[KindlingsSchema[Hold]].schema

  /** What one evaluated run left: the open MAJORs before and after, the most often reopened finding, the escalated
    * ones, whether every open MAJOR was a missing-critique finding (`churn`), and the open pairs of the article's
    * critique checklist when the run was dispatched and after it (docs/research-review-convergence.md §6.8; None when
    * not known — a run evaluated before them).
    */
  final case class RunMark(
      run: String,
      before: Option[Int] = None,
      after: Int = 0,
      maxReopened: Int = 0,
      escalated: Int = 0,
      churn: Boolean = false,
      pairsBefore: Option[Int] = None,
      pairsAfter: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RunMark:
    given Schema[RunMark] = summon[KindlingsSchema[RunMark]].schema

  /** An item's own memory (`manager_items.state`): the evaluated runs, the open MAJORs and the open critique-checklist
    * pairs when the current run was dispatched, how often a person re-opened it; a compile item's approved `spec`; and
    * the sources a review item's article filter returned when its review converged (`filterHits` — more now makes it
    * stale).
    */
  final case class ItemState(
      history: List[RunMark] = Nil,
      before: Option[Int] = None,
      reopened: Int = 0,
      pairsBefore: Option[Int] = None,
      spec: Option[ArticleSpec] = None,
      filterHits: Option[Int] = None,
      oa: Option[OaWork] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ItemState:
    given Schema[ItemState] = summon[KindlingsSchema[ItemState]].schema

  /** A legal open-access work waiting in a watcher's ingest backlog (an `oa-ingest` item's state): what the sweep knew
    * of it, every research goal that found it (one entry however many watchers did; each goal is linked to the source
    * once it is ingested, or to the acquisition item when it has no legal file), why it was picked, the sweep run that
    * found it, and — moved by `repair-acquire` — the text of the acquisition item it replaced (kept for that item,
    * should it go back to the list).
    */
  final case class OaWork(
      doi: String,
      title: String = "",
      year: Option[Int] = None,
      authors: List[String] = Nil,
      venue: Option[String] = None,
      goals: List[String] = Nil,
      why: String = "",
      foundBy: Option[String] = None,
      acquireText: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object OaWork:
    given Schema[OaWork] = summon[KindlingsSchema[OaWork]].schema

  /** One work item. `run` is its current (or last) run, for its progress. */
  final case class ManagerItem(
      id: Long,
      managerId: String,
      kind: String,
      target: String,
      status: String,
      priority: Int = 0,
      attempts: Int = 0,
      failures: Int = 0,
      runId: Option[String] = None,
      evaluatedRun: Option[String] = None,
      notBefore: Option[String] = None,
      hold: Option[Hold] = None,
      state: ItemState = ItemState(),
      outcome: Option[String] = None,
      origin: String = ItemOrigin.Selection.wire,
      createdAt: String = "",
      updatedAt: String = "",
      run: Option[Agents.RunInfo] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerItem:
    given Schema[ManagerItem] = summon[KindlingsSchema[ManagerItem]].schema

  /** What a structured hint does besides being read by the runs: put targets (or those matching a tag, topic or goal)
    * first, hold them until a date or a source exists, skip them — or (`config`, only as a model's proposal a person
    * approves) change the manager's settings by the keys `config` gives.
    */
  final case class HintEffect(
      action: String,
      targets: List[String] = Nil,
      tag: Option[String] = None,
      topic: Option[String] = None,
      goal: Option[String] = None,
      until: Option[String] = None,
      untilSource: Option[String] = None,
      priority: Option[Int] = None,
      config: Option[ManagerConfig] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object HintEffect:
    given Schema[HintEffect] = summon[KindlingsSchema[HintEffect]].schema
    val Prioritise = "prioritise"
    val HoldAction = "hold"
    val Skip = "skip"
    val Config = "config"

    /** What a person picks in the hint box (applied at once). */
    val Actions = List(Prioritise, HoldAction, Skip)

    /** What a model may propose (applied only when a person approves it). */
    val Proposable = Actions :+ Config

  /** A structured action a model proposed for manager `managerId` from hint `hint` (its `text`), with why: the payload
    * of a `hint-effect` approval. `by` = who proposed it (`model:<provider:model>`, an MCP client).
    */
  final case class HintProposal(
      managerId: String,
      hint: Long,
      text: String,
      effect: HintEffect,
      by: String = "",
      why: String = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object HintProposal:
    given Schema[HintProposal] = summon[KindlingsSchema[HintProposal]].schema

  /** One hint. `effect` = the action applied (a person's, or a model's a person approved); `proposed` = the open
    * `hint-effect` approval waiting with a model's proposed action, and that action.
    */
  final case class ManagerHint(
      id: Long,
      managerId: String,
      itemId: Option[Long] = None,
      text: String = "",
      effect: Option[HintEffect] = None,
      author: String = "",
      standing: Boolean = true,
      createdAt: String = "",
      retiredAt: Option[String] = None,
      uses: Int = 0,
      proposal: Option[Long] = None,
      proposed: Option[HintEffect] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerHint:
    given Schema[ManagerHint] = summon[KindlingsSchema[ManagerHint]].schema

  /** `GET /api/agents/managers/{id}` — the manager, its active items (with their runs), its live hints and its newest
    * events.
    */
  final case class ManagerDetail(
      manager: ManagerInfo,
      current: List[ManagerItem] = Nil,
      hints: List[ManagerHint] = Nil,
      events: List[ManagerEvent] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerDetail:
    given Schema[ManagerDetail] = summon[KindlingsSchema[ManagerDetail]].schema

  /** A page of items: `total` matching the status filter. */
  final case class ItemPage(items: List[ManagerItem], total: Int, offset: Int, limit: Int)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object ItemPage:
    given Schema[ItemPage] = summon[KindlingsSchema[ItemPage]].schema

  /** A page of events, newest first; `before` the id to ask for the next older page with. */
  final case class EventPage(events: List[ManagerEvent], before: Option[Long] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object EventPage:
    given Schema[EventPage] = summon[KindlingsSchema[EventPage]].schema

  // ── requests ───────────────────────────────────────────────────────────────────────────────────

  /** `POST /api/agents/managers` — a new manager: `review` and `enrich` (library-wide), `seed` and `watch` (with its
    * `goal`). Created paused unless `paused` is false. `cron` = when it fires (a watcher's default:
    * `agents.managers.watch.cron`).
    */
  final case class ManagerCreate(
      kind: String,
      goal: Option[String] = None,
      config: ManagerConfig = ManagerConfig(),
      paused: Option[Boolean] = None,
      cron: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerCreate:
    given Schema[ManagerCreate] = summon[KindlingsSchema[ManagerCreate]].schema

  /** `PUT /api/agents/managers/{id}` — the settings (replaced as given; absent = the default), the cron. */
  final case class ManagerUpdate(config: Option[ManagerConfig] = None, cron: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object ManagerUpdate:
    given Schema[ManagerUpdate] = summon[KindlingsSchema[ManagerUpdate]].schema

  /** `POST /api/agents/managers/{id}/items` and `POST /api/agents/enqueue` — a target to drive (an article slug, a
    * citekey, a goal slug, by `kind`); an item that exists is re-opened at this priority.
    */
  final case class EnqueueRequest(kind: String, target: String, priority: Option[Int] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object EnqueueRequest:
    given Schema[EnqueueRequest] = summon[KindlingsSchema[EnqueueRequest]].schema

  /** What `POST /api/agents/enqueue` did: the manager the targets went to, the items it now has for them, and what was
    * not queued (an enriched source, a target that does not exist).
    */
  final case class EnqueueResult(managerId: String, items: List[ManagerItem] = Nil, skipped: List[String] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object EnqueueResult:
    given Schema[EnqueueResult] = summon[KindlingsSchema[EnqueueResult]].schema

  /** `PATCH …/items/{item}` — `action`: hold (with `until` or `untilSource`), release, priority (with `priority`),
    * skip, reopen.
    */
  final case class ItemPatch(
      action: String,
      priority: Option[Int] = None,
      until: Option[String] = None,
      untilSource: Option[String] = None,
      reason: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ItemPatch:
    given Schema[ItemPatch] = summon[KindlingsSchema[ItemPatch]].schema
    val Actions = List("hold", "release", "priority", "skip", "reopen")

  /** `POST …/hints` — `item` (an item id or its target) scopes it to one item; `once` = for the next run only. `effect`
    * is the structured part: the person's own (applied at once), or — `propose` — a model's proposal, recorded as a
    * `hint-effect` approval and applied only when a person approves it. `suggest` asks the server's planner model to
    * read the text and propose the action (a `hint-effect` run, then the same approval).
    */
  final case class HintAdd(
      text: String,
      item: Option[String] = None,
      once: Option[Boolean] = None,
      effect: Option[HintEffect] = None,
      author: Option[String] = None,
      propose: Option[Boolean] = None,
      suggest: Option[Boolean] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object HintAdd:
    given Schema[HintAdd] = summon[KindlingsSchema[HintAdd]].schema

  /** The verbs of `POST /api/agents/managers/{id}/{action}`. */
  val Actions: List[String] = List("pause", "resume", "stop")
