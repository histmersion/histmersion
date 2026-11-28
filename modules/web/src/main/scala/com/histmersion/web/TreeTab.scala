package com.histmersion
package web

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.histmersion.api.{Api, Plural, Years}
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.catalogue.Period
import com.histmersion.api.instance.UiConfig
import com.histmersion.api.library.{Bucket, Filter, TimeHistogram}
import com.histmersion.api.UiLabels
import com.histmersion.api.RowCount
import com.histmersion.api.Paging
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{tolerated, LintRule}

object TreeTab:
  private var config: Option[UiConfig] = None
  private var axis: Option[Api.AggregateQuery.By] = None
  private var lastData: Option[TimeHistogram] = None

  /** Drops the answers to a superseded load ([[Latest]]). */
  private val latest = Latest()
  private var timer: Int = 0
  private var lastParams: Option[String] = None

  private val treeContent = Var[List[HtmlElement]](Nil)
  private val countText = Var(UiLabels.Text(""))
  private val loading = Var(false)
  private val failed = Var(false)

  def pane: HtmlElement =
    div(
      cls := "tree-pane",
      idAttr := "tree-pane",
      ResultsPaging.countBar(
        countText.signal,
        loading.signal.map((if _ then ResultsPaging.Loading.Loading else ResultsPaging.Loading.Idle)),
        failed.signal.map((if _ then ResultsPaging.Failed.Failed else ResultsPaging.Failed.Succeeded)),
        id = Some("tree-count")
      ),
      div(idAttr := "tree-list", cls := "tree-list", children <-- treeContent.signal)
    )

  def show(tab: String): Unit =
    val mine = tab match
      case "bytime"   => Some(Api.AggregateQuery.By("time"))
      case "byregion" => Some(Api.AggregateQuery.By("region"))
      case _          => None
    axis = mine
    mine match
      case Some(_) => load()
      case None    => ()

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def init(cfg: UiConfig): Unit =
    config = Some(cfg)
    UiState.signal.foreach { _ =>
      schedule()
    }(using unsafeWindowOwner)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def schedule(): Unit =
    if axis.isEmpty then ()
    else
      val key = paramsKey()
      if lastParams.contains(key) then ()
      else
        lastParams = Some(key)
        dom.window.clearTimeout(timer)
        val ms = config.flatMap(c => Option(c.debounce.reloadMs)).map(_.value).getOrElse(0)
        timer = dom.window.setTimeout(() => load(), UiState.reloadDelay(UiState.ReloadDelayNormalMs(ms)).value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def paramsKey(): String =
    val f = UiState.toFilter()
    s"${axis.fold("")(_.value)}:${f.hashCode}"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def load(): Unit =
    config match
      case None      => ()
      case Some(cfg) =>
        if axis.isEmpty then ()
        else
          val mine = latest.start()
          if countText.now().value.isEmpty then countText.set(UiLabels.Text("loading…")) else loading.set(true)
          failed.set(false)
          doLoad(cfg, mine)

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def doLoad(cfg: UiConfig, mine: Int): Unit =
    val by = axis.getOrElse(Api.AggregateQuery.By("time"))
    val f = UiState.toFilter()
    val asOf =
      if by.value == "region" then
        for a <- UiState.from.now(); b <- UiState.to.now() yield Api.AggregateQuery.AsOf((a + b) / 2)
      else None

    ApiClient
      .aggregate(
        Api.AggregateQuery.tree(
          by = by,
          filter = f,
          depth = Some(Api.AggregateQuery.Depth(cfg.treeDepth.value)),
          asOf = asOf,
          samples = Some(Api.AggregateQuery.Samples(cfg.treeSamples.value))
        )
      )
      .onComplete { r =>
        if latest.isCurrent(mine) then
          loading.set(false)
          latest.answer(
            r,
            showTree,
            m => { failed.set(true); countText.set(UiLabels.Text(s"Could not group the sources: $m")) }
          )
      }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def showTree(data: TimeHistogram): Unit =
    lastData = Some(data)
    val roots = data.tree
    countText.set(
      countLine(
        data,
        RowCount(roots.size),
        Years.Astronomical(UiState.from.now().getOrElse(data.from.value)),
        Years.Astronomical(UiState.to.now().getOrElse(data.to.value))
      )
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def countLine(data: TimeHistogram, roots: RowCount, from: Years.Astronomical, to: Years.Astronomical): UiLabels.Text =
    val groups =
      if data.by.value == "region" then Plural.count(Plural.CountN(roots.value.toLong), Plural.CountSingular("region"))
      else Plural.count(Plural.CountN(roots.value.toLong), Plural.CountSingular("period"))
    ResultsPaging.countLine(
      Paging.Shown(data.total.value),
      Paging.Page.Total(data.total.value),
      Plural.Noun.Singular("source"),
      extras = List(
        UiLabels.Text(groups.value),
        ResultsPaging.span(Some(from.numberAsYear), Some(to.numberAsYear))
      ) ++ data.asOf.map(a => UiLabels.Text(shown"as of ${Fmt.yr(a)}"))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def unitWord(n: Bucket.Count): Plural.Phrase =
    if n.value == 1 then Plural.Phrase("1 source") else Plural.Phrase(s"${n.value} sources")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def renderNode(b: Bucket, depth: Int, data: TimeHistogram): HtmlElement =
    val expanded = Var(false)
    val childrenFilled = Var(false)
    val kidElements = Var[List[HtmlElement]](Nil)
    val kids = b.children
    val openable = kids.nonEmpty || b.kind.value == "time" || b.placeId.isDefined

    val overStr = if b.overlap.value > 0 then
      val noun = if kids.exists(_.kind.value == "time") then "period" else "subregion"
      s" · ${b.overlap} in more than one $noun"
    else ""

    val perStr = if b.kind.value == "time" then "" else Fmt.periodStr(b.start, b.end).value

    val contestedStr = if b.contested.contested && !b.label.value.toLowerCase.contains("contested") then " (contested)"
    else ""

    val alsoStr = if b.alsoUnder.nonEmpty then s" (also under ${b.alsoUnder.mkString(", ")})"
    else ""

    div(
      cls := "tnode",
      styleAttr := s"--tdepth: $depth",
      div(
        cls := s"trow t-${b.kind}" + (if b.key.value == "unlocated" then " t-unlocated" else ""),
        tabIndex := 0,
        span(
          cls := "twist",
          child.text <-- expanded.signal.map(e => if !openable then "·" else if e then "▾" else "▸")
        ),
        span(
          cls := "tlabel",
          b.label.value + contestedStr,
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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def toggle(
      b: Bucket,
      depth: Int,
      data: TimeHistogram,
      expanded: Var[Boolean],
      filled: Var[Boolean],
      kidElements: Var[List[HtmlElement]],
      openable: Boolean
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
      depth: Int,
      data: TimeHistogram,
      kidElements: Var[List[HtmlElement]]
  ): Unit =
    val kidNodes = kids.map(c => renderNode(c, depth, data))
    val sampleNodes = samples.map(s => renderSample(s, depth))
    val empty = if kids.isEmpty then List(div(cls := "tempty", styleAttr := s"--tdepth: $depth", "no finer levels"))
    else Nil
    kidElements.set(kidNodes ++ sampleNodes ++ empty)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def renderSample(s: Bucket.Sample, depth: Int): HtmlElement =
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

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def subtree(b: Bucket, data: TimeHistogram): Future[TimeHistogram] =
    val cfg = config.getOrElse(throw new Exception("no config"))
    val f = UiState.toFilter()
    if b.kind.value == "time" then
      val narrowed = f.modify(_.period).setTo(for a <- b.start; b2 <- b.end yield Period(a, b2))
      ApiClient.aggregate(
        Api.AggregateQuery.tree(
          Api.AggregateQuery.By("time"),
          narrowed,
          depth = Some(Api.AggregateQuery.Depth(cfg.treeDepth.value)),
          samples = Some(Api.AggregateQuery.Samples(cfg.treeSamples.value))
        )
      )
    else
      val narrowed = f.modify(_.places).setTo(b.placeId.toList.map(p => Filter.Places(p.value)))
      ApiClient.aggregate(
        Api.AggregateQuery
          .tree(
            Api.AggregateQuery.By("region"),
            narrowed,
            depth = Some(Api.AggregateQuery.Depth(cfg.treeDepth.value)),
            asOf = data.asOf.map(a => Api.AggregateQuery.AsOf(a.value)),
            samples = Some(Api.AggregateQuery.Samples(cfg.treeSamples.value))
          )
      )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def act(b: Bucket): Unit =
    if b.kind.value == "time" then
      for a <- b.start; b2 <- b.end do UiState.set(UiState.from := Some(a.value), UiState.to := Some(b2.value))()
    else
      b.placeId.foreach { pid =>
        PlacesInput.preview(
          PlacesInput.PlaceId(pid.value),
          lastData.flatMap(_.asOf).map(y => y.numberAsAstronomical)
        )
      }
