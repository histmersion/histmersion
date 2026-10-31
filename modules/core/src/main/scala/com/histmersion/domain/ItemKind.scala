package com.histmersion.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A kind of record that other records point at (`source`, `article`, `event`, `place`, `picture`, `goal`). Each domain
  * names the kinds it owns when it registers its [[Referable]].
  */
type ItemKind = ItemKind.T

object ItemKind extends RefinedType[String, Match["[a-z][a-z-]*"]]
