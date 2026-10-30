package com.histmersion.web

import com.histmersion.api.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** An item chosen in the results or on the map, shown in place of the results: "← Results" back to them, its
  * card, "Pin as window" (it floats over whatever is shown next) and "Open in a new tab"; for a place, what
  * its record holds beyond the card (where its dates come from, its heritage designations, the registers
  * that list it). */
object ResultDetail:

  /** Pure: an item's own page. */
  def pageOf(t: Preview.Target): String = t match
    case Preview.Target.Source(ck)    => Route.Source(ck).path
    case Preview.Target.Article(slug) => Route.Article(slug).path
    case Preview.Target.Place(id, _)  => Route.Place(id).path
    case Preview.Target.Event(id)     => Route.Event(id).path

  /** Pure: what the item is, for the actions' names. */
  def kindOf(t: Preview.Target): String = t match
    case _: Preview.Target.Source  => "source"
    case _: Preview.Target.Article => "article"
    case _: Preview.Target.Place   => "place"
    case _: Preview.Target.Event   => "event"

  private def pin(t: Preview.Target, from: dom.Element): Unit = t match
    case Preview.Target.Source(ck)    => WindowManager.open(WindowKey.Source(ck))
    case Preview.Target.Article(slug) => WindowManager.open(WindowKey.Article(slug))
    case _ =>
      val r = from.getBoundingClientRect()
      Preview.pin(t, () => Some(Preview.Anchor.At(r.left - 360, r.top)))

  def element(t: Preview.Target): HtmlElement =
    val card = Var(Option.empty[Either[String, Preview.Card]])
    val record = Var(Option.empty[PlaceRecord])
    div(
      cls := "result-detail",
      dataAttr("kind") := kindOf(t),
      onMountCallback { _ =>
        Preview.fetch(t).onComplete(r => card.set(Some(r.toEither.left.map(e => Option(e.getMessage).getOrElse(e.toString)))))
        t match
          case Preview.Target.Place(id, asOf) => ApiClient.place(id, asOf).foreach(d => record.set(Some(d.record)))
          case _                               => ()
      },
      div(cls := "detail-bar",
        button(typ := "button", cls := "btn btn--ghost btn--sm detail-back", idAttr := "detail-back", "← Results",
          onClick --> (_ => ResultsPanel.back())),
        div(cls := "btn-group detail-actions",
          button(typ := "button", cls := "btn btn--sm detail-pin", "Pin as window",
            title := s"Keep this ${kindOf(t)} open in a window over the map",
            onClick --> { ev => pin(t, ev.currentTarget.asInstanceOf[dom.Element]) }),
          a(cls := "btn btn--sm detail-tab", href := pageOf(t), target := "_blank", rel := "noopener", Icon(Icon.NewTab), "New tab"))),
      child <-- card.signal.map {
        case None           => p(cls := "muted", "Loading…")
        case Some(Left(m))  => p(cls := "form-error", role := "alert", s"Could not read it: $m")
        case Some(Right(c)) => div(cls := "cite-tooltip detail-card", Preview.cardView(c))
      },
      child.maybe <-- record.signal.map(_.filterNot(_.isEmpty).map(recordView))
    )

  /** What a place's record holds beyond its card. */
  def recordView(r: PlaceRecord): HtmlElement =
    div(
      cls := "place-record",
      r.dateSource.map(s => p(cls := "place-record-line", span(cls := "muted", "Dates from: "), PlaceRecord.dateSourceText(s))),
      Option.when(r.periods.nonEmpty)(p(cls := "place-record-line", span(cls := "muted", "Time periods: "), r.periods.mkString(", "))),
      Option.when(r.designations.nonEmpty)(div(
        h3(cls := "place-record-title", "Heritage designations"),
        ul(r.designations.map { d =>
          val when = List(d.start.map(x => s"from $x"), d.end.map(x => s"until $x")).flatten.mkString(" ")
          li(a(href := s"https://www.wikidata.org/wiki/${d.id}", target := "_blank", rel := "noopener", d.label.getOrElse(d.id)),
            Option.when(when.nonEmpty)(span(cls := "muted", s" ($when)")))
        }))),
      Option.when(r.registers.nonEmpty)(div(
        h3(cls := "place-record-title", "Listed in"),
        ul(r.registers.map(g => li(s"${g.name}: ", code(g.id))))))
    )
