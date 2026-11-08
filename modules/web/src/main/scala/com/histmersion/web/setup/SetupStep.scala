package com.histmersion
package web.setup

import io.github.iltotore.iron.constraint.any.Pure

/** The first-run setup's seven steps, in order. */
enum SetupStep(val label: SetupStep.Label, val optional: SetupStep.Optional = SetupStep.Optional(false)):
  case Welcome extends SetupStep(SetupStep.Label("Welcome"))
  case Papers extends SetupStep(SetupStep.Label("Finding open papers"))
  case Maps extends SetupStep(SetupStep.Label("Maps"))
  case Background extends SetupStep(SetupStep.Label("Background data"))
  case Agents extends SetupStep(SetupStep.Label("Research agents"), optional = SetupStep.Optional(true))
  case Calibre extends SetupStep(SetupStep.Label("Calibre library"), optional = SetupStep.Optional(true))
  case Ready extends SetupStep(SetupStep.Label("Ready"))

  def number: SetupStep.Number = SetupStep.Number(ordinal + 1)
  def next: Option[SetupStep] = SetupStep.values.lift(ordinal + 1)
  def back: Option[SetupStep] = Option.when(ordinal > 0)(SetupStep.values(ordinal - 1))

object SetupStep:
  type Number = Number.T
  object Number extends Newtype[Int, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Optional = Optional.T
  object Optional extends Newtype[Boolean, Pure]

