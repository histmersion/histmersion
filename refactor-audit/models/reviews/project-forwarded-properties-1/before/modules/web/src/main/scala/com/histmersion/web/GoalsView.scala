package com.histmersion.web

import com.histmersion.api.Wording
import com.histmersion.api.automation.Agents.{ApprovalInfo, ApprovalKind, ApprovalState, Pipeline, RunInfo, RunRequest}
import com.histmersion.api.automation.Goal.Plans.*
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.automation.{Agents, AgentsMarkdown, Goal, ListFilters, Managers, Queue}
import com.histmersion.api.research.Articles

/** The research goals (docs/research-embedded-agents.md §14): the goals window (the goals as a tree) and one window per
  * goal — its sub-goals and links (sources, articles, search queries, queue items), the plan waiting for approval with
  * approve / reject, the approved plan, the schedule editor (currency check and acquisition sweep: cron, on/off, cost
  * cap) and the goal's runs. A "plan" button asks the server's planner for a plan (only when the server runs agents);
  * every change is behind the write gate. Nothing here polls: a window reloads when it opens, after a change it made,
  * and when a run settles ([[ApprovalsView.changed]]).
  */
object GoalsView:

  /** How many goals the tree lists, and how many of a goal's runs its window shows. */
  type TreeWalkResultPart2 = TreeWalkResultPart2.T
  object TreeWalkResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeWalkParent = TreeWalkParent.T
  object TreeWalkParent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeWalkDepth = TreeWalkDepth.T
  object TreeWalkDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeWalkSeen = TreeWalkSeen.T
  object TreeWalkSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedNoteMadeId = CreatedNoteMadeId.T
  object CreatedNoteMadeId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedNoteMadeResume = CreatedNoteMadeResume.T
  object CreatedNoteMadeResume extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaFormFieldV = NewAreaFormFieldV.T
  object NewAreaFormFieldV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaFormFieldName = NewAreaFormFieldName.T
  object NewAreaFormFieldName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaFormFieldHint = NewAreaFormFieldHint.T
  object NewAreaFormFieldHint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaFormBoxV = NewAreaFormBoxV.T
  object NewAreaFormBoxV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaFormBoxText = NewAreaFormBoxText.T
  object NewAreaFormBoxText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPaneWriteWhat = GoalPaneWriteWhat.T
  object GoalPaneWriteWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type TreeResultPart2 = TreeResultPart2.T
  object TreeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StagesPart1 = StagesPart1.T
  object StagesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StagesPart2 = StagesPart2.T
  object StagesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MeaningResult = MeaningResult.T
  object MeaningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MeaningStatus = MeaningStatus.T
  object MeaningStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingPlanSlug = PendingPlanSlug.T
  object PendingPlanSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultCronResult = DefaultCronResult.T
  object DefaultCronResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkToAddRaw = LinkToAddRaw.T
  object LinkToAddRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkToAddNote = LinkToAddNote.T
  object LinkToAddNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugifyResult = SlugifyResult.T
  object SlugifyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugifyTitle = SlugifyTitle.T
  object SlugifyTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaResultPart1 = NewAreaResultPart1.T
  object NewAreaResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaTitle = NewAreaTitle.T
  object NewAreaTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaSlug = NewAreaSlug.T
  object NewAreaSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaNotes = NewAreaNotes.T
  object NewAreaNotes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaTags = NewAreaTags.T
  object NewAreaTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaPlan = NewAreaPlan.T
  object NewAreaPlan extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaSeed = NewAreaSeed.T
  object NewAreaSeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaWatch = NewAreaWatch.T
  object NewAreaWatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaAutoIngest = NewAreaAutoIngest.T
  object NewAreaAutoIngest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewAreaPerDay = NewAreaPerDay.T
  object NewAreaPerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedNoteResult = CreatedNoteResult.T
  object CreatedNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedNotePlanned = CreatedNotePlanned.T
  object CreatedNotePlanned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedNoteSeeded = CreatedNoteSeeded.T
  object CreatedNoteSeeded extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedNoteWatched = CreatedNoteWatched.T
  object CreatedNoteWatched extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedNoteActive = CreatedNoteActive.T
  object CreatedNoteActive extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BadgeStatus = BadgeStatus.T
  object BadgeStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BadgeText = BadgeText.T
  object BadgeText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPaneSlug = GoalPaneSlug.T
  object GoalPaneSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanSectionSlug = PlanSectionSlug.T
  object PlanSectionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanSectionBusy = PlanSectionBusy.T
  object PlanSectionBusy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinksSectionSlug = LinksSectionSlug.T
  object LinksSectionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinksSectionBusy = LinksSectionBusy.T
  object LinksSectionBusy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinksSectionWriteArg1 = LinksSectionWriteArg1.T
  object LinksSectionWriteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchedulesSectionSlug = SchedulesSectionSlug.T
  object SchedulesSectionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchedulesSectionBusy = SchedulesSectionBusy.T
  object SchedulesSectionBusy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchedulesSectionWriteArg1 = SchedulesSectionWriteArg1.T
  object SchedulesSectionWriteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Limit = 500
  val RunLimit = 15

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** Goals as a tree, depth first: each root (no parent, or a parent that is not listed) then its sub-goals, by slug,
    * with their depth. A cycle cannot loop: a goal is listed once.
    */
  def tree(goals: List[Goal]): List[(Goal, com.histmersion.web.GoalsView.TreeResultPart2)] =
    val slugs = goals.map(_.slug).toSet
    val byParent = goals.groupBy(g => g.parentSlug.filter(slugs))
    def walk(parent: Option[com.histmersion.web.GoalsView.TreeWalkParent], depth: com.histmersion.web.GoalsView.TreeWalkDepth, seen: Set[com.histmersion.web.GoalsView.TreeWalkSeen]): List[(Goal, com.histmersion.web.GoalsView.TreeWalkResultPart2)] =
      byParent.getOrElse(parent, Nil).sortBy(_.slug).filterNot(g => seen(g.slug)).flatMap { g =>
        (g, depth) :: walk(Some(g.slug), depth + 1, seen + g.slug)
      }
    walk(None, 0, Set.empty)

  /** What each goal stage means, in the order a goal moves through them; the legend above the tree and the tooltip of
    * every status badge.
    */
  val stages: List[(com.histmersion.web.GoalsView.StagesPart1, com.histmersion.web.GoalsView.StagesPart2)] = Goal.Stages.all

  def meaning(status: com.histmersion.web.GoalsView.MeaningStatus): Option[com.histmersion.web.GoalsView.MeaningResult] = Goal.Stages.meaning(status)

  /** The open plan approval of a goal, if a plan run is waiting on one. */
  def pendingPlan(as: List[ApprovalInfo], slug: com.histmersion.web.GoalsView.PendingPlanSlug): Option[ApprovalInfo] =
    as.find(a =>
      a.approvalKind.contains(ApprovalKind.GoalPlan) && a.subject.contains(slug) &&
        a.state == ApprovalState.Open.wire
    )

  /** What the schedule editor starts from: the schedule's own cron, else a sensible default. */
  def defaultCron(k: ScheduleKind): com.histmersion.web.GoalsView.DefaultCronResult = k match
    case ScheduleKind.Currency    => "@weekly"
    case ScheduleKind.Acquisition => "@monthly"

  /** The links of one kind. */
  def linksOf(ls: List[GoalLink], k: LinkKind): List[GoalLink] = ls.filter(_.kind == k)

  /** A link to add, from the editor's kind and text: `None` when the text is blank. */
  def linkToAdd(kind: LinkKind, raw: com.histmersion.web.GoalsView.LinkToAddRaw, note: com.histmersion.web.GoalsView.LinkToAddNote): Option[LinkAdd] =
    Option(raw).map(_.trim).filter(_.nonEmpty).map(r => LinkAdd(kind, r, Option(note).map(_.trim).filter(_.nonEmpty)))

  /** A slug from a title: lower case, runs of anything but letters and digits as one '-'. */
  def slugify(title: com.histmersion.web.GoalsView.SlugifyTitle): com.histmersion.web.GoalsView.SlugifyResult =
    title.trim.toLowerCase.replaceAll("[^a-z0-9]+", "-").stripPrefix("-").stripSuffix("-")

  /** What the New research area form creates: the goal (at `slug`), and whether to plan it (a plan-goal run), seed it
    * (a seeding manager, created paused, whose first item proposes its articles) and watch it (a watcher, created
    * paused, with these settings: its weekly acquisition sweep and currency check, and — opted in — the legal
    * open-access ingest and its daily cap).
    */
  final case class NewArea(
      slug: NewArea.Slug,
      put: Goal.Put,
      plan: NewArea.Plan,
      seed: NewArea.Seed,
      watch: Option[Managers.ManagerConfig] = None
  )

  object NewArea:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Plan = Plan.T
    object Plan extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Seed = Seed.T
    object Seed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The form → what to create. A blank slug comes from the title; tags are comma-separated. Watching: auto-ingest is
    * OFF unless ticked; its cap (blank = the server's default) a whole number.
    */
  def newArea(
      title: com.histmersion.web.GoalsView.NewAreaTitle,
      slug: com.histmersion.web.GoalsView.NewAreaSlug,
      notes: com.histmersion.web.GoalsView.NewAreaNotes,
      tags: com.histmersion.web.GoalsView.NewAreaTags,
      plan: com.histmersion.web.GoalsView.NewAreaPlan,
      seed: com.histmersion.web.GoalsView.NewAreaSeed,
      watch: com.histmersion.web.GoalsView.NewAreaWatch = false,
      autoIngest: com.histmersion.web.GoalsView.NewAreaAutoIngest = false,
      perDay: com.histmersion.web.GoalsView.NewAreaPerDay = ""
  ): Either[com.histmersion.web.GoalsView.NewAreaResultPart1, NewArea] =
    val t = title.trim
    val s = Option(slug.trim).filter(_.nonEmpty).getOrElse(slugify(t))
    val cap = perDay.trim
    if t.isEmpty then Left("a research area needs a title")
    else if watch && autoIngest && cap.nonEmpty && cap.toIntOption.forall(_ < 0) then
      Left("ingests a day: a whole number")
    else
      val ok = s
      val w = Option.when(watch)(
        Managers.ManagerConfig(
          autoIngest = Some(autoIngest),
          autoIngestPerDay = if autoIngest then cap.toIntOption else None
        )
      )
      Right(
        NewArea(
          ok,
          Goal.Put(
            t,
            com.histmersion.api.automation.Goal.Status.Planned,
            Option(notes.trim).filter(_.nonEmpty),
            tags.split(",").toList.map(_.trim).filter(_.nonEmpty).distinct
          ),
          plan,
          seed,
          w
        )
      )

  /** What the form did, in one line. */
  def createdNote(
      a: NewArea,
      planned: com.histmersion.web.GoalsView.CreatedNotePlanned,
      seeded: Option[com.histmersion.web.GoalsView.CreatedNoteSeeded],
      watched: Option[com.histmersion.web.GoalsView.CreatedNoteWatched] = None,
      active: Set[com.histmersion.web.GoalsView.CreatedNoteActive] = Set.empty
  ): com.histmersion.web.GoalsView.CreatedNoteResult =
    def made(id: com.histmersion.web.GoalsView.CreatedNoteMadeId, resume: com.histmersion.web.GoalsView.CreatedNoteMadeResume) =
      if active(id) then s"$id created active (the autopilot is on)" else s"$id created paused — resume it $resume"
    s"research area ${a.slug} created" + (if planned then "; the planner is running (its plan waits for you)" else "") +
      seeded.fold("")(id => s"; seeding manager ${made(id, "to propose its articles")}") +
      watched.fold("")(id =>
        s"; watcher ${made(id, "for its weekly sweeps")}" +
          (if a.watch.exists(_.autoIngest.contains(true)) then " (it ingests legal open-access finds)" else "")
      )

  // ── the goals window ────────────────────────────────────────────────────────────────────────────

  private val goals = Var(List.empty[Goal])
  private val loaded = Var(false)
  private val failure = Var(Option.empty[String])

  private def refresh(): Unit =
    ApiClient.goals(Some(Limit)).onComplete { r =>
      r.fold(e => failure.set(Some(e.getMessage)), gs => { failure.set(None); goals.set(gs.goals); loaded.set(true) })
    }

  def pane: HtmlElement =
    div(
      cls := "tool-pane goals-pane",
      onMountCallback { _ => loaded.set(false); refresh() },
      div(cls := "pane-bar", button(typ := "button", cls := "btn", "refresh", onClick --> (_ => refresh()))),
      newAreaForm(() => refresh()),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child <-- goals.signal.combineWith(loaded.signal).map { (gs, isLoaded) =>
        if !isLoaded then p(cls := "muted", "Loading…")
        else if gs.isEmpty then
          p(cls := "muted", "No research goals yet. Create one with histmersion goals or the put_goal tool.")
        else
          div(
            legend,
            ul(
              cls := "goal-tree",
              tree(gs).map((g, depth) =>
                li(
                  cls := "goal-row",
                  styleAttr := s"--depth:$depth",
                  badge(g.status),
                  WinLink(WindowKey.Goal(g.slug), g.title, title := g.slug)
                )
              )
            )
          )
      }
    )

  /** A goal's status as a coloured badge, its meaning on hover. */
  def badge(status: com.histmersion.web.GoalsView.BadgeStatus, text: com.histmersion.web.GoalsView.BadgeText = ""): HtmlElement =
    span(
      cls := s"job-state goal-status state-$status",
      meaning(status).map(m => title := m),
      if text.nonEmpty then text else Wording.capitalize(status)
    )

  /** The stages, left to right, each badge with what it means. */
  private def legend: HtmlElement =
    ul(cls := "goal-legend", aria.label := "goal stages", stages.map((s, m) => li(badge(s), span(cls := "muted", m))))

  /** New research area: a goal, and — ticked — a plan-goal run for it, a seeding manager and a watcher (both created
    * paused; the watcher's auto-ingest of legal open-access finds is off unless ticked, with its daily cap). The slug
    * must be free: an existing goal is never replaced.
    */
  private def newAreaForm(refresh: () => Unit): HtmlElement =
    val name = Var(""); val slug = Var(""); val notes = Var(""); val tags = Var("")
    val plan = Var(true); val seed = Var(true); val watch = Var(false); val ingest = Var(false); val perDay = Var("")
    val busy = Var(false)
    val msg = Var(Option.empty[Either[String, String]])
    def field(v: Var[com.histmersion.web.GoalsView.NewAreaFormFieldV], name: com.histmersion.web.GoalsView.NewAreaFormFieldName, hint: com.histmersion.web.GoalsView.NewAreaFormFieldHint) =
      input(
        typ := "text",
        cls := "field",
        aria.label := name,
        placeholder := hint,
        controlled(value <-- v.signal, onInput.mapToValue --> v.writer)
      )
    def box(v: Var[com.histmersion.web.GoalsView.NewAreaFormBoxV], text: com.histmersion.web.GoalsView.NewAreaFormBoxText) =
      label(
        cls := "goal-on",
        input(typ := "checkbox", controlled(checked <-- v.signal, onClick.mapToChecked --> v.writer)),
        s" $text"
      )
    def submit(): Unit =
      newArea(
        name.now(),
        slug.now(),
        notes.now(),
        tags.now(),
        plan.now(),
        seed.now(),
        watch.now(),
        ingest.now(),
        perDay.now()
      ) match
        case Left(m)  => msg.set(Some(Left(m)))
        case Right(a) =>
          busy.set(true)
          val created =
            ApiClient
              .getGoal(a.slug)
              .transformWith {
                case scala.util.Success(_) =>
                  scala.concurrent.Future.failed(new Exception(s"a goal '${a.slug}' exists already"))
                case scala.util.Failure(_) => ApiClient.putGoal(a.slug, a.put)
              }
              .flatMap { _ =>
                val planned = if a.plan then
                  ApiClient.startRun(Agents.RunRequest(Pipeline.PlanGoal.wire, a.slug)).map(_ => true)
                else scala.concurrent.Future.successful(false)
                planned.flatMap { p =>
                  val seeded = if a.seed then
                    ApiClient.managerCreate(Managers.ManagerCreate("seed", Some(a.slug))).map(m => Some(m))
                  else scala.concurrent.Future.successful(None)
                  seeded.flatMap { sd =>
                    val watched = a.watch.fold(scala.concurrent.Future.successful(Option.empty[Managers.ManagerInfo]))(
                      c => ApiClient.managerCreate(Managers.ManagerCreate("watch", Some(a.slug), c)).map(m => Some(m))
                    )
                    watched.map(w =>
                      createdNote(
                        a,
                        p,
                        sd.map(_.id),
                        w.map(_.id),
                        (sd.toList ++ w.toList).filter(_.state == Managers.State.Active.wire).map(_.id).toSet
                      )
                    )
                  }
                }
              }
          created.onComplete { r =>
            busy.set(false)
            msg.set(Some(r.fold(e => Left(e.getMessage), Right(_))))
            if r.isSuccess then
              name.set(""); slug.set(""); notes.set(""); tags.set(""); watch.set(false); ingest.set(false);
              perDay.set("")
              WindowManager.open(WindowKey.Goal(a.slug))
            refresh()
          }
    htmlTag("details")(
      cls := "goal-new",
      htmlTag("summary")("New research area"),
      child <-- Auth.writable.map { w =>
        if !w then Auth.keyNeeded("create a research area")
        else
          div(
            cls := "goal-new-form",
            div(cls := "form-row", field(name, "title", "title"), field(slug, "slug", "slug (from the title)")),
            div(
              cls := "form-row",
              field(notes, "notes", "what it should establish (optional)"),
              field(tags, "tags", "tags, comma-separated (optional)")
            ),
            div(
              cls := "form-row goal-new-options",
              box(plan, "plan (sub-goals, queries, works)"),
              box(seed, "seed (propose and compile its articles)"),
              label(
                cls := "goal-on",
                title := "a weekly acquisition sweep (a model picks what to acquire) and a currency check",
                input(typ := "checkbox", controlled(checked <-- watch.signal, onClick.mapToChecked --> watch.writer)),
                " watch"
              ),
              button(
                typ := "button",
                cls := "btn btn--primary",
                "create",
                disabled <-- busy,
                onClick --> (_ => submit())
              )
            ),
            child.maybe <-- watch.signal.map(on =>
              Option.when(on)(
                div(
                  cls := "form-row goal-new-watch",
                  label(
                    cls := "goal-on",
                    title := "ingest the legal open-access works its sweep picks (legal open access only); off: they go to the acquisition list",
                    input(
                      typ := "checkbox",
                      controlled(checked <-- ingest.signal, onClick.mapToChecked --> ingest.writer)
                    ),
                    " ingest legal open-access finds (off by default)"
                  ),
                  input(
                    typ := "number",
                    cls := "field goal-new-cap",
                    aria.label := "ingests a day at most",
                    placeholder := "a day at most (5)",
                    disabled <-- ingest.signal.map(!_),
                    controlled(value <-- perDay.signal, onInput.mapToValue --> perDay.writer)
                  )
                )
              )
            ),
            child.maybe <-- msg.signal.map(_.map {
              case Left(m)  => p(cls := "form-error", role := "alert", m)
              case Right(m) => p(cls := "form-ok", m)
            })
          )
      }
    )

  // ── one goal ────────────────────────────────────────────────────────────────────────────────────

  /** One goal's window; its state is its own (several goals can be open at once). */
  def goalPane(slug: com.histmersion.web.GoalsView.GoalPaneSlug): HtmlElement =
    val view = Var(Option.empty[GoalView])
    val runs = Var(List.empty[RunInfo])
    val approvals = Var(List.empty[ApprovalInfo])
    val failure = Var(Option.empty[String])
    val message = Var(Option.empty[String])
    val busy = Var(false)

    def refresh(): Unit =
      ApiClient
        .goalView(slug)
        .onComplete(_.fold(e => failure.set(Some(e.getMessage)), v => { failure.set(None); view.set(Some(v)) }))
      if RunsView.enabled.now() then
        ApiClient.agentRuns(ListFilters.RunFilter(goal = Some(slug)), Some(RunLimit)).foreach(r => runs.set(r._1))
      // the goal's own open plan, filtered on the server (it used to be looked for among the newest 100 open approvals)
      ApiClient
        .approvals(
          ListFilters.ApprovalFilter(kind = List(ApprovalKind.GoalPlan.wire), goal = Some(slug)),
          Some(ApprovalsView.Limit)
        )
        .foreach(a => approvals.set(a._1))

    def write[A](what: com.histmersion.web.GoalsView.GoalPaneWriteWhat)(f: => scala.concurrent.Future[A]): Unit =
      busy.set(true)
      f.onComplete { r =>
        busy.set(false)
        r.fold(
          e => failure.set(Some(s"$what: ${e.getMessage}")),
          _ => { failure.set(None); message.set(Some(s"$what: done")) }
        )
        refresh()
      }

    def plan(): Unit =
      busy.set(true)
      ApiClient.startRun(RunRequest(Pipeline.PlanGoal.wire, slug)).onComplete { r =>
        busy.set(false)
        r.fold(
          e => failure.set(Some(s"plan: ${e.getMessage}")),
          run => {
            failure.set(None)
            message.set(Some("The planner is running; its plan will wait here for your approval."))
            WindowManager.open(WindowKey.Run(run.id))
          }
        )
        refresh()
      }

    div(
      cls := "tool-pane goal-pane",
      onMountCallback(_ => refresh()),
      ApprovalsView.changed.events --> (_ => refresh()),
      view.signal.changes.collect { case Some(v) if v.goal.title.trim.nonEmpty => v.goal.title } --> (t =>
        WindowManager.retitle(WindowKey.Goal(slug), s"Goal · $t")
      ),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child.maybe <-- message.signal.map(_.map(m => p(cls := "form-ok", m))),
      child <-- view.signal.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(v) =>
          div(
            cls := "goal-detail",
            div(cls := "run-head", badge(v.goal.status), span(cls := "run-title", s" ${v.goal.title}")),
            v.goal.notes.filter(_.nonEmpty).map(n => p(n)),
            dl(
              cls := "run-facts",
              dt("Identifier"),
              dd(code(v.goal.slug)),
              Option
                .when(v.goal.tags.nonEmpty)(List(dt("Tags"), dd(v.goal.tags.map(Wording.value).mkString(", "))))
                .toList
                .flatten,
              Option
                .when(v.goal.regions.nonEmpty)(
                  List(dt("Regions"), dd(v.goal.regions.map(Wording.value).mkString(", ")))
                )
                .toList
                .flatten,
              v.goal.parentSlug.toList.flatMap(ps => List(dt("part of"), dd(WinLink(WindowKey.Goal(ps), ps)))),
              v.budgetUsd.toList.flatMap(b => List(dt("run cap"), dd(s"${AgentsMarkdown.usd(b)} USD")))
            ),
            planSection(slug, approvals.signal, busy.signal, () => plan()),
            ManagersView.goalActions(v.goal.slug),
            Option.when(v.children.nonEmpty)(
              div(
                h4("Sub-goals"),
                ul(v.children.map(c => li(WinLink(WindowKey.Goal(c.slug), c.title), " ", badge(c.status))))
              )
            ),
            linksSection(slug, v.links, busy.signal, write),
            schedulesSection(slug, v.schedules, busy.signal, write),
            v.plan.map(pl =>
              htmlTag("details")(
                cls := "goal-plan",
                htmlTag("summary")("the approved plan"),
                ApprovalsView.planBlock(pl)
              )
            ),
            runsSection(runs.signal)
          )
      }
    )

  private def planSection(
      slug: com.histmersion.web.GoalsView.PlanSectionSlug,
      approvals: Signal[List[ApprovalInfo]],
      busy: Signal[com.histmersion.web.GoalsView.PlanSectionBusy],
      plan: () => Unit
  ): HtmlElement =
    div(
      cls := "goal-section",
      h4("Plan"),
      child <-- approvals.map(as => pendingPlan(as, slug)).map {
        case Some(a) =>
          div(
            p(cls := "muted", "A plan waits for your decision. Nothing is created before you approve it."),
            ul(cls := "job-list approval-list", ApprovalsView.row(a))
          )
        case None =>
          div(child <-- RunsView.enabled.signal.combineWith(Auth.writable).map { (agents, w) =>
            if !agents then p(cls := "muted", "Planning needs the server's embedded agents (agents.enabled).")
            else if !w then Auth.keyNeeded("plan this goal")
            else
              div(
                cls := "form-row",
                button(
                  typ := "button",
                  cls := "btn btn--primary",
                  "plan",
                  disabled <-- busy,
                  onClick --> (_ => plan())
                ),
                span(
                  cls := "muted",
                  " Sub-goals, search queries and works to read; you approve them before anything is created."
                )
              )
          })
      }
    )

  private def linksSection(
      slug: com.histmersion.web.GoalsView.LinksSectionSlug,
      links: List[GoalLink],
      busy: Signal[com.histmersion.web.GoalsView.LinksSectionBusy],
      write: com.histmersion.web.GoalsView.LinksSectionWriteArg1 => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    val kind = Var(LinkKind.Source)
    val ref = Var("")
    val note = Var("")
    def entry(l: GoalLink): HtmlElement =
      li(
        l.kind match
          case LinkKind.Source    => SourceLink(l.ref, label = l.title.map(t => s"${l.ref} — $t"))
          case LinkKind.Article   => com.histmersion.web.ArticleLink(l.ref, l.title.getOrElse(l.ref))
          case LinkKind.Query     => span(s"“${l.ref}”")
          case LinkKind.QueueItem =>
            val text = span(s"#${l.ref} ", span(cls := "muted", l.title.getOrElse("(gone)")))
            l.queue.flatMap(Queue.Kind.parse).zip(l.ref.toIntOption).fold(text)((k, id) => QueueItemLink(k, id, text)),
        Option.when(l.title.isEmpty && l.kind != LinkKind.Query)(span(cls := "muted", " (missing)")),
        l.note.filter(_.nonEmpty).map(n => span(cls := "muted", s" · $n")),
        child.maybe <-- Auth.writable.map(w =>
          Option.when(w)(
            button(
              typ := "button",
              cls := "link-btn goal-unlink",
              "unlink",
              disabled <-- busy,
              onClick --> (_ => write("unlink")(ApiClient.removeGoalLink(slug, l.kind, l.ref)))
            )
          )
        )
      )
    div(
      cls := "goal-section",
      h4("Linked"),
      if links.isEmpty then p(cls := "muted", "Nothing is linked yet.")
      else
        div(
          LinkKind.values.toList
            .map(k => linksOf(links, k))
            .filter(_.nonEmpty)
            .map(ls => div(h5(ls.head.kind.wire.replace('-', ' ')), ul(cls := "goal-links", ls.map(entry))))
        )
      ,
      child <-- Auth.writable.map { w =>
        if !w then Auth.keyNeeded("link to this goal")
        else
          div(
            cls := "form-row goal-link-add",
            select(
              cls := "field",
              aria.label := "link kind",
              LinkKind.values.toList.map(k => option(value := k.wire, k.wire.replace('-', ' '))),
              controlled(
                value <-- kind.signal.map(_.wire),
                onChange.mapToValue --> (v => LinkKind.parse(v).foreach(kind.set))
              )
            ),
            input(
              typ := "text",
              cls := "field",
              aria.label := "what to link",
              placeholder := "citekey, article slug, query or item id",
              controlled(value <-- ref.signal, onInput.mapToValue --> ref.writer)
            ),
            input(
              typ := "text",
              cls := "field",
              aria.label := "note",
              placeholder := "note (optional)",
              controlled(value <-- note.signal, onInput.mapToValue --> note.writer)
            ),
            button(
              typ := "button",
              cls := "btn",
              "link",
              disabled <-- busy,
              onClick --> { _ =>
                linkToAdd(kind.now(), ref.now(), note.now()).foreach { l =>
                  write("link")(ApiClient.addGoalLink(slug, l))
                  ref.set(""); note.set("")
                }
              }
            )
          )
      }
    )

  private def schedulesSection(
      slug: com.histmersion.web.GoalsView.SchedulesSectionSlug,
      ss: List[GoalSchedule],
      busy: Signal[com.histmersion.web.GoalsView.SchedulesSectionBusy],
      write: com.histmersion.web.GoalsView.SchedulesSectionWriteArg1 => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    def editor(k: ScheduleKind): HtmlElement =
      val current = ss.find(_.kind == k)
      val cron = Var(current.map(_.cron).getOrElse(defaultCron(k)))
      val on = Var(current.forall(_.enabled))
      val budget = Var(current.flatMap(_.budgetUsd).fold("")(AgentsMarkdown.usd))
      li(
        cls := "goal-schedule",
        div(
          strong(k.wire),
          span(
            cls := "muted",
            k match
              case ScheduleKind.Currency    => " — assess and followups over the goal's sources"
              case ScheduleKind.Acquisition =>
                " — search its queries; a model picks what to add to the acquisition list"
          )
        ),
        current.map(c =>
          div(
            cls := "muted",
            c.nextRunAt.fold("not scheduled")(n => s"next $n"),
            c.lastRunAt.map(l => s" · last run $l"),
            c.lastRun.map(id => span(" · ", WinLink(WindowKey.Run(id), s"run ${id.take(8)}"))),
            c.lastNote.filter(_.nonEmpty).map(n => s" · $n")
          )
        ),
        child <-- Auth.writable.map { w =>
          if !w then
            current.fold(span(cls := "muted", "no schedule"))(c =>
              span(code(c.cron), if c.enabled then " on" else " off")
            )
          else
            div(
              cls := "form-row",
              input(
                typ := "text",
                cls := "field goal-cron",
                aria.label := s"${k.wire} schedule (cron, UTC)",
                placeholder := "cron in UTC, or @daily, @weekly, @monthly",
                controlled(value <-- cron.signal, onInput.mapToValue --> cron.writer)
              ),
              label(
                cls := "goal-on",
                input(typ := "checkbox", controlled(checked <-- on.signal, onClick.mapToChecked --> on.writer)),
                " on"
              ),
              input(
                typ := "text",
                cls := "field run-budget",
                aria.label := "cost cap per run in USD",
                placeholder := "cap USD (optional)",
                controlled(value <-- budget.signal, onInput.mapToValue --> budget.writer)
              ),
              button(
                typ := "button",
                cls := "btn btn--primary",
                "save",
                disabled <-- busy,
                onClick --> (_ =>
                  write(s"${k.wire} schedule")(
                    ApiClient.setSchedule(slug, k, ScheduleSet(cron.now(), on.now(), RunsView.budget(budget.now())))
                  )
                )
              ),
              Option.when(current.isDefined)(
                button(
                  typ := "button",
                  cls := "btn",
                  "remove",
                  disabled <-- busy,
                  onClick --> (_ => write(s"${k.wire} schedule")(ApiClient.removeSchedule(slug, k)))
                )
              )
            )
        }
      )
    div(
      cls := "goal-section",
      h4("Schedules"),
      p(
        cls := "muted",
        "They run only while the server's embedded agents are on; the daily and monthly cost caps are checked before each start."
      ),
      ul(cls := "goal-schedules", ScheduleKind.values.toList.map(editor))
    )

  private def runsSection(runs: Signal[List[RunInfo]]): HtmlElement =
    div(
      cls := "goal-section",
      child <-- runs.map { rs =>
        if rs.isEmpty then emptyNode
        else
          div(
            h4("Runs"),
            ul(
              cls := "goal-runs",
              rs.map(r =>
                li(
                  span(cls := s"job-state state-${r.status}", r.status),
                  " ",
                  WinLink(WindowKey.Run(r.id), s"${r.pipeline} ${r.target}"),
                  span(cls := "muted", s" · ${r.createdAt}")
                )
              )
            )
          )
      }
    )
