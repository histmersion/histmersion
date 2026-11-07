package com.histmersion.web.ui

import org.scalajs.dom

import scala.scalajs.js
import scala.util.Try

/** Copying text for a person: the browser's clipboard, else the older copy command; what happened is said ([[Flash]]).
  */
object Clipboard:

  type CopyText = _root_.com.histmersion.web.ui.Clipboard.FallbackText
  val CopyText: _root_.com.histmersion.web.ui.Clipboard.FallbackText.type = _root_.com.histmersion.web.ui.Clipboard.FallbackText
  type CopyWhat = _root_.com.histmersion.web.ui.Clipboard.FallbackWhat
  val CopyWhat: _root_.com.histmersion.web.ui.Clipboard.FallbackWhat.type = _root_.com.histmersion.web.ui.Clipboard.FallbackWhat
  type FallbackText = FallbackText.T
  object FallbackText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FallbackWhat = FallbackWhat.T
  object FallbackWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def copy(text: com.histmersion.web.ui.Clipboard.CopyText, what: com.histmersion.web.ui.Clipboard.CopyWhat): Unit =
    val clip = Try(dom.window.navigator.asInstanceOf[js.Dynamic].clipboard).toOption.filterNot(c =>
      js.isUndefined(c) || c == null
    )
    clip match
      case Some(c) =>
        val p = c.writeText(text.value).asInstanceOf[js.Promise[Unit]]
        p.`then`[Unit](_ => Flash.say(Flash.Text(s"Copied $what.")), (_: Any) => fallback(text, what)): Unit
      case None => fallback(text, what)

  private def fallback(text: com.histmersion.web.ui.Clipboard.FallbackText, what: com.histmersion.web.ui.Clipboard.FallbackWhat): Unit =
    val ta = dom.document.createElement("textarea").asInstanceOf[dom.HTMLTextAreaElement]
    ta.value = text.value
    ta.setAttribute("readonly", "")
    ta.className = "offscreen"
    dom.document.body.appendChild(ta)
    ta.select()
    val ok = Try(dom.document.execCommand("copy")).getOrElse(false)
    ta.remove()
    Flash.say(Flash.Text(if ok then s"Copied $what." else s"Could not copy $what: the browser did not allow it."))
