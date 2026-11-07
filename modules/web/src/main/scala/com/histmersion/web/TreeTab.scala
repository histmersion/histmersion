package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.histmersion.api.{Api, Plural}
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.catalogue.Period
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.library.{Bucket, TimeHistogram}

object TreeTab:
  type Depth = Depth.T
  object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Expanded = Expanded.T
  object Expanded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Filled = Filled.T
  object Filled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Openable = Openable.T
  object Openable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Axis = Axis.T
  object Axis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderNodeDepth = _root_.com.histmersion.web.TreeTab.Depth
  val RenderNodeDepth: _root_.com.histmersion.web.TreeTab.Depth.type = _root_.com.histmersion.web.TreeTab.Depth
  type ToggleDepth = _root_.com.histmersion.web.TreeTab.Depth
  val ToggleDepth: _root_.com.histmersion.web.TreeTab.Depth.type = _root_.com.histmersion.web.TreeTab.Depth
  type FillWithDepth = _root_.com.histmersion.web.TreeTab.Depth
  val FillWithDepth: _root_.com.histmersion.web.TreeTab.Depth.type = _root_.com.histmersion.web.TreeTab.Depth

  private var config: Option[UiConfig] = None
  private var axis: Option[com.histmersion.web.TreeTab.Axis] = None
  private var lastData: Option[TimeHistogram] = None

  /** Drops the answers to a superseded load ([[Latest]]). */
  private val latest = Latest()
  private var timer: Int = 0
  private var lastParams: Option[String] = None

  private val treeContent = Var[List[HtmlElement]](Nil)
  private val countText = Var("")
  private val loading = Var(false)
  private val failed = Var(false)

  def pane: HtmlElement =
    div(
      cls := "tree-pane",
      idAttr := "tree-pane",
      ResultsPaging.countBar(countText.signal, loading.signal, failed.signal, id = Some("tree-count")),
      div(idAttr := "tree-list", cls := "tree-list", children <-- treeContent.signal)
    )

  def show(tab: String): Unit =
    val mine = tab match
      case "bytime"   => Some("time")
      case "byregion" => Some("region")
      case _          => None
    axis = mine
    mine match
      case Some(_) => load()
      case None    => ()

  def init(cfg: UiConfig): Unit =
    config = Some(cfg)
    UiState.signal.foreach { _ =>
      schedule()
    }(using unsafeWindowOwner)

  private def schedule(): Unit =
    if axis.isEmpty then ()
    else
      val key = paramsKey()
      if lastParams.contains(key) then ()
      else
        lastParams = Some(key)
        dom.window.clearTimeout(timer)
        val ms = config.flatMap(c => Option(c.debounce.reloadMs)).getOrElse(0)
        timer = dom.window.setTimeout(() => load(), UiState.reloadDelay(ms))

  private def paramsKey(): String =
    val f = UiState.toFilter()
    s"${axis.getOrElse("")}:${f.hashCode}"

  def load(): Unit =
    config match
      case None      => ()
      case Some(cfg) =>
        if axis.isEmpty then ()
        else
          val mine = latest.start()
          if countText.now().isEmpty then countText.set("loading…") else loading.set(true)
          failed.set(false)
          doLoad(cfg, mine)

  private def doLoad(cfg: UiConfig, mine: Int): Unit =
    val by = axis.getOrElse("time")
    val f = UiState.toFilter()
    val asOf = if by == "region" then for a <- UiState.from.now(); b <- UiState.to.now() yield (a + b) / 2
    else None

    ApiClient
      .aggregate(
        Api.AggregateQuery.tree(
          by = by,
          filter = f,
          depth = Some(cfg.treeDepth),
          asOf = asOf,
          samples = Some(cfg.treeSamples)
        )
      )
      .onComplete { r =>
        if latest.isCurrent(mine) then
          loading.set(false)
          latest.answer(r, showTree, m => { failed.set(true); countText.set(s"Could not group the sources: $m") })
      }

  private def showTree(data: TimeHistogram): Unit =
    lastData = Some(data)
    val roots = data.tree
    countText.set(
      countLine(data, roots.size, UiState.from.now().getOrElse(data.from), UiState.to.now().getOrElse(data.to))
    )

    if roots.isEmpty then
      treeContent.set(
        List(
          div(cls := "empty", p("nothing to group — widen the span or remove a chip"))
        )
      )
    else treeContent.set(roots.map(b => renderNode(b, com.histmersion.web.TreeTab.RenderNodeDepth(0), data)))

  /** The count line, in the sidebar's one form ([[ResultsPaging]]): every source is in the tree, so
    * `285 sources · 12 periods · 1475 BCE → 1465 BCE` (`· as of <year>` for regions).
    */
  def countLine(data: TimeHistogram, roots: com.histmersion.api.RowCount, from: com.histmersion.api.Years.Astronomical, to: com.histmersion.api.Years.Astronomical): com.histmersion.api.UiLabels.Text =
    val groups =
      if data.by == "region" then Plural.count(roots.toLong, com.histmersion.api.Plural.CountSingular("region")) else Plural.count(roots.toLong, com.histmersion.api.Plural.CountSingular("period"))
    ResultsPaging.countLine(
      data.total,
      data.total,
      com.histmersion.api.Plural.Noun.Singular("source"),
      extras = List(groups, ResultsPaging.span(Some(from), Some(to))) ++ data.asOf.map(a => s"as of ${Fmt.yr(a)}")
    )

  private def unitWord(n: com.histmersion.api.RowCount): com.histmersion.api.Plural.Phrase = if n == 1 then com.histmersion.api.Plural.Phrase("1 source") else s"$n sources"

  private def renderNode(b: Bucket, depth: com.histmersion.web.TreeTab.RenderNodeDepth, data: TimeHistogram): HtmlElement =
    val expanded = Var(false)
    val childrenFilled = Var(false)
    val kidElements = Var[List[HtmlElement]](Nil)
    val kids = b.children
    val openable = kids.nonEmpty || b.kind == "time" || b.placeId.isDefined

    val overStr = if b.overlap > 0 then
      val noun = if kids.exists(_.kind == "time") then "period" else "subregion"
      s" · ${b.overlap} in more than one $noun"
    else ""

    val perStr = if b.kind == "time" then "" else Fmt.periodStr(b.start, b.end)

    val contestedStr = if b.contested && !b.label.toLowerCase.contains("contested") then " (contested)"
    else ""

    val alsoStr = if b.alsoUnder.nonEmpty then s" (also under ${b.alsoUnder.mkString(", ")})"
    else ""

    div(
      cls := "tnode",
      styleAttr := s"--tdepth: $depth",
      div(
        cls := s"trow t-${b.kind}" + (if b.key == "unlocated" then " t-unlocated" else ""),
        tabIndex := 0,
        span(
          cls := "twist",
          child.text <-- expanded.signal.map(e => if !openable then "·" else if e then "▾" else "▸")
        ),
        span(
          cls := "tlabel",
          b.label + contestedStr,
          onClick --> { _ => act(b) }
        ),
        span(cls := "tcount", unitWord(b.count)),
        span(
          cls := "tmeta",
          overStr +
            (if perStr.nonEmpty then s" · $perStr" else "") +
            alsoStr
        ),
        onClick --> { ev =>
          val target = ev.target.asInstanceOf[dom.Element]
          if target.closest(".tlabel") != null then ()
          else toggle(b, depth, data, expanded, childrenFilled, kidElements, openable)
        },
        onKeyDown --> { ev =>
          if ev.key == "Enter" || ev.key == " " then
            ev.preventDefault()
            toggle(b, depth, data, expanded, childrenFilled, kidElements, openable)
          else if ev.key == "ArrowRight" && !expanded.now() then
            toggle(b, depth, data, expanded, childrenFilled, kidElements, openable)
          else if ev.key == "ArrowLeft" && expanded.now() then
            toggle(b, depth, data, expanded, childrenFilled, kidElements, openable)
        }
      ),
      div(
        cls := "tkids",
        cls("is-hidden") <-- expanded.signal.map(!_),
        children <-- kidElements.signal
      )
    )

  private def toggle(
      b: Bucket,
      depth: com.histmersion.web.TreeTab.ToggleDepth,
      data: TimeHistogram,
      expanded: Var[com.histmersion.web.TreeTab.Expanded],
      filled: Var[com.histmersion.web.TreeTab.Filled],
      kidElements: Var[List[HtmlElement]],
      openable: com.histmersion.web.TreeTab.Openable
  ): Unit =
    if !openable then ()
    else
      val opening = !expanded.now()
      expanded.set(opening)
      if !opening || filled.now() then ()
      else
        filled.set(com.histmersion.web.TreeTab.Filled(true))
        if b.children.nonEmpty || b.samples.nonEmpty then fillWith(b.children, b.samples, depth + 1, data, kidElements)
        else
          kidElements.set(
            List(
              div(cls := "tempty", styleAttr := s"--tdepth: ${depth + 1}", "loading…")
            )
          )
          subtree(b, data).foreach { deeper =>
            val got = deeper.tree
            val (finalKids, finalSamples) =
              if got.length == 1 && got.head.key == b.key then (got.head.children, got.head.samples)
              else (got, Nil)
            fillWith(finalKids, if b.samples.nonEmpty then b.samples else finalSamples, depth + 1, data, kidElements)
          }

  private def fillWith(
      kids: List[Bucket],
      samples: List[Bucket.Sample],
      depth: com.histmersion.web.TreeTab.FillWithDepth,
      data: TimeHistogram,
      kidElements: Var[List[HtmlElement]]
  ): Unit =
    val kidNodes = kids.map(c => renderNode(c, depth, data))
    val sampleNodes = samples.map(s => renderSample(s, depth))
    val empty = if kids.isEmpty then List(div(cls := "tempty", styleAttr := s"--tdepth: $depth", "no finer levels"))
    else Nil
    kidElements.set(kidNodes ++ sampleNodes ++ empty)

  private def renderSample(s: Bucket.Sample, depth: com.histmersion.web.TreeTab.Depth): HtmlElement =
    div(
      cls := "tsample",
      styleAttr := s"--tdepth: $depth",
      tabIndex := 0,
      span(cls := "ck", SourceLink(s.citekey)),
      " ",
      span(cls := "title", s.title),
      onClick --> { _ => ArticleModal.open(s.citekey, Map("q" -> UiState.q.now().getOrElse(""))) },
      onKeyDown --> { ev =>
        if ev.key == "Enter" || ev.key == " " then
          ev.preventDefault()
          ArticleModal.open(s.citekey, Map("q" -> UiState.q.now().getOrElse("")))
      }
    )

  private def subtree(b: Bucket, data: TimeHistogram): Future[TimeHistogram] =
    val cfg = config.getOrElse(throw new Exception("no config"))
    val f = UiState.toFilter()
    if b.kind == "time" then
      val narrowed = f.modify(_.period).setTo(for a <- b.start; b2 <- b.end yield Period(a, b2))
      ApiClient.aggregate(
        Api.AggregateQuery.tree(com.histmersion.api.Api.AggregateQuery.By("time"), narrowed, depth = Some(cfg.treeDepth), samples = Some(cfg.treeSamples))
      )
    else
      val narrowed = f.modify(_.places).setTo(b.placeId.toList)
      ApiClient.aggregate(
        Api.AggregateQuery
          .tree(com.histmersion.api.Api.AggregateQuery.By("region"), narrowed, depth = Some(cfg.treeDepth), asOf = data.asOf, samples = Some(cfg.treeSamples))
      )

  private def act(b: Bucket): Unit =
    if b.kind == "time" then
      for a <- b.start; b2 <- b.end do UiState.set(UiState.from := Some(a), UiState.to := Some(b2))()
    else
      b.placeId.foreach { pid =>
        PlacesInput.preview(pid, lastData.flatMap(_.asOf))
      }
