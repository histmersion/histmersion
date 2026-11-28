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
  val groups: List[api.catalogue.eventtaxonomy.Kind.Group] =
    List(api.catalogue.eventtaxonomy.Kind.Group(Natural), api.catalogue.eventtaxonomy.Kind.Group(Human))

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
  val kinds: List[api.catalogue.eventtaxonomy.Kind] = List(
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Eruption),
      api.catalogue.eventtaxonomy.Kind.Group(Natural),
      api.catalogue.eventtaxonomy.Kind.Label("eruption"),
      api.catalogue.eventtaxonomy.Kind.Icon("mountain")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Earthquake),
      api.catalogue.eventtaxonomy.Kind.Group(Natural),
      api.catalogue.eventtaxonomy.Kind.Label("earthquake"),
      api.catalogue.eventtaxonomy.Kind.Icon("activity")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Tsunami),
      api.catalogue.eventtaxonomy.Kind.Group(Natural),
      api.catalogue.eventtaxonomy.Kind.Label("tsunami"),
      api.catalogue.eventtaxonomy.Kind.Icon("waves")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Flood),
      api.catalogue.eventtaxonomy.Kind.Group(Natural),
      api.catalogue.eventtaxonomy.Kind.Label("flood"),
      api.catalogue.eventtaxonomy.Kind.Icon("droplets")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Drought),
      api.catalogue.eventtaxonomy.Kind.Group(Natural),
      api.catalogue.eventtaxonomy.Kind.Label("drought"),
      api.catalogue.eventtaxonomy.Kind.Icon("sun")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(ClimateEvent),
      api.catalogue.eventtaxonomy.Kind.Group(Natural),
      api.catalogue.eventtaxonomy.Kind.Label("climate event"),
      api.catalogue.eventtaxonomy.Kind.Icon("thermometer")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Epidemic),
      api.catalogue.eventtaxonomy.Kind.Group(Natural),
      api.catalogue.eventtaxonomy.Kind.Label("epidemic"),
      api.catalogue.eventtaxonomy.Kind.Icon("skull")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Famine),
      api.catalogue.eventtaxonomy.Kind.Group(Natural),
      api.catalogue.eventtaxonomy.Kind.Label("famine"),
      api.catalogue.eventtaxonomy.Kind.Icon("wheat")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(NaturalGeneric),
      api.catalogue.eventtaxonomy.Kind.Group(Natural),
      api.catalogue.eventtaxonomy.Kind.Label("natural disaster"),
      api.catalogue.eventtaxonomy.Kind.Icon("cloud-lightning")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Battle),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("battle"),
      api.catalogue.eventtaxonomy.Kind.Icon("swords")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(NavalBattle),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("naval battle"),
      api.catalogue.eventtaxonomy.Kind.Icon("sailboat")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Siege),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("siege"),
      api.catalogue.eventtaxonomy.Kind.Icon("castle")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(War),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("war"),
      api.catalogue.eventtaxonomy.Kind.Icon("shield")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Revolt),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("revolt"),
      api.catalogue.eventtaxonomy.Kind.Icon("megaphone")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Treaty),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("treaty"),
      api.catalogue.eventtaxonomy.Kind.Icon("scroll")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(PolityFormation),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("polity formation"),
      api.catalogue.eventtaxonomy.Kind.Icon("crown")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Collapse),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("collapse"),
      api.catalogue.eventtaxonomy.Kind.Icon("trending-down")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Destruction),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("destruction"),
      api.catalogue.eventtaxonomy.Kind.Icon("flame")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Migration),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("migration"),
      api.catalogue.eventtaxonomy.Kind.Icon("footprints")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Invasion),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("invasion"),
      api.catalogue.eventtaxonomy.Kind.Icon("arrow-big-right")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(TechChange),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("technological change"),
      api.catalogue.eventtaxonomy.Kind.Icon("lightbulb")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(CulturalShift),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("cultural shift"),
      api.catalogue.eventtaxonomy.Kind.Icon("sparkles")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Era),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("era"),
      api.catalogue.eventtaxonomy.Kind.Icon("hourglass")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Construction),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("construction"),
      api.catalogue.eventtaxonomy.Kind.Icon("hammer")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(Founding),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("founding"),
      api.catalogue.eventtaxonomy.Kind.Icon("flag")
    ),
    api.catalogue.eventtaxonomy.Kind(
      EventSummary.Kind(HumanGeneric),
      api.catalogue.eventtaxonomy.Kind.Group(Human),
      api.catalogue.eventtaxonomy.Kind.Label("event"),
      api.catalogue.eventtaxonomy.Kind.Icon("circle-dot")
    )
  )

  private val byKind: Map[EventSummary.Kind, api.catalogue.eventtaxonomy.Kind] = kinds.map(k => k.kind -> k).toMap

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
  def of(kind: EventSummary.Kind): api.catalogue.eventtaxonomy.Kind =
    val k = canonical(kind)
    byKind.getOrElse(
      k,
      byKind(EventSummary.Kind(HumanGeneric))
        .copy(kind = k, label = api.catalogue.eventtaxonomy.Kind.Label((k).value.replace('_', ' ')))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def group(kind: EventSummary.Kind): api.catalogue.eventtaxonomy.Kind.Group = of(kind).group
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def icon(kind: EventSummary.Kind): api.catalogue.eventtaxonomy.Kind.Icon = of(kind).icon
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isNatural(kind: EventSummary.Kind): Boolean = group(kind) == api.catalogue.eventtaxonomy.Kind.Group(Natural)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def condition(filter: Option[KindCondition.Filter]): Option[KindCondition] =
    filter.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty).map { f =>
      val natural = (kinds.filter(_.group == api.catalogue.eventtaxonomy.Kind.Group(Natural)).map(_.kind) ++
        aliases.collect {
          case (a, v) if byKind.get(v).exists(_.group == api.catalogue.eventtaxonomy.Kind.Group(Natural)) => a
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
