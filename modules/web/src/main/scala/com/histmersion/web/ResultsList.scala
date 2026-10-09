package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ResultsList:

  case class SourceRenderOpts(
      findings: Int = 0,
      total: Int = 0,
      inView: Boolean = false,
      params: Option[Filter] = None
  )

  private sealed trait LastRender
  private case class LastSources(result: SearchResult, opts: SourceRenderOpts) extends LastRender

  private var last: Option[LastRender] = None
  private var pageHits: List[Hit] = Nil
  private var pageTotal: Int = 0
  private var pageParams: Option[Filter] = None
  private var pageOpts: Option[SourceRenderOpts] = None

  private val content = Var[List[HtmlElement]](Nil)
  private val countText = Var("")
  private val isBusy = Var(false)
  private val isError = Var(false)

  val element: HtmlElement =
    div(
      cls := "results-container",
      div(
        idAttr := "count",
        cls <-- isBusy.signal.combineWith(isError.signal).map { (busy, err) =>
          "count" + (if busy then " is-loading" else "") + (if err then " is-error" else "")
        },
        child.text <-- countText.signal
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
              if rows(i) == active || (active != null && rows(i).contains(active)) then idx = i
              i += 1
            if idx >= 0 then
              ev.preventDefault()
              val nextIdx = idx + (if ev.key == "ArrowDown" then 1 else -1)
              if nextIdx >= 0 && nextIdx < rows.length then
                rows(nextIdx).asInstanceOf[dom.HTMLElement].focus()
        }
      )
    )

  def busy(on: Boolean): Unit =
    isBusy.set(on)
    if on then isError.set(false)

  def failed(msg: String): Unit =
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

    isBusy.set(false)
    isError.set(false)

    if hits.isEmpty then
      countText.set(countLine())
      content.set(List(emptyState()))
    else
      countText.set(countLine())
      val rows = hits.map(hitRow) ++ moreButton()
      content.set(rows)

  def pick(items: List[GeoHit], title: String, onZoom: Option[() => Unit]): Unit =
    // on a phone the list is a sheet at the bottom: a picked cluster must be where the reader looks
    if Viewport.narrowNow && UiState.sheet.now() == Sheet.Peek then UiState.set(UiState.sheet := Sheet.Half)()
    val bySource = scala.collection.mutable.LinkedHashMap.empty[String, (String, String, scala.collection.mutable.ListBuffer[GeoHit])]
    items.foreach { pt =>
      val entry = bySource.getOrElseUpdate(pt.citekey, (pt.citekey, pt.title, scala.collection.mutable.ListBuffer.empty))
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
        case Some(fn) => button(
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
        div(cls := "ck", SourceLink(ck, pts.head.doi)),
        div(cls := "title", ttl),
        div(cls := "meta",
          s"${plural(pts.size, "finding")}" + (if per.nonEmpty then s" · ${per}" else "")
        ),
        div(cls := "claim", Fmt.clip(pts.head.claim, 140)),
        onClick --> { _ => ArticleModal.open(ck, Map("q" -> UiState.q.now().getOrElse(""))) },
        onKeyDown --> { ev =>
          if ev.key == "Enter" || ev.key == " " then
            ev.preventDefault()
            ArticleModal.open(ck, Map("q" -> UiState.q.now().getOrElse("")))
        }
      )
    }

    content.set(header :: rows)

  private def plural(n: Int, word: String): String = s"$n $word${if n == 1 then "" else "s"}"

  private def snippetHtml(text: String): String =
    val cleaned = text.replaceAll("\\s+", " ").trim
    Fmt.esc(cleaned).replaceAll("\\*\\*(.+?)\\*\\*", "<mark>$1</mark>")

  private def countLine(): String =
    val from = UiState.from.now()
    val to = UiState.to.now()
    val span = s"${Fmt.yr(from)} → ${Fmt.yr(to)}"
    val shown = pageHits.length
    val head =
      if pageTotal == 0 then "no sources"
      else if shown < pageTotal then s"showing 1–$shown of ${plural(pageTotal, "source")}"
      else plural(pageTotal, "source")
    val findingsStr = pageOpts match
      case Some(o) if o.inView => s" · ${Plural.count(o.findings, "finding")} in view / ${o.total} total"
      case Some(o) => s" · ${plural(o.findings, "finding")}"
      case None => ""
    s"$head$findingsStr · $span"

  private def hitRow(h: Hit): HtmlElement =
    val src = h.source
    val period = Fmt.periodStr(src.periodStart, src.periodEnd)
    val meta = List(
      src.year.map(_.toString),
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
      div(cls := "ck", SourceLink(src.citekey, src.doi)),
      div(cls := "title", src.title),
      div(cls := "meta", meta),
      src.snippet.filter(_.nonEmpty).map(s =>
        div(cls := "snip", onMountCallback(ctx => ctx.thisNode.ref.innerHTML = snippetHtml(s)))
      ).getOrElse(emptyNode),
      if why.nonEmpty then
        ul(cls := "why", why.map(w => li(w)))
      else emptyNode,
      if matched.nonEmpty then
        div(cls := "matches",
          span(cls := "lbl", "matches:"),
          ul(
            shownMatches.map { m =>
              val mwhy = m.why.filterNot(w => "(?i)^period\\b".r.findFirstIn(w).isDefined)
              li(
                span(cls := "claim", Fmt.clip(m.claim, 160)),
                Fmt.periodStr(m.periodStart, m.periodEnd) match
                  case "" => emptyNode
                  case p  => span(cls := "p", s" ${p}"),
                m.regionName.filter(_.nonEmpty).map(r => span(cls := "rg", s" ${Fmt.display(r)}")).getOrElse(emptyNode),
                if mwhy.nonEmpty then span(cls := "mwhy", s" ${mwhy.mkString(", ")}") else emptyNode
              )
            } ++ (if matched.length > shownMatches.length then
              List(li(cls := "more-matches", s"+${matched.length - shownMatches.length} more"))
            else Nil)
          )
        )
      else emptyNode,
      onClick --> { _ => ArticleModal.open(src.citekey, Map("q" -> UiState.q.now().getOrElse(""))) },
      onKeyDown --> { ev =>
        if ev.key == "Enter" || ev.key == " " then
          ev.preventDefault()
          ArticleModal.open(src.citekey, Map("q" -> UiState.q.now().getOrElse("")))
      }
    )

  private def emptyState(): HtmlElement =
    val chips = UiState.activeChips()
    val causes = scala.collection.mutable.ListBuffer.empty[String]
    if UiState.inView.now() then causes += "the map viewport (\"only sources in view\" is on)"
    causes += s"the span ${Fmt.yr(UiState.from.now())} → ${Fmt.yr(UiState.to.now())}"
    if chips.nonEmpty then causes += s"${plural(chips.length, "filter")} below"

    div(cls := "empty",
      p(s"No sources match ${causes.mkString(", ")}."),
      if chips.nonEmpty then
        ul(cls := "empty-chips",
          chips.map { c =>
            // a chosen place is named by its label and kind, never by its id (#places-fix)
            if c.key == UiState.placeIds.key then
              li(child <-- PlacesInput.chipTextSignal(c.value).map(t =>
                Chip(t, prefix = Some(s"${c.label}: "), extraCls = "place",
                  remove = Some(() => UiState.removeChip(c.key, c.value)))))
            else
              li(Chip(c.shown.getOrElse(Fmt.displayTag(c.value)), prefix = Some(s"${c.label}: "),
                remove = Some(() => UiState.removeChip(c.key, c.value))))
          }
        )
      else
        p(cls := "muted", "No filters are active — widen the span, or pan the map.")
    )

  private def moreButton(): List[HtmlElement] =
    pageParams match
      case None => Nil
      case Some(params) =>
        val left = pageTotal - pageHits.length
        if left <= 0 then Nil
        else
          val size = math.min(left, math.max(params.limit, 20))
          val loading = Var(false)
          List(button(
            typ := "button",
            cls := "btn btn--block more",
            child.text <-- loading.signal.map(l =>
              if l then "loading…"
              else s"show $size more ($left left)"
            ),
            disabled <-- loading.signal,
            onClick --> { _ =>
              loading.set(true)
              val nextParams = params.copy(offset = pageHits.length)
              ApiClient.sources(nextParams).foreach { r =>
                pageTotal = r.total
                pageHits = pageHits ++ r.hits
                countText.set(countLine())
                val rows = pageHits.map(hitRow) ++ moreButton()
                content.set(rows)
              }
            }
          ))
