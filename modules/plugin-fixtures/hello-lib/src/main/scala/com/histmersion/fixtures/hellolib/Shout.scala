package com.histmersion.fixtures.hellolib

/** A TEST FIXTURE: a library no Histmersion module depends on, so that a plugin using it needs it installed beside the
  * plugin (the dependency-resolution tests and `scripts/bundle/smoke.sh`).
  */
object Shout:
  type ApplyResult = ApplyResult.T
  object ApplyResult
      extends _root_.io.github.iltotore.iron.RefinedType[
        _root_.java.lang.String,
        _root_.io.github.iltotore.iron.constraint.any.Pure
      ]
  type ApplyText = ApplyText.T
  object ApplyText
      extends _root_.io.github.iltotore.iron.RefinedType[
        _root_.java.lang.String,
        _root_.io.github.iltotore.iron.constraint.any.Pure
      ]

  def apply(
      text: com.histmersion.fixtures.hellolib.Shout.ApplyText
  ): com.histmersion.fixtures.hellolib.Shout.ApplyResult =
    _root_.com.histmersion.fixtures.hellolib.Shout.ApplyResult((text).value.toUpperCase(java.util.Locale.ROOT) + "!")
