package com.histmersion.web

import com.histmersion.core.*
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The three bookkeeping lists — the acquisition queue, the backlog and the worklog — each a window
  * of the main UI, drawn by this ONE implementation parameterised by [[Queue.Kind]] (`/acquire`,
  * `/tasks` and `/log` open the app with that window, so plain links keep working).
  *
  * The two checkbox lists (acquire, tasks) group their items under their topic path, in the order
  * each group was first added; an item can be ticked off, edited (text and topic) and deleted. An
  * acquisition item can also be fulfilled: "Upload…" opens the upload form filled in from it (the
  * OCR job ticks it off when it succeeds), and "Link existing source…" picks a source already in
  * the corpus and ticks it off at once; either way it reads `ingested as [[citekey]]`. The
  * worklog's topic is the entry's date: it is grouped by that, newest date first, and its entries
  * can only be added. Every write follows [[Auth]]. */
object QueueView:

  /** A run of an item's text: the queue items are short Markdown lines. */
  enum Seg:
    case Text(s: String)
    case Bold(s: String)
    case Italic(s: String)
    /** A DOI, linked to its resolver. */
    case Doi(doi: String)
    case Url(url: String)
    /** A `[[citekey]]` — a source in the corpus. */
    case Cite(citekey: String)
    /** A bare snake_case identifier — maybe a citekey or an article slug ([[IdentLink]] decides). */
    case Ident(id: String)

  /** What differs between the three windows; everything else is shared.
    *
    * @param showDone the "show done" switch in [[UiState]] (in the URL); `None` = the list has no
    *                 done state (the worklog), so it is listed whole
    * @param openNoun how the count of open items reads ("12 to get", "30 open")
    * @param tick     the checkbox's label as (mark done, mark open again) */
  final case class Spec(
      kind: Queue.Kind,
      icon: Icon,
      empty: String,
      untitled: String,
      openNoun: String,
      showDone: Option[Field[Boolean]],
      tick: (String, String)
  ):
    /** Whether items carry a checkbox; the worklog's do not. */
    def checkable: Boolean = kind.checkboxes
    /** Whether an item can be edited and deleted here; the worklog is only added to. */
    def editable: Boolean = kind.checkboxes
    /** Whether an item carries a reason (why it is wanted); a worklog entry is its own record. */
    def reasons: Boolean = kind.checkboxes
    /** Whether an item is fulfilled by a source — uploaded, or linked to one in the corpus. */
    def fulfils: Boolean = kind == Queue.Kind.Acquire

  def spec(kind: Queue.Kind): Spec = kind match
    case Queue.Kind.Acquire =>
      Spec(kind, Icon.Acquire, "Nothing in the acquisition queue.", "Uncategorised", "to get",
        Some(UiState.acquireDone), ("mark as got", "mark as still to get"))
    case Queue.Kind.Tasks =>
      Spec(kind, Icon.Tasks, "Nothing in the backlog.", "Uncategorised", "open",
        Some(UiState.tasksDone), ("mark as done", "mark as still open"))
    case Queue.Kind.Log =>
      Spec(kind, Icon.Log, "Nothing in the worklog.", "Undated", "", None, ("", ""))

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  private val Token =
    ("""\*\*(.+?)\*\*|\*([^*\s][^*]*?)\*|\[\[([a-z][a-z0-9_.-]{1,120})\]\]|(https?://[^\s)\]]*[^\s)\].,;])|""" +
      """\b(10\.\d{4,9}/[^\s,;)\]]*[^\s,;.)\]])|\b([a-z][a-z0-9]*(?:_[a-z0-9]+)+)\b""").r

  /** Split an item's text into runs to render: bold, italic, DOIs, URLs, citekeys and bare
    * identifiers; the rest text. */
  def segments(text: String): List[Seg] =
    val out  = List.newBuilder[Seg]
    var last = 0
    Token.findAllMatchIn(text).foreach { m =>
      if m.start > last then out += Seg.Text(text.substring(last, m.start))
      out += (
        if m.group(1) != null then Seg.Bold(m.group(1))
        else if m.group(2) != null then Seg.Italic(m.group(2))
        else if m.group(3) != null then Seg.Cite(m.group(3))
        else if m.group(4) != null then Seg.Url(m.group(4))
        else if m.group(5) != null then Seg.Doi(m.group(5))
        else Seg.Ident(m.group(6)))
      last = m.end
    }
    if last < text.length then out += Seg.Text(text.substring(last))
    out.result()

  /** A topic path (`**Group** > sub_topic`) as its heading and the path above it, readable;
    * `untitled` stands in for an item with no topic. */
  def heading(topic: Option[String], untitled: String = "Uncategorised"): (String, List[String]) =
    val parts = topic.getOrElse("").split(" > ").toList.map(_.trim.stripPrefix("**").stripSuffix("**").trim)
      .filter(_.nonEmpty).map(Label.display)
    parts match
      case Nil => (untitled, Nil)
      case ps  => (ps.last, ps.init)

  private val LeadingDate = """^(\d{4}-\d{2}-\d{2}).*""".r

  /** The day a worklog group is about: the date its topic starts with (a range such as
    * `2026-06-24/25` keeps its first day), else the day its newest entry was written; `""` when
    * neither is known, which sorts last. */
  def logDate(topic: Option[String], items: List[Queue.QueueItem]): String =
    topic.map(_.trim) match
      case Some(LeadingDate(d)) => d
      case _                    => items.flatMap(_.createdAt).maxOption.map(_.take(10)).getOrElse("")

  /** The items grouped by topic, each group in id order. The checkbox lists keep the groups in the
    * order their first item was added; the worklog puts the newest date first (ties: file order). */
  def groups(kind: Queue.Kind, items: List[Queue.QueueItem]): List[(Option[String], List[Queue.QueueItem])] =
    val gs = items.groupBy(_.topic).toList.map((t, is) => t -> is.sortBy(_.id))
    if kind.checkboxes then gs.sortBy((_, is) => is.head.id)
    else gs.sortBy((t, is) => (logDate(t, is), is.head.id))(using Ordering.Tuple2(using Ordering.String.reverse, Ordering.Int))

  /** The line above the list: how many are left (and done), or for the worklog how many entries
    * over how many days. */
  def summary(spec: Spec, items: List[Queue.QueueItem]): String =
    if spec.checkable then
      val done = items.count(_.status == Queue.Done)
      s"${items.size - done} ${spec.openNoun}" + (if done > 0 then s" · $done done" else "")
    else
      val days = items.map(_.topic).distinct.size
      s"${plural(items.size, "entry", "entries")} · ${plural(days, "day", "days")}"

  private def plural(n: Int, one: String, many: String): String = s"$n ${if n == 1 then one else many}"


  // ── writing: add · edit · fulfil (pure) ─────────────────────────────────────────────────────────

  /** A list changed outside its own window (an upload ticked an acquisition item off): the open
    * window of that list reloads. */
  val changed: EventBus[Queue.Kind] = new EventBus[Queue.Kind]

  /** The write that ticks `item` off as fulfilled by the source `citekey` — the core rule
    * ([[Queue.fulfil]]) the server's OCR job applies too, addressed to the item. */
  def fulfil(item: Queue.QueueItem, citekey: String): (Int, Queue.QueuePatch) =
    item.id -> Queue.fulfil(item, citekey)

  /** The add form, as typed. */
  final case class AddDraft(text: String = "", topic: String = "", reason: String = "")

  private val EmptyText = "Write the item's text first."

  /** The item to add, or why not: the text must not be blank; a blank topic or reason is left out
    * (a worklog entry with no topic joins today's date, server-side); the worklog takes no reason. */
  def validateAdd(sp: Spec, d: AddDraft): Either[String, Queue.QueueAdd] =
    if d.text.trim.isEmpty then Left(EmptyText)
    else Right(Queue.QueueAdd(
      text = d.text.strip,
      topic = Some(d.topic.trim).filter(_.nonEmpty),
      reason = Some(d.reason.trim).filter(_ => sp.reasons).filter(_.nonEmpty)
    ))

  /** The patch an inline edit makes: only what changed (`None` = nothing did); a blank topic moves
    * the item out of its group; a blank text is refused. */
  def editPatch(item: Queue.QueueItem, text: String, topic: String): Either[String, Option[Queue.QueuePatch]] =
    if text.trim.isEmpty then Left(EmptyText)
    else
      val t        = text.strip
      val tp       = topic.trim
      val newText  = Option.when(t != item.text)(t)
      val newTopic = Option.when(Some(tp).filter(_.nonEmpty) != item.topic)(tp)
      Right(Option.when(newText.isDefined || newTopic.isDefined)(Queue.QueuePatch(text = newText, topic = newTopic)))

  /** The topics to offer while one is typed: those the list already uses, in the order they first
    * appear, matching what is typed (case and underscores aside), without the one typed exactly. */
  def topicSuggestions(items: List[Queue.QueueItem], typed: String, limit: Int = 12): List[String] =
    def norm(s: String) = Label.display(s).toLowerCase.trim
    val q = norm(typed)
    items.sortBy(_.id).flatMap(_.topic).distinct
      .filter(t => norm(t).contains(q) && t != typed.trim)
      .take(limit)

  // ── view ────────────────────────────────────────────────────────────────────────────────────────

  /** What an item's row is showing besides the item itself. */
  private enum Mode:
    case Idle, Editing, ConfirmDelete, Linking

  /** One window's pane. Its state lives with the pane, so each window loads its own list. */
  def pane(kind: Queue.Kind): HtmlElement =
    val sp      = spec(kind)
    val items   = Var(List.empty[Queue.QueueItem])
    val loaded  = Var(false)
    val failure = Var(Option.empty[String])
    val adding  = Var(false)
    val topicsId = s"queue-topics-${kind.label}"

    // a list without a done state is listed whole; the others follow their "show done" switch
    def load(): Unit =
      ApiClient.queueList(kind.label, sp.showDone.fold(true)(_.now())).onComplete { r =>
        r.fold(
          e => failure.set(Some(s"Could not load the list: ${e.getMessage}")),
          l =>
            failure.set(None)
            items.set(l.items.filterNot(_.status == Queue.Note))
            loaded.set(true)
        )
      }

    /** Every write of this pane: its failure lands in the pane; the list reloads either way. */
    def write[A](what: String, f: scala.concurrent.Future[A])(done: A => Unit = (_: A) => ()): Unit =
      f.onComplete { r =>
        r.fold(e => failure.set(Some(s"Could not $what: ${e.getMessage}")), a => { failure.set(None); done(a) })
        load()
      }

    val ops = Ops(
      setDone = (item, d) =>
        write(s"update item ${item.id}",
          ApiClient.queuePatch(item.id, Queue.QueuePatch(status = Some(if d then Queue.Done else Queue.Open))))(),
      edit = (item, p, after) => write(s"edit item ${item.id}", ApiClient.queuePatch(item.id, p))(_ => after()),
      delete = item => write(s"delete item ${item.id}", ApiClient.queueDelete(item.id))(),
      fulfil = (item, ck) =>
        val (id, p) = fulfil(item, ck)
        write(s"tick item $id off", ApiClient.queuePatch(id, p))()
    )

    div(
      cls := s"tool-pane queue-pane ${kind.label}-pane",
      onMountCallback(_ => load()),
      // the "show done" switch is UiState (in the URL), and the listing follows it
      sp.showDone.map(f => f.signal.changes --> (_ => load())),
      changed.events.filter(_ == kind) --> (_ => load()),
      htmlTag("datalist")(idAttr := topicsId,
        children <-- items.signal.map(its => topicSuggestions(its, "", limit = 200).map(t =>
          option(value := t, htmlAttr("label", com.raquo.laminar.codecs.StringAsIsCodec) := topicLabel(t))))),
      div(
        cls := "pane-bar",
        sp.showDone.map(f => LayerToggle(sp.icon, "show done", f.signal, v => UiState.set(f := v)())),
        LayerToggle(Icon.Edit, if sp.editable then "add item" else "add entry", adding.signal, adding.set),
        span(cls := "muted", child.text <-- items.signal.map(summary(sp, _)))
      ),
      child.maybe <-- Auth.writable.map(w =>
        Option.when(!w)(div(cls := "pane-note", Auth.keyNeeded(if sp.editable then "change the list" else "add entries")))),
      child.maybe <-- adding.signal.map(Option.when(_)(addForm(sp, items.signal, topicsId,
        (in, reset) => write("add the item", ApiClient.queueAdd(kind.label, in))(_ => reset()),
        () => adding.set(false)))),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child <-- items.signal.combineWith(loaded.signal).map { (its, ok) =>
        if !ok then p(cls := "muted", "Loading…")
        else if its.isEmpty then p(cls := "muted", sp.empty)
        else div(cls := "queue-groups", groups(kind, its).map((t, is) => renderGroup(sp, t, is, ops, topicsId)))
      }
    )

  /** The pane's writes, handed to the rows. */
  private final case class Ops(
      setDone: (Queue.QueueItem, Boolean) => Unit,
      edit: (Queue.QueueItem, Queue.QueuePatch, () => Unit) => Unit,
      delete: Queue.QueueItem => Unit,
      fulfil: (Queue.QueueItem, String) => Unit
  )

  /** A topic path as the suggestion list shows it: readable, the levels joined by `›`. */
  private def topicLabel(t: String): String =
    val (last, path) = heading(Some(t))
    (path :+ last).mkString(" › ")

  private def addForm(sp: Spec, items: Signal[List[Queue.QueueItem]], topicsId: String,
      send: (Queue.QueueAdd, () => Unit) => Unit, close: () => Unit): HtmlElement =
    val draft = Var(AddDraft())
    val error = Var(Option.empty[String])
    def submit(): Unit =
      validateAdd(sp, draft.now()) match
        case Left(m)   => error.set(Some(m))
        case Right(in) =>
          error.set(None)
          // the topic stays: the next item usually goes under the same one
          send(in, () => draft.update(d => AddDraft(topic = d.topic)))
    div(
      cls := "queue-form",
      div(cls := "form-field",
        label(if sp.editable then "item" else "entry",
          textArea(cls := "field", rows := 3,
            placeholder := (if sp.fulfils then "e.g. Author, Title (Publisher, 2024; 10.…) — why it is wanted"
                            else if sp.editable then "what is left to do" else "what was done"),
            controlled(value <-- draft.signal.map(_.text), onInput.mapToValue --> (v => draft.update(_.copy(text = v))))))),
      div(cls := "form-field",
        label(if sp.editable then "topic" else "date",
          input(typ := "text", cls := "field", listId := topicsId,
            placeholder := (if sp.editable then "optional — groups the item" else "optional — today when left empty"),
            controlled(value <-- draft.signal.map(_.topic), onInput.mapToValue --> (v => draft.update(_.copy(topic = v))))))),
      if sp.reasons then
        div(cls := "form-field",
          label("reason",
            input(typ := "text", cls := "field", placeholder := "optional — why it is wanted",
              controlled(value <-- draft.signal.map(_.reason), onInput.mapToValue --> (v => draft.update(_.copy(reason = v)))))))
      else emptyNode,
      child.maybe <-- error.signal.map(_.map(m => div(cls := "field-error", role := "alert", m))),
      div(cls := "form-actions",
        button(typ := "button", cls := "btn btn--primary", "add", disabled <-- Auth.writable.map(!_), onClick --> (_ => submit())),
        button(typ := "button", cls := "btn", "close", onClick --> (_ => close())))
    )

  private def renderGroup(sp: Spec, topic: Option[String], its: List[Queue.QueueItem], ops: Ops,
      topicsId: String): HtmlElement =
    val (title, path) = heading(topic, sp.untitled)
    div(
      cls := "queue-group",
      if path.nonEmpty then div(cls := "group-path muted", path.mkString(" › ")) else emptyNode,
      h3(cls := "group-topic", title),
      ul(cls := "queue-items", its.map(renderItem(sp, _, ops, topicsId)))
    )

  private def renderItem(sp: Spec, item: Queue.QueueItem, ops: Ops, topicsId: String): HtmlElement =
    val done = item.status == Queue.Done
    val mode = Var(Mode.Idle)
    li(
      cls := "queue-item",
      cls("done") := done,
      if sp.checkable then
        label(
          cls := "item-check",
          input(
            typ := "checkbox",
            aria.label := (if done then sp.tick._2 else sp.tick._1),
            checked := done,
            disabled <-- Auth.writable.map(!_),
            onChange.mapToChecked --> (v => ops.setDone(item, v))
          )
        )
      else emptyNode,
      div(
        cls := "item-body",
        child <-- mode.signal.map {
          case Mode.Editing => editForm(item, ops, topicsId, () => mode.set(Mode.Idle))
          case _            => div(cls := "item-text", segments(item.text).map(render))
        },
        item.reason.filter(_.trim.nonEmpty).map(r => div(cls := "item-reason muted", segments(r).map(render))),
        item.doneAt.filter(_ => done).map(d => div(cls := "item-reason muted", s"done ${d.take(10)}")),
        if !sp.editable then emptyNode
        else
          child <-- mode.signal.map {
            case Mode.ConfirmDelete =>
              div(cls := "item-actions confirm",
                span("Delete this item? "),
                actionButton("delete", () => ops.delete(item)).amend(cls := "btn--danger"),
                actionButton("cancel", () => mode.set(Mode.Idle), write = false))
            case Mode.Linking => linkPicker(item, ops, () => mode.set(Mode.Idle))
            case Mode.Editing => emptyNode
            case Mode.Idle =>
              div(cls := "item-actions",
                actionButton("edit", () => mode.set(Mode.Editing)),
                actionButton("delete", () => mode.set(Mode.ConfirmDelete)),
                if sp.fulfils && !done then
                  List(
                    actionButton("Upload…", () => UploadView.prefill(QueueItemRef.uploadDraft(item))),
                    actionButton("Link existing source…", () => mode.set(Mode.Linking)))
                else List.empty[HtmlElement])
          }
      )
    )

  /** A small button on an item; a write is disabled while writes are not allowed. */
  private def actionButton(text: String, act: () => Unit, write: Boolean = true): HtmlElement =
    button(typ := "button", cls := "btn btn--sm", text,
      if write then disabled <-- Auth.writable.map(!_) else emptyMod,
      onClick --> (_ => act()))

  private def editForm(item: Queue.QueueItem, ops: Ops, topicsId: String, close: () => Unit): HtmlElement =
    val text  = Var(item.text)
    val topic = Var(item.topic.getOrElse(""))
    val error = Var(Option.empty[String])
    def save(): Unit =
      editPatch(item, text.now(), topic.now()) match
        case Left(m)        => error.set(Some(m))
        case Right(None)    => close()
        case Right(Some(p)) => ops.edit(item, p, close)
    div(
      cls := "queue-form inline",
      textArea(cls := "field", rows := 3, aria.label := "item text",
        controlled(value <-- text.signal, onInput.mapToValue --> text.writer)),
      input(typ := "text", cls := "field", listId := topicsId, aria.label := "topic",
        placeholder := "topic — empty moves it out of its group",
        controlled(value <-- topic.signal, onInput.mapToValue --> topic.writer)),
      child.maybe <-- error.signal.map(_.map(m => div(cls := "field-error", role := "alert", m))),
      div(cls := "form-actions",
        button(typ := "button", cls := "btn btn--primary", "save", disabled <-- Auth.writable.map(!_), onClick --> (_ => save())),
        button(typ := "button", cls := "btn", "cancel", onClick --> (_ => close())))
    )

  /** Search the corpus as the reader types; picking a source ticks the item off as fulfilled by it. */
  private def linkPicker(item: Queue.QueueItem, ops: Ops, close: () => Unit): HtmlElement =
    val query   = Var(QueueItemRef.searchQuery(item))
    val hits    = Var(Option.empty[List[SearchHit]])
    val problem = Var(Option.empty[String])
    def search(q: String): Unit =
      if q.trim.isEmpty then hits.set(Some(Nil))
      else
        ApiClient.sources(Filter(q = Some(q.trim), limit = 8)).onComplete { r =>
          // a slow answer to an older query must not replace the current one
          if query.now() == q then
            r.fold(e => problem.set(Some(s"Could not search: ${e.getMessage}")),
              res => { problem.set(None); hits.set(Some(res.hits.map(_.source))) })
        }
    div(
      cls := "queue-form inline link-picker",
      onMountCallback(_ => search(query.now())),
      input(typ := "search", cls := "field", aria.label := "search the corpus for the source",
        placeholder := "title, author or DOI of the source",
        controlled(value <-- query.signal, onInput.mapToValue --> query.writer),
        query.signal.changes.debounce(250) --> (q => search(q))),
      child.maybe <-- problem.signal.map(_.map(m => div(cls := "field-error", role := "alert", m))),
      child <-- hits.signal.map {
        case None               => p(cls := "muted", "Searching…")
        case Some(Nil)          => p(cls := "muted", "No source in the corpus matches.")
        case Some(hs)           =>
          ul(cls := "link-hits", hs.map(h =>
            li(button(typ := "button", cls := "link-hit",
              disabled <-- Auth.writable.map(!_),
              title := s"tick the item off as ingested as ${h.citekey}",
              span(cls := "hit-title", h.title), h.year.map(y => span(cls := "muted", s" ($y)")),
              span(cls := "muted hit-key", " · ", code(h.citekey)),
              onClick --> (_ => { ops.fulfil(item, h.citekey); close() })))))
      },
      div(cls := "form-actions", button(typ := "button", cls := "btn", "cancel", onClick --> (_ => close())))
    )

  private def render(s: Seg): Modifier[HtmlElement] = s match
    case Seg.Text(t)   => t
    case Seg.Bold(t)   => b(t)
    case Seg.Italic(t) => em(t)
    case Seg.Doi(d)    => a(cls := "doi-link", href := s"https://doi.org/$d", target := "_blank", rel := "noopener", d)
    case Seg.Url(u)    => a(href := u, target := "_blank", rel := "noopener", u)
    case Seg.Cite(ck)  => SourceLink(ck)
    case Seg.Ident(id) => IdentLink(id)
