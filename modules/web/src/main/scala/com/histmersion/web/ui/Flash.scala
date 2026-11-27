package com.histmersion
package web.ui

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A short line saying that something happened ("Copied the link"), read out by screen readers, gone after a few
  * seconds. Mounted once by the page.
  */
object Flash:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  private val line = Var(Option.empty[String])
  // `var`: the timer that clears the line
  private var timer = 0

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def say(text: Text): Unit =
    line.set(Some(text.value))
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
