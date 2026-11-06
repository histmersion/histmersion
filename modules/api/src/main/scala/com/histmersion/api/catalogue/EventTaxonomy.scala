package com.histmersion.api.catalogue
import com.histmersion.api.given
object EventTaxonomy:

  val Natural = "natural"
  val Human = "human"
  val groups: List[com.histmersion.api.catalogue.EventTaxonomy.Kind.Group] = List(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human))

  final case class Kind(kind: com.histmersion.api.catalogue.EventSummary.Kind, group: Kind.Group, label: Kind.Label, icon: Kind.Icon)

  object Kind:
    type Group = Group.T
    object Group extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Icon = Icon.T
    object Icon extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  val kinds: List[Kind] = List(
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Eruption), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("eruption"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("mountain")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Earthquake), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("earthquake"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("activity")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Tsunami), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("tsunami"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("waves")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Flood), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("flood"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("droplets")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Drought), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("drought"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("sun")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(ClimateEvent), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("climate event"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("thermometer")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Epidemic), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("epidemic"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("skull")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Famine), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("famine"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("wheat")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(NaturalGeneric), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("natural disaster"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("cloud-lightning")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Battle), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("battle"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("swords")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(NavalBattle), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("naval battle"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("sailboat")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Siege), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("siege"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("castle")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(War), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("war"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("shield")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Revolt), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("revolt"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("megaphone")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Treaty), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("treaty"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("scroll")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(PolityFormation), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("polity formation"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("crown")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Collapse), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("collapse"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("trending-down")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Destruction), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("destruction"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("flame")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Migration), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("migration"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("footprints")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Invasion), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("invasion"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("arrow-big-right")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(TechChange), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("technological change"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("lightbulb")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(CulturalShift), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("cultural shift"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("sparkles")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Era), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("era"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("hourglass")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Construction), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("construction"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("hammer")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Founding), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("founding"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("flag")),
    Kind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(HumanGeneric), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("event"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("circle-dot"))
  )

  private val byKind: Map[com.histmersion.api.catalogue.EventSummary.Kind, Kind] = kinds.map(k => k.kind -> k).toMap

  private val aliases: Map[com.histmersion.api.catalogue.EventSummary.Kind, com.histmersion.api.catalogue.EventSummary.Kind] = Map(
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("rebellion") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(Revolt),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("uprising") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(Revolt),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("revolt") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(Revolt),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("naval_battle") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(NavalBattle),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("sea_battle") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(NavalBattle),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("volcanic_eruption") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(Eruption),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("plague") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(Epidemic),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("pandemic") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(Epidemic),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("climate_change") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(ClimateEvent),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("climatic_event") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(ClimateEvent),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("reign") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(Era),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("period") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(Era),
    _root_.com.histmersion.api.catalogue.EventSummary.Kind("diplomatic_era") -> _root_.com.histmersion.api.catalogue.EventSummary.Kind(Era)
  )

  def canonical(kind: com.histmersion.api.catalogue.EventSummary.Kind): com.histmersion.api.catalogue.EventSummary.Kind =
    val k = EventScale.normKind(kind)
    aliases.get(k).orElse(Option.when(k.value.endsWith("_era"))(_root_.com.histmersion.api.catalogue.EventSummary.Kind(Era))).getOrElse(k)

  val labelRules: List[(com.histmersion.api.ItemKind.Label.Pattern, com.histmersion.api.catalogue.EventSummary.Kind)] = List(
    (_root_.com.histmersion.api.ItemKind.Label.Pattern("erupt|volcan"), _root_.com.histmersion.api.catalogue.EventSummary.Kind(Eruption)),
    (_root_.com.histmersion.api.ItemKind.Label.Pattern("earthquake|seism|quake"), _root_.com.histmersion.api.catalogue.EventSummary.Kind(Earthquake)),
    (_root_.com.histmersion.api.ItemKind.Label.Pattern("tsunami"), _root_.com.histmersion.api.catalogue.EventSummary.Kind(Tsunami)),
    (_root_.com.histmersion.api.ItemKind.Label.Pattern("flood|deluge"), _root_.com.histmersion.api.catalogue.EventSummary.Kind(Flood)),
    (_root_.com.histmersion.api.ItemKind.Label.Pattern("drought|aridi"), _root_.com.histmersion.api.catalogue.EventSummary.Kind(Drought)),
    (_root_.com.histmersion.api.ItemKind.Label.Pattern("kiloyear|climat|cooling|warming"), _root_.com.histmersion.api.catalogue.EventSummary.Kind(ClimateEvent)),
    (_root_.com.histmersion.api.ItemKind.Label.Pattern("plague|epidemic|pandemic|pestilence"), _root_.com.histmersion.api.catalogue.EventSummary.Kind(Epidemic)),
    (_root_.com.histmersion.api.ItemKind.Label.Pattern("famine"), _root_.com.histmersion.api.catalogue.EventSummary.Kind(Famine))
  )

  def refine(kind: com.histmersion.api.catalogue.EventSummary.Kind, label: com.histmersion.api.catalogue.EventSummary.Label): com.histmersion.api.catalogue.EventSummary.Kind =
    val k = canonical(kind)
    if k.value != NaturalGeneric then k
    else
      val l = (label).value.toLowerCase(java.util.Locale.ROOT)
      labelRules.collectFirst { case (re, sub) if (re).value.r.findFirstIn(l).isDefined => sub }.getOrElse(k)

  def of(kind: com.histmersion.api.catalogue.EventSummary.Kind): Kind =
    val k = canonical(kind)
    byKind.getOrElse(k, byKind(_root_.com.histmersion.api.catalogue.EventSummary.Kind(HumanGeneric)).copy(kind = k, label = _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label((k).value.replace('_', ' '))))

  def group(kind: com.histmersion.api.catalogue.EventSummary.Kind): com.histmersion.api.catalogue.EventTaxonomy.Kind.Group = of(kind).group
  def icon(kind: com.histmersion.api.catalogue.EventSummary.Kind): com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon = of(kind).icon
  def isNatural(kind: com.histmersion.api.catalogue.EventSummary.Kind): Boolean = group(kind) == _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural)

  def matches(filter: com.histmersion.api.catalogue.EventTaxonomy.KindCondition.Filter, kind: com.histmersion.api.catalogue.EventSummary.Kind): Boolean =
    val f = (filter).value.trim.toLowerCase(java.util.Locale.ROOT)
    f.isEmpty || f == group(kind).value || canonical(_root_.com.histmersion.api.catalogue.EventSummary.Kind(f)) == canonical(kind)

  final case class KindCondition(kinds: List[com.histmersion.api.catalogue.EventSummary.Kind], negate: KindCondition.Negate)

  object KindCondition:
    type Filter = Filter.T
    object Filter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Negate = Negate.T
    object Negate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def condition(filter: Option[com.histmersion.api.catalogue.EventTaxonomy.KindCondition.Filter]): Option[KindCondition] =
    filter.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty).map { f =>
      val natural = (kinds.filter(_.group == _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural)).map(_.kind) ++
        aliases.collect { case (a, v) if byKind.get(v).exists(_.group == _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural)) => a }).distinct.sortBy(_.value)
      if f == Natural then KindCondition(natural, negate = _root_.com.histmersion.api.catalogue.EventTaxonomy.KindCondition.Negate(false))
      else if f == Human then KindCondition(natural, negate = _root_.com.histmersion.api.catalogue.EventTaxonomy.KindCondition.Negate(true))
      else
        val c = canonical(_root_.com.histmersion.api.catalogue.EventSummary.Kind(f))
        KindCondition((c :: aliases.collect { case (a, v) if v == c => a }.toList).distinct.sortBy(_.value), negate = _root_.com.histmersion.api.catalogue.EventTaxonomy.KindCondition.Negate(false))
    }
