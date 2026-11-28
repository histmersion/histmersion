package com.histmersion
package domain.library.workflows

import io.github.iltotore.iron.constraint.any.Pure

final case class Upstreamed(
    ran: Upstreamed.Ran,
    summary: Upstreamed.Summary,
    retracted: Upstreamed.Retracted = Upstreamed.Retracted(false)
)

object Upstreamed:
  type Ran = Ran.T
  object Ran extends Newtype[Boolean, Pure]
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type Retracted = Retracted.T
  object Retracted extends Newtype[Boolean, Pure]
