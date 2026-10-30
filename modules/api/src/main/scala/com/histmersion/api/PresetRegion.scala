package com.histmersion.api

import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

/** The region headings the web timeline lists its period presets under. A heading is NAVIGATION — where a definition
  * applies is PeriodO's own spatial coverage, carried on every row as `region` — so the boxes are only what the map
  * must mostly show for a heading to come first in the menu. The wire name is how the seed (`histmersion.presets`) and
  * the `periods.preset` column spell it. Declared in the order the menu lists them: alphabetical, the continent-wide
  * definitions last.
  */
enum PresetRegion(val wire: String, val label: String, val box: Option[BBox]):
  case Aegean extends PresetRegion("aegean", "Aegean", Some(BBox(19.5, 34.5, 29.5, 41.5)))
  case Anatolia extends PresetRegion("anatolia", "Anatolia", Some(BBox(26.0, 35.8, 45.0, 42.2)))
  case BritainIreland extends PresetRegion("britain-ireland", "Britain and Ireland", Some(BBox(-10.7, 49.8, 1.8, 60.9)))
  case CentralAsia extends PresetRegion("central-asia", "Central Asia", Some(BBox(52.0, 35.0, 80.0, 48.0)))
  case CentralEurope extends PresetRegion("central-europe", "Central Europe", Some(BBox(5.9, 45.7, 24.2, 55.1)))
  case China extends PresetRegion("china", "China", Some(BBox(73.5, 18.0, 134.8, 53.6)))
  case Cyprus extends PresetRegion("cyprus", "Cyprus", Some(BBox(32.2, 34.5, 34.7, 35.8)))
  case EasternEurope extends PresetRegion("eastern-europe", "Eastern Europe", Some(BBox(22.1, 44.3, 40.3, 52.4)))
  case Egypt extends PresetRegion("egypt", "Egypt", Some(BBox(24.7, 22.0, 35.8, 31.7)))
  case Iberia extends PresetRegion("iberia", "Iberia", Some(BBox(-9.6, 36.0, 3.4, 43.8)))
  case Iran extends PresetRegion("iran", "Iran", Some(BBox(44.0, 25.0, 63.4, 39.8)))
  case Italy extends PresetRegion("italy", "Italy", Some(BBox(6.6, 36.6, 18.6, 47.1)))
  case LevantMesopotamia
      extends PresetRegion("levant-mesopotamia", "Levant and Mesopotamia", Some(BBox(34.0, 29.0, 48.6, 37.5)))
  case Scandinavia extends PresetRegion("scandinavia", "Scandinavia", Some(BBox(4.5, 54.5, 31.1, 71.2)))
  case SouthAsia extends PresetRegion("south-asia", "South Asia", Some(BBox(60.8, 5.9, 97.4, 37.1)))
  case SoutheastEurope extends PresetRegion("southeast-europe", "Southeast Europe", Some(BBox(18.8, 41.2, 29.8, 48.3)))
  case Broad extends PresetRegion("broad", "Continent-wide definitions", None)

object PresetRegion:
  def fromWire(s: String): Option[PresetRegion] = values.find(_.wire == s.trim)

  given WireEnumValues[PresetRegion]:
    def all: Array[PresetRegion] = PresetRegion.values
    def wire(e: PresetRegion): String = e.wire
    def fromWire(s: String): Option[PresetRegion] = PresetRegion.fromWire(s)

  given io.circe.Encoder[PresetRegion] = WireEnum.encoder
  given io.circe.Decoder[PresetRegion] = WireEnum.decoder
  given Schema[PresetRegion] = WireEnum.schema
