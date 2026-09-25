package com.histmersion.web

import com.histmersion.core.*
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The acquisition queue — what to buy, borrow or download — as a window of the main UI, like every
  * other view (`/acquire` opens the app with this window, so old links keep working). Items are
  * grouped under their topic path; ticking one off is a write, so it follows [[Auth]]. */
object AcquireView:

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

  /** A topic path (`**Group** > sub_topic`) as its heading and the path above it, readable. */
  def heading(topic: Option[String]): (String, List[String]) =
    val parts = topic.getOrElse("").split(" > ").toList.map(_.trim.stripPrefix("**").stripSuffix("**").trim)
      .filter(_.nonEmpty).map(Label.display)
    parts match
      case Nil => ("Uncategorised", Nil)
      case ps  => (ps.last, ps.init)

  /** The items grouped by topic, groups in the order their first item was added. */
  def groups(items: List[Queue.QueueItem]): List[(Option[String], List[Queue.QueueItem])] =
    items.groupBy(_.topic).toList.sortBy((_, is) => is.map(_.id).min).map((t, is) => t -> is.sortBy(_.id))

  // ── state ───────────────────────────────────────────────────────────────────────────────────────

  private val items   = Var(List.empty[Queue.QueueItem])
  private val loaded  = Var(false)
  private val failure = Var(Option.empty[String])

  private def load(): Unit =
    ApiClient.queueList(Queue.Kind.Acquire.label, UiState.acquireDone.now()).onComplete { r =>
      r.fold(
        e => failure.set(Some(s"Could not load the queue: ${e.getMessage}")),
        l =>
          failure.set(None)
          items.set(l.items.filterNot(_.status == Queue.Note))
          loaded.set(true)
      )
    }

  private def setDone(item: Queue.QueueItem, done: Boolean): Unit =
    ApiClient.queuePatch(item.id, Queue.QueuePatch(status = Some(if done then Queue.Done else Queue.Open)))
      .onComplete { r =>
        r.failed.foreach(e => failure.set(Some(s"Could not update item ${item.id}: ${e.getMessage}")))
        load()
      }

  // ── view ────────────────────────────────────────────────────────────────────────────────────────

  def pane: HtmlElement =
    div(
      cls := "tool-pane acquire-pane",
      onMountCallback(_ => load()),
      // the "show done" switch is UiState (in the URL), and the listing follows it
      UiState.acquireDone.signal.changes --> (_ => load()),
      div(
        cls := "pane-bar",
        LayerToggle(Icon.Acquire, "show done", UiState.acquireDone.signal, v => UiState.set(UiState.acquireDone := v)()),
        span(cls := "muted", child.text <-- items.signal.map { its =>
          val done = its.count(_.status == Queue.Done)
          s"${its.size - done} to get" + (if done > 0 then s" · $done done" else "")
        })
      ),
      child.maybe <-- Auth.writable.map(w => Option.when(!w)(div(cls := "pane-note", Auth.keyNeeded("tick items off")))),
      child.maybe <-- failure.signal.map(_.map(m => p(cls := "form-error", role := "alert", m))),
      child <-- items.signal.combineWith(loaded.signal).map { (its, ok) =>
        if !ok then p(cls := "muted", "Loading…")
        else if its.isEmpty then p(cls := "muted", "Nothing in the acquisition queue.")
        else div(cls := "acquire-groups", groups(its).map(renderGroup))
      }
    )

  private def renderGroup(topic: Option[String], its: List[Queue.QueueItem]): HtmlElement =
    val (title, path) = heading(topic)
    div(
      cls := "acquire-group",
      if path.nonEmpty then div(cls := "group-path muted", path.mkString(" › ")) else emptyNode,
      h3(cls := "group-topic", title),
      ul(cls := "acquire-items", its.map(renderItem))
    )

  private def renderItem(item: Queue.QueueItem): HtmlElement =
    val done = item.status == Queue.Done
    li(
      cls := "acquire-item",
      cls("done") := done,
      label(
        cls := "item-check",
        input(
          typ := "checkbox",
          aria.label := (if done then "mark as still to get" else "mark as got"),
          checked := done,
          disabled <-- Auth.writable.map(!_),
          onChange.mapToChecked --> (v => setDone(item, v))
        )
      ),
      div(
        cls := "item-body",
        div(cls := "item-text", segments(item.text).map(render)),
        item.reason.filter(_.trim.nonEmpty).map(r => div(cls := "item-reason muted", r)),
        item.doneAt.filter(_ => done).map(d => div(cls := "item-reason muted", s"done ${d.take(10)}"))
      )
    )

  private def render(s: Seg): Modifier[HtmlElement] = s match
    case Seg.Text(t)   => t
    case Seg.Bold(t)   => b(t)
    case Seg.Italic(t) => em(t)
    case Seg.Doi(d)    => a(cls := "doi-link", href := s"https://doi.org/$d", target := "_blank", rel := "noopener", d)
    case Seg.Url(u)    => a(href := u, target := "_blank", rel := "noopener", u)
    case Seg.Cite(ck)  => SourceLink(ck)
    case Seg.Ident(id) => IdentLink(id)
