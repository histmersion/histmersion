package com.histmersion
package api.library

type Citekey = Citekey.T
object Citekey extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]
