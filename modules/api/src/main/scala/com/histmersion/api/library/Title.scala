package com.histmersion
package api.library

type Title = Title.T
object Title extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]:
  given Display[T] = Display(_.value)

  extension (title: Title)
    def trim: Title = Title(title.value.trim)
    def isBlank: Boolean = title.value.isBlank
