package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.window
import com.histmersion.lint.{tolerated, LintRule}

/** A link to something that opens as a window (a run, a goal, a manager) or as a page that took over an old toolbar
  * window (the backlog, the approvals): the address of its page, so it can be opened in a new tab, and a plain click
  * does what it always did — open the window, or show the page.
  */
object WinLink:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def apply(key: WindowKey, mods: Modifier[HtmlElement]*): HtmlElement =
    a(
      cls := "link-btn",
      href := Route.hrefOf(key),
      mods,
      onClick --> { ev =>
        if Router.plain(ev) then { ev.preventDefault(); ev.stopPropagation(); WindowManager.open(key) }
      }
    )
