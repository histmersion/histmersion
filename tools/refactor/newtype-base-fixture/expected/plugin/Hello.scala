package com.histmersion.fixtures.hello

import com.histmersion.Newtype
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object HelloConfig:
  type Greeting = Greeting.T
  object Greeting extends Newtype[String, Pure]
