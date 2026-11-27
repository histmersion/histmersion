package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.window
import com.histmersion.lint.{tolerated, LintRule}

/** A link to a page of the web UI: a real `<a href>`, so ⌘/Ctrl/Shift/middle click opens it in a new tab; a plain click
  * moves there without reloading.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object RouteLink:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def apply(r: Route, mods: Modifier[HtmlElement]*): HtmlElement =
    a(href := r.path, mods, onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); Router.go(r) } })
