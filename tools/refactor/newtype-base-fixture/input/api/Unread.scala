package com.histmersion
package domain.personal.marks

object UnreadItem:
  type Status = Status.T
  object Status extends io.github.iltotore.iron.RefinedType[String, io.github.iltotore.iron.Pure]
