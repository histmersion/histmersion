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

  type Axis = Axis.T
  object Axis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Timer = Timer.T
  object Timer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastParams = LastParams.T
  object LastParams extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowTab = ShowTab.T
  object ShowTab extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParamsKeyResult = ParamsKeyResult.T
  object ParamsKeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoLoadMine = DoLoadMine.T
  object DoLoadMine extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLineResult = CountLineResult.T
  object CountLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLineRoots = CountLineRoots.T
  object CountLineRoots extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLineFrom = CountLineFrom.T
  object CountLineFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLineTo = CountLineTo.T
  object CountLineTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnitWordResult = UnitWordResult.T
  object UnitWordResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnitWordN = UnitWordN.T
  object UnitWordN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderNodeDepth = RenderNodeDepth.T
  object RenderNodeDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleDepth = ToggleDepth.T
  object ToggleDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleExpanded = ToggleExpanded.T
  object ToggleExpanded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleFilled = ToggleFilled.T
  object ToggleFilled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleOpenable = ToggleOpenable.T
  object ToggleOpenable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FillWithDepth = FillWithDepth.T
  object FillWithDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderSampleDepth = RenderSampleDepth.T
  object RenderSampleDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private var config: Option[UiConfig] = None
  private var axis: Option[com.histmersion.web.TreeTab.Axis] = None
  private var lastData: Option[TimeHistogram] = None

  /** Drops the answers to a superseded load ([[Latest]]). */
  private val latest = Latest()
  private var timer: com.histmersion.web.TreeTab.Timer = 0
  private var lastParams: Option[com.histmersion.web.TreeTab.LastParams] = None

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

  def show(tab: com.histmersion.web.TreeTab.ShowTab): Unit =
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

  private def paramsKey(): com.histmersion.web.TreeTab.ParamsKeyResult =
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

  private def doLoad(cfg: UiConfig, mine: com.histmersion.web.TreeTab.DoLoadMine): Unit =
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
    else treeContent.set(roots.map(b => renderNode(b, 0, data)))

  /** The count line, in the sidebar's one form ([[ResultsPaging]]): every source is in the tree, so
    * `285 sources · 12 periods · 1475 BCE → 1465 BCE` (`· as of <year>` for regions).
    */
  def countLine(data: TimeHistogram, roots: com.histmersion.web.TreeTab.CountLineRoots, from: com.histmersion.web.TreeTab.CountLineFrom, to: com.histmersion.web.TreeTab.CountLineTo): com.histmersion.web.TreeTab.CountLineResult =
    val groups =
      if data.by == "region" then Plural.count(roots.toLong, "region") else Plural.count(roots.toLong, "period")
    ResultsPaging.countLine(
      data.total,
      data.total,
      "source",
      extras = List(groups, ResultsPaging.span(Some(from), Some(to))) ++ data.asOf.map(a => s"as of ${Fmt.yr(a)}")
    )

  private def unitWord(n: com.histmersion.web.TreeTab.UnitWordN): com.histmersion.web.TreeTab.UnitWordResult = if n == 1 then "1 source" else s"$n sources"

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
      expanded: Var[com.histmersion.web.TreeTab.ToggleExpanded],
      filled: Var[com.histmersion.web.TreeTab.ToggleFilled],
      kidElements: Var[List[HtmlElement]],
      openable: com.histmersion.web.TreeTab.ToggleOpenable
  ): Unit =
    if !openable then ()
    else
      val opening = !expanded.now()
      expanded.set(opening)
      if !opening || filled.now() then ()
      else
        filled.set(true)
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

  private def renderSample(s: Bucket.Sample, depth: com.histmersion.web.TreeTab.RenderSampleDepth): HtmlElement =
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
        Api.AggregateQuery.tree("time", narrowed, depth = Some(cfg.treeDepth), samples = Some(cfg.treeSamples))
      )
    else
      val narrowed = f.modify(_.places).setTo(b.placeId.toList)
      ApiClient.aggregate(
        Api.AggregateQuery
          .tree("region", narrowed, depth = Some(cfg.treeDepth), asOf = data.asOf, samples = Some(cfg.treeSamples))
      )

  private def act(b: Bucket): Unit =
    if b.kind == "time" then
      for a <- b.start; b2 <- b.end do UiState.set(UiState.from := Some(a), UiState.to := Some(b2))()
    else
      b.placeId.foreach { pid =>
        PlacesInput.preview(pid, lastData.flatMap(_.asOf))
      }
