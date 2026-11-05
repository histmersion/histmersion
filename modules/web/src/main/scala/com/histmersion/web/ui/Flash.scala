package com.histmersion.web.ui

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** A short line saying that something happened ("Copied the link"), read out by screen readers, gone after a few
  * seconds. Mounted once by the page.
  */
object Flash:

  type SayText = SayText.T
  object SayText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val line = Var(Option.empty[String])
  // `var`: the timer that clears the line
  private var timer = 0

  def say(text: com.histmersion.web.ui.Flash.SayText): Unit =
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
