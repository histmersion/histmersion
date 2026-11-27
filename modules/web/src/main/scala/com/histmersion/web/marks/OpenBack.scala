package com.histmersion
package web.marks

import com.histmersion.api.personal.Target
import com.histmersion.api.ItemKind
import com.histmersion.web.*
import com.histmersion.web.library.LibraryAddress
import com.histmersion.web.map.{LatLon, MapSurface, TheMap}
import org.scalajs.dom
import com.histmersion.lint.{tolerated, LintRule}

/** Opening a bookmark or a note back where it was left: a source or an article in Library's reading pane, a goal on its
  * page, a place or an event in Explore's detail, a picture full size, a place on the map at its point (and its years),
  * a saved view with its filters, its span and the map where it was, a period as the span.
  */
object OpenBack:

  /** The zoom a point on the map opens at when it was kept without one. */
  val PointZoom = 8.0

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(t: Target): Unit =
    val key = t.itemKey
    t.kind match
      case ItemKind.Source | ItemKind.Article =>
        val section = if t.kind == ItemKind.Article then LibrarySection.Articles else LibrarySection.Sources
        Router.go(Route.Library(section), search = LibraryAddress.search(key))
      case ItemKind.Goal  => key.foreach(k => Router.go(Route.Goal(Route.Goal.Slug(k.value))))
      case ItemKind.Place =>
        key.foreach { k =>
          Router.go(Route.Explore); ResultsPanel.show(Preview.Target.Place(Preview.Target.Place.Id(k.value), None))
        }
      case ItemKind.Event =>
        key.foreach { k =>
          Router.go(Route.Explore); ResultsPanel.show(Preview.Target.Event(Preview.Target.Event.Id(k.value)))
        }
      case ItemKind.Picture =>
        key.foreach { k =>
          val _ = dom.window.open(ItemRef.page(t).value, "_blank", "noopener")
        }
      case ItemKind.View =>
        Router.go(Route.Explore)
        t.view.foreach(v => UiState.loadHash(UiState.UrlHash(v.value)))
        span(t)
        UiState.set()(push = UiState.SetPush.Push)
        at(t)
      case ItemKind.Point =>
        Router.go(Route.Explore)
        span(t)
        UiState.set()(push = UiState.SetPush.Push)
        at(t)
      case ItemKind.Period =>
        Router.go(Route.Explore)
        span(t)
        UiState.set()(push = UiState.SetPush.Push)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def span(t: Target): Unit =
    for a <- t.from; b <- t.to do
      UiState.from.set(Some(a.value.toLong))
      UiState.to.set(Some(b.value.toLong))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def at(t: Target): Unit =
    if TheMap.isUp then
      for la <- t.lat; lo <- t.lon do
        TheMap.surface.setView(
          LatLon(LatLon.Lat(la.value), LatLon.Lon(lo.value)),
          MapSurface.Zoom(t.zoom.fold(math.max(TheMap.surface.zoom.value, PointZoom))(_.value))
        )
