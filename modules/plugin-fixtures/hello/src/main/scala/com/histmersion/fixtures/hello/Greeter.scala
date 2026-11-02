package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

trait Greeter:
  def greet(who: String): String
