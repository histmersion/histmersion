package com.histmersion.web.ui

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** A short line saying that something happened ("Copied the link"), read out by screen readers, gone after a few
  * seconds. Mounted once by the page.
  */
object Flash:

  private val line = Var(Option.empty[String])
  // `var`: the timer that clears the line
  private var timer = 0

  def say(text: String): Unit =
    line.set(Some(text))
    dom.window.clearTimeout(timer)
    timer = dom.window.setTimeout(() => line.set(None), 3000)

  lazy val element: HtmlElement =
    div(
      cls := "flash",
      role := "status",
      aria.live := "polite",
      cls("is-on") <-- line.signal.map(_.isDefined),
      child.text <-- line.signal.map(_.getOrElse(""))
    )
