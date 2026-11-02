package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class QuietGreeter extends Greeter:
  def greet(who: String) = s"(${who.toLowerCase(java.util.Locale.ROOT)})"
