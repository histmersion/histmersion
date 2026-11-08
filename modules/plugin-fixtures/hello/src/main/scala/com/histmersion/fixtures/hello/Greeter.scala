package com.histmersion.fixtures.hello

import com.histmersion.Newtype
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

trait Greeter:
  def greet(who: Greeter.Who): Greeter.Greeting

object Greeter:
  type Greeting = Greeting.T
  object Greeting extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Who = Who.T
  object Who extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
