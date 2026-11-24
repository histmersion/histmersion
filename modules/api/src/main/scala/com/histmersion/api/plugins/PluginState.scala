package com.histmersion
package api.plugins
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
enum PluginState:
  case Registered

  case Disabled

  case Failed

  case Refused

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wire: PluginState.Wire = PluginState.Wire(toString.toLowerCase(java.util.Locale.ROOT))

object PluginState:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[PluginState] =
    values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
