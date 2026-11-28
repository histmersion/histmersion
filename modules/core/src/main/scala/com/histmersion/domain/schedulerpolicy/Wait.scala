package com.histmersion
package domain.schedulerpolicy

import hearth.kindlings.optics.syntax.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.schedulerpolicy.Block
import com.histmersion.Newtype

final case class Wait(
    code: Wait.Code,
    why: Wait.Why,
    behind: List[Wait.Behind] = Nil,
    position: Wait.Position = Wait.Position(0)
)

object Wait:
  export Block.{Code, Why}
  type Behind = Behind.T
  object Behind extends Newtype[String, Pure]
  type Position = Position.T
  object Position extends Newtype[Int, Pure]
