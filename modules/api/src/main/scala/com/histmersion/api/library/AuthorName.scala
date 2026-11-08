package com.histmersion
package api.library

type AuthorName = AuthorName.T
object AuthorName extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]
