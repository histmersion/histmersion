package com.histmersion
package fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "a test plugin standing in for a third-party author: it builds and reads its values directly")
final class LoudGreeter extends Greeter:
  def greet(who: Greeter.Who): Greeter.Greeting = Greeter.Greeting(s"${who.value.toUpperCase(java.util.Locale.ROOT)}!")
