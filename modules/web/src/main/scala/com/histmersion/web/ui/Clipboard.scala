package com.histmersion.web.ui

import org.scalajs.dom

import scala.scalajs.js
import scala.util.Try

/** Copying text for a person: the browser's clipboard, else the older copy command; what happened is said ([[Flash]]).
  */
object Clipboard:

  def copy(text: String, what: String): Unit =
    val clip = Try(dom.window.navigator.asInstanceOf[js.Dynamic].clipboard).toOption.filterNot(c =>
      js.isUndefined(c) || c == null
    )
    clip match
      case Some(c) =>
        val p = c.writeText(text).asInstanceOf[js.Promise[Unit]]
        p.`then`[Unit](_ => Flash.say(s"Copied $what."), (_: Any) => fallback(text, what)): Unit
      case None => fallback(text, what)

  private def fallback(text: String, what: String): Unit =
    val ta = dom.document.createElement("textarea").asInstanceOf[dom.HTMLTextAreaElement]
    ta.value = text
    ta.setAttribute("readonly", "")
    ta.className = "offscreen"
    dom.document.body.appendChild(ta)
    ta.select()
    val ok = Try(dom.document.execCommand("copy")).getOrElse(false)
    ta.remove()
    Flash.say(if ok then s"Copied $what." else s"Could not copy $what: the browser did not allow it.")
