package com.histmersion
package web.marks

import com.histmersion.api.personal.{Bookmark, MapZoom, Target, ViewState}
import com.histmersion.api.ItemKind
import com.histmersion.api.catalogue.{Latitude, Longitude, Year}
import com.histmersion.web.*
import com.histmersion.web.map.TheMap
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Title
import com.histmersion.web.ui.Flash

/** "Save this view": Explore as it is now — the filters, the span, the layers and where the map is — kept as a bookmark
  * to come back to, under a name. Listed here (Explore's filter panel) and in My library › Bookmarks.
  */
object SavedViews:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]

  /** The view on screen, as a bookmark's target. */
  def current(): Target =
    val s = Option.when(TheMap.isUp)(TheMap.surface)
    Target(
      ItemKind.View,
      lat = s.flatMap(x => Some(Latitude(x.center.lat.value))),
      lon = s.flatMap(x => Some(Longitude(x.center.lon.value))),
      zoom = s.flatMap(x => Some(MapZoom(math.round(x.zoom.value * 100) / 100.0))),
      from = UiState.from.now().map(v => Year(v.toInt)),
      to = UiState.to.now().map(v => Year(v.toInt)),
      view = Some(ViewState(UiState.hashString.value))
    )

  /** A name to offer for the view: its span, and the words or the first filter. Pure. */
  def suggestedName(words: Option[String], from: Option[Year], to: Option[Year]): Name =
    val span = Fmt.periodStr(from, to)
    List(words.map(_.trim).filter(_.nonEmpty).map(w => s"“$w”"), Option(span.value).filter(_.nonEmpty)).flatten
      .mkString(", ") match
      case "" => Name("My view")
      case s  => Name(s)

  val saved: Signal[List[Bookmark]] = MyItems.bookmarks.signal.map(_.filter(_.target.kind == ItemKind.View))

  private val onExplore: Signal[Boolean] = Router.route.map(_ == Route.Explore)

  /** Save the view on screen as `name`. */
  def save(name: Name): Unit =
    if Auth.writableNow then
      MyItems
        .add(
          current(),
          Some(name.value.trim).filter(_.nonEmpty).map(Bookmark.Label(_)),
          Title(name.value.trim)
        )
        .foreach(_ => Flash.say(Flash.Text(s"Saved the view “${name.value.trim}”.")))
    else WindowManager.open(WindowKey.ApiKey)

  /** The filter panel's "Saved views" part (Explore only). */
  def panelSection: HtmlElement =
    val naming = Var(Option.empty[Name])
    div(
      cls := "f saved-views",
      cls("is-hidden") <-- onExplore.map(!_),
      span(cls := "f-label saved-views-title", "Saved views"),
      p(cls := "muted", "Keep this set of filters and map position to come back to."),
      child <-- naming.signal.map {
        case None =>
          button(
            typ := "button",
            cls := "btn save-view-btn",
            idAttr := "save-view",
            "Save this view…",
            onClick --> (_ => naming.set(Some(suggestedName(UiState.q.now(), UiState.from.now().map(Year(_)), UiState.to.now().map(Year(_))))))
          )
        case Some(name0) =>
          val name = Var(name0.value: String)
          form(
            cls := "save-view-form",
            onSubmit.preventDefault --> { _ => save(Name(name.now())); naming.set(None) },
            label(forId := "save-view-name", cls := "sr-only", "Name of the view"),
            input(
              typ := "text",
              idAttr := "save-view-name",
              cls := "field",
              maxLength := 200,
              controlled(value <-- name.signal, onInput.mapToValue --> name.writer),
              onMountCallback { ctx => ctx.thisNode.ref.focus(); ctx.thisNode.ref.select() },
              onKeyDown --> (ev =>
                if ev.key == "Escape" then { ev.preventDefault(); ev.stopPropagation(); naming.set(None) }
              )
            ),
            div(
              cls := "toolbar",
              button(typ := "submit", cls := "btn btn--primary btn--sm", "Save"),
              button(typ := "button", cls := "btn btn--ghost btn--sm", "Cancel", onClick --> (_ => naming.set(None)))
            )
          )
      },
      ul(
        cls := "saved-view-list",
        children <-- saved.map(
          _.map(b =>
            li(
              cls := "saved-view",
              a(
                cls := "saved-view-open",
                href := ItemRef.back(b.target).value,
                b.name.value,
                title := s"Open the view “${b.name}”: ${Target.where(b.target)}",
                onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); OpenBack(b.target) } }
              ),
              button(
                typ := "button",
                cls := "btn btn--bare btn--icon btn--sm saved-view-remove",
                aria.label := s"Remove the view ${b.name}",
                title := "Remove this saved view",
                "×",
                onClick --> (_ => MyItems.remove(b): Unit)
              )
            )
          )
        )
      )
    )
