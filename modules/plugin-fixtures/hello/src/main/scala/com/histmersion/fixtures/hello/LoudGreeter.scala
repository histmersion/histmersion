package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class LoudGreeter extends Greeter:
  def greet(who: com.histmersion.fixtures.hello.LoudGreeter.GreetWho) = s"${(who).value.toUpperCase(java.util.Locale.ROOT)}!"

object LoudGreeter:
  type GreetWho = GreetWho.T
  object GreetWho extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
