package com.histmersion.api.catalogue
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.{BBox, WireEnum}
import com.histmersion.api.given

enum PresetRegion(val wire: PresetRegion.Wire, val label: PresetRegion.Label, val box: Option[BBox]):
  case Aegean extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("aegean"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Aegean"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(19.5), _root_.com.histmersion.api.catalogue.Latitude(34.5), _root_.com.histmersion.api.catalogue.Longitude(29.5), _root_.com.histmersion.api.catalogue.Latitude(41.5))))
  case Anatolia extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("anatolia"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Anatolia"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(26.0), _root_.com.histmersion.api.catalogue.Latitude(35.8), _root_.com.histmersion.api.catalogue.Longitude(45.0), _root_.com.histmersion.api.catalogue.Latitude(42.2))))
  case BritainIreland extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("britain-ireland"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Britain and Ireland"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(-10.7), _root_.com.histmersion.api.catalogue.Latitude(49.8), _root_.com.histmersion.api.catalogue.Longitude(1.8), _root_.com.histmersion.api.catalogue.Latitude(60.9))))
  case CentralAsia extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("central-asia"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Central Asia"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(52.0), _root_.com.histmersion.api.catalogue.Latitude(35.0), _root_.com.histmersion.api.catalogue.Longitude(80.0), _root_.com.histmersion.api.catalogue.Latitude(48.0))))
  case CentralEurope extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("central-europe"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Central Europe"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(5.9), _root_.com.histmersion.api.catalogue.Latitude(45.7), _root_.com.histmersion.api.catalogue.Longitude(24.2), _root_.com.histmersion.api.catalogue.Latitude(55.1))))
  case China extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("china"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("China"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(73.5), _root_.com.histmersion.api.catalogue.Latitude(18.0), _root_.com.histmersion.api.catalogue.Longitude(134.8), _root_.com.histmersion.api.catalogue.Latitude(53.6))))
  case Cyprus extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("cyprus"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Cyprus"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(32.2), _root_.com.histmersion.api.catalogue.Latitude(34.5), _root_.com.histmersion.api.catalogue.Longitude(34.7), _root_.com.histmersion.api.catalogue.Latitude(35.8))))
  case EasternEurope extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("eastern-europe"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Eastern Europe"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(22.1), _root_.com.histmersion.api.catalogue.Latitude(44.3), _root_.com.histmersion.api.catalogue.Longitude(40.3), _root_.com.histmersion.api.catalogue.Latitude(52.4))))
  case Egypt extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("egypt"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Egypt"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(24.7), _root_.com.histmersion.api.catalogue.Latitude(22.0), _root_.com.histmersion.api.catalogue.Longitude(35.8), _root_.com.histmersion.api.catalogue.Latitude(31.7))))
  case Iberia extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("iberia"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Iberia"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(-9.6), _root_.com.histmersion.api.catalogue.Latitude(36.0), _root_.com.histmersion.api.catalogue.Longitude(3.4), _root_.com.histmersion.api.catalogue.Latitude(43.8))))
  case Iran extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("iran"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Iran"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(44.0), _root_.com.histmersion.api.catalogue.Latitude(25.0), _root_.com.histmersion.api.catalogue.Longitude(63.4), _root_.com.histmersion.api.catalogue.Latitude(39.8))))
  case Italy extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("italy"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Italy"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(6.6), _root_.com.histmersion.api.catalogue.Latitude(36.6), _root_.com.histmersion.api.catalogue.Longitude(18.6), _root_.com.histmersion.api.catalogue.Latitude(47.1))))
  case LevantMesopotamia
      extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("levant-mesopotamia"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Levant and Mesopotamia"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(34.0), _root_.com.histmersion.api.catalogue.Latitude(29.0), _root_.com.histmersion.api.catalogue.Longitude(48.6), _root_.com.histmersion.api.catalogue.Latitude(37.5))))
  case Scandinavia extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("scandinavia"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Scandinavia"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(4.5), _root_.com.histmersion.api.catalogue.Latitude(54.5), _root_.com.histmersion.api.catalogue.Longitude(31.1), _root_.com.histmersion.api.catalogue.Latitude(71.2))))
  case SouthAsia extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("south-asia"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("South Asia"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(60.8), _root_.com.histmersion.api.catalogue.Latitude(5.9), _root_.com.histmersion.api.catalogue.Longitude(97.4), _root_.com.histmersion.api.catalogue.Latitude(37.1))))
  case SoutheastEurope extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("southeast-europe"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Southeast Europe"), Some(BBox(_root_.com.histmersion.api.catalogue.Longitude(18.8), _root_.com.histmersion.api.catalogue.Latitude(41.2), _root_.com.histmersion.api.catalogue.Longitude(29.8), _root_.com.histmersion.api.catalogue.Latitude(48.3))))
  case Broad extends PresetRegion(_root_.com.histmersion.api.catalogue.PresetRegion.Wire("broad"), _root_.com.histmersion.api.catalogue.PresetRegion.Label("Continent-wide definitions"), None)

object PresetRegion:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def fromWire(s: com.histmersion.api.catalogue.PresetRegion.FromWireS): Option[PresetRegion] = values.find(_.wire == _root_.com.histmersion.api.catalogue.PresetRegion.Wire((s).value.trim))

  given WireEnumValues[PresetRegion]:
    def all: Array[PresetRegion] = PresetRegion.values
    def wire(e: PresetRegion): _root_.com.histmersion.api.WireEnumValues.WireResult = _root_.com.histmersion.api.WireEnumValues.WireResult((e.wire).value)
    def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[PresetRegion] = PresetRegion.fromWire(_root_.com.histmersion.api.catalogue.PresetRegion.FromWireS((s).value))

  given io.circe.Encoder[PresetRegion] = WireEnum.encoder
  given io.circe.Decoder[PresetRegion] = WireEnum.decoder
  given Schema[PresetRegion] = WireEnum.schema
