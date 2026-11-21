package com.histmersion
package domain.automation

import io.github.iltotore.iron.constraint.any.Pure

object Step:

  enum Status(val wire: Status.Wire):
    case Done extends Status(Status.Wire("done"))
    case Failed extends Status(Status.Wire("failed"))

  object Status:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[Status] = values.find(_.wire == s)
