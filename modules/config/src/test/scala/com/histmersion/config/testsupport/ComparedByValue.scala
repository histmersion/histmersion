package com.histmersion
package config.testsupport

import io.github.iltotore.iron.RefinedType
import munit.Compare
import com.histmersion.lint.{tolerated, LintRule}

/** Tests state expected values as literals of the base type. Two values compare by `==` when their types have the same
  * shape — the same type constructor, or one a subtype of the other (`Option` and `Some`, `Seq` and `List`, `Either`
  * and `Right`, tuples of the same arity, an option and `Some[…] | None.type`) — and every pair of types inside it is
  * one type, a subtype and its supertype, or a newtype and its own base type (a newtype erases to its value). Anything
  * else does not compile: two newtypes, a newtype and another base type, a `Vector` and a `List`.
  */
object ComparedByValue:
  given comparedByValue: [A, B] => (byValue: ByValue[A, B]) => Compare[A, B] = Compare.defaultCompare[A, B]

  /** `A` and `B` compare by value. */
  final class ByValue[A, B]

  trait SupertypeWithSubtype:
    given supertypeWithSubtype: [A, B] => (B <:< A) => ByValue[A, B] = ByValue()

  trait SubtypeWithSupertype extends SupertypeWithSubtype:
    given subtypeWithSupertype: [A, B] => (A <:< B) => ByValue[A, B] = ByValue()

  trait NewtypeWithBase extends SubtypeWithSupertype:
    given newtypeWithBase: [N, B] => (RefinedType.Mirror[N] { type BaseType = B }) => ByValue[N, B] = ByValue()
    given baseWithNewtype: [B, N] => (RefinedType.Mirror[N] { type BaseType = B }) => ByValue[B, N] = ByValue()

  trait Same extends NewtypeWithBase:
    given same: [A] => ByValue[A, A] = ByValue()

  trait OneParameter extends Same:
    given oneParameter: [F[_], G[_], A, B] => (Related[F[Any], G[Any]], ByValue[A, B]) => ByValue[F[A], G[B]] =
      ByValue()

  trait TwoParameters extends OneParameter:
    given twoParameters: [F[_, _], G[_, _], A1, A2, B1, B2]
      => (Related[F[Any, Any], G[Any, Any]], ByValue[A1, B1], ByValue[A2, B2]) => ByValue[F[A1, A2], G[B1, B2]] =
      ByValue()

  object ByValue extends TwoParameters:
    /** An expected list mixing `Some(…)` and `None` (or `Left(…)` and `Right(…)`) infers the union of the alternatives.
      */
    given optionAlternatives: [O[x] <: Option[x], A, B] => ByValue[A, B] => ByValue[O[A], Some[B] | None.type] =
      ByValue()
    given eitherAlternatives: [E[l, r] <: Either[l, r], L1, R1, L2, R2]
      => (ByValue[L1, L2], ByValue[R1, R2]) => ByValue[E[L1, R1], Left[L2, Nothing] | Right[Nothing, R2]] =
      ByValue()
    given emptyTuples: ByValue[EmptyTuple, EmptyTuple] = ByValue()
    given tuples
        : [H1, T1 <: Tuple, H2, T2 <: Tuple] => (ByValue[H1, H2], ByValue[T1, T2]) => ByValue[H1 *: T1, H2 *: T2] =
      ByValue()

  /** `X` and `Y` are one type, or one is a subtype of the other. */
  final class Related[X, Y]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  trait RelatedSupertype:
    given relatedSupertype: [X, Y] => (Y <:< X) => Related[X, Y] = Related()

  object Related extends RelatedSupertype:
    given relatedSubtype: [X, Y] => (X <:< Y) => Related[X, Y] = Related()
