package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive

type PageSize = PageSize.T

object PageSize extends Newtype[Int, Positive]
