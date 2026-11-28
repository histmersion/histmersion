package com.histmersion
package api.catalogue

import com.histmersion.api.catalogue.eventtaxonomy.KindCondition
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ItemKind
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object EventTaxonomy:

  val Natural = "natural"
  val Human = "human"
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val groups: List[api.catalogue.eventtaxonomy.Genre.Group] =
    List(api.catalogue.eventtaxonomy.Genre.Group(Natural), api.catalogue.eventtaxonomy.Genre.Group(Human))

  val Eruption = "eruption"
  val Earthquake = "earthquake"
  val Tsunami = "tsunami"
  val Flood = "flood"
  val Drought = "drought"
  val ClimateEvent = "climate_event"
  val Epidemic = "epidemic"
  val Famine = "famine"
  val NaturalGeneric = "natural_disaster"
  val Battle = "battle"
  val NavalBattle = "naval_battle"
  val Siege = "siege"
  val War = "war"
  val Revolt = "revolt"
  val Treaty = "treaty"
  val PolityFormation = "polity_formation"
  val Collapse = "collapse"
  val Destruction = "destruction"
  val Migration = "migration"
  val Invasion = "invasion"
  val TechChange = "technological_change"
  val CulturalShift = "cultural_shift"
  val Era = "era"
  val Construction = "construction"
  val Founding = "founding"
  val HumanGeneric = "event"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val kinds: List[api.catalogue.eventtaxonomy.Genre] = List(
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Eruption),
      api.catalogue.eventtaxonomy.Genre.Group(Natural),
      api.catalogue.eventtaxonomy.Genre.Label("eruption"),
      api.catalogue.eventtaxonomy.Genre.Icon("mountain")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Earthquake),
      api.catalogue.eventtaxonomy.Genre.Group(Natural),
      api.catalogue.eventtaxonomy.Genre.Label("earthquake"),
      api.catalogue.eventtaxonomy.Genre.Icon("activity")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Tsunami),
      api.catalogue.eventtaxonomy.Genre.Group(Natural),
      api.catalogue.eventtaxonomy.Genre.Label("tsunami"),
      api.catalogue.eventtaxonomy.Genre.Icon("waves")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Flood),
      api.catalogue.eventtaxonomy.Genre.Group(Natural),
      api.catalogue.eventtaxonomy.Genre.Label("flood"),
      api.catalogue.eventtaxonomy.Genre.Icon("droplets")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Drought),
      api.catalogue.eventtaxonomy.Genre.Group(Natural),
      api.catalogue.eventtaxonomy.Genre.Label("drought"),
      api.catalogue.eventtaxonomy.Genre.Icon("sun")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(ClimateEvent),
      api.catalogue.eventtaxonomy.Genre.Group(Natural),
      api.catalogue.eventtaxonomy.Genre.Label("climate event"),
      api.catalogue.eventtaxonomy.Genre.Icon("thermometer")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Epidemic),
      api.catalogue.eventtaxonomy.Genre.Group(Natural),
      api.catalogue.eventtaxonomy.Genre.Label("epidemic"),
      api.catalogue.eventtaxonomy.Genre.Icon("skull")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Famine),
      api.catalogue.eventtaxonomy.Genre.Group(Natural),
      api.catalogue.eventtaxonomy.Genre.Label("famine"),
      api.catalogue.eventtaxonomy.Genre.Icon("wheat")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(NaturalGeneric),
      api.catalogue.eventtaxonomy.Genre.Group(Natural),
      api.catalogue.eventtaxonomy.Genre.Label("natural disaster"),
      api.catalogue.eventtaxonomy.Genre.Icon("cloud-lightning")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Battle),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("battle"),
      api.catalogue.eventtaxonomy.Genre.Icon("swords")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(NavalBattle),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("naval battle"),
      api.catalogue.eventtaxonomy.Genre.Icon("sailboat")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Siege),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("siege"),
      api.catalogue.eventtaxonomy.Genre.Icon("castle")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(War),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("war"),
      api.catalogue.eventtaxonomy.Genre.Icon("shield")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Revolt),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("revolt"),
      api.catalogue.eventtaxonomy.Genre.Icon("megaphone")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Treaty),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("treaty"),
      api.catalogue.eventtaxonomy.Genre.Icon("scroll")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(PolityFormation),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("polity formation"),
      api.catalogue.eventtaxonomy.Genre.Icon("crown")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Collapse),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("collapse"),
      api.catalogue.eventtaxonomy.Genre.Icon("trending-down")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Destruction),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("destruction"),
      api.catalogue.eventtaxonomy.Genre.Icon("flame")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Migration),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("migration"),
      api.catalogue.eventtaxonomy.Genre.Icon("footprints")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Invasion),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("invasion"),
      api.catalogue.eventtaxonomy.Genre.Icon("arrow-big-right")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(TechChange),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("technological change"),
      api.catalogue.eventtaxonomy.Genre.Icon("lightbulb")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(CulturalShift),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("cultural shift"),
      api.catalogue.eventtaxonomy.Genre.Icon("sparkles")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Era),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("era"),
      api.catalogue.eventtaxonomy.Genre.Icon("hourglass")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Construction),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("construction"),
      api.catalogue.eventtaxonomy.Genre.Icon("hammer")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(Founding),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("founding"),
      api.catalogue.eventtaxonomy.Genre.Icon("flag")
    ),
    api.catalogue.eventtaxonomy.Genre(
      EventSummary.Kind(HumanGeneric),
      api.catalogue.eventtaxonomy.Genre.Group(Human),
      api.catalogue.eventtaxonomy.Genre.Label("event"),
      api.catalogue.eventtaxonomy.Genre.Icon("circle-dot")
    )
  )

  private val byKind: Map[EventSummary.Kind, api.catalogue.eventtaxonomy.Genre] = kinds.map(k => k.kind -> k).toMap

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val aliases: Map[EventSummary.Kind, EventSummary.Kind] = Map(
    EventSummary.Kind("rebellion") -> EventSummary.Kind(Revolt),
    EventSummary.Kind("uprising") -> EventSummary.Kind(Revolt),
    EventSummary.Kind("revolt") -> EventSummary.Kind(Revolt),
    EventSummary.Kind("naval_battle") -> EventSummary.Kind(NavalBattle),
    EventSummary.Kind("sea_battle") -> EventSummary.Kind(NavalBattle),
    EventSummary.Kind("volcanic_eruption") -> EventSummary.Kind(Eruption),
    EventSummary.Kind("plague") -> EventSummary.Kind(Epidemic),
    EventSummary.Kind("pandemic") -> EventSummary.Kind(Epidemic),
    EventSummary.Kind("climate_change") -> EventSummary.Kind(ClimateEvent),
    EventSummary.Kind("climatic_event") -> EventSummary.Kind(ClimateEvent),
    EventSummary.Kind("reign") -> EventSummary.Kind(Era),
    EventSummary.Kind("period") -> EventSummary.Kind(Era),
    EventSummary.Kind("diplomatic_era") -> EventSummary.Kind(Era)
  )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def canonical(kind: EventSummary.Kind): EventSummary.Kind =
    val k = EventScale.normKind(kind)
    aliases.get(k).orElse(Option.when(k.value.endsWith("_era"))(EventSummary.Kind(Era))).getOrElse(k)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val labelRules: List[(ItemKind.Label.Pattern, EventSummary.Kind)] = List(
    (ItemKind.Label.Pattern("erupt|volcan"), EventSummary.Kind(Eruption)),
    (ItemKind.Label.Pattern("earthquake|seism|quake"), EventSummary.Kind(Earthquake)),
    (ItemKind.Label.Pattern("tsunami"), EventSummary.Kind(Tsunami)),
    (ItemKind.Label.Pattern("flood|deluge"), EventSummary.Kind(Flood)),
    (ItemKind.Label.Pattern("drought|aridi"), EventSummary.Kind(Drought)),
    (ItemKind.Label.Pattern("kiloyear|climat|cooling|warming"), EventSummary.Kind(ClimateEvent)),
    (ItemKind.Label.Pattern("plague|epidemic|pandemic|pestilence"), EventSummary.Kind(Epidemic)),
    (ItemKind.Label.Pattern("famine"), EventSummary.Kind(Famine))
  )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def refine(kind: EventSummary.Kind, label: EventSummary.Label): EventSummary.Kind =
    val k = canonical(kind)
    if k.value != NaturalGeneric then k
    else
      val l = (label).value.toLowerCase(java.util.Locale.ROOT)
      labelRules.collectFirst { case (re, sub) if (re).value.r.findFirstIn(l).isDefined => sub }.getOrElse(k)

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(kind: EventSummary.Kind): api.catalogue.eventtaxonomy.Genre =
    val k = canonical(kind)
    byKind.getOrElse(
      k,
      byKind(EventSummary.Kind(HumanGeneric))
        .copy(kind = k, label = api.catalogue.eventtaxonomy.Genre.Label((k).value.replace('_', ' ')))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def group(kind: EventSummary.Kind): api.catalogue.eventtaxonomy.Genre.Group = of(kind).group
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def icon(kind: EventSummary.Kind): api.catalogue.eventtaxonomy.Genre.Icon = of(kind).icon
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isNatural(kind: EventSummary.Kind): Boolean = group(kind) == api.catalogue.eventtaxonomy.Genre.Group(Natural)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def condition(filter: Option[KindCondition.Filter]): Option[KindCondition] =
    filter.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty).map { f =>
      val natural = (kinds.filter(_.group == api.catalogue.eventtaxonomy.Genre.Group(Natural)).map(_.kind) ++
        aliases.collect {
          case (a, v) if byKind.get(v).exists(_.group == api.catalogue.eventtaxonomy.Genre.Group(Natural)) => a
        }).distinct.sorted
      if f == Natural then KindCondition(natural, negate = KindCondition.Negate.Affirm)
      else if f == Human then KindCondition(natural, negate = KindCondition.Negate.Negate)
      else
        val c = canonical(EventSummary.Kind(f))
        KindCondition(
          (c :: aliases.collect { case (a, v) if v == c => a }.toList).distinct.sorted,
          negate = KindCondition.Negate.Affirm
        )
    }
