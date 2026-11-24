package com.histmersion
package api.library

type Doi = Doi.T
object Doi extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]:
  given Display[T] = Display(_.value)
