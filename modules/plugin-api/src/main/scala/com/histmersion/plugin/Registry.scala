package com.histmersion
package plugin

import com.histmersion.util.problem.Problem
import scala.compiletime.deferred
import io.github.iltotore.iron.constraint.any.Pure

/** One extension point. `add` refuses an id already taken in this registry. */
trait Registry[A]:
  def add(id: Registry.AddId, a: A): Either[Problem, Unit]

object Registry:
  type RefusingAddId = AddId
  val RefusingAddId: AddId.type = AddId

  type AddId = AddId.T
  object AddId extends Newtype[String, Pure]

  def refusing[A](why: Problem): Registry[A] = new Registry[A]:
    def add(id: AddId, a: A): Either[Problem, Unit] = Left(why)
