package com.histmersion
package api

type Sha256 = Sha256.T
object Sha256 extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]
