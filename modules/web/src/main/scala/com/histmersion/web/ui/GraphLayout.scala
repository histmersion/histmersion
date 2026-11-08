package com.histmersion
package web.ui

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The graph's force layout and label sizes (its colours are `--graph-*` tokens). */
object GraphLayout:
  type Repulsion = Repulsion.T
  object Repulsion extends RefinedType[Double, Pure]
  type SpringK = SpringK.T
  object SpringK extends RefinedType[Double, Pure]
  type RestLen = RestLen.T
  object RestLen extends RefinedType[Double, Pure]
  type Damping = Damping.T
  object Damping extends RefinedType[Double, Pure]
  type Centering = Centering.T
  object Centering extends RefinedType[Double, Pure]
  type MaxTicks = MaxTicks.T
  object MaxTicks extends RefinedType[Int, Pure]
  type RootRadius = RootRadius.T
  object RootRadius extends RefinedType[Double, Pure]
  type Depth1Radius = Depth1Radius.T
  object Depth1Radius extends RefinedType[Double, Pure]
  type LeafRadius = LeafRadius.T
  object LeafRadius extends RefinedType[Double, Pure]
  type LabelMaxLen = LabelMaxLen.T
  object LabelMaxLen extends RefinedType[Int, Pure]
  type LabelTruncLen = LabelTruncLen.T
  object LabelTruncLen extends RefinedType[Int, Pure]

  val repulsion: Repulsion = Repulsion(3000.0)
  val springK: SpringK = SpringK(0.008)
  val restLen: RestLen = RestLen(120.0)
  val damping: Damping = Damping(0.92)
  val centering: Centering = Centering(0.01)
  val maxTicks: MaxTicks = MaxTicks(300)
  val rootRadius: RootRadius = RootRadius(8.0)
  val depth1Radius: Depth1Radius = Depth1Radius(6.0)
  val leafRadius: LeafRadius = LeafRadius(4.5)
  val labelMaxLen: LabelMaxLen = LabelMaxLen(30)
  val labelTruncLen: LabelTruncLen = LabelTruncLen(28)
