package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.window

/** A link to a page of the web UI: a real `<a href>`, so ⌘/Ctrl/Shift/middle click opens it in a new tab; a plain click
  * moves there without reloading.
  */
object RouteLink:
  def apply(r: Route, mods: Modifier[HtmlElement]*): HtmlElement =
    a(href := r.path, mods, onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); Router.go(r) } })
