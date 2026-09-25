package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object TreeTab:

  private var config: Option[UiConfig] = None
  private var axis: Option[String] = None
  private var lastData: Option[TimeHistogram] = None
  private var reqId: Int = 0
  private var timer: Int = 0
  private var lastParams: Option[String] = None

  private val treeContent = Var[List[HtmlElement]](Nil)
  private val countText = Var("")

  def pane: HtmlElement =
    div(
      cls := "tree-pane",
      idAttr := "tree-pane",
      div(idAttr := "tree-count", cls := "tree-count", child.text <-- countText.signal),
      div(idAttr := "tree-list", cls := "tree-list", children <-- treeContent.signal)
    )

  def show(tab: String): Unit =
    val mine = tab match
      case "bytime"    => Some("time")
      case "byregion"  => Some("region")
      case _           => None
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
        timer = dom.window.setTimeout(() => load(), ms)

  private def paramsKey(): String =
    val f = UiState.toFilter()
    s"${axis.getOrElse("")}:${f.hashCode}"

  def load(): Unit =
    config match
      case None => ()
      case Some(cfg) =>
        if axis.isEmpty then ()
        else
          val mine = { reqId += 1; reqId }
          countText.set("loading…")
          doLoad(cfg, mine)

  private def doLoad(cfg: UiConfig, mine: Int): Unit =
    val by = axis.getOrElse("time")
    val f = UiState.toFilter()
    val asOf = if by == "region" then
      for a <- UiState.from.now(); b <- UiState.to.now() yield (a + b) / 2
    else None

    ApiClient.aggregate(Api.AggregateQuery.tree(
      by = by,
      filter = f,
      depth = Some(cfg.treeDepth),
      asOf = asOf,
      samples = Some(cfg.treeSamples)
    )).foreach { data =>
      if mine != reqId then ()
      else
        lastData = Some(data)
        val roots = data.tree
        val from = UiState.from.now().getOrElse(data.from)
        val to = UiState.to.now().getOrElse(data.to)
        val span = s"${Fmt.yr(from)} → ${Fmt.yr(to)}"
        val asOfStr = data.asOf.map(a => s" · as of ${Fmt.yr(a)}").getOrElse("")
        countText.set(s"${unitWord(data.total)} · by ${data.by} · $span$asOfStr")

        if roots.isEmpty then
          treeContent.set(List(
            div(cls := "empty", p("nothing to group — widen the span or remove a chip"))
          ))
        else
          treeContent.set(roots.map(b => renderNode(b, 0, data)))
    }

  private def unitWord(n: Int): String = if n == 1 then "1 source" else s"$n sources"

  private def renderNode(b: Bucket, depth: Int, data: TimeHistogram): HtmlElement =
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

    val contestedStr = if b.contested && !b.label.toLowerCase.contains("contested") then
      " (contested)"
    else ""

    val alsoStr = if b.alsoUnder.nonEmpty then
      s" (also under ${b.alsoUnder.mkString(", ")})"
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
        span(cls := "tmeta",
          overStr +
          (if perStr.nonEmpty then s" · ${perStr}" else "") +
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
        display <-- expanded.signal.map(if _ then "block" else "none"),
        children <-- kidElements.signal
      )
    )

  private def toggle(b: Bucket, depth: Int, data: TimeHistogram,
      expanded: Var[Boolean], filled: Var[Boolean], kidElements: Var[List[HtmlElement]],
      openable: Boolean): Unit =
    if !openable then ()
    else
      val opening = !expanded.now()
      expanded.set(opening)
      if !opening || filled.now() then ()
      else
        filled.set(true)
        if b.children.nonEmpty || b.samples.nonEmpty then
          fillWith(b.children, b.samples, depth + 1, data, kidElements)
        else
          kidElements.set(List(
            div(cls := "tempty", styleAttr := s"--tdepth: ${depth + 1}", "loading…")
          ))
          subtree(b, data).foreach { deeper =>
            val got = deeper.tree
            val (finalKids, finalSamples) =
              if got.length == 1 && got.head.key == b.key then
                (got.head.children, got.head.samples)
              else (got, Nil)
            fillWith(finalKids, if b.samples.nonEmpty then b.samples else finalSamples, depth + 1, data, kidElements)
          }

  private def fillWith(kids: List[Bucket], samples: List[BucketSample], depth: Int,
      data: TimeHistogram, kidElements: Var[List[HtmlElement]]): Unit =
    val kidNodes = kids.map(c => renderNode(c, depth, data))
    val sampleNodes = samples.map(s => renderSample(s, depth))
    val empty = if kids.isEmpty then
      List(div(cls := "tempty", styleAttr := s"--tdepth: $depth", "no finer levels"))
    else Nil
    kidElements.set(kidNodes ++ sampleNodes ++ empty)

  private def renderSample(s: BucketSample, depth: Int): HtmlElement =
    div(
      cls := "tsample",
      styleAttr := s"--tdepth: $depth",
      tabIndex := 0,
      span(cls := "ck", s.citekey),
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
      val narrowed = f.copy(period = for a <- b.start; b2 <- b.end yield Period(a, b2))
      ApiClient.aggregate(Api.AggregateQuery.tree("time", narrowed, depth = Some(cfg.treeDepth), samples = Some(cfg.treeSamples)))
    else
      val narrowed = f.copy(places = b.placeId.toList)
      ApiClient.aggregate(Api.AggregateQuery.tree("region", narrowed, depth = Some(cfg.treeDepth), asOf = data.asOf, samples = Some(cfg.treeSamples)))

  private def act(b: Bucket): Unit =
    if b.kind == "time" then
      for a <- b.start; b2 <- b.end do
        UiState.set(("from", a), ("to", b2))()
    else
      b.placeId.foreach { pid =>
        PlacesInput.preview(pid, lastData.flatMap(_.asOf))
      }
