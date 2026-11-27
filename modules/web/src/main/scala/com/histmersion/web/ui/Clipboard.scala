package com.histmersion
package web.ui

import org.scalajs.dom

import scala.scalajs.js
import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** Copying text for a person: the browser's clipboard, else the older copy command; what happened is said ([[Flash]]).
  */
@atBoundary(BoundaryKind.Dom, "copies text through the browser clipboard")
object Clipboard:

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CopyText = FallbackText
  val CopyText: FallbackText.type = FallbackText
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CopyWhat = FallbackWhat
  val CopyWhat: FallbackWhat.type = FallbackWhat
  type FallbackText = FallbackText.T
  object FallbackText extends Newtype[String, Pure]
  type FallbackWhat = FallbackWhat.T
  object FallbackWhat extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def copy(text: CopyText, what: CopyWhat): Unit =
    val clip = Try(dom.window.navigator.asInstanceOf[js.Dynamic].clipboard).toOption.filterNot(c =>
      js.isUndefined(c) || c == null
    )
    clip match
      case Some(c) =>
        val p = c.writeText(text.value).asInstanceOf[js.Promise[Unit]]
        p.`then`[Unit](_ => Flash.say(Flash.Text(shown"Copied $what.")), (_: Any) => fallback(text, what)): Unit
      case None => fallback(text, what)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def fallback(text: FallbackText, what: FallbackWhat): Unit =
    val ta = dom.document.createElement("textarea").asInstanceOf[dom.HTMLTextAreaElement]
    ta.value = text.value
    ta.setAttribute("readonly", "")
    ta.className = "offscreen"
    dom.document.body.appendChild(ta)
    ta.select()
    val ok = Try(dom.document.execCommand("copy")).getOrElse(false)
    ta.remove()
    Flash.say(Flash.Text(if ok then shown"Copied $what."
    else shown"Could not copy $what: the browser did not allow it."))
