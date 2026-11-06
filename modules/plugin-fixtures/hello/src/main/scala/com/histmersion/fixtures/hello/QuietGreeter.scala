package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class QuietGreeter extends Greeter:
  def greet(who: com.histmersion.fixtures.hello.QuietGreeter.GreetWho) = s"(${(who).value.toLowerCase(java.util.Locale.ROOT)})"

object QuietGreeter:
  type GreetWho = GreetWho.T
  object GreetWho extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
