package com.histmersion.api.catalogue
import com.histmersion.api.given
object EventTaxonomy:

  type Groups = Groups.T
  object Groups extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByKindPart1 = ByKindPart1.T
  object ByKindPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AliasesPart1 = AliasesPart1.T
  object AliasesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AliasesPart2 = AliasesPart2.T
  object AliasesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalResult = CanonicalResult.T
  object CanonicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalKind = CanonicalKind.T
  object CanonicalKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelRulesPart1 = LabelRulesPart1.T
  object LabelRulesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelRulesPart2 = LabelRulesPart2.T
  object LabelRulesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefineResult = RefineResult.T
  object RefineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefineKind = RefineKind.T
  object RefineKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefineLabel = RefineLabel.T
  object RefineLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfKind = OfKind.T
  object OfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupResult = GroupResult.T
  object GroupResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupKind = GroupKind.T
  object GroupKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IconResult = IconResult.T
  object IconResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IconKind = IconKind.T
  object IconKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsNaturalResult = IsNaturalResult.T
  object IsNaturalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsNaturalKind = IsNaturalKind.T
  object IsNaturalKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesResult = MatchesResult.T
  object MatchesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesFilter = MatchesFilter.T
  object MatchesFilter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesKind = MatchesKind.T
  object MatchesKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConditionFilter = ConditionFilter.T
  object ConditionFilter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Natural = "natural"
  val Human = "human"
  val groups: List[com.histmersion.api.catalogue.EventTaxonomy.Groups] = List(_root_.com.histmersion.api.catalogue.EventTaxonomy.Groups(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Groups(Human))

  final case class Kind(kind: Kind.Value, group: Kind.Group, label: Kind.Label, icon: Kind.Icon)

  object Kind:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Eruption), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("eruption"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("mountain")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Earthquake), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("earthquake"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("activity")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Tsunami), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("tsunami"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("waves")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Flood), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("flood"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("droplets")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Drought), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("drought"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("sun")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(ClimateEvent), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("climate event"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("thermometer")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Epidemic), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("epidemic"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("skull")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Famine), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("famine"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("wheat")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(NaturalGeneric), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Natural), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("natural disaster"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("cloud-lightning")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Battle), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("battle"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("swords")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(NavalBattle), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("naval battle"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("sailboat")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Siege), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("siege"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("castle")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(War), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("war"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("shield")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Revolt), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("revolt"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("megaphone")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Treaty), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("treaty"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("scroll")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(PolityFormation), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("polity formation"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("crown")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Collapse), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("collapse"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("trending-down")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Destruction), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("destruction"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("flame")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Migration), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("migration"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("footprints")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Invasion), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("invasion"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("arrow-big-right")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(TechChange), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("technological change"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("lightbulb")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(CulturalShift), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("cultural shift"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("sparkles")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Era), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("era"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("hourglass")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Construction), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("construction"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("hammer")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(Founding), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("founding"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("flag")),
    Kind(_root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Value(HumanGeneric), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Group(Human), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Label("event"), _root_.com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon("circle-dot"))
  )

  private val byKind: Map[com.histmersion.api.catalogue.EventTaxonomy.ByKindPart1, Kind] = kinds.map(k => k.kind -> k).toMap

  private val aliases: Map[com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1, com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2] = Map(
    { val refactorTuple22 = "rebellion" -> Revolt; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple22._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple22._2)) },
    { val refactorTuple23 = "uprising" -> Revolt; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple23._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple23._2)) },
    { val refactorTuple3 = "revolt" -> Revolt; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple3._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple3._2)) },
    { val refactorTuple4 = "naval_battle" -> NavalBattle; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple4._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple4._2)) },
    { val refactorTuple5 = "sea_battle" -> NavalBattle; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple5._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple5._2)) },
    { val refactorTuple6 = "volcanic_eruption" -> Eruption; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple6._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple6._2)) },
    { val refactorTuple28 = "plague" -> Epidemic; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple28._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple28._2)) },
    { val refactorTuple29 = "pandemic" -> Epidemic; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple29._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple29._2)) },
    { val refactorTuple30 = "climate_change" -> ClimateEvent; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple30._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple30._2)) },
    { val refactorTuple31 = "climatic_event" -> ClimateEvent; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple31._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple31._2)) },
    { val refactorTuple11 = "reign" -> Era; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple11._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple11._2)) },
    { val refactorTuple33 = "period" -> Era; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple33._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple33._2)) },
    { val refactorTuple34 = "diplomatic_era" -> Era; (_root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart1(refactorTuple34._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.AliasesPart2(refactorTuple34._2)) }
  )

  def canonical(kind: com.histmersion.api.catalogue.EventTaxonomy.CanonicalKind): com.histmersion.api.catalogue.EventTaxonomy.CanonicalResult =
    val k = EventScale.normKind(kind)
    aliases.get(k).orElse(Option.when(k.endsWith("_era"))(Era)).getOrElse(k)

  val labelRules: List[(com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart1, com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart2)] = List(
    { val refactorTuple35 = "erupt|volcan" -> Eruption; (_root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart1(refactorTuple35._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart2(refactorTuple35._2)) },
    { val refactorTuple15 = "earthquake|seism|quake" -> Earthquake; (_root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart1(refactorTuple15._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart2(refactorTuple15._2)) },
    { val refactorTuple16 = "tsunami" -> Tsunami; (_root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart1(refactorTuple16._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart2(refactorTuple16._2)) },
    { val refactorTuple17 = "flood|deluge" -> Flood; (_root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart1(refactorTuple17._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart2(refactorTuple17._2)) },
    { val refactorTuple18 = "drought|aridi" -> Drought; (_root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart1(refactorTuple18._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart2(refactorTuple18._2)) },
    { val refactorTuple19 = "kiloyear|climat|cooling|warming" -> ClimateEvent; (_root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart1(refactorTuple19._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart2(refactorTuple19._2)) },
    { val refactorTuple20 = "plague|epidemic|pandemic|pestilence" -> Epidemic; (_root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart1(refactorTuple20._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart2(refactorTuple20._2)) },
    { val refactorTuple42 = "famine" -> Famine; (_root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart1(refactorTuple42._1), _root_.com.histmersion.api.catalogue.EventTaxonomy.LabelRulesPart2(refactorTuple42._2)) }
  )

  def refine(kind: com.histmersion.api.catalogue.EventTaxonomy.RefineKind, label: com.histmersion.api.catalogue.EventTaxonomy.RefineLabel): com.histmersion.api.catalogue.EventTaxonomy.RefineResult =
    val k = canonical(kind)
    if k != NaturalGeneric then k
    else
      val l = (label).value.toLowerCase(java.util.Locale.ROOT)
      labelRules.collectFirst { case (re, sub) if (re).value.r.findFirstIn(l).isDefined => sub }.getOrElse(k)

  def of(kind: com.histmersion.api.catalogue.EventTaxonomy.OfKind): Kind =
    val k = canonical(kind)
    byKind.getOrElse(k, byKind(HumanGeneric).copy(kind = k, label = k.replace('_', ' ')))

  def group(kind: com.histmersion.api.catalogue.EventTaxonomy.GroupKind): com.histmersion.api.catalogue.EventTaxonomy.GroupResult = of(kind).group
  def icon(kind: com.histmersion.api.catalogue.EventTaxonomy.IconKind): com.histmersion.api.catalogue.EventTaxonomy.IconResult = of(kind).icon
  def isNatural(kind: com.histmersion.api.catalogue.EventTaxonomy.IsNaturalKind): com.histmersion.api.catalogue.EventTaxonomy.IsNaturalResult = group(kind) == Natural

  def matches(filter: com.histmersion.api.catalogue.EventTaxonomy.MatchesFilter, kind: com.histmersion.api.catalogue.EventTaxonomy.MatchesKind): com.histmersion.api.catalogue.EventTaxonomy.MatchesResult =
    val f = (filter).value.trim.toLowerCase(java.util.Locale.ROOT)
    f.isEmpty || f == group(kind) || canonical(f) == canonical(kind)

  final case class KindCondition(kinds: List[KindCondition.Kinds], negate: KindCondition.Negate)

  object KindCondition:
    type Kinds = Kinds.T
    object Kinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Negate = Negate.T
    object Negate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def condition(filter: Option[com.histmersion.api.catalogue.EventTaxonomy.ConditionFilter]): Option[KindCondition] =
    filter.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty).map { f =>
      val natural = (kinds.filter(_.group == Natural).map(_.kind) ++
        aliases.collect { case (a, v) if byKind.get(v).exists(_.group == Natural) => a }).distinct.sorted
      if f == Natural then KindCondition(natural, negate = _root_.com.histmersion.api.catalogue.EventTaxonomy.KindCondition.Negate(false))
      else if f == Human then KindCondition(natural, negate = _root_.com.histmersion.api.catalogue.EventTaxonomy.KindCondition.Negate(true))
      else
        val c = canonical(_root_.com.histmersion.api.catalogue.EventTaxonomy.CanonicalKind(f))
        KindCondition((c :: aliases.collect { case (a, v) if v == c => a }.toList).distinct.sorted, negate = _root_.com.histmersion.api.catalogue.EventTaxonomy.KindCondition.Negate(false))
    }
