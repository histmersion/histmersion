package com.histmersion
package util.problem

import io.github.iltotore.iron.constraint.any.Pure

/** What the code before the closed [[Problem]] wrote, while it is brought over case by case. */
object Legacy:

  /** A failure's sentence as an unclassified site wrote it. */
  type Text = Text.T
  object Text extends Newtype[String, Pure]:
    /** An exception's message as it is, as the values module's String `Problem.of` took it (`null` when it has none).
      */
    def of(thrown: Throwable): Text = Text(thrown.getMessage)
