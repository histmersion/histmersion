package crossimport

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Number:
  type Count = Count.T
  object Count extends RefinedType[Int, Pure]
  def make(raw: _root_.scala.Int): Count = raw
