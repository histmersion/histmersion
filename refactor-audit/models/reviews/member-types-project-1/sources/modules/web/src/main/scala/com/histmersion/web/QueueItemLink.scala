package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.window

/** A link to one item of a list (a backlog item, something to acquire): its list's page, scrolled to it. */
object QueueItemLink:
  type ApplyId = ApplyId.T
  object ApplyId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(kind: com.histmersion.api.automation.Queue.Kind, id: com.histmersion.web.QueueItemLink.ApplyId, mods: Modifier[HtmlElement]*): HtmlElement =
    val to = Route.queueItem(kind, id)
    a(
      cls := "link-btn",
      href := to,
      mods,
      onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); ev.stopPropagation(); Router.goHref(to) } }
    )
