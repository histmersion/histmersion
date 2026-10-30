package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given

final class LoudGreeter extends Greeter:
  def greet(who: String) = s"${who.toUpperCase(java.util.Locale.ROOT)}!"
