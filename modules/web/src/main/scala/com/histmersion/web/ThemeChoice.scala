package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js

/** The colour theme a reader picked: follow the system's setting (the default), always light, or always dark. One
  * button cycles them in that order, as the documentation site's does.
  */
enum ThemeChoice(val wire: ThemeChoice.Wire, val label: ThemeChoice.Label, val icon: Icon):
  case System extends ThemeChoice(com.histmersion.web.ThemeChoice.Wire("system"), com.histmersion.web.ThemeChoice.Label("Theme: follows the system"), Icon.ThemeSystem)
  case Light extends ThemeChoice(com.histmersion.web.ThemeChoice.Wire("light"), com.histmersion.web.ThemeChoice.Label("Theme: light"), Icon.ThemeLight)
  case Dark extends ThemeChoice(com.histmersion.web.ThemeChoice.Wire("dark"), com.histmersion.web.ThemeChoice.Label("Theme: dark"), Icon.ThemeDark)

object ThemeChoice:
  /** A stored or attribute value; anything unknown (nothing stored, an old value) is the system's. */
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = com.histmersion.api.UiLabels.Text
  val Label: _root_.com.histmersion.api.UiLabels.Text.type = _root_.com.histmersion.api.UiLabels.Text


  def fromWire(s: String): ThemeChoice = values.find(_.wire == Wire(Option(s).fold("")(_.trim.toLowerCase))).getOrElse(System)

  /** The button's next choice: system → light → dark → system. */
  def next(c: ThemeChoice): ThemeChoice = c match
    case System => Light
    case Light  => Dark
    case Dark   => System

  /** What a press does, read out and shown on hover: the current choice and the one a press picks. */
  def tooltip(c: ThemeChoice): com.histmersion.api.UiLabels.Text =
    com.histmersion.api.UiLabels.Text(s"${c.label}. Click for ${next(c) match
        case System => "the system's setting"
        case Light  => "light"
        case Dark   => "dark"
      }.")
