package com.histmersion.api.catalogue
import com.histmersion.api.given
object EventTaxonomy:

  val Natural = "natural"
  val Human = "human"
  val groups: List[String] = List(Natural, Human)

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
    Kind(Eruption, Natural, "eruption", "mountain"),
    Kind(Earthquake, Natural, "earthquake", "activity"),
    Kind(Tsunami, Natural, "tsunami", "waves"),
    Kind(Flood, Natural, "flood", "droplets"),
    Kind(Drought, Natural, "drought", "sun"),
    Kind(ClimateEvent, Natural, "climate event", "thermometer"),
    Kind(Epidemic, Natural, "epidemic", "skull"),
    Kind(Famine, Natural, "famine", "wheat"),
    Kind(NaturalGeneric, Natural, "natural disaster", "cloud-lightning"),
    Kind(Battle, Human, "battle", "swords"),
    Kind(NavalBattle, Human, "naval battle", "sailboat"),
    Kind(Siege, Human, "siege", "castle"),
    Kind(War, Human, "war", "shield"),
    Kind(Revolt, Human, "revolt", "megaphone"),
    Kind(Treaty, Human, "treaty", "scroll"),
    Kind(PolityFormation, Human, "polity formation", "crown"),
    Kind(Collapse, Human, "collapse", "trending-down"),
    Kind(Destruction, Human, "destruction", "flame"),
    Kind(Migration, Human, "migration", "footprints"),
    Kind(Invasion, Human, "invasion", "arrow-big-right"),
    Kind(TechChange, Human, "technological change", "lightbulb"),
    Kind(CulturalShift, Human, "cultural shift", "sparkles"),
    Kind(Era, Human, "era", "hourglass"),
    Kind(Construction, Human, "construction", "hammer"),
    Kind(Founding, Human, "founding", "flag"),
    Kind(HumanGeneric, Human, "event", "circle-dot")
  )

  private val byKind: Map[String, Kind] = kinds.map(k => k.kind -> k).toMap

  private val aliases: Map[String, String] = Map(
    "rebellion" -> Revolt,
    "uprising" -> Revolt,
    "revolt" -> Revolt,
    "naval_battle" -> NavalBattle,
    "sea_battle" -> NavalBattle,
    "volcanic_eruption" -> Eruption,
    "plague" -> Epidemic,
    "pandemic" -> Epidemic,
    "climate_change" -> ClimateEvent,
    "climatic_event" -> ClimateEvent,
    "reign" -> Era,
    "period" -> Era,
    "diplomatic_era" -> Era
  )

  def canonical(kind: String): String =
    val k = EventScale.normKind(kind)
    aliases.get(k).orElse(Option.when(k.endsWith("_era"))(Era)).getOrElse(k)

  val labelRules: List[(String, String)] = List(
    "erupt|volcan" -> Eruption,
    "earthquake|seism|quake" -> Earthquake,
    "tsunami" -> Tsunami,
    "flood|deluge" -> Flood,
    "drought|aridi" -> Drought,
    "kiloyear|climat|cooling|warming" -> ClimateEvent,
    "plague|epidemic|pandemic|pestilence" -> Epidemic,
    "famine" -> Famine
  )

  def refine(kind: String, label: String): String =
    val k = canonical(kind)
    if k != NaturalGeneric then k
    else
      val l = label.toLowerCase(java.util.Locale.ROOT)
      labelRules.collectFirst { case (re, sub) if re.r.findFirstIn(l).isDefined => sub }.getOrElse(k)

  def of(kind: String): Kind =
    val k = canonical(kind)
    byKind.getOrElse(k, byKind(HumanGeneric).copy(kind = k, label = k.replace('_', ' ')))

  def group(kind: String): String = of(kind).group
  def icon(kind: String): String = of(kind).icon
  def isNatural(kind: String): Boolean = group(kind) == Natural

  def matches(filter: String, kind: String): Boolean =
    val f = filter.trim.toLowerCase(java.util.Locale.ROOT)
    f.isEmpty || f == group(kind) || canonical(f) == canonical(kind)

  final case class KindCondition(kinds: List[KindCondition.Kinds], negate: KindCondition.Negate)

  object KindCondition:
    type Kinds = Kinds.T
    object Kinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Negate = Negate.T
    object Negate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def condition(filter: Option[String]): Option[KindCondition] =
    filter.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty).map { f =>
      val natural = (kinds.filter(_.group == Natural).map(_.kind) ++
        aliases.collect { case (a, v) if byKind.get(v).exists(_.group == Natural) => a }).distinct.sorted
      if f == Natural then KindCondition(natural, negate = false)
      else if f == Human then KindCondition(natural, negate = true)
      else
        val c = canonical(f)
        KindCondition((c :: aliases.collect { case (a, v) if v == c => a }.toList).distinct.sorted, negate = false)
    }
