package com.histmersion.web

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

// S16 UNCHANGED package: `util.Try` would mean com.histmersion.util once the clause is chained
object View:
  def parsed(s: String): Option[Int] = util.Try(s.toInt).toOption
  type Count = Count.T
  // S17 REWRITE: RefinedType comes from the wildcard import, Pure gets an import
  object Count extends RefinedType[Int, Pure]

object Outcome:
  case object None
  final case class Left(n: Int)
  type Unit = Int
  // S18 REWRITE to scala.None / scala.util.Left / scala.Unit: the bare names mean Outcome's own members
  def empty: Option[Int] = scala.None
  def left: Either[Int, Int] = scala.util.Left(1)
  def unit(x: Int): scala.Unit = ()
  // S19 REWRITE: bare names that mean the same symbol
  def some: Option[Int] = Some(1)
  def own: Left = Left(2)

enum Family(val wire: String):
  case Cc0 extends Family("cc0")
  case CcBy extends Family("cc-by")
  // S22 REWRITE Family.CcBy to CcBy only: `wire` alone would be this.wire, not CcBy's; the parentheses stay
  def attribution: Boolean = wire.startsWith((CcBy.wire)) || this == Cc0

object Secret:
  trait Vault
  object Vault
  enum Ref:
    case Plain(text: String)
    // S23 UNCHANGED: in the case's own signature the enum's members are out of reach
    case Vault(name: Ref.Vault.Name)
    // S24 UNCHANGED to Vault: Secret.Vault and the case would be ambiguous (the qualifier drops to Ref.Vault only)
    def shown: Int = this match
      case Ref.Vault(_) => 1
      case _ => 0
  object Ref:
    object Vault:
      type Name = String
