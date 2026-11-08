package com.histmersion
package web

import com.histmersion.api.Problem
import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.histmersion.api.Plural
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.library.{Filter, GeoHit, Hit, SearchResult}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.RowCount
import com.histmersion.api.UiLabels
import com.histmersion.api.library.Citekey
import com.histmersion.api.Markdown
import com.histmersion.api.Paging
import com.histmersion.api.catalogue.Year
import com.histmersion.web.marks.RowActions
import com.histmersion.web.marks.ItemRef
import com.histmersion.api.library.Title

object ResultsList:
  type Busy = Busy.T
  object Busy extends Newtype[Boolean, Pure]
  type InPanel = InPanel.T
  object InPanel extends Newtype[Boolean, Pure]

  type HitRowInPanel = InPanel
  val HitRowInPanel: InPanel.type = InPanel

  case class SourceRenderOpts(
      findings: SourceRenderOpts.Findings = SourceRenderOpts.Findings(0),
      total: SourceRenderOpts.Total = SourceRenderOpts.Total(0),
      inView: SourceRenderOpts.InView = SourceRenderOpts.InView(false),
      params: Option[Filter] = None
  )

  object SourceRenderOpts:
    type Findings = Findings.T
    object Findings extends Newtype[Int, Pure]
    type Total = Total.T
    object Total extends Newtype[Int, Pure]
    type InView = InView.T
    object InView extends Newtype[Boolean, Pure]

  sealed private trait LastRender
  private case class LastSources(result: SearchResult, opts: SourceRenderOpts) extends LastRender

  private var last: Option[LastRender] = None
  private var pageHits: List[Hit] = Nil
  private var pageTotal: RowCount = RowCount(0)
  private var pageParams: Option[Filter] = None
  private var pageOpts: Option[SourceRenderOpts] = None

  private val content = Var[List[HtmlElement]](Nil)

  /** How many sources the filter selects (the Sources tab's count), once answered. */
  val total: Var[Option[RowCount]] = Var(None)
  private val countText = Var(UiLabels.Text(""))
  private val isBusy = Var(false)
  private val isError = Var(false)

  /** The "show more" button's label (none while a cluster is picked or every source is shown), and whether its page is
    * on its way.
    */
  private val moreLabel = Var(Option.empty[UiLabels.Text])
  private val moreLoading = Var(false)

  val element: HtmlElement =
    div(
      cls := "results-container",
      ResultsPaging.countBar(
        countText.signal,
        isBusy.signal.map(b => ResultsPaging.Loading(b)),
        isError.signal.map(b => ResultsPaging.Failed(b)),
        id = Some("count")
      ),
      div(
        idAttr := "list",
        cls <-- isBusy.signal.map(b => if b then "list is-busy" else "list"),
        role := "list",
        children <-- content.signal,
        onKeyDown --> { ev =>
          if ev.key == "ArrowDown" || ev.key == "ArrowUp" then
            val listEl = ev.currentTarget.asInstanceOf[dom.Element]
            val rows = listEl.querySelectorAll(".hit")
            val active = dom.document.activeElement
            var idx = -1
            var i = 0
            while i < rows.length do
              if (rows(i) eq active) || (active != null && rows(i).contains(active)) then idx = i
              i += 1
            if idx >= 0 then
              ev.preventDefault()
              val nextIdx = idx + (if ev.key == "ArrowDown" then 1 else -1)
              if nextIdx >= 0 && nextIdx < rows.length then rows(nextIdx).asInstanceOf[dom.HTMLElement].focus()
        }
      ),
      ResultsPaging.moreButton(moreLabel.signal, moreLoading.signal.map(b => ResultsPaging.Loading(b)), () => more())
    )

  def busy(on: Busy): Unit =
    isBusy.set(on.value)
    if on.value then isError.set(false)

  def failed(msg: Problem): Unit =
    isError.set(true)
    isBusy.set(false)
    countText.set(UiLabels.Text(if msg.value.nonEmpty then msg.value
    else "failed to load — retrying on the next change"))

  def renderSources(result: SearchResult, opts: SourceRenderOpts): Unit =
    last = Some(LastSources(result, opts))
    val hits = result.hits
    pageHits = hits
    pageTotal = result.total
    pageParams = opts.params
    pageOpts = Some(opts)
    total.set(Some(result.total))

    isBusy.set(false)
    isError.set(false)

    countText.set(countLine())
    content.set(if hits.isEmpty then List(emptyState()) else hits.map(hitRow(_, inPanel = HitRowInPanel(true))))
    moreLoading.set(false)
    moreLabel.set(nextLabel())

  /** List what a map bubble (or a dot of coincident findings) holds: its findings by source — a row opens the source —
    * then its other dots (`others`: sites, events, pictures, each with its kind's icon), whose row opens what it is
    * ([[DotCluster]]: a site's or an event's card pinned under the row, a picture's popup on the map).
    */
  def pick(
      items: List[GeoHit],
      title: UiLabels.Text,
      onZoom: Option[() => Unit],
      others: List[(DotEntry, dom.Element => Unit)] = Nil
  ): Unit =
    // on a phone the list is a sheet at the bottom: a picked cluster must be where the reader looks
    if Viewport.narrowNow && UiState.sheet.now() == Sheet.Peek then UiState.set(UiState.sheet := Sheet.Half)()
    val bySource = scala.collection.mutable.LinkedHashMap
      .empty[Citekey, (Citekey, GeoHit.Title, scala.collection.mutable.ListBuffer[GeoHit])]
    items.foreach { pt =>
      val entry =
        bySource.getOrElseUpdate(pt.citekey, (pt.citekey, pt.title, scala.collection.mutable.ListBuffer.empty))
      entry._3 += pt
    }

    val sorted = bySource.values.toList.sortBy(g => (-g._3.size, g._1.value))

    val header = div(
      cls := "pick-head",
      a(
        href := "#",
        cls := "back",
        "◀ back to results",
        onClick.preventDefault --> { _ =>
          last match
            case Some(LastSources(r, o)) => renderSources(r, o)
            case _                       => content.set(Nil)
        }
      ),
      div(cls := "pick-title", title.value),
      onZoom match
        case Some(fn) =>
          button(
            typ := "button",
            cls := "btn btn--sm pick-zoom",
            "zoom to cluster",
            onClick --> { _ => fn() }
          )
        case None => emptyNode
    )

    val rows = sorted.map { case (ck, ttl, pts) =>
      val per = Fmt.periodStr(
        pts.flatMap(_.periodStart).minByOption(y => y.value: Long),
        pts.flatMap(_.periodEnd).maxByOption(y => y.value: Long)
      )
      div(
        cls := "hit hit-source",
        role := "button",
        tabIndex := 0,
        div(cls := "ck", pickIcon(Glyphs.Name(CardIcons.GenericSource), "finding"), SourceLink(ck, pts.head.doi)),
        div(cls := "title", ttl.value),
        div(cls := "meta", s"${plural(pts.size, "finding")}" + (if per.value.nonEmpty then s" · $per" else "")),
        div(cls := "claim", Fmt.clip(pts.head.claim.value, Markdown.CharLimit(140))),
        onClick --> { _ => ArticleModal.open(ck, Map("q" -> UiState.q.now().getOrElse(""))) },
        onKeyDown --> { ev =>
          if ev.key == "Enter" || ev.key == " " then
            ev.preventDefault()
            ArticleModal.open(ck, Map("q" -> UiState.q.now().getOrElse("")))
        }
      )
    }

    content.set(header :: rows ++ others.map(otherRow))
    moreLabel.set(None)

  /** A kind's icon at the head of a picked row, as a card's title has it. */
  private def pickIcon(icon: Glyphs.Name, kind: String): HtmlElement =
    span(
      cls := "cite-tip-icon pick-icon",
      title := kind,
      onMountCallback(ctx => ctx.thisNode.ref.innerHTML = Glyphs.svg(icon).value)
    )

  /** A picked dot that is not a finding: its icon, its name, its kind and years; a click or Enter opens it. */
  private def otherRow(entry: DotEntry, open: dom.Element => Unit): HtmlElement =
    div(
      cls := s"hit hit-dot hit-${entry.kind.one}",
      role := "button",
      tabIndex := 0,
      dataAttr("dot-kind") := entry.kind.toString.toLowerCase,
      div(cls := "title", pickIcon(Glyphs.Name(entry.icon.value), entry.kind.one.value), entry.label.value),
      Option.when(entry.note.value.nonEmpty)(div(cls := "meta", entry.note.value)),
      onClick --> { ev => open(ev.currentTarget.asInstanceOf[dom.Element]) },
      onKeyDown --> { ev =>
        if ev.key == "Enter" || ev.key == " " then
          ev.preventDefault()
          open(ev.currentTarget.asInstanceOf[dom.Element])
      }
    )

  private def plural(n: Int, word: String): String = s"$n $word${if n == 1 then "" else "s"}"

  private def snippetHtml(text: UiLabels.Text): String =
    val cleaned = text.value.replaceAll("\\s+", " ").trim
    Fmt.esc(cleaned).replaceAll("\\*\\*(.+?)\\*\\*", "<mark>$1</mark>")

  private def countLine(): UiLabels.Text =
    val findings = pageOpts match
      case Some(o) if o.inView.value =>
        s"${Plural.count(Plural.Count(o.findings.value.toLong), Plural.CountSingular("finding"))} in view / ${o.total} total"
      case Some(o) => Plural.count(Plural.Count(o.findings.value.toLong), Plural.CountSingular("finding")).value
      case None    => ""
    ResultsPaging.countLine(
      Paging.Shown(pageHits.length),
      Paging.Page.Total(pageTotal.value),
      Plural.Noun.Singular("source"),
      extras = List(
        UiLabels.Text(findings),
        ResultsPaging.span(UiState.from.now().map(Year(_)), UiState.to.now().map(Year(_)))
      )
    )

  /** The next page's size: the request's own limit (at least 20). */
  private def pageSize: Paging.Limit = Paging.Limit(pageParams.fold(20)(p => math.max(p.limit.value, 20)))

  private def nextLabel(): Option[UiLabels.Text] =
    pageParams.flatMap(_ =>
      ResultsPaging.moreLabel(RowCount(pageTotal.value - pageHits.length), ResultsPaging.PageNumber(pageSize.value))
    )

  /** "show more": the next page from the server, appended; a failure says so in the count line. */
  private def more(): Unit =
    pageParams.foreach { params =>
      moreLoading.set(true)
      ApiClient.sources(params.modify(_.offset).setTo(Filter.Offset(pageHits.length))).onComplete {
        case scala.util.Success(r) =>
          pageTotal = r.total
          pageHits = pageHits ++ r.hits
          countText.set(countLine())
          content.set(pageHits.map(hitRow(_, inPanel = HitRowInPanel(true))))
          moreLoading.set(false)
          moreLabel.set(nextLabel())
        case scala.util.Failure(e) =>
          moreLoading.set(false)
          failed(Problem(s"Could not list more sources: ${Option(e.getMessage).getOrElse(e.toString)}"))
      }
    }

  /** A source's row: its link (in Explore's results a plain click shows its detail there; elsewhere it opens its
    * window), what it is, why it matched, and "open in a new tab".
    */
  private[web] def hitRow(h: Hit, inPanel: HitRowInPanel = HitRowInPanel(false)): HtmlElement =
    val src = h.source
    val period = Fmt.periodStr(src.periodStart, src.periodEnd)
    val meta = List(
      src.year.map(_.value.toInt).map(_.toString),
      Some(src.status.wire.value),
      Some(period.value).filter(_.nonEmpty)
    ).flatten.map(Fmt.esc).mkString(" · ")

    val why = h.why.filterNot(w => "(?i)^period\\b".r.findFirstIn(w.value).isDefined)
    val matched = h.matched
    val shownMatches = matched.take(2)

    div(
      cls := "hit hit-source",
      role := "button",
      tabIndex := 0,
      div(cls := "ck", SourceLink(src.citekey, src.doi)),
      a(
        cls := "title row-link",
        href := SourceLink.href(src.citekey),
        src.title.value,
        onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); openSource(src.citekey, inPanel) } }
      ),
      div(cls := "meta", meta),
      src.snippet
        .filter(_.value.nonEmpty)
        .map(s =>
          div(cls := "snip", onMountCallback(ctx => ctx.thisNode.ref.innerHTML = snippetHtml(UiLabels.Text(s.value))))
        )
        .getOrElse(emptyNode),
      if why.nonEmpty then ul(cls := "why", why.map(w => li(w.value)))
      else emptyNode,
      if matched.nonEmpty then
        div(
          cls := "matches",
          span(cls := "lbl", "matches:"),
          ul(
            shownMatches.map { m =>
              val mwhy = m.why.filterNot(w => "(?i)^period\\b".r.findFirstIn(w.value).isDefined)
              li(
                span(cls := "claim", Fmt.clip(m.claim.value, Markdown.CharLimit(160))),
                Fmt.periodStr(m.periodStart, m.periodEnd).value match
                  case "" => emptyNode
                  case p  => span(cls := "p", s" $p"),
                m.regionName
                  .filter(_.value.nonEmpty)
                  .map(r => span(cls := "rg", s" ${Fmt.display(UiLabels.Text(r.value))}"))
                  .getOrElse(emptyNode),
                if mwhy.nonEmpty then span(cls := "mwhy", s" ${mwhy.mkString(", ")}") else emptyNode
              )
            } ++ (if matched.length > shownMatches.length then
                    List(li(cls := "more-matches", s"+${matched.length - shownMatches.length} more"))
                  else Nil)
          )
        )
      else emptyNode,
      RowActions(
        ItemRef.source(src.citekey, Title(src.title.value)),
        ItemRef.Href(SourceLink.href(src.citekey)),
        List(src.authors.map(_.value).take(3).mkString(", "), src.year.map(_.value.toInt).fold("")(_.toString))
          .filter(_.nonEmpty)
          .mkString(", ")
      ),
      onClick --> { ev =>
        val inLink = Option(ev.target.asInstanceOf[dom.Element].closest("a, button")).isDefined
        if !inLink then openSource(src.citekey, inPanel)
      },
      onKeyDown --> { ev =>
        if (ev.key == "Enter" || ev.key == " ") && (ev.target eq ev.currentTarget) then
          ev.preventDefault()
          openSource(src.citekey, inPanel)
      }
    )

  private def openSource(ck: Preview.Target.Source.Citekey, inPanel: InPanel): Unit =
    if inPanel.value then ResultsPanel.show(Preview.Target.Source(ck))
    else ArticleModal.open(ck, Map("q" -> UiState.q.now().getOrElse("")))

  private def emptyState(): HtmlElement =
    val chips = UiState.activeChips()
    val causes = scala.collection.mutable.ListBuffer.empty[String]
    if UiState.inView.now() then causes += "the map viewport (\"only sources in view\" is on)"
    causes += s"the span ${Fmt.yr(UiState.from.now().map(Year(_)))} → ${Fmt.yr(UiState.to.now().map(Year(_)))}"
    if chips.nonEmpty then causes += s"${plural(chips.length, "filter")} below"

    div(
      cls := "empty",
      p(s"No sources match ${causes.mkString(", ")}."),
      if chips.nonEmpty then
        ul(
          cls := "empty-chips",
          chips.map { c =>
            // a chosen place is named by its label and kind, never by its id (#places-fix)
            if c.key.value == UiState.placeIds.key.value then
              li(
                child <-- PlacesInput
                  .chipTextSignal(Pages.PlaceId(c.value.value))
                  .map(t =>
                    Chip(
                      Chip.Text(t.value),
                      prefix = Some(Chip.Prefix(s"${c.label}: ")),
                      extraCls = Chip.ExtraClass("place"),
                      remove = Some(() =>
                        UiState.removeChip(UiState.RemoveChipKey(c.key.value), UiState.FilterTerm(c.value.value))
                      )
                    )
                  )
              )
            else
              li(
                Chip(
                  Chip.Text(c.shown.map(_.value).getOrElse(Fmt.displayTag(Fmt.DisplayTagS(c.value.value)).value)),
                  prefix = Some(Chip.Prefix(s"${c.label}: ")),
                  remove = Some(() =>
                    UiState.removeChip(UiState.RemoveChipKey(c.key.value), UiState.FilterTerm(c.value.value))
                  )
                )
              )
          }
        )
      else p(cls := "muted", "No filters are active — widen the span, or pan the map.")
    )
