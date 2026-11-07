package com.histmersion.web.setup

/** The first-run setup's seven steps, in order. */
enum SetupStep(val label: SetupStep.Label, val optional: SetupStep.Optional = false):
  case Welcome extends SetupStep("Welcome")
  case Papers extends SetupStep("Finding open papers")
  case Maps extends SetupStep("Maps")
  case Background extends SetupStep("Background data")
  case Agents extends SetupStep("Research agents", optional = true)
  case Calibre extends SetupStep("Calibre library", optional = true)
  case Ready extends SetupStep("Ready")

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

