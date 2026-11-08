package com.histmersion
package web.ui

import io.github.iltotore.iron.constraint.any.Pure

/** The graph's force layout and label sizes (its colours are `--graph-*` tokens). */
object GraphLayout:
  type Repulsion = Repulsion.T
  object Repulsion extends Newtype[Double, Pure]
  type SpringK = SpringK.T
  object SpringK extends Newtype[Double, Pure]
  type RestLen = RestLen.T
  object RestLen extends Newtype[Double, Pure]
  type Damping = Damping.T
  object Damping extends Newtype[Double, Pure]
  type Centering = Centering.T
  object Centering extends Newtype[Double, Pure]
  type MaxTicks = MaxTicks.T
  object MaxTicks extends Newtype[Int, Pure]
  type RootRadius = RootRadius.T
  object RootRadius extends Newtype[Double, Pure]
  type Depth1Radius = Depth1Radius.T
  object Depth1Radius extends Newtype[Double, Pure]
  type LeafRadius = LeafRadius.T
  object LeafRadius extends Newtype[Double, Pure]
  type LabelMaxLen = LabelMaxLen.T
  object LabelMaxLen extends Newtype[Int, Pure]
  type LabelTruncLen = LabelTruncLen.T
  object LabelTruncLen extends Newtype[Int, Pure]

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
