package com.histmersion.fixtures.hello

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object HelloConfig:
  type Greeting = Greeting.T
  object Greeting extends RefinedType[String, Pure]
