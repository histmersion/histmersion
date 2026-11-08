package com.histmersion
package plugin

import scala.compiletime.deferred
import io.github.iltotore.iron.constraint.any.Pure

/** One extension point. `add` refuses an id already taken in this registry. */
trait Registry[A]:
  def add(id: Registry.AddId, a: A): Either[Registry.AddResultPart1, Unit]

object Registry:
  /** A registry that takes nothing, saying why. */
  type RefusingAddId = AddId
  val RefusingAddId: AddId.type = AddId

  type AddResultPart1 = AddResultPart1.T
  object AddResultPart1 extends Newtype[String, Pure]
  type AddId = AddId.T
  object AddId extends Newtype[String, Pure]
  type RefusingWhy = RefusingWhy.T
  object RefusingWhy extends Newtype[String, Pure]

  def refusing[A](why: RefusingWhy): Registry[A] = new Registry[A]:
    def add(id: AddId, a: A): Either[AddResultPart1, Unit] = Left(AddResultPart1(s"'$id': $why"))
