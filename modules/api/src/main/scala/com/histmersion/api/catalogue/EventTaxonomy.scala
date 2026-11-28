package com.histmersion
package api.catalogue
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
  val groups: List[Kind.Group] = List(Kind.Group(Natural), Kind.Group(Human))

  final case class Kind(kind: EventSummary.Kind, group: Kind.Group, label: Kind.Label, icon: Kind.Icon)

  object Kind:
    type Group = Group.T
    object Group extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Icon = Icon.T
    object Icon extends Newtype[String, Pure]

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
  val kinds: List[Kind] = List(
    Kind(EventSummary.Kind(Eruption), Kind.Group(Natural), Kind.Label("eruption"), Kind.Icon("mountain")),
    Kind(EventSummary.Kind(Earthquake), Kind.Group(Natural), Kind.Label("earthquake"), Kind.Icon("activity")),
    Kind(EventSummary.Kind(Tsunami), Kind.Group(Natural), Kind.Label("tsunami"), Kind.Icon("waves")),
    Kind(EventSummary.Kind(Flood), Kind.Group(Natural), Kind.Label("flood"), Kind.Icon("droplets")),
    Kind(EventSummary.Kind(Drought), Kind.Group(Natural), Kind.Label("drought"), Kind.Icon("sun")),
    Kind(EventSummary.Kind(ClimateEvent), Kind.Group(Natural), Kind.Label("climate event"), Kind.Icon("thermometer")),
    Kind(EventSummary.Kind(Epidemic), Kind.Group(Natural), Kind.Label("epidemic"), Kind.Icon("skull")),
    Kind(EventSummary.Kind(Famine), Kind.Group(Natural), Kind.Label("famine"), Kind.Icon("wheat")),
    Kind(
      EventSummary.Kind(NaturalGeneric),
      Kind.Group(Natural),
      Kind.Label("natural disaster"),
      Kind.Icon("cloud-lightning")
    ),
    Kind(EventSummary.Kind(Battle), Kind.Group(Human), Kind.Label("battle"), Kind.Icon("swords")),
    Kind(EventSummary.Kind(NavalBattle), Kind.Group(Human), Kind.Label("naval battle"), Kind.Icon("sailboat")),
    Kind(EventSummary.Kind(Siege), Kind.Group(Human), Kind.Label("siege"), Kind.Icon("castle")),
    Kind(EventSummary.Kind(War), Kind.Group(Human), Kind.Label("war"), Kind.Icon("shield")),
    Kind(EventSummary.Kind(Revolt), Kind.Group(Human), Kind.Label("revolt"), Kind.Icon("megaphone")),
    Kind(EventSummary.Kind(Treaty), Kind.Group(Human), Kind.Label("treaty"), Kind.Icon("scroll")),
    Kind(EventSummary.Kind(PolityFormation), Kind.Group(Human), Kind.Label("polity formation"), Kind.Icon("crown")),
    Kind(EventSummary.Kind(Collapse), Kind.Group(Human), Kind.Label("collapse"), Kind.Icon("trending-down")),
    Kind(EventSummary.Kind(Destruction), Kind.Group(Human), Kind.Label("destruction"), Kind.Icon("flame")),
    Kind(EventSummary.Kind(Migration), Kind.Group(Human), Kind.Label("migration"), Kind.Icon("footprints")),
    Kind(EventSummary.Kind(Invasion), Kind.Group(Human), Kind.Label("invasion"), Kind.Icon("arrow-big-right")),
    Kind(EventSummary.Kind(TechChange), Kind.Group(Human), Kind.Label("technological change"), Kind.Icon("lightbulb")),
    Kind(EventSummary.Kind(CulturalShift), Kind.Group(Human), Kind.Label("cultural shift"), Kind.Icon("sparkles")),
    Kind(EventSummary.Kind(Era), Kind.Group(Human), Kind.Label("era"), Kind.Icon("hourglass")),
    Kind(EventSummary.Kind(Construction), Kind.Group(Human), Kind.Label("construction"), Kind.Icon("hammer")),
    Kind(EventSummary.Kind(Founding), Kind.Group(Human), Kind.Label("founding"), Kind.Icon("flag")),
    Kind(EventSummary.Kind(HumanGeneric), Kind.Group(Human), Kind.Label("event"), Kind.Icon("circle-dot"))
  )

  private val byKind: Map[EventSummary.Kind, Kind] = kinds.map(k => k.kind -> k).toMap

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
  def of(kind: EventSummary.Kind): Kind =
    val k = canonical(kind)
    byKind.getOrElse(
      k,
      byKind(EventSummary.Kind(HumanGeneric)).copy(kind = k, label = Kind.Label((k).value.replace('_', ' ')))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def group(kind: EventSummary.Kind): Kind.Group = of(kind).group
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def icon(kind: EventSummary.Kind): Kind.Icon = of(kind).icon
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isNatural(kind: EventSummary.Kind): Boolean = group(kind) == Kind.Group(Natural)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def matches(filter: KindCondition.Filter, kind: EventSummary.Kind): Boolean =
    val f = (filter).value.trim.toLowerCase(java.util.Locale.ROOT)
    f.isEmpty || f == group(kind).value || canonical(EventSummary.Kind(f)) == canonical(kind)

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class KindCondition(kinds: List[EventSummary.Kind], negate: KindCondition.Negate)

  object KindCondition:
    type Filter = Filter.T
    object Filter extends Newtype[String, Pure]
    enum Negate derives CanEqual:
      case Negate, Affirm
    object Negate extends TwoStates[Negate]:
      extension (negate: Negate) def negate: Boolean = negate == Negate

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def condition(filter: Option[KindCondition.Filter]): Option[KindCondition] =
    filter.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty).map { f =>
      val natural = (kinds.filter(_.group == Kind.Group(Natural)).map(_.kind) ++
        aliases.collect { case (a, v) if byKind.get(v).exists(_.group == Kind.Group(Natural)) => a }).distinct.sorted
      if f == Natural then KindCondition(natural, negate = KindCondition.Negate.Affirm)
      else if f == Human then KindCondition(natural, negate = KindCondition.Negate.Negate)
      else
        val c = canonical(EventSummary.Kind(f))
        KindCondition(
          (c :: aliases.collect { case (a, v) if v == c => a }.toList).distinct.sorted,
          negate = KindCondition.Negate.Affirm
        )
    }
