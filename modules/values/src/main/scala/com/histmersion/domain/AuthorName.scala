package com.histmersion
package domain
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type AuthorName = AuthorName.T

object AuthorName extends Newtype[String, Pure]
