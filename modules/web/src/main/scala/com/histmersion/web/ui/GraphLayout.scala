package com.histmersion.web.ui

/** The graph's force layout and label sizes (its colours are `--graph-*` tokens). */
object GraphLayout:
  type Repulsion = Repulsion.T
  object Repulsion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpringK = SpringK.T
  object SpringK extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestLen = RestLen.T
  object RestLen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Damping = Damping.T
  object Damping extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Centering = Centering.T
  object Centering extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxTicks = MaxTicks.T
  object MaxTicks extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RootRadius = RootRadius.T
  object RootRadius extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Depth1Radius = Depth1Radius.T
  object Depth1Radius extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeafRadius = LeafRadius.T
  object LeafRadius extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelMaxLen = LabelMaxLen.T
  object LabelMaxLen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelTruncLen = LabelTruncLen.T
  object LabelTruncLen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val repulsion: com.histmersion.web.ui.GraphLayout.Repulsion = 3000.0
  val springK: com.histmersion.web.ui.GraphLayout.SpringK = 0.008
  val restLen: com.histmersion.web.ui.GraphLayout.RestLen = 120.0
  val damping: com.histmersion.web.ui.GraphLayout.Damping = 0.92
  val centering: com.histmersion.web.ui.GraphLayout.Centering = 0.01
  val maxTicks: com.histmersion.web.ui.GraphLayout.MaxTicks = com.histmersion.web.ui.GraphLayout.MaxTicks(300)
  val rootRadius: com.histmersion.web.ui.GraphLayout.RootRadius = 8.0
  val depth1Radius: com.histmersion.web.ui.GraphLayout.Depth1Radius = 6.0
  val leafRadius: com.histmersion.web.ui.GraphLayout.LeafRadius = 4.5
  val labelMaxLen: com.histmersion.web.ui.GraphLayout.LabelMaxLen = com.histmersion.web.ui.GraphLayout.LabelMaxLen(30)
  val labelTruncLen: com.histmersion.web.ui.GraphLayout.LabelTruncLen = com.histmersion.web.ui.GraphLayout.LabelTruncLen(28)
