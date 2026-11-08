package com.histmersion
package api.library

type Title = Title.T
object Title extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]
