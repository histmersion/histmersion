package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.UiLabels
import com.histmersion.lint.{tolerated, LintRule}

/** The colour theme a reader picked: follow the system's setting (the default), always light, or always dark. One
  * button cycles them in that order, as the documentation site's does.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
enum ThemeChoice(val wire: ThemeChoice.Wire, val label: ThemeChoice.Label, val icon: Icon) derives CanEqual:
  case System
      extends ThemeChoice(ThemeChoice.Wire("system"), ThemeChoice.Label("Theme: follows the system"), Icon.ThemeSystem)
  case Light extends ThemeChoice(ThemeChoice.Wire("light"), ThemeChoice.Label("Theme: light"), Icon.ThemeLight)
  case Dark extends ThemeChoice(ThemeChoice.Wire("dark"), ThemeChoice.Label("Theme: dark"), Icon.ThemeDark)

object ThemeChoice:
  /** A stored or attribute value; anything unknown (nothing stored, an old value) is the system's. */
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  export UiLabels.Text as Label

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fromWire(s: String): ThemeChoice =
    values.find(_.wire == Wire(Option(s).fold("")(_.trim.toLowerCase))).getOrElse(System)

  /** The button's next choice: system → light → dark → system. */
  def next(c: ThemeChoice): ThemeChoice = c match
    case System => Light
    case Light  => Dark
    case Dark   => System

  /** What a press does, read out and shown on hover: the current choice and the one a press picks. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tooltip(c: ThemeChoice): UiLabels.Text =
    UiLabels.Text(s"${c.label}. Click for ${next(c) match
        case System => "the system's setting"
        case Light  => "light"
        case Dark   => "dark"
      }.")
