package com.histmersion
package api.catalogue
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.{BBox, WireEnum}
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

enum PresetRegion(val wire: PresetRegion.Wire, val label: PresetRegion.Label, val box: Option[BBox]) derives CanEqual:
  case Aegean
      extends PresetRegion(
        PresetRegion.Wire("aegean"),
        PresetRegion.Label("Aegean"),
        Some(BBox(Longitude(19.5), Latitude(34.5), Longitude(29.5), Latitude(41.5)))
      )
  case Anatolia
      extends PresetRegion(
        PresetRegion.Wire("anatolia"),
        PresetRegion.Label("Anatolia"),
        Some(BBox(Longitude(26.0), Latitude(35.8), Longitude(45.0), Latitude(42.2)))
      )
  case BritainIreland
      extends PresetRegion(
        PresetRegion.Wire("britain-ireland"),
        PresetRegion.Label("Britain and Ireland"),
        Some(BBox(Longitude(-10.7), Latitude(49.8), Longitude(1.8), Latitude(60.9)))
      )
  case CentralAsia
      extends PresetRegion(
        PresetRegion.Wire("central-asia"),
        PresetRegion.Label("Central Asia"),
        Some(BBox(Longitude(52.0), Latitude(35.0), Longitude(80.0), Latitude(48.0)))
      )
  case CentralEurope
      extends PresetRegion(
        PresetRegion.Wire("central-europe"),
        PresetRegion.Label("Central Europe"),
        Some(BBox(Longitude(5.9), Latitude(45.7), Longitude(24.2), Latitude(55.1)))
      )
  case China
      extends PresetRegion(
        PresetRegion.Wire("china"),
        PresetRegion.Label("China"),
        Some(BBox(Longitude(73.5), Latitude(18.0), Longitude(134.8), Latitude(53.6)))
      )
  case Cyprus
      extends PresetRegion(
        PresetRegion.Wire("cyprus"),
        PresetRegion.Label("Cyprus"),
        Some(BBox(Longitude(32.2), Latitude(34.5), Longitude(34.7), Latitude(35.8)))
      )
  case EasternEurope
      extends PresetRegion(
        PresetRegion.Wire("eastern-europe"),
        PresetRegion.Label("Eastern Europe"),
        Some(BBox(Longitude(22.1), Latitude(44.3), Longitude(40.3), Latitude(52.4)))
      )
  case Egypt
      extends PresetRegion(
        PresetRegion.Wire("egypt"),
        PresetRegion.Label("Egypt"),
        Some(BBox(Longitude(24.7), Latitude(22.0), Longitude(35.8), Latitude(31.7)))
      )
  case Iberia
      extends PresetRegion(
        PresetRegion.Wire("iberia"),
        PresetRegion.Label("Iberia"),
        Some(BBox(Longitude(-9.6), Latitude(36.0), Longitude(3.4), Latitude(43.8)))
      )
  case Iran
      extends PresetRegion(
        PresetRegion.Wire("iran"),
        PresetRegion.Label("Iran"),
        Some(BBox(Longitude(44.0), Latitude(25.0), Longitude(63.4), Latitude(39.8)))
      )
  case Italy
      extends PresetRegion(
        PresetRegion.Wire("italy"),
        PresetRegion.Label("Italy"),
        Some(BBox(Longitude(6.6), Latitude(36.6), Longitude(18.6), Latitude(47.1)))
      )
  case LevantMesopotamia
      extends PresetRegion(
        PresetRegion.Wire("levant-mesopotamia"),
        PresetRegion.Label("Levant and Mesopotamia"),
        Some(BBox(Longitude(34.0), Latitude(29.0), Longitude(48.6), Latitude(37.5)))
      )
  case Scandinavia
      extends PresetRegion(
        PresetRegion.Wire("scandinavia"),
        PresetRegion.Label("Scandinavia"),
        Some(BBox(Longitude(4.5), Latitude(54.5), Longitude(31.1), Latitude(71.2)))
      )
  case SouthAsia
      extends PresetRegion(
        PresetRegion.Wire("south-asia"),
        PresetRegion.Label("South Asia"),
        Some(BBox(Longitude(60.8), Latitude(5.9), Longitude(97.4), Latitude(37.1)))
      )
  case SoutheastEurope
      extends PresetRegion(
        PresetRegion.Wire("southeast-europe"),
        PresetRegion.Label("Southeast Europe"),
        Some(BBox(Longitude(18.8), Latitude(41.2), Longitude(29.8), Latitude(48.3)))
      )
  case Broad extends PresetRegion(PresetRegion.Wire("broad"), PresetRegion.Label("Continent-wide definitions"), None)

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object PresetRegion:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fromWire(s: Wire): Option[PresetRegion] = values.find(_.wire == Wire((s).value.trim))

  given WireEnumValues[PresetRegion]:
    def all: Array[PresetRegion] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: PresetRegion): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
    def fromWire(s: WireEnumValues.Wire): Option[PresetRegion] = PresetRegion.fromWire(Wire((s).value))

  given com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[PresetRegion] = WireEnum.codec
  given Schema[PresetRegion] = WireEnum.schema

  type PeriodSource = PeriodSource.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PeriodSource extends Newtype[String, Pure]

  type PeriodoId = PeriodoId.T
  object PeriodoId extends Newtype[String, Pure]

  type Share = Share.T
  object Share extends Newtype[Double, Pure]

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  final case class Group(region: PresetRegion, presets: List[HistoricalPeriod], inView: Boolean)

  val MinShare: Share = Share(0.5)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def share(view: BBox, box: BBox): Share =
    val area = (view.maxLon.value - view.minLon.value) * (view.maxLat.value - view.minLat.value)
    if view.crossesAntimeridian || area <= 0 then Share(0.0)
    else
      val w = math.min(view.maxLon.value, box.maxLon.value) - math.max(view.minLon.value, box.minLon.value)
      val h = math.min(view.maxLat.value, box.maxLat.value) - math.max(view.minLat.value, box.minLat.value)
      if w <= 0 || h <= 0 then Share(0.0) else Share((w * h) / area)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def groups(ps: List[HistoricalPeriod], view: Option[BBox] = None): List[Group] =
    val byRegion = ps.flatMap(p => p.preset.map(_ -> p)).groupMap(_._1)(_._2)
    def cover(r: PresetRegion) = (for v <- view; b <- r.box yield share(v, b)).getOrElse(Share(0.0))
    values.toList
      .flatMap { r =>
        byRegion
          .get(r)
          .map(rows =>
            Group(
              r,
              rows.sortBy(p => (p.period.start.value: Long, p.period.end.value: Long, p.label.value: String)),
              cover(r) >= MinShare
            )
          )
      }
      .sortBy(g => if g.inView then (0, -cover(g.region).value) else (1, 0.0))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def source(p: HistoricalPeriod): Option[PeriodSource] =
    p.authority.map(a =>
      a.value.split(" · ", 2) match
        case Array(id, rest) if id.startsWith("PeriodO ") => PeriodSource(rest)
        case _                                            => PeriodSource(a.value)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def periodoId(p: HistoricalPeriod): Option[PeriodoId] =
    p.authority.flatMap(a =>
      Option.when(a.value.startsWith("PeriodO "))(PeriodoId(a.value.drop("PeriodO ".length).takeWhile(!_.isWhitespace)))
    )
