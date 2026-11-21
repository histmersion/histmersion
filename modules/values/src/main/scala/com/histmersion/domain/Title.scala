package com.histmersion
package domain
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Title = Title.T

object Title extends Newtype[String, Pure]
