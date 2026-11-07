package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class QuietGreeter extends Greeter:
  def greet(who: Greeter.Who): Greeter.Greeting = Greeter.Greeting(s"(${who.value.toLowerCase(java.util.Locale.ROOT)})")

