package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Problem = Problem.T
object Problem extends RefinedType[String, Pure] // no finding: the layer's one Problem
