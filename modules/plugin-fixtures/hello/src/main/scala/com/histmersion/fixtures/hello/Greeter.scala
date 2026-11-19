package com.histmersion
package fixtures.hello

import com.histmersion.Newtype
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import io.github.iltotore.iron.constraint.any.Pure

trait Greeter:
  def greet(who: Greeter.Who): Greeter.Greeting

object Greeter:
  type Greeting = Greeting.T
  object Greeting extends Newtype[String, Pure]
  type Who = Who.T
  object Who extends Newtype[String, Pure]
