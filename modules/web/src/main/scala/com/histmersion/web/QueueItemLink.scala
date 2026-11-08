package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.window
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Goal

/** A link to one item of a list (a backlog item, something to acquire): its list's page, scrolled to it. */
object QueueItemLink:

  def apply(kind: Queue.Kind, id: Goal.Plans.GoalLink.QueueNumber, mods: Modifier[HtmlElement]*): HtmlElement =
    val to = Route.queueItem(kind, id)
    a(
      cls := "link-btn",
      href := to,
      mods,
      onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); ev.stopPropagation(); Router.goHref(to) } }
    )
