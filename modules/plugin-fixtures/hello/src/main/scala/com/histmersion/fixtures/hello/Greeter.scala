package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

trait Greeter:
  def greet(who: com.histmersion.fixtures.hello.Greeter.GreetWho): com.histmersion.fixtures.hello.Greeter.GreetResult

object Greeter:
  type GreetResult = GreetResult.T
  object GreetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GreetWho = GreetWho.T
  object GreetWho extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
