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
  type CreatedNote = CreatedNote.T
  object CreatedNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Depth = Depth.T
  object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TagsText = TagsText.T
  object TagsText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerDay = PerDay.T
  object PerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Planned = Planned.T
  object Planned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Watch = Watch.T
  object Watch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** How many goals the tree lists, and how many of a goal's runs its window shows. */

  export com.histmersion.web.GoalsView.NewArea.{Plan as NewAreaPlan, Seed as NewAreaSeed}
  export com.histmersion.api.automation.Goal.{StatusValue as BadgeStatus}
  type BadgeText = BadgeText.T
  object BadgeText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  export com.histmersion.api.automation.Goal.{Slug as PlanSectionSlug}

  val Limit = 500
  val RunLimit = 15

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** Goals as a tree, depth first: each root (no parent, or a parent that is not listed) then its sub-goals, by slug,
    * with their depth. A cycle cannot loop: a goal is listed once.
    */
  def tree(goals: List[Goal]): List[(Goal, com.histmersion.web.GoalsView.Depth)] =
    val slugs = goals.map(_.slug).toSet
    val byParent = goals.groupBy(g => g.parentSlug.filter(slugs))
    def walk(parent: Option[com.histmersion.api.automation.Goal.Slug], depth: com.histmersion.web.GoalsView.Depth, seen: Set[com.histmersion.api.automation.Goal.Slug]): List[(Goal, com.histmersion.web.GoalsView.Depth)] =
      byParent.getOrElse(parent, Nil).sortBy(_.slug.value).filterNot(g => seen(g.slug)).flatMap { g =>
        (g, depth) :: walk(Some(g.slug), Depth(depth.value + 1), seen + g.slug)
      }
    walk(None, com.histmersion.web.GoalsView.Depth(0), Set.empty)

  /** What each goal stage means, in the order a goal moves through them; the legend above the tree and the tooltip of
    * every status badge.
    */
  val stages: List[(com.histmersion.api.automation.Goal.Stage, com.histmersion.api.automation.Goal.StageMeaning)] = Goal.Stages.all

  def meaning(status: com.histmersion.api.automation.Goal.Stage): Option[com.histmersion.api.automation.Goal.StageMeaning] = Goal.Stages.meaning(status)

  /** The open plan approval of a goal, if a plan run is waiting on one. */
  def pendingPlan(as: List[ApprovalInfo], slug: com.histmersion.api.automation.Goal.Slug): Option[ApprovalInfo] =
    as.find(a =>
      a.approvalKind.contains(ApprovalKind.GoalPlan) && a.subject.contains(slug) &&
        a.state == ApprovalState.Open.wire
    )

  /** What the schedule editor starts from: the schedule's own cron, else a sensible default. */
  def defaultCron(k: ScheduleKind): com.histmersion.api.automation.Goal.Plans.GoalSchedule.Cron = k match
    case ScheduleKind.Currency    => com.histmersion.api.automation.Goal.Plans.GoalSchedule.Cron("@weekly")
    case ScheduleKind.Acquisition => com.histmersion.api.automation.Goal.Plans.GoalSchedule.Cron("@monthly")

  /** The links of one kind. */
  def linksOf(ls: List[GoalLink], k: LinkKind): List[GoalLink] = ls.filter(_.kind == k)

  /** A link to add, from the editor's kind and text: `None` when the text is blank. */
  def linkToAdd(kind: LinkKind, raw: com.histmersion.api.automation.Goal.Plans.LinkAdd.Ref, note: com.histmersion.api.automation.Goal.Plans.LinkAdd.Note): Option[LinkAdd] =
    Option(raw.value).map(_.trim).filter(_.nonEmpty).map(r =>
      LinkAdd(kind, LinkAdd.Ref(r), Option(note.value).map(_.trim).filter(_.nonEmpty).map(LinkAdd.Note(_)))
    )

  /** A slug from a title: lower case, runs of anything but letters and digits as one '-'. */
  def slugify(title: com.histmersion.api.automation.Goal.Title): com.histmersion.api.automation.Goal.Slug =
    com.histmersion.api.automation.Goal.Slug(
      title.value.trim.toLowerCase.replaceAll("[^a-z0-9]+", "-").stripPrefix("-").stripSuffix("-")
    )

  /** What the New research area form creates: the goal (at `slug`), and whether to plan it (a plan-goal run), seed it
    * (a seeding manager, created paused, whose first item proposes its articles) and watch it (a watcher, created
    * paused, with these settings: its weekly acquisition sweep and currency check, and — opted in — the legal
    * open-access ingest and its daily cap).
    */
  final case class NewArea(
      slug: com.histmersion.api.automation.Goal.Slug,
      put: Goal.Put,
      plan: NewArea.Plan,
      seed: NewArea.Seed,
      watch: Option[Managers.ManagerConfig] = None
  )

  object NewArea:
    type Plan = Plan.T
    object Plan extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Seed = Seed.T
    object Seed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The form → what to create. A blank slug comes from the title; tags are comma-separated. Watching: auto-ingest is
    * OFF unless ticked; its cap (blank = the server's default) a whole number.
    */
  def newArea(
      title: com.histmersion.api.automation.Goal.Title,
      slug: com.histmersion.api.automation.Goal.Slug,
      notes: com.histmersion.api.automation.Goal.Notes,
      tags: com.histmersion.web.GoalsView.TagsText,
      plan: com.histmersion.web.GoalsView.NewAreaPlan,
      seed: com.histmersion.web.GoalsView.NewAreaSeed,
      watch: com.histmersion.web.GoalsView.Watch = com.histmersion.web.GoalsView.Watch(false),
      autoIngest: com.histmersion.api.automation.Managers.ManagerConfig.AutoIngest = com.histmersion.api.automation.Managers.ManagerConfig.AutoIngest(false),
      perDay: com.histmersion.web.GoalsView.PerDay = com.histmersion.web.GoalsView.PerDay("")
  ): Either[com.histmersion.web.GoalsView.Problem, NewArea] =
    val t = title.value.trim
    val s = Option(slug.value.trim).filter(_.nonEmpty).map(com.histmersion.api.automation.Goal.Slug(_)).getOrElse(slugify(com.histmersion.api.automation.Goal.Title(t)))
    val cap = perDay.value.trim
    if t.isEmpty then Left(Problem("a research area needs a title"))
    else if watch.value && autoIngest.value && cap.nonEmpty && cap.toIntOption.forall(_ < 0) then
      Left(Problem("ingests a day: a whole number"))
    else
      val ok = s
      val w = Option.when(watch.value)(
        Managers.ManagerConfig(
          autoIngest = Some(autoIngest),
          autoIngestPerDay = if autoIngest.value then cap.toIntOption.map(Managers.ManagerConfig.AutoIngestPerDay(_)) else None
        )
      )
      Right(
        NewArea(
          ok,
          Goal.Put(
            com.histmersion.api.automation.Goal.Title(t),
            com.histmersion.api.automation.Goal.StatusValue(com.histmersion.api.automation.Goal.Status.Planned),
            Option(notes.value.trim).filter(_.nonEmpty).map(com.histmersion.api.automation.Goal.Notes(_)),
            tags.value.split(",").toList.map(_.trim).filter(_.nonEmpty).distinct.map(com.histmersion.api.automation.Goal.Tags(_))
          ),
          plan,
          seed,
          w
        )
      )

  /** What the form did, in one line. */
  def createdNote(
      a: NewArea,
      planned: com.histmersion.web.GoalsView.Planned,
      seeded: Option[Managers.ManagerInfo.Id],
      watched: Option[Managers.ManagerInfo.Id] = None,
      active: Set[Managers.ManagerInfo.Id] = Set.empty
  ): com.histmersion.web.GoalsView.CreatedNote =
    def made(id: Managers.ManagerInfo.Id, resume: String) =
      if active(id) then s"$id created active (the autopilot is on)" else s"$id created paused — resume it $resume"
    CreatedNote(s"research area ${a.slug} created" + (if planned.value then "; the planner is running (its plan waits for you)" else "") +
      seeded.fold("")(id => s"; seeding manager ${made(id, "to propose its articles")}") +
      watched.fold("")(id =>
        s"; watcher ${made(id, "for its weekly sweeps")}" +
          (if a.watch.exists(_.autoIngest.exists(_.value)) then " (it ingests legal open-access finds)" else "")
      ))

  // ── the goals window ────────────────────────────────────────────────────────────────────────────

  private val goals = Var(List.empty[Goal])
  private val loaded = Var(false)
  private val failure = Var(Option.empty[String])

  private def refresh(): Unit =
    ApiClient.goals(Some(com.histmersion.api.Paging.Limit(Limit))).onComplete { r =>
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
  def badge(status: com.histmersion.web.GoalsView.BadgeStatus, text: com.histmersion.web.GoalsView.BadgeText = com.histmersion.web.GoalsView.BadgeText("")): HtmlElement =
    span(
      cls := s"job-state goal-status state-${status.value}",
      meaning(com.histmersion.api.automation.Goal.Stage(status.value)).map(m => title := m.value),
      if text.value.nonEmpty then text.value else Wording.capitalize(status.value)
    )

  /** The stages, left to right, each badge with what it means. */
  private def legend: HtmlElement =
    ul(cls := "goal-legend", aria.label := "goal stages", stages.map((s, m) => li(badge(BadgeStatus(s.value)), span(cls := "muted", m.value))))

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
        com.histmersion.api.automation.Goal.Title(name.now()),
        com.histmersion.api.automation.Goal.Slug(slug.now()),
        com.histmersion.api.automation.Goal.Notes(notes.now()),
        TagsText(tags.now()),
        NewAreaPlan(plan.now()),
        NewAreaSeed(seed.now()),
        Watch(watch.now()),
        Managers.ManagerConfig.AutoIngest(ingest.now()),
        PerDay(perDay.now())
      ) match
        case Left(m)  => msg.set(Some(Left(m.value)))
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
                val planned = if a.plan.value then
                  ApiClient.startRun(Agents.RunRequest(Agents.RunRequest.Pipeline(Pipeline.PlanGoal.wire.value), Agents.RunRequest.Target(a.slug.value))).map(_ => true)
                else scala.concurrent.Future.successful(false)
                planned.flatMap { p =>
                  val seeded = if a.seed.value then
                    ApiClient.managerCreate(Managers.ManagerCreate(Managers.ManagerCreate.Kind("seed"), Some(a.slug))).map(m => Some(m))
                  else scala.concurrent.Future.successful(None)
                  seeded.flatMap { sd =>
                    val watched = a.watch.fold(scala.concurrent.Future.successful(Option.empty[Managers.ManagerInfo]))(
                      c => ApiClient.managerCreate(Managers.ManagerCreate(Managers.ManagerCreate.Kind("watch"), Some(a.slug), c)).map(m => Some(m))
                    )
                    watched.map(w =>
                      createdNote(
                        a,
                        Planned(p),
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
  def goalPane(slug: com.histmersion.api.automation.Goal.Slug): HtmlElement =
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
        ApiClient.agentRuns(ListFilters.RunFilter(goal = Some(slug)), Some(com.histmersion.api.Paging.Limit(RunLimit))).foreach(r => runs.set(r._1))
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
      ApiClient.startRun(RunRequest(RunRequest.Pipeline(Pipeline.PlanGoal.wire.value), RunRequest.Target(slug.value))).onComplete { r =>
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
        WindowManager.retitle(WindowKey.Goal(WindowKey.Goal.Slug(slug.value)), com.histmersion.api.UiLabels.Text(s"Goal · ${t.value}"))
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
                .when(v.goal.tags.nonEmpty)(List(dt("Tags"), dd(v.goal.tags.map(t => Wording.value(t.value)).mkString(", "))))
                .toList
                .flatten,
              Option
                .when(v.goal.regions.nonEmpty)(
                  List(dt("Regions"), dd(v.goal.regions.map(t => Wording.value(t.value)).mkString(", ")))
                )
                .toList
                .flatten,
              v.goal.parentSlug.toList.flatMap(ps => List(dt("part of"), dd(WinLink(WindowKey.Goal(WindowKey.Goal.Slug(ps.value)), ps.value)))),
              v.budgetUsd.toList.flatMap(b => List(dt("run cap"), dd(s"${AgentsMarkdown.usd(b.value)} USD")))
            ),
            planSection(slug, approvals.signal, busy.signal, () => plan()),
            ManagersView.goalActions(v.goal.slug),
            Option.when(v.children.nonEmpty)(
              div(
                h4("Sub-goals"),
                ul(v.children.map(c => li(WinLink(WindowKey.Goal(WindowKey.Goal.Slug(c.slug.value)), c.title.value), " ", badge(c.status))))
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
      slug: com.histmersion.api.automation.Goal.Slug,
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
          case LinkKind.Source    => SourceLink(Preview.Target.Source.Citekey(l.ref.value), label = l.title.map(t => com.histmersion.api.UiLabels.Text(s"${l.ref.value} — ${t.value}")))
          case LinkKind.Article   => com.histmersion.web.ArticleLink(com.histmersion.api.research.Article.Slug(l.ref.value), l.title.map(_.value).getOrElse(l.ref.value))
          case LinkKind.Query     => span(s"“${l.ref}”")
          case LinkKind.QueueItem =>
            val text = span(s"#${l.ref} ", span(cls := "muted", l.title.map(_.value).getOrElse("(gone)")))
            l.queue.flatMap(q => Queue.Kind.parse(Queue.Kind.Label(q.value))).zip(l.ref.value.toIntOption).fold(text)((k, id) => QueueItemLink(k, GoalLink.QueueNumber(id), text)),
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
      slug: com.histmersion.api.automation.Goal.Slug,
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
            c.lastRun.map(id => span(" · ", WinLink(WindowKey.Run(WindowKey.Run.Id(id.value)), s"run ${id.value.take(8)}"))),
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
                        RunsView.budget(com.histmersion.api.UiLabels.Text(budget.now())).map(b => ScheduleSet.BudgetUsd(b.value))
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
