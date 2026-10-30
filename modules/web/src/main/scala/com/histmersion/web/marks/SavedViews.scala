package com.histmersion.web.marks

import com.histmersion.api.personal.{Bookmark, ItemKind, MapZoom, Target, ViewState}
import com.histmersion.values.{Latitude, Longitude, Year}
import com.histmersion.web.*
import com.histmersion.web.map.TheMap
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** "Save this view": Explore as it is now — the filters, the span, the layers and where the map is — kept as a bookmark
  * to come back to, under a name. Listed here (Explore's filter panel) and in My library › Bookmarks.
  */
object SavedViews:

  /** The view on screen, as a bookmark's target. */
  def current(): Target =
    val s = Option.when(TheMap.isUp)(TheMap.surface)
    Target(
      ItemKind.View,
      lat = s.flatMap(x => Latitude.option(x.center.lat)),
      lon = s.flatMap(x => Longitude.option(x.center.lon)),
      zoom = s.flatMap(x => MapZoom.option(math.round(x.zoom * 100) / 100.0)),
      from = UiState.from.now().map(v => Year(v.toInt)),
      to = UiState.to.now().map(v => Year(v.toInt)),
      view = Some(ViewState(UiState.hashString))
    )

  /** A name to offer for the view: its span, and the words or the first filter. Pure. */
  def suggestedName(words: Option[String], from: Option[Long], to: Option[Long]): String =
    val span = Fmt.periodStr(from, to)
    List(words.map(_.trim).filter(_.nonEmpty).map(w => s"“$w”"), Option(span).filter(_.nonEmpty)).flatten
      .mkString(", ") match
      case "" => "My view"
      case s  => s

  val saved: Signal[List[Bookmark]] = MyItems.bookmarks.signal.map(_.filter(_.target.kind == ItemKind.View))

  private val onExplore: Signal[Boolean] = Router.route.map(_ == Route.Explore)

  /** Save the view on screen as `name`. */
  def save(name: String): Unit =
    if Auth.writableNow then
      MyItems
        .add(current(), Some(name.trim).filter(_.nonEmpty), name.trim)
        .foreach(_ => com.histmersion.web.ui.Flash.say(s"Saved the view “${name.trim}”."))
    else WindowManager.open(WindowKey.ApiKey)

  /** The filter panel's "Saved views" part (Explore only). */
  def panelSection: HtmlElement =
    val naming = Var(Option.empty[String])
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
            onClick --> (_ => naming.set(Some(suggestedName(UiState.q.now(), UiState.from.now(), UiState.to.now()))))
          )
        case Some(name0) =>
          val name = Var(name0)
          form(
            cls := "save-view-form",
            onSubmit.preventDefault --> { _ => save(name.now()); naming.set(None) },
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
                href := ItemRef.back(b.target),
                b.name,
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
