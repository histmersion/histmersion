package com.histmersion
package web

import com.histmersion.web.ui.ThemeTokens
import com.histmersion.lint.{tolerated, LintRule}

/** The colour tokens for suites that compute colours: the tests run in Node, where there is no stylesheet to read them
  * from. The ramp is the shipped one (web/style.css), so the colours the tests expect are the ones the page shows.
  */
object TestTokens:
  val values: Map[String, String] = Map(
    "--ramp-0" -> "rgb(68,1,84)",
    "--ramp-1" -> "rgb(59,82,139)",
    "--ramp-2" -> "rgb(33,145,140)",
    "--ramp-3" -> "rgb(94,201,98)",
    "--ramp-4" -> "rgb(253,231,37)",
    "--edge-references" -> "#6ea8fe",
    "--edge-default" -> "#9aa3b2",
    "--status-default" -> "#666666"
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def install(): Unit =
    ThemeTokens.lookup = n => values.get(n.value).map(ThemeTokens.CssText(_))
    ThemeTokens.refresh()
