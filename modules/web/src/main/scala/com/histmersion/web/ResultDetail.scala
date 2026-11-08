package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.catalogue.Place
import com.histmersion.api.UiLabels
import com.histmersion.api.Api
import com.histmersion.web.marks.ItemRef
import com.histmersion.api.library.Title
import com.histmersion.web.marks.MyItems
import com.histmersion.web.ui.ContextMenu
import com.histmersion.web.marks.ItemMenu
import com.histmersion.web.marks.ItemActions
import com.histmersion.web.marks.NoteList

/** An item chosen in the results or on the map, shown in place of the results: "← Results" back to them, its card, "Pin
  * as window" (it floats over whatever is shown next) and "Open in a new tab"; for a place, what its record holds
  * beyond the card (where its dates come from, its heritage designations, the registers that list it).
  */
object ResultDetail:

  /** Pure: an item's own page. */

  def pageOf(t: Preview.Target): UiLabels.Text = t match
    case Preview.Target.Source(ck)    => UiLabels.Text(Route.Source(ck).path)
    case Preview.Target.Article(slug) => UiLabels.Text(Route.Article(slug).path)
    case Preview.Target.Place(id, _)  => UiLabels.Text(Route.Place(id).path)
    case Preview.Target.Event(id)     => UiLabels.Text(Route.Event(id).path)

  /** Pure: what the item is, for the actions' names. */
  def kindOf(t: Preview.Target): UiLabels.Text = t match
    case _: Preview.Target.Source  => UiLabels.Text("source")
    case _: Preview.Target.Article => UiLabels.Text("article")
    case _: Preview.Target.Place   => UiLabels.Text("place")
    case _: Preview.Target.Event   => UiLabels.Text("event")

  private def pin(t: Preview.Target, from: dom.Element): Unit = t match
    case Preview.Target.Source(ck)    => WindowManager.open(WindowKey.Source(WindowKey.Source.Citekey(ck.value)))
    case Preview.Target.Article(slug) => WindowManager.open(WindowKey.Article(WindowKey.Article.Slug(slug.value)))
    case _                            =>
      val r = from.getBoundingClientRect()
      Preview.pin(t, () => Some(Preview.Anchor.At(Preview.Anchor.At.X(r.left - 360), Preview.Anchor.At.Y(r.top))))

  def element(t: Preview.Target): HtmlElement =
    val card = Var(Option.empty[Either[String, Preview.Card]])
    val record = Var(Option.empty[Place.Record])
    div(
      cls := "result-detail",
      dataAttr("kind") := kindOf(t).value,
      onMountCallback { _ =>
        Preview
          .fetch(t)
          .onComplete(r => card.set(Some(r.toEither.left.map(e => Option(e.getMessage).getOrElse(e.toString)))))
        t match
          case Preview.Target.Place(id, asOf) =>
            ApiClient
              .place(Place.Id(id.value), asOf.map(a => Api.AggregateQuery.AsOf(a.value)))
              .foreach(d => record.set(Some(d.record)))
          case _ => ()
      },
      div(
        cls := "detail-bar",
        button(
          typ := "button",
          cls := "btn btn--ghost btn--sm detail-back",
          idAttr := "detail-back",
          "← Results",
          onClick --> (_ => ResultsPanel.back())
        ),
        div(
          cls := "btn-group detail-actions",
          button(
            typ := "button",
            cls := "btn btn--sm detail-pin",
            "Pin as window",
            title := s"Keep this ${kindOf(t)} open in a window over the map",
            onClick --> { ev => pin(t, ev.currentTarget.asInstanceOf[dom.Element]) }
          ),
          a(
            cls := "btn btn--sm detail-tab",
            href := pageOf(t).value,
            target := "_blank",
            rel := "noopener",
            Icon(Icon.NewTab),
            "New tab"
          )
        )
      ),
      // the item's own marks once its card says what it is called
      child.maybe <-- card.signal.map(_.flatMap(_.toOption).map { c =>
        val ref = ItemRef.of(t, Title(c.title.value))
        MyItems.opened(ref)
        div(
          cls := "detail-marks",
          ContextMenu.on(
            ContextMenu.OnTitle(s"Actions: ${c.title}"),
            () => ItemMenu.entries(ref, c.byline.value)
          ),
          ItemActions(ref, c.byline.value)
        )
      }),
      child <-- card.signal.map {
        case None           => p(cls := "muted", "Loading…")
        case Some(Left(m))  => p(cls := "form-error", role := "alert", s"Could not read it: $m")
        case Some(Right(c)) => div(cls := "cite-tooltip detail-card", Preview.cardView(c))
      },
      child.maybe <-- card.signal.map(_.flatMap(_.toOption).map(c => NoteList.on(ItemRef.of(t, Title(c.title.value))))),
      child.maybe <-- record.signal.map(_.filterNot(_.isEmpty).map(recordView))
    )

  /** What a place's record holds beyond its card. */
  def recordView(r: Place.Record): HtmlElement =
    div(
      cls := "place-record",
      r.dateSource.map(s =>
        p(cls := "place-record-line", span(cls := "muted", "Dates from: "), Place.Record.dateSourceText(s).value)
      ),
      Option.when(r.periods.nonEmpty)(
        p(cls := "place-record-line", span(cls := "muted", "Time periods: "), r.periods.mkString(", "))
      ),
      Option.when(r.designations.nonEmpty)(
        div(
          h3(cls := "place-record-title", "Heritage designations"),
          ul(r.designations.map { d =>
            val when = List(d.start.map(x => s"from $x"), d.end.map(x => s"until $x")).flatten.mkString(" ")
            li(
              a(
                href := s"https://www.wikidata.org/wiki/${d.id}",
                target := "_blank",
                rel := "noopener",
                d.label.map(_.value).getOrElse(d.id.value)
              ),
              Option.when(when.nonEmpty)(span(cls := "muted", s" ($when)"))
            )
          })
        )
      ),
      Option.when(r.registers.nonEmpty)(
        div(
          h3(cls := "place-record-title", "Listed in"),
          ul(r.registers.map(g => li(s"${g.name}: ", code(g.id.value))))
        )
      )
    )
