package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.histmersion.api.Plural
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.library.{Filter, GeoHit, Hit, SearchResult}

object ResultsList:

  type BusyOn = BusyOn.T
  object BusyOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FailedMsg = FailedMsg.T
  object FailedMsg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickTitle = PickTitle.T
  object PickTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickIconIcon = PickIconIcon.T
  object PickIconIcon extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickIconKind = PickIconKind.T
  object PickIconKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluralResult = PluralResult.T
  object PluralResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluralN = PluralN.T
  object PluralN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluralWord = PluralWord.T
  object PluralWord extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SnippetHtmlResult = SnippetHtmlResult.T
  object SnippetHtmlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SnippetHtmlText = SnippetHtmlText.T
  object SnippetHtmlText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLineResult = CountLineResult.T
  object CountLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageSizeResult = PageSizeResult.T
  object PageSizeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextLabelResult = NextLabelResult.T
  object NextLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HitRowInPanel = HitRowInPanel.T
  object HitRowInPanel extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenSourceCk = OpenSourceCk.T
  object OpenSourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenSourceInPanel = OpenSourceInPanel.T
  object OpenSourceInPanel extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  case class SourceRenderOpts(
      findings: SourceRenderOpts.Findings = 0,
      total: SourceRenderOpts.Total = 0,
      inView: SourceRenderOpts.InView = false,
      params: Option[Filter] = None
  )

  object SourceRenderOpts:
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Total = Total.T
    object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InView = InView.T
    object InView extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  sealed private trait LastRender
  private case class LastSources(result: SearchResult, opts: SourceRenderOpts) extends LastRender

  private var last: Option[LastRender] = None
  private var pageHits: List[Hit] = Nil
  private var pageTotal: com.histmersion.api.RowCount = com.histmersion.api.RowCount(0)
  private var pageParams: Option[Filter] = None
  private var pageOpts: Option[SourceRenderOpts] = None

  private val content = Var[List[HtmlElement]](Nil)

  /** How many sources the filter selects (the Sources tab's count), once answered. */
  val total: Var[Option[com.histmersion.api.RowCount]] = Var(None)
  private val countText = Var("")
  private val isBusy = Var(false)
  private val isError = Var(false)

  /** The "show more" button's label (none while a cluster is picked or every source is shown), and whether its page is
    * on its way.
    */
  private val moreLabel = Var(Option.empty[String])
  private val moreLoading = Var(false)

  val element: HtmlElement =
    div(
      cls := "results-container",
      ResultsPaging.countBar(countText.signal, isBusy.signal, isError.signal, id = Some("count")),
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
              if rows(i) == active || (active != null && rows(i).contains(active)) then idx = i
              i += 1
            if idx >= 0 then
              ev.preventDefault()
              val nextIdx = idx + (if ev.key == "ArrowDown" then 1 else -1)
              if nextIdx >= 0 && nextIdx < rows.length then rows(nextIdx).asInstanceOf[dom.HTMLElement].focus()
        }
      ),
      ResultsPaging.moreButton(moreLabel.signal, moreLoading.signal, () => more())
    )

  def busy(on: com.histmersion.web.ResultsList.BusyOn): Unit =
    isBusy.set(on)
    if on then isError.set(false)

  def failed(msg: com.histmersion.web.ResultsList.FailedMsg): Unit =
    isError.set(true)
    isBusy.set(false)
    countText.set(if msg.nonEmpty then msg else "failed to load — retrying on the next change")

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
    content.set(if hits.isEmpty then List(emptyState()) else hits.map(hitRow(_, inPanel = true)))
    moreLoading.set(false)
    moreLabel.set(nextLabel())

  /** List what a map bubble (or a dot of coincident findings) holds: its findings by source — a row opens the source —
    * then its other dots (`others`: sites, events, pictures, each with its kind's icon), whose row opens what it is
    * ([[DotCluster]]: a site's or an event's card pinned under the row, a picture's popup on the map).
    */
  def pick(
      items: List[GeoHit],
      title: com.histmersion.web.ResultsList.PickTitle,
      onZoom: Option[() => Unit],
      others: List[(DotEntry, dom.Element => Unit)] = Nil
  ): Unit =
    // on a phone the list is a sheet at the bottom: a picked cluster must be where the reader looks
    if Viewport.narrowNow && UiState.sheet.now() == Sheet.Peek then UiState.set(UiState.sheet := Sheet.Half)()
    val bySource = scala.collection.mutable.LinkedHashMap
      .empty[String, (String, String, scala.collection.mutable.ListBuffer[GeoHit])]
    items.foreach { pt =>
      val entry =
        bySource.getOrElseUpdate(pt.citekey, (pt.citekey, pt.title, scala.collection.mutable.ListBuffer.empty))
      entry._3 += pt
    }

    val sorted = bySource.values.toList.sortBy(g => (-g._3.size, g._1))

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
      div(cls := "pick-title", title),
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
        pts.flatMap(_.periodStart).minOption,
        pts.flatMap(_.periodEnd).maxOption
      )
      div(
        cls := "hit hit-source",
        role := "button",
        tabIndex := 0,
        div(cls := "ck", pickIcon(CardIcons.GenericSource, "finding"), SourceLink(ck, pts.head.doi)),
        div(cls := "title", ttl),
        div(cls := "meta", s"${plural(pts.size, "finding")}" + (if per.nonEmpty then s" · $per" else "")),
        div(cls := "claim", Fmt.clip(pts.head.claim, 140)),
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
  private def pickIcon(icon: com.histmersion.web.ResultsList.PickIconIcon, kind: com.histmersion.web.ResultsList.PickIconKind): HtmlElement =
    span(
      cls := "cite-tip-icon pick-icon",
      title := kind,
      onMountCallback(ctx => ctx.thisNode.ref.innerHTML = Glyphs.svg(icon))
    )

  /** A picked dot that is not a finding: its icon, its name, its kind and years; a click or Enter opens it. */
  private def otherRow(entry: DotEntry, open: dom.Element => Unit): HtmlElement =
    div(
      cls := s"hit hit-dot hit-${entry.kind.one}",
      role := "button",
      tabIndex := 0,
      dataAttr("dot-kind") := entry.kind.toString.toLowerCase,
      div(cls := "title", pickIcon(entry.icon, entry.kind.one), entry.label),
      Option.when(entry.note.nonEmpty)(div(cls := "meta", entry.note)),
      onClick --> { ev => open(ev.currentTarget.asInstanceOf[dom.Element]) },
      onKeyDown --> { ev =>
        if ev.key == "Enter" || ev.key == " " then
          ev.preventDefault()
          open(ev.currentTarget.asInstanceOf[dom.Element])
      }
    )

  private def plural(n: com.histmersion.web.ResultsList.PluralN, word: com.histmersion.web.ResultsList.PluralWord): com.histmersion.web.ResultsList.PluralResult = s"$n $word${if n == 1 then "" else "s"}"

  private def snippetHtml(text: com.histmersion.web.ResultsList.SnippetHtmlText): com.histmersion.web.ResultsList.SnippetHtmlResult =
    val cleaned = text.replaceAll("\\s+", " ").trim
    Fmt.esc(cleaned).replaceAll("\\*\\*(.+?)\\*\\*", "<mark>$1</mark>")

  private def countLine(): com.histmersion.web.ResultsList.CountLineResult =
    val findings = pageOpts match
      case Some(o) if o.inView => s"${Plural.count(o.findings, "finding")} in view / ${o.total} total"
      case Some(o)             => Plural.count(o.findings, "finding")
      case None                => ""
    ResultsPaging.countLine(
      pageHits.length,
      pageTotal.value,
      "source",
      extras = List(findings, ResultsPaging.span(UiState.from.now(), UiState.to.now()))
    )

  /** The next page's size: the request's own limit (at least 20). */
  private def pageSize: com.histmersion.web.ResultsList.PageSizeResult = pageParams.fold(20)(p => math.max(p.limit, 20))

  private def nextLabel(): Option[com.histmersion.web.ResultsList.NextLabelResult] =
    pageParams.flatMap(_ => ResultsPaging.moreLabel(pageTotal.value - pageHits.length, pageSize))

  /** "show more": the next page from the server, appended; a failure says so in the count line. */
  private def more(): Unit =
    pageParams.foreach { params =>
      moreLoading.set(true)
      ApiClient.sources(params.modify(_.offset).setTo(pageHits.length)).onComplete {
        case scala.util.Success(r) =>
          pageTotal = r.total
          pageHits = pageHits ++ r.hits
          countText.set(countLine())
          content.set(pageHits.map(hitRow(_, inPanel = true)))
          moreLoading.set(false)
          moreLabel.set(nextLabel())
        case scala.util.Failure(e) =>
          moreLoading.set(false)
          failed(s"Could not list more sources: ${Option(e.getMessage).getOrElse(e.toString)}")
      }
    }

  /** A source's row: its link (in Explore's results a plain click shows its detail there; elsewhere it opens its
    * window), what it is, why it matched, and "open in a new tab".
    */
  private[web] def hitRow(h: Hit, inPanel: com.histmersion.web.ResultsList.HitRowInPanel = false): HtmlElement =
    val src = h.source
    val period = Fmt.periodStr(src.periodStart, src.periodEnd)
    val meta = List(
      src.year.map(_.value.toInt).map(_.toString),
      Some(src.status.wire),
      Some(period).filter(_.nonEmpty)
    ).flatten.map(Fmt.esc).mkString(" · ")

    val why = h.why.filterNot(w => "(?i)^period\\b".r.findFirstIn(w).isDefined)
    val matched = h.matched
    val shownMatches = matched.take(2)

    div(
      cls := "hit hit-source",
      role := "button",
      tabIndex := 0,
      div(cls := "ck", SourceLink(src.citekey.value.toString, src.doi.map(_.value))),
      a(
        cls := "title row-link",
        href := SourceLink.href(src.citekey.value.toString),
        src.title.value,
        onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); openSource(src.citekey.value.toString, inPanel) } }
      ),
      div(cls := "meta", meta),
      src.snippet
        .filter(_.nonEmpty)
        .map(s => div(cls := "snip", onMountCallback(ctx => ctx.thisNode.ref.innerHTML = snippetHtml(s))))
        .getOrElse(emptyNode),
      if why.nonEmpty then ul(cls := "why", why.map(w => li(w)))
      else emptyNode,
      if matched.nonEmpty then
        div(
          cls := "matches",
          span(cls := "lbl", "matches:"),
          ul(
            shownMatches.map { m =>
              val mwhy = m.why.filterNot(w => "(?i)^period\\b".r.findFirstIn(w).isDefined)
              li(
                span(cls := "claim", Fmt.clip(m.claim, 160)),
                Fmt.periodStr(m.periodStart, m.periodEnd) match
                  case "" => emptyNode
                  case p  => span(cls := "p", s" $p"),
                m.regionName.filter(_.nonEmpty).map(r => span(cls := "rg", s" ${Fmt.display(r)}")).getOrElse(emptyNode),
                if mwhy.nonEmpty then span(cls := "mwhy", s" ${mwhy.mkString(", ")}") else emptyNode
              )
            } ++ (if matched.length > shownMatches.length then
                    List(li(cls := "more-matches", s"+${matched.length - shownMatches.length} more"))
                  else Nil)
          )
        )
      else emptyNode,
      marks.RowActions(
        marks.ItemRef.source(src.citekey.value.toString, src.title.value),
        SourceLink.href(src.citekey.value.toString),
        List(src.authors.map(_.value).take(3).mkString(", "), src.year.map(_.value.toInt).fold("")(_.toString)).filter(_.nonEmpty).mkString(", ")
      ),
      onClick --> { ev =>
        val inLink = Option(ev.target.asInstanceOf[dom.Element].closest("a, button")).isDefined
        if !inLink then openSource(src.citekey.value.toString, inPanel)
      },
      onKeyDown --> { ev =>
        if (ev.key == "Enter" || ev.key == " ") && ev.target == ev.currentTarget then
          ev.preventDefault()
          openSource(src.citekey.value.toString, inPanel)
      }
    )

  private def openSource(ck: com.histmersion.web.ResultsList.OpenSourceCk, inPanel: com.histmersion.web.ResultsList.OpenSourceInPanel): Unit =
    if inPanel then ResultsPanel.show(Preview.Target.Source(ck))
    else ArticleModal.open(ck, Map("q" -> UiState.q.now().getOrElse("")))

  private def emptyState(): HtmlElement =
    val chips = UiState.activeChips()
    val causes = scala.collection.mutable.ListBuffer.empty[String]
    if UiState.inView.now() then causes += "the map viewport (\"only sources in view\" is on)"
    causes += s"the span ${Fmt.yr(UiState.from.now())} → ${Fmt.yr(UiState.to.now())}"
    if chips.nonEmpty then causes += s"${plural(chips.length, "filter")} below"

    div(
      cls := "empty",
      p(s"No sources match ${causes.mkString(", ")}."),
      if chips.nonEmpty then
        ul(
          cls := "empty-chips",
          chips.map { c =>
            // a chosen place is named by its label and kind, never by its id (#places-fix)
            if c.key == UiState.placeIds.key then
              li(
                child <-- PlacesInput
                  .chipTextSignal(c.value)
                  .map(t =>
                    Chip(
                      t,
                      prefix = Some(s"${c.label}: "),
                      extraCls = "place",
                      remove = Some(() => UiState.removeChip(c.key, c.value))
                    )
                  )
              )
            else
              li(
                Chip(
                  c.shown.getOrElse(Fmt.displayTag(c.value)),
                  prefix = Some(s"${c.label}: "),
                  remove = Some(() => UiState.removeChip(c.key, c.value))
                )
              )
          }
        )
      else p(cls := "muted", "No filters are active — widen the span, or pan the map.")
    )
