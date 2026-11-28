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

  given between: [A <: reflect.Enum, B <: reflect.Enum] => (from: TwoCases[A], to: TwoCases[B]) => Transformer[A, B] =
    a => to.values(a.ordinal)
