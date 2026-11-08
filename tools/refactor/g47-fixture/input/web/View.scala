package com.histmersion.web

import io.github.iltotore.iron.*

// S16 UNCHANGED package: `util.Try` would mean com.histmersion.util once the clause is chained
object View:
  def parsed(s: String): Option[Int] = util.Try(s.toInt).toOption
  type Count = Count.T
  // S17 REWRITE: RefinedType comes from the wildcard import, Pure gets an import
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

object Outcome:
  case object None
  final case class Left(n: Int)
  type Unit = Int
  // S18 REWRITE to scala.None / scala.util.Left / scala.Unit: the bare names mean Outcome's own members
  def empty: Option[Int] = _root_.scala.None
  def left: Either[Int, Int] = _root_.scala.util.Left(1)
  def unit(x: Int): _root_.scala.Unit = ()
  // S19 REWRITE: bare names that mean the same symbol
  def some: Option[Int] = _root_.scala.Some(1)
  def own: Outcome.Left = Outcome.Left(2)

enum Family(val wire: String):
  case Cc0 extends Family("cc0")
  case CcBy extends Family("cc-by")
  // S22 REWRITE Family.CcBy to CcBy only: `wire` alone would be this.wire, not CcBy's; the parentheses stay
  def attribution: Boolean = wire.startsWith((Family.CcBy.wire)) || this == Family.Cc0
