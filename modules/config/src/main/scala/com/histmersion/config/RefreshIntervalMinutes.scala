package com.histmersion.config

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.numeric.Positive

type RefreshIntervalMinutes = RefreshIntervalMinutes.T
object RefreshIntervalMinutes extends RefinedType[Int, Positive]
