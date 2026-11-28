package com.histmersion
package domain

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import io.scalaland.chimney.Transformer

object TwoStateTransformers:
  given toState: [N, E <: reflect.Enum]
    => (m: RefinedType.Mirror[N] { type BaseType = Boolean; type ConstraintType = Pure }, states: TwoCases[E])
    => Transformer[N, E] =
    n => states.values(if n.asInstanceOf[Boolean] then 0 else 1)

  given fromState: [E <: reflect.Enum, N]
    => (states: TwoCases[E], m: RefinedType.Mirror[N] { type BaseType = Boolean; type ConstraintType = Pure })
    => Transformer[E, N] =
    e => (e.ordinal == 0).asInstanceOf[N]

  /** One two-case enum into another case by case in order (a wire enum and the domain's of the same Boolean): the first
    * case is the one a `true` stood for on both sides, so the value carries over as the Boolean did.
    */
  given between: [A <: reflect.Enum, B <: reflect.Enum] => (from: TwoCases[A], to: TwoCases[B]) => Transformer[A, B] =
    a => to.values(a.ordinal)
