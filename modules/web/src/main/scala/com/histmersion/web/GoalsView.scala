package com.histmersion
package web
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.Wording
import com.histmersion.api.automation.Agents.{ApprovalInfo, ApprovalKind, ApprovalState, Pipeline, RunInfo, RunRequest}
import com.histmersion.api.automation.Goal.Plans.*
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.automation.{Agents, AgentsMarkdown, Goal, ListFilters, Managers, Queue}
import com.histmersion.api.research.Articles
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Paging
import com.histmersion.api.UiLabels
import com.histmersion.api.research.Article

/** The research goals (docs/research-embedded-agents.md §14): the goals window (the goals as a tree) and one window per
  * goal — its sub-goals and links (sources, articles, search queries, queue items), the plan waiting for approval with
  * approve / reject, the approved plan, the schedule editor (currency check and acquisition sweep: cron, on/off, cost
  * cap) and the goal's runs. A "plan" button asks the server's planner for a plan (only when the server runs agents);
  * every change is behind the write gate. Nothing here polls: a window reloads when it opens, after a change it made,
  * and when a run settles ([[ApprovalsView.changed]]).
  */
object GoalsView:
  type CreatedNote = CreatedNote.T
  object CreatedNote extends Newtype[String, Pure]
  type Depth = Depth.T
  object Depth extends Newtype[Int, Pure]
  type TagsText = TagsText.T
  object TagsText extends Newtype[String, Pure]
  type PerDay = PerDay.T
  object PerDay extends Newtype[String, Pure]
  type Planned = Planned.T
  object Planned extends Newtype[Boolean, Pure]
  type Watch = Watch.T
  object Watch extends Newtype[Boolean, Pure]

  /** How many goals the tree lists, and how many of a goal's runs its window shows. */

  export NewArea.{Plan as NewAreaPlan, Seed as NewAreaSeed}
  export Goal.StatusValue as BadgeStatus
  type BadgeText = BadgeText.T
  object BadgeText extends Newtype[String, Pure]
  export Goal.Slug as PlanSectionSlug

  val Limit = 500
  val RunLimit = 15

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** Goals as a tree, depth first: each root (no parent, or a parent that is not listed) then its sub-goals, by slug,
    * with their depth. A cycle cannot loop: a goal is listed once.
    */
  def tree(goals: List[Goal]): List[(Goal, Depth)] =
    val slugs = goals.map(_.slug).toSet
    val byParent = goals.groupBy(g => g.parentSlug.filter(slugs))
    def walk(parent: Option[Goal.Slug], depth: Depth, seen: Set[Goal.Slug]): List[(Goal, Depth)] =
      byParent.getOrElse(parent, Nil).sortBy(_.slug.value).filterNot(g => seen(g.slug)).flatMap { g =>
        (g, depth) :: walk(Some(g.slug), Depth(depth.value + 1), seen + g.slug)
      }
    walk(None, Depth(0), Set.empty)

  /** What each goal stage means, in the order a goal moves through them; the legend above the tree and the tooltip of
    * every status badge.
    */
  val stages: List[(Goal.Stage, Goal.StageMeaning)] = Goal.Stages.all

  def meaning(status: Goal.Stage): Option[Goal.StageMeaning] = Goal.Stages.meaning(status)

  /** The open plan approval of a goal, if a plan run is waiting on one. */
  def pendingPlan(as: List[ApprovalInfo], slug: Goal.Slug): Option[ApprovalInfo] =
    as.find(a =>
      a.approvalKind.contains(ApprovalKind.GoalPlan) && a.subject.contains(slug) &&
        a.state == ApprovalState.Open.wire
    )

  /** What the schedule editor starts from: the schedule's own cron, else a sensible default. */
  def defaultCron(k: ScheduleKind): Goal.Plans.GoalSchedule.Cron = k match
    case ScheduleKind.Currency    => Goal.Plans.GoalSchedule.Cron("@weekly")
    case ScheduleKind.Acquisition => Goal.Plans.GoalSchedule.Cron("@monthly")

  /** The links of one kind. */
  def linksOf(ls: List[GoalLink], k: LinkKind): List[GoalLink] = ls.filter(_.kind == k)

  /** A link to add, from the editor's kind and text: `None` when the text is blank. */
  def linkToAdd(kind: LinkKind, raw: Goal.Plans.LinkAdd.Ref, note: Goal.Plans.LinkAdd.Note): Option[LinkAdd] =
    Option(raw.value)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(r => LinkAdd(kind, LinkAdd.Ref(r), Option(note.value).map(_.trim).filter(_.nonEmpty).map(LinkAdd.Note(_))))

  /** A slug from a title: lower case, runs of anything but letters and digits as one '-'. */
  def slugify(title: Goal.Title): Goal.Slug =
    Goal.Slug(
      title.value.trim.toLowerCase.replaceAll("[^a-z0-9]+", "-").stripPrefix("-").stripSuffix("-")
    )

  /** What the New research area form creates: the goal (at `slug`), and whether to plan it (a plan-goal run), seed it
    * (a seeding manager, created paused, whose first item proposes its articles) and watch it (a watcher, created
    * paused, with these settings: its weekly acquisition sweep and currency check, and — opted in — the legal
    * open-access ingest and its daily cap).
    */
  final case class NewArea(
      slug: Goal.Slug,
      put: Goal.Put,
      plan: NewArea.Plan,
      seed: NewArea.Seed,
      watch: Option[Managers.ManagerConfig] = None
  ):
    /** The one request that creates all of it, on the server, in one transaction. */
    def create: Goal.AreaCreate = Goal.AreaCreate(slug, put, plan, seed, watch)

  object NewArea:
    export Goal.AreaCreate.{Plan, Seed}

  /** The form → what to create. A blank slug comes from the title; tags are comma-separated. Watching: auto-ingest is
    * OFF unless ticked; its cap (blank = the server's default) a whole number.
    */
  def newArea(
      title: Goal.Title,
      slug: Goal.Slug,
      notes: Goal.Notes,
      tags: TagsText,
      plan: GoalsView.NewAreaPlan,
      seed: GoalsView.NewAreaSeed,
      watch: Watch = Watch(false),
      autoIngest: Managers.ManagerConfig.AutoIngest = Managers.ManagerConfig.AutoIngest(false),
      perDay: PerDay = PerDay("")
  ): Either[Problem, NewArea] =
    val t = title.value.trim
    val s = Option(slug.value.trim).filter(_.nonEmpty).map(Goal.Slug(_)).getOrElse(slugify(Goal.Title(t)))
    val cap = perDay.value.trim
    if t.isEmpty then Left(Problem.Unclassified(Legacy.Text("a research area needs a title")))
    else if watch.value && autoIngest.value && cap.nonEmpty && cap.toIntOption.forall(_ < 0) then
      Left(Problem.Unclassified(Legacy.Text("ingests a day: a whole number")))
    else
      val ok = s
      val w = Option.when(watch.value)(
        Managers.ManagerConfig(
          autoIngest = Some(autoIngest),
          autoIngestPerDay =
            if autoIngest.value then cap.toIntOption.map(Managers.ManagerConfig.AutoIngestPerDay(_)) else None
        )
      )
      Right(
        NewArea(
          ok,
          Goal.Put(
            Goal.Title(t),
            Goal.StatusValue(Goal.Status.Planned),
            Option(notes.value.trim).filter(_.nonEmpty).map(Goal.Notes(_)),
            tags.value.split(",").toList.map(_.trim).filter(_.nonEmpty).distinct.map(Goal.Tags(_))
          ),
          plan,
          seed,
          w
        )
      )

  /** What the form did, in one line. */
  def createdNote(
      a: NewArea,
      planned: Planned,
      seeded: Option[Managers.ManagerInfo.Id],
      watched: Option[Managers.ManagerInfo.Id] = None,
      active: Set[Managers.ManagerInfo.Id] = Set.empty
  ): CreatedNote =
    def made(id: Managers.ManagerInfo.Id, resume: String) =
      if active(id) then s"$id created active (the autopilot is on)" else s"$id created paused — resume it $resume"
    CreatedNote(
      s"research area ${a.slug} created" + (if planned.value then "; the planner is running (its plan waits for you)"
                                            else "") +
        seeded.fold("")(id => s"; seeding manager ${made(id, "to propose its articles")}") +
        watched.fold("")(id =>
          s"; watcher ${made(id, "for its weekly sweeps")}" +
            (if a.watch.exists(_.autoIngest.exists(_.value)) then " (it ingests legal open-access finds)" else "")
        )
    )

  /** What the server's one operation made, in one line. */
  def madeNote(a: NewArea, made: Goal.AreaCreated): CreatedNote =
    val managers = made.seed.toList ++ made.watch.toList
    createdNote(
      a,
      Planned(made.run.nonEmpty),
      made.seed.map(_.id),
      made.watch.map(_.id),
      managers.filter(_.state == Managers.State.Active.wire).map(_.id).toSet
    )

  // ── the goals window ────────────────────────────────────────────────────────────────────────────

  private val goals = Var(List.empty[Goal])
  private val loaded = Var(false)
  private val failure = Var(Option.empty[String])

  private def refresh(): Unit =
    ApiClient.goals(Some(Paging.Limit(Limit))).onComplete { r =>
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
                  WinLink(WindowKey.Goal(WindowKey.Goal.Slug(g.slug.value)), g.title.value, title := g.slug.value)
                )
              )
            )
          )
      }
    )

  /** A goal's status as a coloured badge, its meaning on hover. */
  def badge(status: GoalsView.BadgeStatus, text: BadgeText = BadgeText("")): HtmlElement =
    span(
      cls := s"job-state goal-status state-${status.value}",
      meaning(Goal.Stage(status.value)).map(m => title := m.value),
      if text.value.nonEmpty then text.value else Wording.capitalize(status.value)
    )

  /** The stages, left to right, each badge with what it means. */
  private def legend: HtmlElement =
    ul(
      cls := "goal-legend",
      aria.label := "goal stages",
      stages.map((s, m) => li(badge(BadgeStatus(s.value)), span(cls := "muted", m.value)))
    )

  /** New research area: a goal, and — ticked — a plan-goal run for it, a seeding manager and a watcher (both created
    * paused; the watcher's auto-ingest of legal open-access finds is off unless ticked, with its daily cap). The slug
    * must be free: an existing goal is never replaced.
    */
  private def newAreaForm(refresh: () => Unit): HtmlElement =
    val name = Var(""); val slug = Var(""); val notes = Var(""); val tags = Var("")
    val plan = Var(true); val seed = Var(true); val watch = Var(false); val ingest = Var(false); val perDay = Var("")
    val busy = Var(false)
    val msg = Var(Option.empty[Either[String, String]])
    def field(v: Var[String], name: String, hint: String) =
      input(
        typ := "text",
        cls := "field",
        aria.label := name,
        placeholder := hint,
        controlled(value <-- v.signal, onInput.mapToValue --> v.writer)
      )
    def box(v: Var[Boolean], text: String) =
      label(
        cls := "goal-on",
        input(typ := "checkbox", controlled(checked <-- v.signal, onClick.mapToChecked --> v.writer)),
        s" $text"
      )
    def submit(): Unit =
      newArea(
        Goal.Title(name.now()),
        Goal.Slug(slug.now()),
        Goal.Notes(notes.now()),
        TagsText(tags.now()),
        NewAreaPlan(plan.now()),
        NewAreaSeed(seed.now()),
        Watch(watch.now()),
        Managers.ManagerConfig.AutoIngest(ingest.now()),
        PerDay(perDay.now())
      ) match
        case Left(m)  => msg.set(Some(Left(m.sentence)))
        case Right(a) =>
          busy.set(true)
          // ONE request: the server creates the goal (never replacing one), its plan run and its managers in one
          // transaction, or nothing at all
          val created = ApiClient.createArea(a.create).map(made => madeNote(a, made))
          created.onComplete { r =>
            busy.set(false)
            msg.set(Some(r.fold(e => Left(e.getMessage), n => Right(n.value))))
            if r.isSuccess then
              name.set(""); slug.set(""); notes.set(""); tags.set(""); watch.set(false); ingest.set(false);
              perDay.set("")
              WindowManager.open(WindowKey.Goal(WindowKey.Goal.Slug(a.slug.value)))
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
  def goalPane(slug: Goal.Slug): HtmlElement =
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
      if RunsView.enabled.now().value then
        ApiClient
          .agentRuns(ListFilters.RunFilter(goal = Some(slug)), Some(Paging.Limit(RunLimit)))
          .foreach(r => runs.set(r._1))
      // the goal's own open plan, filtered on the server (it used to be looked for among the newest 100 open approvals)
      ApiClient
        .approvals(
          ListFilters.ApprovalFilter(kind = List(ApprovalKind.GoalPlan.wire), goal = Some(slug)),
          Some(ApprovalsView.Limit)
        )
        .foreach(a => approvals.set(a._1))

    def write[A](what: String)(f: => scala.concurrent.Future[A]): Unit =
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
      ApiClient
        .startRun(RunRequest(RunRequest.Pipeline(Pipeline.PlanGoal.wire.value), RunRequest.Target(slug.value)))
        .onComplete { r =>
          busy.set(false)
          r.fold(
            e => failure.set(Some(s"plan: ${e.getMessage}")),
            run => {
              failure.set(None)
              message.set(Some("The planner is running; its plan will wait here for your approval."))
              WindowManager.open(WindowKey.Run(WindowKey.Run.Id(run.id.value)))
            }
          )
          refresh()
        }

    div(
      cls := "tool-pane goal-pane",
      onMountCallback(_ => refresh()),
      ApprovalsView.changed.events --> (_ => refresh()),
      view.signal.changes.collect { case Some(v) if v.goal.title.value.trim.nonEmpty => v.goal.title } --> (t =>
        WindowManager.retitle(WindowKey.Goal(WindowKey.Goal.Slug(slug.value)), UiLabels.Text(s"Goal · ${t.value}"))
      ),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child.maybe <-- message.signal.map(_.map(m => p(cls := "form-ok", m))),
      child <-- view.signal.map {
        case None    => p(cls := "muted", "Loading…")
        case Some(v) =>
          div(
            cls := "goal-detail",
            div(cls := "run-head", badge(v.goal.status), span(cls := "run-title", s" ${v.goal.title}")),
            v.goal.notes.filter(_.value.nonEmpty).map(n => p(n.value)),
            dl(
              cls := "run-facts",
              dt("Identifier"),
              dd(code(v.goal.slug.value)),
              Option
                .when(v.goal.tags.nonEmpty)(
                  List(dt("Tags"), dd(v.goal.tags.map(t => Wording.value(t.value)).mkString(", ")))
                )
                .toList
                .flatten,
              Option
                .when(v.goal.regions.nonEmpty)(
                  List(dt("Regions"), dd(v.goal.regions.map(t => Wording.value(t.value)).mkString(", ")))
                )
                .toList
                .flatten,
              v.goal.parentSlug.toList.flatMap(ps =>
                List(dt("part of"), dd(WinLink(WindowKey.Goal(WindowKey.Goal.Slug(ps.value)), ps.value)))
              ),
              v.budgetUsd.toList.flatMap(b => List(dt("run cap"), dd(s"${AgentsMarkdown.usd(b.value)} USD")))
            ),
            planSection(slug, approvals.signal, busy.signal, () => plan()),
            ManagersView.goalActions(v.goal.slug),
            Option.when(v.children.nonEmpty)(
              div(
                h4("Sub-goals"),
                ul(
                  v.children.map(c =>
                    li(WinLink(WindowKey.Goal(WindowKey.Goal.Slug(c.slug.value)), c.title.value), " ", badge(c.status))
                  )
                )
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
      slug: GoalsView.PlanSectionSlug,
      approvals: Signal[List[ApprovalInfo]],
      busy: Signal[Boolean],
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
            if !agents.value then p(cls := "muted", "Planning needs the server's embedded agents (agents.enabled).")
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
      slug: Goal.Slug,
      links: List[GoalLink],
      busy: Signal[Boolean],
      write: String => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    val kind = Var(LinkKind.Source)
    val ref = Var("")
    val note = Var("")
    def entry(l: GoalLink): HtmlElement =
      li(
        l.kind match
          case LinkKind.Source =>
            SourceLink(
              Preview.Target.Source.Citekey(l.ref.value),
              label = l.title.map(t => UiLabels.Text(s"${l.ref.value} — ${t.value}"))
            )
          case LinkKind.Article   => ArticleLink(Article.Slug(l.ref.value), l.title.map(_.value).getOrElse(l.ref.value))
          case LinkKind.Query     => span(s"“${l.ref}”")
          case LinkKind.QueueItem =>
            val text = span(s"#${l.ref} ", span(cls := "muted", l.title.map(_.value).getOrElse("(gone)")))
            l.queue
              .flatMap(q => Queue.Kind.parse(Queue.Kind.Label(q.value)))
              .zip(l.ref.value.toIntOption)
              .fold(text)((k, id) => QueueItemLink(k, GoalLink.QueueNumber(id), text)),
        Option.when(l.title.isEmpty && l.kind != LinkKind.Query)(span(cls := "muted", " (missing)")),
        l.note.filter(_.value.nonEmpty).map(n => span(cls := "muted", s" · ${n.value}")),
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
            .map(ls => div(h5(ls.head.kind.wire.value.replace('-', ' ')), ul(cls := "goal-links", ls.map(entry))))
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
              LinkKind.values.toList.map(k => option(value := k.wire.value, k.wire.value.replace('-', ' '))),
              controlled(
                value <-- kind.signal.map(_.wire.value),
                onChange.mapToValue --> (v => LinkKind.parse(LinkKind.Wire(v)).foreach(kind.set))
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
                linkToAdd(kind.now(), LinkAdd.Ref(ref.now()), LinkAdd.Note(note.now())).foreach { l =>
                  write("link")(ApiClient.addGoalLink(slug, l))
                  ref.set(""); note.set("")
                }
              }
            )
          )
      }
    )

  private def schedulesSection(
      slug: Goal.Slug,
      ss: List[GoalSchedule],
      busy: Signal[Boolean],
      write: String => (=> scala.concurrent.Future[Any]) => Unit
  ): HtmlElement =
    def editor(k: ScheduleKind): HtmlElement =
      val current = ss.find(_.kind == k)
      val cron = Var[String](current.map(_.cron.value).getOrElse(defaultCron(k).value))
      val on = Var(current.forall(_.enabled.value))
      val budget = Var(current.flatMap(_.budgetUsd).fold("")(b => AgentsMarkdown.usd(b.value)))
      li(
        cls := "goal-schedule",
        div(
          strong(k.wire.value),
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
            c.lastRun.map(id =>
              span(" · ", WinLink(WindowKey.Run(WindowKey.Run.Id(id.value)), s"run ${id.value.take(8)}"))
            ),
            c.lastNote.filter(_.value.nonEmpty).map(n => s" · ${n.value}")
          )
        ),
        child <-- Auth.writable.map { w =>
          if !w then
            current.fold(span(cls := "muted", "no schedule"))(c =>
              span(code(c.cron.value), if c.enabled.value then " on" else " off")
            )
          else
            div(
              cls := "form-row",
              input(
                typ := "text",
                cls := "field goal-cron",
                aria.label := s"${k.wire.value} schedule (cron, UTC)",
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
                  write(s"${k.wire.value} schedule")(
                    ApiClient.setSchedule(
                      slug,
                      k,
                      ScheduleSet(
                        ScheduleSet.Cron(cron.now()),
                        ScheduleSet.Enabled(on.now()),
                        RunsView.budget(UiLabels.Text(budget.now())).map(b => ScheduleSet.BudgetUsd(b.value))
                      )
                    )
                  )
                )
              ),
              Option.when(current.isDefined)(
                button(
                  typ := "button",
                  cls := "btn",
                  "remove",
                  disabled <-- busy,
                  onClick --> (_ => write(s"${k.wire.value} schedule")(ApiClient.removeSchedule(slug, k)))
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
                  span(cls := s"job-state state-${r.status.value}", r.status.value),
                  " ",
                  WinLink(WindowKey.Run(WindowKey.Run.Id(r.id.value)), s"${r.pipeline} ${r.target}"),
                  span(cls := "muted", s" · ${r.createdAt}")
                )
              )
            )
          )
      }
    )
