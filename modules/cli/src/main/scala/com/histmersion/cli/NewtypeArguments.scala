package com.histmersion.cli

import cats.data.{Validated, ValidatedNel}
import com.monovore.decline.Argument
import io.github.iltotore.iron.RefinedType

/** The command-line argument of every newtype, from its base type's: the same metavar, the constraint checked. */
object NewtypeArguments:
  type ReadResultPart1 = ReadResultPart1.T
  object ReadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadRaw = ReadRaw.T
  object ReadRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultMetavarResult = DefaultMetavarResult.T
  object DefaultMetavarResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given argument: [T] => (m: RefinedType.Mirror[T]) => (base: Argument[m.BaseType]) => Argument[T] = new Argument[T]:
    def read(raw: com.histmersion.cli.NewtypeArguments.ReadRaw): ValidatedNel[com.histmersion.cli.NewtypeArguments.ReadResultPart1, T] =
      base.read(raw).andThen(v => Validated.fromEither(m.ops.either(v).map(_.asInstanceOf[T])).toValidatedNel)
    def defaultMetavar: com.histmersion.cli.NewtypeArguments.DefaultMetavarResult = base.defaultMetavar
