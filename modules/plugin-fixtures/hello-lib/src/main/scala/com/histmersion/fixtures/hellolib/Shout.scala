package com.histmersion.fixtures.hellolib

/** A TEST FIXTURE: a library no Histmersion module depends on, so that a plugin using it needs it
  * installed beside the plugin (the dependency-resolution tests and `scripts/bundle/smoke.sh`). */
object Shout:
  def apply(text: String): String = text.toUpperCase(java.util.Locale.ROOT) + "!"
