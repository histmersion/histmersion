package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

final case class Lamp(name: String, lit: Boolean)

class Panel(val shown: Boolean):
  def toggle(on: Boolean, label: String): String = if on then label else ""
  def hide(on: Boolean = false): Boolean = !on
  def all(flags: Boolean*): Boolean = flags.forall(identity)
  private def kept(on: Boolean): Boolean = on
  private[domain] def shared(on: Boolean): Boolean = kept(on = on)
  def passedOn(on: Boolean): Boolean =
    kept(on)
  override def equals(other: Any): Boolean = other.isInstanceOf[Panel]
  override def hashCode: Int = 1

enum Light:
  case On(dim: Boolean)
  case Off

type Flag = Flag.T
object Flag extends RefinedType[Boolean, Pure]

final case class Lever(flag: Flag)

object Switches:
  extension (p: Panel) def lit(on: Boolean): Boolean = p.shown && on
  def configure(verbose: Boolean)(dryRun: Boolean): Int = if verbose && dryRun then 1 else 0
  def counted(n: Int)(using strict: Boolean): Int = if strict then n else 0

  val isOn: Boolean = Lamp("x", true).lit
  val named: Lamp = Lamp("x", lit = true)
  val built: Panel = new Panel(true)
  val builtNamed: Panel = new Panel(shown = isOn)
  val toggled: String = built.toggle(isOn, "label")
  val toggledNamed: String = built.toggle(on = isOn, "label")
  val viaExtension: Boolean = built.lit(false)
  val viaExtensionNamed: Boolean = built.lit(on = false)
  val curried: Int = configure(verbose = true)(isOn)
  val curriedNamed: Int = configure(verbose = true)(dryRun = false)
  val explicitUsing: Int = counted(1)(using true)
  val enumCase: Light = Light.On(isOn && !named.lit)
  val enumCaseNamed: Light = Light.On(dim = false)
  val repeated: Boolean = built.all(true, isOn)
  val defaulted: Boolean = built.hide()
  val outside: Option[Int] = Option.when(isOn)(1)
  val operators: Boolean = isOn && (isOn || !isOn) == true
  val newtype: Lever = Lever(Flag(true))
  val lambda: Boolean => Boolean = (b: Boolean) => b
  def local(n: Int): Int =
    def inner(on: Boolean): Int = if on then n else 0
    inner(on = n > 0)
