package com.histmersion
package config

import io.github.iltotore.iron.constraint.numeric.Positive

type RefreshIntervalMinutes = RefreshIntervalMinutes.T
object RefreshIntervalMinutes extends Newtype[Int, Positive]
