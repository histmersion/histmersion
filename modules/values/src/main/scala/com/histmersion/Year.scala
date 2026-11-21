package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Year = Year.T

object Year extends Newtype[Int, Pure]
