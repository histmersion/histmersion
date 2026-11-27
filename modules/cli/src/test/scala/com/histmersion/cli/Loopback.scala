package com.histmersion
package cli

import com.histmersion.lint.{tolerated, LintRule}
object Loopback:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def url(port: Int): Client.ServerUrl = Client.ServerUrl(s"http://127.0.0.1:$port")
