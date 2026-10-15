package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Persistent process managers (docs/research-process-managers.md): the vocabulary and the wire DTOs
  * of `/api/agents/managers/…`. A MANAGER owns WORK ITEMS ("article X until its review converges"),
  * decides the next run of each, evaluates what the run left behind, and stops an item with a reason a
  * person can act on. It takes HINTS from the person and records EVENTS. Wire types only; the policy is
  * pure in `core/jvm` ([[ManagerPolicy]]), the supervisor in the server. */
object Managers:

  /** What a manager drives. Phase 1 builds `review`; the others are reserved (phases 2 and 3). */
  enum Kind(val wire: String):
    case Review extends Kind("review")
    case Seed   extends Kind("seed")
    case Watch  extends Kind("watch")
    case Enrich extends Kind("enrich")

  object Kind:
    def parse(s: String): Option[Kind] = values.find(_.wire == s.trim.toLowerCase)

  /** The person's switch: `paused` starts nothing new (in-flight runs go on unless paused with them),
    * `stopped` cancelled its in-flight runs and items. */
  enum State(val wire: String):
    case Active  extends State("active")
    case Paused  extends State("paused")
    case Stopped extends State("stopped")

  object State:
    def parse(s: String): Option[State] = values.find(_.wire == s.trim.toLowerCase)

  enum ItemStatus(val wire: String):
    case Pending   extends ItemStatus("pending")
    case Held      extends ItemStatus("held")
    case Active    extends ItemStatus("active")
    case Done      extends ItemStatus("done")
    case Stopped   extends ItemStatus("stopped")
    case Failed    extends ItemStatus("failed")
    case Cancelled extends ItemStatus("cancelled")

    /** Settled: only a person (re-open, enqueue) brings it back. */
    def isFinal: Boolean = this == Done || this == Stopped || this == Failed || this == Cancelled

  object ItemStatus:
    def parse(s: String): Option[ItemStatus] = values.find(_.wire == s.trim.toLowerCase)

  /** Why an item exists: the manager's own selection, a person, a run that chained it, a hint. */
  enum ItemOrigin(val wire: String):
    case Selection extends ItemOrigin("selection")
    case Person    extends ItemOrigin("person")
    case Chain     extends ItemOrigin("chain")
    case Hint      extends ItemOrigin("hint")

  object ItemOrigin:
    def parse(s: String): Option[ItemOrigin] = values.find(_.wire == s.trim.toLowerCase)

  /** The kinds of a manager event. */
  object Feed:
    val Started   = "started"
    val Settled   = "settled"
    val Evaluated = "evaluated"
    val Stopped   = "stopped"
    val Escalated = "escalated"
    val Held      = "held"
    val Hint      = "hint"
    val Paused    = "paused"
    val Stall     = "stall"
    val Starved   = "starved"

  /** The item kind of the review manager: one article until its review converges. */
  val ArticleReview = "article-review"

  /** The library-wide review manager's id. */
  val ReviewId = "review"

  /** A manager's saved settings (`agent_managers.config`). Every field is optional: an absent one is
    * the server's default (`agents.managers.<kind>.*`). `models` = a model per role for its runs,
    * over the role's chain. `includeTags`/`includeGoal` narrow what the selection picks. */
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
      models: Option[List[Agents.ModelOverride]] = None
  ) derives KindlingsCodecAsObject:
    /** `other`'s values where it has one, else these. */
    def merge(other: ManagerConfig): ManagerConfig = ManagerConfig(
      other.maxRuns.orElse(maxRuns), other.maxOpen.orElse(maxOpen), other.rounds.orElse(rounds),
      other.maxRunsPerArticle.orElse(maxRunsPerArticle), other.noProgressRuns.orElse(noProgressRuns),
      other.reopenLimit.orElse(reopenLimit), other.cooldownMinutes.orElse(cooldownMinutes),
      other.lowWater.orElse(lowWater), other.refillBatch.orElse(refillBatch), other.maxFailures.orElse(maxFailures),
      other.budgetUsd.orElse(budgetUsd), other.includeTags.orElse(includeTags), other.includeGoal.orElse(includeGoal),
      other.models.orElse(models))
  object ManagerConfig:
    given Schema[ManagerConfig] = KindlingsSchema.derived[ManagerConfig].schema

    /** `k=v` pairs (the CLI's `--config`) → a config; a key it does not know, or a value that does not
      * parse, is an error. `models` takes `role=provider:model` entries separated by `;`; tags by `,`. */
    def parse(kvs: List[String]): Either[String, ManagerConfig] =
      kvs.foldLeft[Either[String, ManagerConfig]](Right(ManagerConfig())) { (acc, kv) =>
        acc.flatMap { c =>
          kv.split("=", 2) match
            case Array(k0, v0) =>
              val (k, v) = (k0.trim, v0.trim)
              def int = v.toIntOption.filter(_ >= 0).toRight(s"$k: '$v' is not a whole number")
              k match
                case "maxRuns" | "max-runs"                     => int.map(n => c.copy(maxRuns = Some(n)))
                case "maxOpen" | "max-open"                     => int.map(n => c.copy(maxOpen = Some(n)))
                case "rounds"                                   => int.map(n => c.copy(rounds = Some(n)))
                case "maxRunsPerArticle" | "max-runs-per-article" => int.map(n => c.copy(maxRunsPerArticle = Some(n)))
                case "noProgressRuns" | "no-progress-runs"      => int.map(n => c.copy(noProgressRuns = Some(n)))
                case "reopenLimit" | "reopen-limit"             => int.map(n => c.copy(reopenLimit = Some(n)))
                case "cooldownMinutes" | "cooldown-minutes"     => int.map(n => c.copy(cooldownMinutes = Some(n)))
                case "lowWater" | "low-water"                   => int.map(n => c.copy(lowWater = Some(n)))
                case "refillBatch" | "refill-batch"             => int.map(n => c.copy(refillBatch = Some(n)))
                case "maxFailures" | "max-failures"             => int.map(n => c.copy(maxFailures = Some(n)))
                case "budgetUsd" | "budget-usd" =>
                  v.toDoubleOption.filter(_ > 0).toRight(s"$k: '$v' is not a positive number").map(d => c.copy(budgetUsd = Some(d)))
                case "includeTags" | "include-tags" | "tags" =>
                  Right(c.copy(includeTags = Some(v.split(",").toList.map(_.trim).filter(_.nonEmpty))))
                case "includeGoal" | "include-goal" | "goal" =>
                  Right(c.copy(includeGoal = Option(v).filter(_.nonEmpty)))
                case "models" =>
                  v.split(";").toList.map(_.trim).filter(_.nonEmpty).map(Agents.ModelOverride.parse).partitionMap(identity) match
                    case (Nil, ms) => Right(c.copy(models = Some(ms)))
                    case (errs, _) => Left(errs.mkString("; "))
                case other => Left(s"unknown manager setting '$other'")
            case _ => Left(s"'$kv' is not key=value")
        }
      }

  /** Counts of a manager's items by status. */
  final case class ItemCounts(pending: Int = 0, held: Int = 0, active: Int = 0, done: Int = 0, stopped: Int = 0,
      failed: Int = 0, cancelled: Int = 0) derives KindlingsCodecAsObject:
    def open: Int = pending + held + active
  object ItemCounts:
    given Schema[ItemCounts] = KindlingsSchema.derived[ItemCounts].schema

    def of(rows: List[(String, Int)]): ItemCounts =
      rows.foldLeft(ItemCounts()) { case (c, (s, n)) =>
        ItemStatus.parse(s) match
          case Some(ItemStatus.Pending)   => c.copy(pending = c.pending + n)
          case Some(ItemStatus.Held)      => c.copy(held = c.held + n)
          case Some(ItemStatus.Active)    => c.copy(active = c.active + n)
          case Some(ItemStatus.Done)      => c.copy(done = c.done + n)
          case Some(ItemStatus.Stopped)   => c.copy(stopped = c.stopped + n)
          case Some(ItemStatus.Failed)    => c.copy(failed = c.failed + n)
          case Some(ItemStatus.Cancelled) => c.copy(cancelled = c.cancelled + n)
          case None                       => c
      }

  /** One manager event. */
  final case class ManagerEvent(id: Long, managerId: String, itemId: Option[Long] = None, runId: Option[String] = None,
      at: String = "", kind: String = "", text: String = "") derives KindlingsCodecAsObject
  object ManagerEvent:
    given Schema[ManagerEvent] = KindlingsSchema.derived[ManagerEvent].schema

  /** One manager, as listed. `statusNote` is derived by the supervisor ("starved: …", "at capacity",
    * "idle", "3 pending"); `inFlight` = its runs not settled yet. */
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
      lastEvent: Option[ManagerEvent] = None
  ) derives KindlingsCodecAsObject
  object ManagerInfo:
    given Schema[ManagerInfo] = KindlingsSchema.derived[ManagerInfo].schema

  /** Why an item is held and until when: a date, or a source (DOI or citekey) entering the corpus. */
  final case class Hold(until: Option[String] = None, untilSource: Option[String] = None, reason: String = "",
      hint: Option[Long] = None) derives KindlingsCodecAsObject
  object Hold:
    given Schema[Hold] = KindlingsSchema.derived[Hold].schema

  /** What one evaluated run left: the open MAJORs before and after, the most often reopened finding,
    * the escalated ones, and whether the open ones were all fresh missing-critique findings. */
  final case class RunMark(run: String, before: Option[Int] = None, after: Int = 0, maxReopened: Int = 0,
      escalated: Int = 0, churn: Boolean = false) derives KindlingsCodecAsObject
  object RunMark:
    given Schema[RunMark] = KindlingsSchema.derived[RunMark].schema

  /** An item's own memory (`manager_items.state`): the evaluated runs, the open MAJORs when the current
    * run was dispatched, how often a person re-opened it. */
  final case class ItemState(history: List[RunMark] = Nil, before: Option[Int] = None, reopened: Int = 0)
      derives KindlingsCodecAsObject
  object ItemState:
    given Schema[ItemState] = KindlingsSchema.derived[ItemState].schema

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
  ) derives KindlingsCodecAsObject
  object ManagerItem:
    given Schema[ManagerItem] = KindlingsSchema.derived[ManagerItem].schema

  /** What a structured hint does besides being read by the runs: put targets (or those matching a tag,
    * topic or goal) first, hold them until a date or a source exists, or skip them. */
  final case class HintEffect(action: String, targets: List[String] = Nil, tag: Option[String] = None,
      topic: Option[String] = None, goal: Option[String] = None, until: Option[String] = None,
      untilSource: Option[String] = None, priority: Option[Int] = None) derives KindlingsCodecAsObject
  object HintEffect:
    given Schema[HintEffect] = KindlingsSchema.derived[HintEffect].schema
    val Prioritise = "prioritise"
    val HoldAction = "hold"
    val Skip       = "skip"
    val Actions    = List(Prioritise, HoldAction, Skip)

  final case class ManagerHint(id: Long, managerId: String, itemId: Option[Long] = None, text: String = "",
      effect: Option[HintEffect] = None, author: String = "", standing: Boolean = true, createdAt: String = "",
      retiredAt: Option[String] = None, uses: Int = 0) derives KindlingsCodecAsObject
  object ManagerHint:
    given Schema[ManagerHint] = KindlingsSchema.derived[ManagerHint].schema

  /** `GET /api/agents/managers/{id}` — the manager, its active items (with their runs), its live hints
    * and its newest events. */
  final case class ManagerDetail(manager: ManagerInfo, current: List[ManagerItem] = Nil, hints: List[ManagerHint] = Nil,
      events: List[ManagerEvent] = Nil) derives KindlingsCodecAsObject
  object ManagerDetail:
    given Schema[ManagerDetail] = KindlingsSchema.derived[ManagerDetail].schema

  /** A page of items: `total` matching the status filter. */
  final case class ItemPage(items: List[ManagerItem], total: Int, offset: Int, limit: Int) derives KindlingsCodecAsObject
  object ItemPage:
    given Schema[ItemPage] = KindlingsSchema.derived[ItemPage].schema

  /** A page of events, newest first; `before` the id to ask for the next older page with. */
  final case class EventPage(events: List[ManagerEvent], before: Option[Long] = None) derives KindlingsCodecAsObject
  object EventPage:
    given Schema[EventPage] = KindlingsSchema.derived[EventPage].schema

  // ── requests ───────────────────────────────────────────────────────────────────────────────────

  /** `POST /api/agents/managers` — a new manager (phase 1: `review`). */
  final case class ManagerCreate(kind: String, goal: Option[String] = None, config: ManagerConfig = ManagerConfig(),
      paused: Option[Boolean] = None) derives KindlingsCodecAsObject
  object ManagerCreate:
    given Schema[ManagerCreate] = KindlingsSchema.derived[ManagerCreate].schema

  /** `PUT /api/agents/managers/{id}` — the settings (replaced as given; absent = the default), the cron. */
  final case class ManagerUpdate(config: Option[ManagerConfig] = None, cron: Option[String] = None)
      derives KindlingsCodecAsObject
  object ManagerUpdate:
    given Schema[ManagerUpdate] = KindlingsSchema.derived[ManagerUpdate].schema

  /** `POST /api/agents/managers/{id}/items` and `POST /api/agents/enqueue` — a target to drive (an
    * article slug for the review manager); an item that exists is re-opened at this priority. */
  final case class EnqueueRequest(kind: String, target: String, priority: Option[Int] = None)
      derives KindlingsCodecAsObject
  object EnqueueRequest:
    given Schema[EnqueueRequest] = KindlingsSchema.derived[EnqueueRequest].schema

  /** `PATCH …/items/{item}` — `action`: hold (with `until` or `untilSource`), release, priority (with
    * `priority`), skip, reopen. */
  final case class ItemPatch(action: String, priority: Option[Int] = None, until: Option[String] = None,
      untilSource: Option[String] = None, reason: Option[String] = None) derives KindlingsCodecAsObject
  object ItemPatch:
    given Schema[ItemPatch] = KindlingsSchema.derived[ItemPatch].schema
    val Actions = List("hold", "release", "priority", "skip", "reopen")

  /** `POST …/hints` — `item` (an item id or its target) scopes it to one item; `once` = for the next run
    * only. `effect` is the structured part the person chose. */
  final case class HintAdd(text: String, item: Option[String] = None, once: Option[Boolean] = None,
      effect: Option[HintEffect] = None, author: Option[String] = None) derives KindlingsCodecAsObject
  object HintAdd:
    given Schema[HintAdd] = KindlingsSchema.derived[HintAdd].schema

  /** The verbs of `POST /api/agents/managers/{id}/{action}`. */
  val Actions: List[String] = List("pause", "resume", "stop")

/** The managers as Markdown — every surface renders the same text. Pure. */
object ManagersMarkdown:
  import Managers.*

  private def counts(c: ItemCounts): String =
    List("pending" -> c.pending, "held" -> c.held, "active" -> c.active, "done" -> c.done, "stopped" -> c.stopped,
      "failed" -> c.failed, "cancelled" -> c.cancelled).filter(_._2 > 0).map((k, n) => s"$n $k").mkString(", ") match
      case "" => "no items"
      case s  => s

  def line(m: ManagerInfo): String =
    val goal = m.goal.fold("")(g => s" · goal $g")
    val runs = if m.pauseRuns then " (its runs paused too)" else ""
    s"**${m.id}** (${m.kind}$goal) · ${m.state}$runs · ${counts(m.counts)} · ${m.inFlight} run(s) in flight" +
      m.statusNote.filter(_.nonEmpty).fold("")(n => s" · $n") +
      m.lastEvent.fold("")(e => s" · last: ${e.kind} ${e.text.take(120)}")

  def list(ms: List[ManagerInfo]): String =
    if ms.isEmpty then "_No managers._" else "# Managers\n\n" + ms.map(m => s"- ${line(m)}").mkString("\n")

  def item(i: ManagerItem): String =
    val run = i.run.fold(i.runId.fold("")(r => s" · run ${r.take(8)}"))(r =>
      s" · run ${r.id.take(8)} ${r.status}" + r.progress.filter(_.nonEmpty).fold("")(p => s" (${p.take(80)})"))
    val hold = i.hold.fold("")(h => s" · held${h.until.fold("")(u => s" until $u")}${h.untilSource.fold("")(s => s" until $s exists")}" +
      Option(h.reason).filter(_.nonEmpty).fold("")(r => s": $r"))
    val nb = i.notBefore.fold("")(t => s" · not before $t")
    s"#${i.id} ${i.target} · ${i.status} · priority ${i.priority} · ${i.attempts} run(s)" +
      (if i.failures > 0 then s", ${i.failures} failed" else "") + run + hold + nb +
      i.outcome.filter(_.nonEmpty).fold("")(o => s" · $o")

  def items(p: ItemPage): String =
    if p.items.isEmpty then "_No items._"
    else s"Items ${p.offset + 1}–${p.offset + p.items.size} of ${p.total}\n\n" + p.items.map(i => s"- ${item(i)}").mkString("\n")

  def event(e: ManagerEvent): String =
    s"${e.at} · ${e.kind}${e.itemId.fold("")(i => s" · item #$i")}${e.runId.fold("")(r => s" · run ${r.take(8)}")} · ${e.text}"

  def events(p: EventPage): String =
    if p.events.isEmpty then "_No events._" else p.events.map(e => s"- ${event(e)}").mkString("\n")

  def hint(h: ManagerHint): String =
    val scope = h.itemId.fold("manager-wide")(i => s"item #$i")
    val eff = h.effect.fold("")(e => s" · ${e.action}" + (e.targets ++ e.tag.map(t => s"tag $t") ++ e.topic.map(t => s"topic $t") ++
      e.goal.map(g => s"goal $g")).mkString(" ", ", ", "") + e.until.fold("")(u => s" until $u") +
      e.untilSource.fold("")(s => s" until $s exists"))
    s"#${h.id} (${if h.standing then "standing" else "once"}, $scope, by ${h.author}$eff) ${h.text}" +
      h.retiredAt.fold("")(r => s" · retired $r") + (if h.uses > 0 then s" · reached ${h.uses} run(s)" else "")

  def detail(d: ManagerDetail): String =
    val m = d.manager
    val cfg = m.effective
    val settings = List(cfg.maxRuns.map(n => s"max-runs $n"), cfg.rounds.map(n => s"rounds $n"),
      cfg.maxRunsPerArticle.map(n => s"max-runs-per-article $n"), cfg.noProgressRuns.map(n => s"no-progress-runs $n"),
      cfg.reopenLimit.map(n => s"reopen-limit $n"), cfg.cooldownMinutes.map(n => s"cooldown ${n} min"),
      cfg.includeTags.filter(_.nonEmpty).map(t => s"tags ${t.mkString(", ")}"), cfg.includeGoal.map(g => s"goal $g"),
      cfg.models.filter(_.nonEmpty).map(ms => s"models ${ms.map(_.text).mkString("; ")}"),
      cfg.budgetUsd.map(u => s"budget $u USD/run")).flatten.mkString(" · ")
    s"# Manager ${m.id}\n\n- ${line(m)}\n- settings: $settings\n" +
      "\n## In flight\n\n" + (if d.current.isEmpty then "_none_" else d.current.map(i => s"- ${item(i)}").mkString("\n")) +
      "\n\n## Hints\n\n" + (if d.hints.isEmpty then "_none_" else d.hints.map(h => s"- ${hint(h)}").mkString("\n")) +
      "\n\n## Events\n\n" + (if d.events.isEmpty then "_none_" else d.events.map(e => s"- ${event(e)}").mkString("\n"))
