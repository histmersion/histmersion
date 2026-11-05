package com.histmersion.web.setup

/** The first-run setup's seven steps, in order. */
enum SetupStep(val label: String, val optional: Boolean = false):
  case Welcome extends SetupStep("Welcome")
  case Papers extends SetupStep("Finding open papers")
  case Maps extends SetupStep("Maps")
  case Background extends SetupStep("Background data")
  case Agents extends SetupStep("Research agents", optional = true)
  case Calibre extends SetupStep("Calibre library", optional = true)
  case Ready extends SetupStep("Ready")

  def number: Int = ordinal + 1
  def next: Option[SetupStep] = SetupStep.values.lift(ordinal + 1)
  def back: Option[SetupStep] = Option.when(ordinal > 0)(SetupStep.values(ordinal - 1))
