package com.histmersion
package web
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Wording
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.{Filter, Label, SearchHit}
import com.histmersion.api.library.Acquire
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.UiLabels
import com.histmersion.api.Api
import com.histmersion.api.Paging
import com.histmersion.api.automation.Goal
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.automation.Creation

/** The three bookkeeping lists — the acquisition queue, the backlog and the worklog — each a window of the main UI,
  * drawn by this ONE implementation parameterised by [[Queue.Kind]] (`/acquire`, `/tasks` and `/log` open the app with
  * that window, so plain links keep working).
  *
  * The two checkbox lists (acquire, tasks) group their items under their topic path, in the order each group was first
  * added; an item can be ticked off, edited (text and topic) and deleted. An acquisition item can also be fulfilled:
  * "Upload…" opens the upload form filled in from it (the OCR job ticks it off when it succeeds), and "Link existing
  * source…" picks a source already in the corpus and ticks it off at once; either way it reads
  * `ingested as [[citekey]]`. An item the legal re-check looked at says when and what it found (`checked 2026-10-27: no
  * legal copy (…)`). The worklog's topic is the entry's date: it is grouped by that, newest date first, and its entries
  * can only be added. Every write follows [[Auth]].
  */
object QueueView:
  type Done = Done.T
  object Done extends Newtype[Boolean, Pure]
  type GroupCount = GroupCount.T
  object GroupCount extends Newtype[Int, Pure]
  type ItemIsTarget = ItemIsTarget.T
  object ItemIsTarget extends Newtype[Boolean, Pure]
  type Writes = Writes.T
  object Writes extends Newtype[Boolean, Pure]

  /** A run of an item's text: the queue items are short Markdown lines. */

  type RenderGroupTopicsId = String
  type RenderItemTopicsId = String

  enum Seg:
    case Text(s: Seg.Text.S)
    case Bold(s: Seg.Bold.S)
    case Italic(s: Seg.Italic.S)

    /** A DOI, linked to its resolver. */
    case Doi(doi: Seg.Doi.Value)
    case Url(url: Seg.Url.Value)

    /** A `[[citekey]]` — a source in the corpus. */
    case Cite(citekey: Seg.Cite.Citekey)

    /** A bare snake_case identifier — maybe a citekey or an article slug ([[IdentLink]] decides). */
    case Ident(id: Seg.Ident.Id)

  object Seg:
    object Text:
      export UiLabels.Text as S
    object Bold:
      export UiLabels.Text as S
    object Italic:
      export UiLabels.Text as S
    object Doi:
      export QueueItemRef.Ref.Doi as Value
    object Url:
      export Preview.Item.Href as Value
    object Cite:
      type Citekey = Preview.Target.Source.Citekey
      val Citekey: Preview.Target.Source.Citekey.type = Preview.Target.Source.Citekey
    object Ident:
      export IdentLink.Id

  /** What differs between the three windows; everything else is shared.
    *
    * @param showDone
    *   the "show done" switch in [[UiState]] (in the URL); `None` = the list has no done state (the worklog), so it is
    *   listed whole
    * @param openNoun
    *   how the count of open items reads ("12 to get", "30 open")
    * @param tick
    *   the checkbox's label as (mark done, mark open again)
    */
  final case class Spec(
      kind: Queue.Kind,
      icon: Icon,
      empty: Spec.Empty,
      untitled: Spec.Untitled,
      openNoun: Spec.OpenNoun,
      showDone: Option[Field[Boolean]],
      tick: (Spec.TickDone, Spec.TickReopen)
  ):
    /** Whether items carry a checkbox; the worklog's do not. */
    def checkable: Boolean = kind.checkboxes.value

    /** Whether an item can be edited and deleted here; the worklog is only added to. */
    def editable: Boolean = kind.checkboxes.value

    /** Whether an item carries a reason (why it is wanted); a worklog entry is its own record. */
    def reasons: Boolean = kind.checkboxes.value

    /** Whether an item is fulfilled by a source — uploaded, or linked to one in the corpus. */
    def fulfils: Boolean = kind == Queue.Kind.Acquire

  object Spec:

    type Empty = Empty.T
    object Empty extends Newtype[String, Pure]
    type Untitled = Untitled.T
    object Untitled extends Newtype[String, Pure]
    type OpenNoun = OpenNoun.T
    object OpenNoun extends Newtype[String, Pure]
    type TickDone = TickDone.T
    object TickDone extends Newtype[String, Pure]
    type TickReopen = TickReopen.T
    object TickReopen extends Newtype[String, Pure]

  def spec(kind: Queue.Kind): Spec = kind match
    case Queue.Kind.Acquire =>
      Spec(
        kind,
        Icon.Acquire,
        Spec.Empty("Nothing in the acquisition queue."),
        Spec.Untitled("Uncategorised"),
        Spec.OpenNoun("to get"),
        Some(UiState.acquireDone),
        (Spec.TickDone("mark as got"), Spec.TickReopen("mark as still to get"))
      )
    case Queue.Kind.Tasks =>
      Spec(
        kind,
        Icon.Tasks,
        Spec.Empty("Nothing in the backlog."),
        Spec.Untitled("Uncategorised"),
        Spec.OpenNoun("open"),
        Some(UiState.tasksDone),
        (Spec.TickDone("mark as done"), Spec.TickReopen("mark as still open"))
      )
    case Queue.Kind.Log =>
      Spec(
        kind,
        Icon.Log,
        Spec.Empty("Nothing in the worklog."),
        Spec.Untitled("Undated"),
        Spec.OpenNoun(""),
        None,
        (Spec.TickDone(""), Spec.TickReopen(""))
      )

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  private val Token =
    ("""\*\*(.+?)\*\*|\*([^*\s][^*]*?)\*|\[\[([a-z][a-z0-9_.-]{1,120})\]\]|(https?://[^\s)\]]*[^\s)\].,;])|""" +
      """\b(10\.\d{4,9}/[^\s,;)\]]*[^\s,;.)\]])|\b([a-z][a-z0-9]*(?:_[a-z0-9]+)+)\b""").r

  /** Split an item's text into runs to render: bold, italic, DOIs, URLs, citekeys and bare identifiers; the rest text.
    */
  def segments(text: UiLabels.Text): List[Seg] =
    val out = List.newBuilder[Seg]
    var last = 0
    val raw = text.value
    Token.findAllMatchIn(raw).foreach { m =>
      if m.start > last then out += Seg.Text(UiLabels.Text(raw.substring(last, m.start)))
      out += (if m.group(1) != null then Seg.Bold(UiLabels.Text(m.group(1)))
              else if m.group(2) != null then Seg.Italic(UiLabels.Text(m.group(2)))
              else if m.group(3) != null then Seg.Cite(Seg.Cite.Citekey(m.group(3)))
              else if m.group(4) != null then Seg.Url(Seg.Url.Value(m.group(4)))
              else if m.group(5) != null then Seg.Doi(Seg.Doi.Value(m.group(5)))
              else Seg.Ident(Seg.Ident.Id(m.group(6))))
      last = m.end
    }
    if last < raw.length then out += Seg.Text(UiLabels.Text(raw.substring(last)))
    out.result()

  /** A topic path (`**Group** > sub_topic`) as its heading and the path above it, readable; `untitled` stands in for an
    * item with no topic.
    */
  def heading(
      topic: Option[Queue.QueueItem.Topic],
      untitled: UiLabels.Text = UiLabels.Text("Uncategorised")
  ): (UiLabels.Text, List[UiLabels.Text]) =
    val parts = topic
      .map(_.value)
      .getOrElse("")
      .split(" > ")
      .toList
      .map(_.trim.stripPrefix("**").stripSuffix("**").trim)
      .filter(_.nonEmpty)
      .map(h => UiLabels.Text(Wording.heading(h)))
    parts match
      case Nil => (untitled, Nil)
      case ps  => (ps.last, ps.init)

  private val LeadingDate = """^(\d{4}-\d{2}-\d{2}).*""".r

  /** The day a worklog group is about: the date its topic starts with (a range such as `2026-06-24/25` keeps its first
    * day), else the day its newest entry was written; `""` when neither is known, which sorts last.
    */
  def logDate(topic: Option[Queue.QueueItem.Topic], items: List[Queue.QueueItem]): UiLabels.Text =
    topic.map(_.value.trim) match
      case Some(LeadingDate(d)) => UiLabels.Text(d)
      case _                    =>
        items
          .flatMap(_.createdAt)
          .maxOption
          .map(c => UiLabels.Text(c.value.take(10)))
          .getOrElse(UiLabels.Text(""))

  /** The items grouped by topic, each group in id order. The checkbox lists keep the groups in the order their first
    * item was added; the worklog puts the newest date first (ties: file order).
    */
  def groups(
      kind: Queue.Kind,
      items: List[Queue.QueueItem]
  ): List[(Option[Queue.QueueItem.Topic], List[Queue.QueueItem])] =
    val gs = items.groupBy(_.topic).toList.map((t, is) => t -> is.sortBy(_.id))
    if kind.checkboxes.value then gs.sortBy((_, is) => is.head.id)
    else
      gs.sortBy((t, is) => (logDate(t, is).value, is.head.id.value))(using
        Ordering.Tuple2(using Ordering.String.reverse, Ordering.Int)
      )

  /** The line above the list: how many are left (and done), or for the worklog how many entries over how many days.
    */
  def summary(spec: Spec, items: List[Queue.QueueItem]): UiLabels.Text =
    if spec.checkable then
      val done = items.count(_.status.value == Queue.Done)
      UiLabels.Text(s"${items.size - done} ${spec.openNoun}" + (if done > 0 then s" · $done done" else ""))
    else
      val days = items.map(_.topic).distinct.size
      UiLabels.Text(s"${plural(items.size, "entry", "entries")} · ${plural(days, "day", "days")}")

  private def plural(n: Int, one: String, many: String): String = s"$n ${if n == 1 then one else many}"

  // ── writing: add · edit · fulfil (pure) ─────────────────────────────────────────────────────────

  /** A list changed outside its own window (an upload ticked an acquisition item off): the open window of that list
    * reloads.
    */
  val changed: EventBus[Queue.Kind] = new EventBus[Queue.Kind]

  /** The write that ticks `item` off as fulfilled by the source `citekey` — the core rule ([[Queue.fulfil]]) the
    * server's OCR job applies too, addressed to the item.
    */
  def fulfil(item: Queue.QueueItem, citekey: Preview.Target.Source.Citekey): (Queue.QueueItem.Id, Queue.QueuePatch) =
    item.id -> Queue.fulfil(item, api.library.Citekey(citekey.value))

  /** The add form, as typed. */
  final case class AddDraft(
      text: AddDraft.Text = AddDraft.Text(""),
      topic: AddDraft.Topic = AddDraft.Topic(""),
      reason: Problem = Problem.Unclassified(Legacy.Text(""))
  )

  object AddDraft:
    type Text = Text.T
    object Text extends Newtype[String, Pure]
    type Topic = Topic.T
    object Topic extends Newtype[String, Pure]

  private val EmptyText = "Write the item's text first."

  /** The item to add, or why not: the text must not be blank; a blank topic or reason is left out (a worklog entry with
    * no topic joins today's date, server-side); the worklog takes no reason.
    */
  def validateAdd(sp: Spec, d: AddDraft): Either[Problem, Queue.QueueAdd] =
    if d.text.value.trim.isEmpty then Left(Problem.Unclassified(Legacy.Text(EmptyText)))
    else
      Right(
        Queue.QueueAdd(
          text = Queue.QueueItem.Text(d.text.value.strip),
          topic = Some(d.topic.value.trim).filter(_.nonEmpty).map(Queue.QueueItem.Topic(_)),
          reason = Some(d.reason.sentence.trim)
            .filter(_ => sp.reasons)
            .filter(_.nonEmpty)
            .map((text => Queue.Rationale(text))),
          via = Some(Creation.Web)
        )
      )

  /** The patch an inline edit makes: only what changed (`None` = nothing did); a blank topic moves the item out of its
    * group; a blank text is refused.
    */
  def editPatch(
      item: Queue.QueueItem,
      text: Queue.QueueItem.Text,
      topic: Queue.QueueItem.Topic
  ): Either[Problem, Option[Queue.QueuePatch]] =
    if text.value.trim.isEmpty then Left(Problem.Unclassified(Legacy.Text(EmptyText)))
    else
      val t = text.value.strip
      val tp = topic.value.trim
      val newText = Option.when(t != item.text.value)(Queue.QueueItem.Text(t))
      val newTopic = Option.when(Some(tp).filter(_.nonEmpty) != item.topic.map(_.value))(Queue.QueueItem.Topic(tp))
      Right(Option.when(newText.isDefined || newTopic.isDefined)(Queue.QueuePatch(text = newText, topic = newTopic)))

  /** The topics to offer while one is typed: those the list already uses, in the order they first appear, matching what
    * is typed (case and underscores aside), without the one typed exactly.
    */
  def topicSuggestions(
      items: List[Queue.QueueItem],
      typed: Api.Search.Text,
      limit: Paging.Limit = Paging.Limit(12)
  ): List[Queue.QueueItem.Topic] =
    def norm(s: String) = Label.display(Label.Text(s)).value.toLowerCase.trim
    val q = norm(typed.value)
    items
      .sortBy(_.id)
      .flatMap(_.topic)
      .distinct
      .filter(t => norm(t.value).contains(q) && t.value != typed.value.trim)
      .take(limit.value)

  // ── view ────────────────────────────────────────────────────────────────────────────────────────

  /** What an item's row is showing besides the item itself. */
  private enum Mode:
    case Idle, Editing, ConfirmDelete, Linking

  /** One window's pane. Its state lives with the pane, so each window loads its own list. */
  /** A list's page; with `goal`, only the items that serve that research goal. */
  def pane(kind: Queue.Kind, goal: Option[Goal.Slug] = None): HtmlElement =
    val sp = spec(kind)
    val recheck = Var(Option.empty[Either[String, Acquire.Checks.RecheckResult]])
    val items = Var(List.empty[Queue.QueueItem])
    val checks = Var(Map.empty[Queue.QueueItem.Id, Acquire.Checks.AcquireCheck])
    val goals = Var(Map.empty[Queue.QueueItem.Id, List[Queue.GoalRef]])
    // the item a link pointed at (`?item=`): marked, and scrolled to once it is listed
    val target = targetItem(Api.Search.Text(org.scalajs.dom.window.location.search))
    var scrolled = false
    val loaded = Var(false)
    val failure = Var(Option.empty[String])
    val adding = Var(false)
    val topicsId = s"queue-topics-${kind.label}"

    // a list without a done state is listed whole; the others follow their "show done" switch
    def load(): Unit =
      val all = sp.showDone.fold(true)(_.now())
      val list = kind.label
      goal.fold(ApiClient.queueList(list, all))(g => ApiClient.queueForGoal(list, g, all)).onComplete { r =>
        r.fold(
          e => failure.set(Some(s"Could not load the list: ${e.getMessage}")),
          l =>
            failure.set(None)
            items.set(l.items.filterNot(_.status.value == Queue.Note))
            checks.set(l.checks.map(c => c.item -> c).toMap)
            goals.set(l.goals.map(g => g.item -> g.goals).toMap)
            loaded.set(true)
            // an item that is done is listed with "show done" on: turn it on for the item asked for
            target.foreach { id =>
              if !l.items.exists(_.id == id) then sp.showDone.filterNot(_.now()).foreach(f => UiState.set(f := true)())
              else if !scrolled then
                scrolled = true
                org.scalajs.dom.window.setTimeout(
                  () =>
                    Option(org.scalajs.dom.document.getElementById(itemAnchor(id))).foreach(
                      _.asInstanceOf[scala.scalajs.js.Dynamic]
                        .scrollIntoView(scala.scalajs.js.Dynamic.literal(block = "center"))
                    ),
                  0
                )
            }
        )
      }

    /** Every write of this pane: its failure lands in the pane; the list reloads either way. */
    def write[A](what: UiLabels.Text, f: scala.concurrent.Future[A])(done: A => Unit = (_: A) => ()): Unit =
      f.onComplete { r =>
        r.fold(e => failure.set(Some(s"Could not $what: ${e.getMessage}")), a => { failure.set(None); done(a) })
        load()
        Notices.refreshSoon() // the toolbar's count follows (the open window keeps it read)
      }

    val ops = Ops(
      setDone = (item, d) =>
        write(
          UiLabels.Text(s"update item ${item.id}"),
          ApiClient.queuePatch(
            item.id,
            Queue.QueuePatch(status = Some(Queue.QueueItem.Status(if d.value then Queue.Done else Queue.Open)))
          )
        )(),
      edit = (item, p, after) =>
        write(UiLabels.Text(s"edit item ${item.id}"), ApiClient.queuePatch(item.id, p))(_ => after()),
      delete = item => write(UiLabels.Text(s"delete item ${item.id}"), ApiClient.queueDelete(item.id))(),
      fulfil = (item, ck) =>
        val (id, p) = fulfil(item, ck)
        write(UiLabels.Text(s"tick item $id off"), ApiClient.queuePatch(id, p))()
      ,
      recheck = item =>
        write(
          UiLabels.Text(s"re-check item ${item.id}"),
          ApiClient.acquireRecheck(
            Acquire.Checks.RecheckRequest(
              id = Some(Acquire.Checks.RecheckRequest.Id(item.id.value)),
              by = Some(Acquire.Checks.RecheckRequest.By("web"))
            )
          )
        )(r => recheck.set(Some(Right(r))))
    )

    def recheckAll(): Unit =
      if !Auth.writableNow then WindowManager.open(WindowKey.ApiKey)
      else
        ApiClient
          .acquireRecheck(
            Acquire.Checks.RecheckRequest(
              all = Acquire.Checks.RecheckRequest.All(true),
              by = Some(Acquire.Checks.RecheckRequest.By("web"))
            )
          )
          .onComplete { r =>
            recheck.set(Some(r.toEither.left.map(_.getMessage)))
            load()
          }

    div(
      cls := s"tool-pane queue-pane ${kind.label}-pane",
      onMountCallback(_ => load()),
      // the "show done" switch is UiState (in the URL), and the listing follows it
      sp.showDone.map(f => f.signal.changes --> (_ => load())),
      changed.events.filter(_ == kind) --> (_ => load()),
      htmlTag("datalist")(
        idAttr := topicsId,
        children <-- items.signal.map(its =>
          topicSuggestions(its, Api.Search.Text(""), limit = Paging.Limit(200)).map(t =>
            option(value := t.value, htmlAttr("label", com.raquo.laminar.codecs.StringAsIsCodec) := topicLabel(t).value)
          )
        )
      ),
      div(
        cls := "pane-bar",
        sp.showDone.map(f => LayerToggle(sp.icon, "show done", f.signal, v => UiState.set(f := v)())),
        LayerToggle(Icon.Edit, if sp.editable then "add item" else "add entry", adding.signal, adding.set),
        Option.when(sp.fulfils && goal.isEmpty)(
          button(
            typ := "button",
            cls := "btn recheck-all",
            "Re-check all",
            title := "Look for a legal open copy of every open item naming a DOI, now (a background job)",
            onClick --> (_ => recheckAll())
          )
        ),
        span(cls := "muted", child.text <-- items.signal.map(its => summary(sp, its).value))
      ),
      goal.map(g =>
        p(
          cls := "muted queue-goal",
          "Only the items of the goal ",
          RouteLink(Route.Goal(g), Wording.humanize(Wording.Id(g.value))),
          " · ",
          RouteLink(
            if kind == Queue.Kind.Acquire then Route.Research(ResearchSection.Acquire)
            else Route.Research(ResearchSection.Backlog),
            "show every item"
          )
        )
      ),
      child.maybe <-- recheck.signal.map(_.map {
        case Left(m)  => p(cls := "form-error", role := "alert", s"Could not re-check: $m")
        case Right(r) =>
          p(
            cls := "muted recheck-note",
            role := "status",
            r.job.fold[Modifier[HtmlElement]](
              span(s"Re-checked: ${r.closed} closed, ${r.fetched} found, ${r.noCopy} with no legal copy yet.")
            )(j =>
              span(
                "Re-check running as ",
                RouteLink(Route.Job(Pages.JobId(j.id.value)), s"job ${j.id.value.take(8)}"),
                " — it closes what it finds; ",
                RouteLink(Route.Activity(ActivityTab.Jobs), "follow it in Activity"),
                "."
              )
            )
          )
      }),
      child.maybe <-- Auth.writable.map(w =>
        Option.when(!w)(
          div(cls := "pane-note", Auth.keyNeeded(if sp.editable then "change the list" else "add entries"))
        )
      ),
      child.maybe <-- adding.signal.map(
        Option.when(_)(
          addForm(
            sp,
            items.signal,
            topicsId,
            (in, reset) => write(UiLabels.Text("add the item"), ApiClient.queueAdd(kind.label, in))(_ => reset()),
            () => adding.set(false)
          )
        )
      ),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child <-- items.signal.combineWith(loaded.signal, checks.signal, goals.signal).map { (its, ok, cs, gs) =>
        if !ok then p(cls := "muted", "Loading…")
        else if its.isEmpty then p(cls := "muted", sp.empty.value)
        else
          div(
            cls := "queue-groups",
            groups(kind, its).map((t, is) => renderGroup(sp, t, is, ops, topicsId, cs, gs, target))
          )
      }
    )

  /** The pane's writes, handed to the rows. */
  final private case class Ops(
      setDone: (Queue.QueueItem, Done) => Unit,
      edit: (Queue.QueueItem, Queue.QueuePatch, () => Unit) => Unit,
      delete: Queue.QueueItem => Unit,
      fulfil: (Queue.QueueItem, Preview.Target.Source.Citekey) => Unit,
      recheck: Queue.QueueItem => Unit
  )

  /** A topic path as the suggestion list shows it: readable, the levels joined by `›`. */
  private def topicLabel(t: Queue.QueueItem.Topic): UiLabels.Text =
    val (last, path) = heading(Some(t))
    UiLabels.Text((path :+ last).mkString(" › "))

  private def addForm(
      sp: Spec,
      items: Signal[List[Queue.QueueItem]],
      topicsId: String,
      send: (Queue.QueueAdd, () => Unit) => Unit,
      close: () => Unit
  ): HtmlElement =
    val draft = Var(AddDraft())
    val error = Var(Option.empty[String])
    def submit(): Unit =
      validateAdd(sp, draft.now()) match
        case Left(m)   => error.set(Some(m.sentence))
        case Right(in) =>
          error.set(None)
          // the topic stays: the next item usually goes under the same one
          send(in, () => draft.update(d => AddDraft(topic = d.topic)))
    div(
      cls := "queue-form",
      div(
        cls := "form-field",
        label(
          if sp.editable then "item" else "entry",
          textArea(
            cls := "field",
            rows := 3,
            placeholder := (if sp.fulfils then "e.g. Author, Title (Publisher, 2024; 10.…) — why it is wanted"
                            else if sp.editable then "what is left to do"
                            else "what was done"),
            controlled(
              value <-- draft.signal.map(_.text.value),
              onInput.mapToValue --> (v => draft.update(_.modify(_.text).setTo(AddDraft.Text(v))))
            )
          )
        )
      ),
      div(
        cls := "form-field",
        label(
          if sp.editable then "topic" else "date",
          input(
            typ := "text",
            cls := "field",
            listId := topicsId,
            placeholder := (if sp.editable then "optional — groups the item" else "optional — today when left empty"),
            controlled(
              value <-- draft.signal.map(_.topic.value),
              onInput.mapToValue --> (v => draft.update(_.modify(_.topic).setTo(AddDraft.Topic(v))))
            )
          )
        )
      ),
      if sp.reasons then
        div(
          cls := "form-field",
          label(
            "reason",
            input(
              typ := "text",
              cls := "field",
              placeholder := "optional — why it is wanted",
              controlled(
                value <-- draft.signal.map(_.reason.sentence),
                onInput.mapToValue --> (v =>
                  draft.update(_.modify(_.reason).setTo(Problem.Unclassified(Legacy.Text(v))))
                )
              )
            )
          )
        )
      else emptyNode,
      child.maybe <-- error.signal.map(_.map(m => div(cls := "field-error", role := "alert", m))),
      div(
        cls := "form-actions",
        button(
          typ := "button",
          cls := "btn btn--primary",
          "add",
          disabled <-- Auth.writable.map(!_),
          onClick --> (_ => submit())
        ),
        button(typ := "button", cls := "btn", "close", onClick --> (_ => close()))
      )
    )

  private def renderGroup(
      sp: Spec,
      topic: Option[Queue.QueueItem.Topic],
      its: List[Queue.QueueItem],
      ops: Ops,
      topicsId: RenderGroupTopicsId,
      checks: Map[Queue.QueueItem.Id, Acquire.Checks.AcquireCheck],
      goals: Map[Queue.QueueItem.Id, List[Queue.GoalRef]],
      target: Option[Queue.QueueItem.Id]
  ): HtmlElement =
    val (title, path) = heading(topic, UiLabels.Text(sp.untitled.value))
    div(
      cls := "queue-group",
      if path.nonEmpty then div(cls := "group-path muted", path.mkString(" › ")) else emptyNode,
      h3(cls := "group-topic", title.value),
      ul(
        cls := "queue-items",
        its.map(i =>
          renderItem(
            sp,
            i,
            ops,
            topicsId,
            checks.get(i.id),
            goals.getOrElse(i.id, Nil),
            ItemIsTarget(target.contains(i.id))
          )
        )
      )
    )

  private def renderItem(
      sp: Spec,
      item: Queue.QueueItem,
      ops: Ops,
      topicsId: RenderItemTopicsId,
      check: Option[Acquire.Checks.AcquireCheck],
      goals: List[Queue.GoalRef],
      isTarget: ItemIsTarget
  ): HtmlElement =
    val done = item.status.value == Queue.Done
    val mode = Var(Mode.Idle)
    li(
      cls := "queue-item",
      idAttr := itemAnchor(item.id),
      cls("done") := done,
      cls("is-target") := isTarget.value,
      if sp.checkable then
        label(
          cls := "item-check",
          input(
            typ := "checkbox",
            aria.label := (if done then sp.tick._2.value else sp.tick._1.value),
            checked := done,
            disabled <-- Auth.writable.map(!_),
            onChange.mapToChecked --> (v => ops.setDone(item, Done(v)))
          )
        )
      else emptyNode,
      div(
        cls := "item-body",
        child <-- mode.signal.map {
          case Mode.Editing => editForm(item, ops, topicsId, () => mode.set(Mode.Idle))
          case _            => div(cls := "item-text", segments(UiLabels.Text(item.text.value)).map(render))
        },
        item.reason
          .filter(w => s"$w".trim.nonEmpty)
          .map(r => div(cls := "item-reason muted", segments(UiLabels.Text(s"$r")).map(render))),
        item.doneAt.filter(_ => done).map(d => div(cls := "item-reason muted", s"done ${d.value.take(10)}")),
        check
          .filter(_ => !done)
          .map(c =>
            div(
              cls := "item-reason muted",
              title := s"checked ${c.checks} time(s)",
              segments(UiLabels.Text(s"${c.reason}")).map(render)
            )
          ),
        Option.when(goals.nonEmpty)(
          div(
            cls := "item-goals muted",
            if goals.size == 1 then "Goal: " else "Goals: ",
            goals
              .map(g =>
                WinLink(
                  WindowKey.Goal(WindowKey.Goal.Slug(g.slug.value)),
                  title := g.slug.value,
                  Option(g.title)
                    .filter(_.value.trim.nonEmpty)
                    .map(_.value)
                    .getOrElse(Wording.humanize(Wording.Id(g.slug.value)))
                )
              )
              .flatMap(l => List[Modifier[HtmlElement]](", ", l))
              .drop(1)
          )
        ),
        provenance(item),
        if !sp.editable then emptyNode
        else
          child <-- mode.signal.map {
            case Mode.ConfirmDelete =>
              div(
                cls := "item-actions confirm",
                span("Delete this item? "),
                actionButton(UiLabels.Text("delete"), () => ops.delete(item)).amend(cls := "btn--danger"),
                actionButton(UiLabels.Text("cancel"), () => mode.set(Mode.Idle), write = Writes(false))
              )
            case Mode.Linking => linkPicker(item, ops, () => mode.set(Mode.Idle))
            case Mode.Editing => emptyNode
            case Mode.Idle    =>
              div(
                cls := "item-actions",
                actionButton(UiLabels.Text("edit"), () => mode.set(Mode.Editing)),
                actionButton(UiLabels.Text("delete"), () => mode.set(Mode.ConfirmDelete)),
                if sp.fulfils && !done then
                  List(
                    actionButton(UiLabels.Text("Re-check now"), () => ops.recheck(item))
                      .amend(cls := "recheck-one", title := "Look for a legal open copy of this one, now"),
                    actionButton(UiLabels.Text("Upload…"), () => UploadView.prefill(QueueItemRef.uploadDraft(item))),
                    actionButton(UiLabels.Text("Link existing source…"), () => mode.set(Mode.Linking))
                  )
                else List.empty[HtmlElement]
              )
          }
      )
    )

  /** A list as Research's side navigation names it. */
  def listName(kind: Queue.Kind): UiLabels.Text = kind match
    case Queue.Kind.Acquire => UiLabels.Text("To acquire")
    case Queue.Kind.Tasks   => UiLabels.Text("Backlog")
    case Queue.Kind.Log     => UiLabels.Text("Worklog")

  /** Who wrote an item and who ticked it off: the agent run or background job, each a link to its page. */
  private def provenance(item: Queue.QueueItem): Option[HtmlElement] =
    val parts = item.createdBy
      .map(id =>
        span("Written by ", RouteLink(Route.Job(Pages.JobId(id.value)), s"agent work or job ${id.value.take(8)}"))
      )
      .toList ++
      item.closedBy
        .map(id =>
          span("ticked off by ", RouteLink(Route.Job(Pages.JobId(id.value)), s"agent work or job ${id.value.take(8)}"))
        )
        .toList
    Option.when(parts.nonEmpty)(
      div(cls := "item-provenance muted", parts.flatMap(p => List[Modifier[HtmlElement]](" · ", p)).drop(1))
    )

  /** What one agent run or background job wrote to the lists and ticked off — its page's side of the links. */
  def ofJob(jobId: JobInfo.Id): HtmlElement =
    val found = Var(Option.empty[List[Queue.QueueItem]])
    div(
      cls := "job-items",
      onMountCallback(_ =>
        scala.concurrent.Future
          .sequence(Queue.Kind.values.toList.map(k => ApiClient.queueOfJob(k.label, jobId)))
          .onComplete(r => found.set(Some(r.toOption.toList.flatten.flatMap(_.items))))
      ),
      child <-- found.signal.map {
        case None | Some(Nil) => emptyNode
        case Some(is)         =>
          div(
            h4("In the lists"),
            ul(
              cls := "job-items-list",
              is.map { i =>
                val kind = Queue.Kind.parse(i.list).getOrElse(Queue.Kind.Log)
                li(
                  span(
                    cls := "muted",
                    if i.closedBy.exists(_.value == jobId.value) then "ticked off · " else "wrote · "
                  ),
                  QueueItemLink(
                    kind,
                    Goal.Plans.GoalLink.QueueNumber(i.id.value),
                    s"${listName(kind)}: ${i.text.value.take(120)}"
                  )
                )
              }
            )
          )
      }
    )

  /** The id of an item's row, which a link to it scrolls to. */
  def itemAnchor(id: Queue.QueueItem.Id): String = s"queue-item-$id"

  /** The item a link to the list points at (`?item=12`). Pure. */
  def targetItem(search: Api.Search.Text): Option[Queue.QueueItem.Id] =
    Option(search.value)
      .map(_.stripPrefix("?"))
      .getOrElse("")
      .split('&')
      .toList
      .collectFirst { case kv if kv.startsWith("item=") => kv.drop(5) }
      .flatMap(_.toIntOption)
      .map(Queue.QueueItem.Id(_))

  /** A small button on an item; a write is disabled while writes are not allowed. */
  private def actionButton(text: UiLabels.Text, act: () => Unit, write: Writes = Writes(true)): HtmlElement =
    button(
      typ := "button",
      cls := "btn btn--sm",
      text.value,
      if write.value then disabled <-- Auth.writable.map(!_) else emptyMod,
      onClick --> (_ => act())
    )

  private def editForm(item: Queue.QueueItem, ops: Ops, topicsId: String, close: () => Unit): HtmlElement =
    val text = Var(item.text)
    val topic = Var(item.topic.getOrElse(Queue.QueueItem.Topic("")))
    val error = Var(Option.empty[String])
    def save(): Unit =
      editPatch(item, text.now(), topic.now()) match
        case Left(m)        => error.set(Some(m.sentence))
        case Right(None)    => close()
        case Right(Some(p)) => ops.edit(item, p, close)
    div(
      cls := "queue-form inline",
      textArea(
        cls := "field",
        rows := 3,
        aria.label := "item text",
        controlled(value <-- text.signal.map(_.value), onInput.mapToValue.map(Queue.QueueItem.Text(_)) --> text.writer)
      ),
      input(
        typ := "text",
        cls := "field",
        listId := topicsId,
        aria.label := "topic",
        placeholder := "topic — empty moves it out of its group",
        controlled(
          value <-- topic.signal.map(_.value),
          onInput.mapToValue.map(Queue.QueueItem.Topic(_)) --> topic.writer
        )
      ),
      child.maybe <-- error.signal.map(_.map(m => div(cls := "field-error", role := "alert", m))),
      div(
        cls := "form-actions",
        button(
          typ := "button",
          cls := "btn btn--primary",
          "save",
          disabled <-- Auth.writable.map(!_),
          onClick --> (_ => save())
        ),
        button(typ := "button", cls := "btn", "cancel", onClick --> (_ => close()))
      )
    )

  /** Search the corpus as the reader types; picking a source ticks the item off as fulfilled by it. */
  private def linkPicker(item: Queue.QueueItem, ops: Ops, close: () => Unit): HtmlElement =
    val query = Var(QueueItemRef.searchQuery(item))
    val hits = Var(Option.empty[List[SearchHit]])
    val problem = Var(Option.empty[String])
    def search(q: Api.Search.Text): Unit =
      if q.value.trim.isEmpty then hits.set(Some(Nil))
      else
        ApiClient.sources(Filter(q = Some(Filter.Q(q.value.trim)), limit = Filter.Limit(8))).onComplete { r =>
          // a slow answer to an older query must not replace the current one
          if query.now() == q then
            r.fold(
              e => problem.set(Some(s"Could not search: ${e.getMessage}")),
              res => { problem.set(None); hits.set(Some(res.hits.map(_.source))) }
            )
        }
    div(
      cls := "queue-form inline link-picker",
      onMountCallback(_ => search(query.now())),
      input(
        typ := "search",
        cls := "field",
        aria.label := "search the corpus for the source",
        placeholder := "title, author or DOI of the source",
        controlled(value <-- query.signal.map(_.value), onInput.mapToValue.map(Api.Search.Text(_)) --> query.writer),
        query.signal.changes.debounce(250) --> (q => search(q))
      ),
      child.maybe <-- problem.signal.map(_.map(m => div(cls := "field-error", role := "alert", m))),
      child <-- hits.signal.map {
        case None      => p(cls := "muted", "Searching…")
        case Some(Nil) => p(cls := "muted", "No source in the corpus matches.")
        case Some(hs)  =>
          ul(
            cls := "link-hits",
            hs.map(h =>
              li(
                button(
                  typ := "button",
                  cls := "link-hit",
                  disabled <-- Auth.writable.map(!_),
                  title := s"tick the item off as ingested as ${h.citekey}",
                  span(cls := "hit-title", h.title.value),
                  h.year.map(y => span(cls := "muted", s" ($y)")),
                  span(cls := "muted hit-key", " · ", code(h.citekey.value)),
                  onClick --> (_ => { ops.fulfil(item, Preview.Target.Source.Citekey(h.citekey.value)); close() })
                )
              )
            )
          )
      },
      div(cls := "form-actions", button(typ := "button", cls := "btn", "cancel", onClick --> (_ => close())))
    )

  private def render(s: Seg): Modifier[HtmlElement] = s match
    case Seg.Text(t)   => t.value
    case Seg.Bold(t)   => b(t.value)
    case Seg.Italic(t) => em(t.value)
    case Seg.Doi(d)    =>
      a(cls := "doi-link", href := s"https://doi.org/$d", target := "_blank", rel := "noopener", d.value)
    case Seg.Url(u)    => a(href := u.value, target := "_blank", rel := "noopener", u.value)
    case Seg.Cite(ck)  => SourceLink(ck)
    case Seg.Ident(id) => IdentLink(id)
