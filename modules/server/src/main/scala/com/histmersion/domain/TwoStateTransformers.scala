package com.histmersion
package domain

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import io.scalaland.chimney.Transformer

import api.json.TwoStates

object TwoStateTransformers:
  given toState: [N, E <: reflect.Enum]
    => (m: RefinedType.Mirror[N] { type BaseType = Boolean; type ConstraintType = Pure }, states: TwoStates[E])
    => Transformer[N, E] =
    n => states.values(if n.asInstanceOf[Boolean] then 0 else 1)

  given fromState: [E <: reflect.Enum, N]
    => (states: TwoStates[E], m: RefinedType.Mirror[N] { type BaseType = Boolean; type ConstraintType = Pure })
    => Transformer[E, N] =
    e => (e.ordinal == 0).asInstanceOf[N]
