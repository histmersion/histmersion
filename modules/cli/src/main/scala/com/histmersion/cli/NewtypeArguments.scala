package com.histmersion
package cli

import cats.data.{Validated, ValidatedNel}
import com.monovore.decline.Argument
import io.github.iltotore.iron.RefinedType

/** The command-line argument of every newtype, from its base type's: the same metavar, the constraint checked. */
object NewtypeArguments:
  given argument: [T] => (m: RefinedType.Mirror[T]) => (base: Argument[m.BaseType]) => Argument[T] = new Argument[T]:
    def read(raw: String): ValidatedNel[String, T] =
      base.read(raw).andThen(v => Validated.fromEither(m.ops.either(v).map(_.asInstanceOf[T])).toValidatedNel)
    def defaultMetavar: String = base.defaultMetavar
