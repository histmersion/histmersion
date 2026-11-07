package com.histmersion.web.setup

/** The first-run setup's seven steps, in order. */
enum SetupStep(val label: SetupStep.Label, val optional: SetupStep.Optional = com.histmersion.web.setup.SetupStep.Optional(false)):
  case Welcome extends SetupStep(com.histmersion.web.setup.SetupStep.Label("Welcome"))
  case Papers extends SetupStep(com.histmersion.web.setup.SetupStep.Label("Finding open papers"))
  case Maps extends SetupStep(com.histmersion.web.setup.SetupStep.Label("Maps"))
  case Background extends SetupStep(com.histmersion.web.setup.SetupStep.Label("Background data"))
  case Agents extends SetupStep(com.histmersion.web.setup.SetupStep.Label("Research agents"), optional = com.histmersion.web.setup.SetupStep.Optional(true))
  case Calibre extends SetupStep(com.histmersion.web.setup.SetupStep.Label("Calibre library"), optional = com.histmersion.web.setup.SetupStep.Optional(true))
  case Ready extends SetupStep(com.histmersion.web.setup.SetupStep.Label("Ready"))

  def number: com.histmersion.web.setup.SetupStep.Number = ordinal + 1
  def next: Option[SetupStep] = SetupStep.values.lift(ordinal + 1)
  def back: Option[SetupStep] = Option.when(ordinal > 0)(SetupStep.values(ordinal - 1))

object SetupStep:
  type Number = Number.T
  object Number extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Optional = Optional.T
  object Optional extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

