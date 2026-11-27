package com.histmersion
package fixture

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Title = Title.T
object Title extends RefinedType[String, Pure]:
  given Display[T] = Display(_.value)

type Wire = Wire.T
object Wire extends RefinedType[String, Pure]:
  given Display[T] = Display(_.value.capitalize)

type Name = Name.T
object Name extends RefinedType[String, Pure]

type Count = Count.T
object Count extends RefinedType[Int, Pure]:
  given Display[T] = Display(count => s"${count.value}")
