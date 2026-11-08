package com.histmersion
package api.instance

import com.histmersion.api.catalogue.Place
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
enum MapStartMode(val wire: MapStartMode.Wire, val label: MapStartMode.Label):
  case Last extends MapStartMode(MapStartMode.Wire("last"), MapStartMode.Label("Where I last left it"))
  case Place extends MapStartMode(MapStartMode.Wire("place"), MapStartMode.Label("At a place I choose"))

object MapStartMode:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]


  def parse(s: Wire): MapStartMode = values.find(_.wire == Wire(Option(s).fold("")((_).value.trim.toLowerCase))).getOrElse(Last)
