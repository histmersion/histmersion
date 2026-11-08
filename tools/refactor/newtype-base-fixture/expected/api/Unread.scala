package com.histmersion
package domain.personal.marks

object UnreadItem:
  type Status = Status.T
  object Status extends Newtype[String, io.github.iltotore.iron.Pure]
