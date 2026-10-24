package com.histmersion.api

/** The KIND taxonomy of events (#1449): every kind is NATURAL (it happened to people) or HUMAN (people
  * did it), carries the icon its map marker draws so the kind reads before a hover, and a label for the
  * legend. Kinds outside the table stay valid (the vocabulary is open: an agent may record a
  * `coronation`), drawn with the generic icon of their group.
  *
  * The kinds of the 50 events compiled from sources before the taxonomy (destruction, migration,
  * technological_change, polity_formation, natural_disaster, battle, war, cultural_shift, collapse,
  * diplomatic_era, invasion) are all in it; `natural_disaster` became the natural sub-kind its label
  * names where it names one (V085, [[refine]]), and stays as the generic natural kind otherwise.
  *
  * Icons are names in the web UI's glyph registry (`web.Glyphs`, vendored Lucide icons), each chosen to
  * stay legible — and unlike its siblings — at marker size (~15 px): a skull, not the biohazard sign, for
  * an epidemic; a plain ear of wheat, not the struck-through one, for a famine; a scroll, not the
  * handshake, for a treaty; a light bulb, not the cog (a sun's twin at that size), for a technological
  * change. */
object EventTaxonomy:

  val Natural = "natural"
  val Human   = "human"
  val groups: List[String] = List(Natural, Human)

  /** One kind: its wire name, group, legend label and icon. */
  final case class Kind(kind: String, group: String, label: String, icon: String)

  // natural sub-kinds
  val Eruption       = "eruption"
  val Earthquake     = "earthquake"
  val Tsunami        = "tsunami"
  val Flood          = "flood"
  val Drought        = "drought"
  val ClimateEvent   = "climate_event"
  val Epidemic       = "epidemic"
  val Famine         = "famine"
  val NaturalGeneric = "natural_disaster"
  // human kinds
  val Battle         = "battle"
  val NavalBattle    = "naval_battle"
  val Siege          = "siege"
  val War            = "war"
  val Revolt         = "revolt"
  val Treaty         = "treaty"
  val PolityFormation = "polity_formation"
  val Collapse       = "collapse"
  val Destruction    = "destruction"
  val Migration      = "migration"
  val Invasion       = "invasion"
  val TechChange     = "technological_change"
  val CulturalShift  = "cultural_shift"
  val Era            = "era"
  val Construction   = "construction"
  val Founding       = "founding"
  val HumanGeneric   = "event"

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
    Kind(HumanGeneric, Human, "event", "circle-dot"))

  private val byKind: Map[String, Kind] = kinds.map(k => k.kind -> k).toMap

  /** Older spellings and the vocabulary of the first events (plan §1.2) → their kind here. An `*_era`,
    * a reign or a period is an era. */
  private val aliases: Map[String, String] = Map(
    "rebellion" -> Revolt, "uprising" -> Revolt, "revolt" -> Revolt,
    "naval_battle" -> NavalBattle, "sea_battle" -> NavalBattle,
    "volcanic_eruption" -> Eruption, "plague" -> Epidemic, "pandemic" -> Epidemic,
    "climate_change" -> ClimateEvent, "climatic_event" -> ClimateEvent,
    "reign" -> Era, "period" -> Era, "diplomatic_era" -> Era)

  /** Pure: the canonical kind of a stored or given one — spelling normalised ([[EventScale.normKind]]),
    * an alias resolved; an unknown kind is kept as normalised (the vocabulary is open). */
  def canonical(kind: String): String =
    val k = EventScale.normKind(kind)
    aliases.get(k).orElse(Option.when(k.endsWith("_era"))(Era)).getOrElse(k)

  /** The natural sub-kinds a generic `natural_disaster` becomes when its LABEL names one — in order:
    * the first pattern that matches wins. V085 applies the same rules (in SQL) to the stored rows. */
  val labelRules: List[(String, String)] = List(
    "erupt|volcan" -> Eruption,
    "earthquake|seism|quake" -> Earthquake,
    "tsunami" -> Tsunami,
    "flood|deluge" -> Flood,
    "drought|aridi" -> Drought,
    "kiloyear|climat|cooling|warming" -> ClimateEvent,
    "plague|epidemic|pandemic|pestilence" -> Epidemic,
    "famine" -> Famine)

  /** Pure: a kind made precise by the event's label — only the generic natural kind is refined (a
    * `natural_disaster` labelled "Minoan eruption of Thera" is an eruption); every other kind is
    * [[canonical]]. */
  def refine(kind: String, label: String): String =
    val k = canonical(kind)
    if k != NaturalGeneric then k
    else
      val l = label.toLowerCase(java.util.Locale.ROOT)
      labelRules.collectFirst { case (re, sub) if re.r.findFirstIn(l).isDefined => sub }.getOrElse(k)

  /** Pure: the entry of a kind — a known one, else the generic one of its group (human, unless the kind
    * names a natural phenomenon). */
  def of(kind: String): Kind =
    val k = canonical(kind)
    byKind.getOrElse(k, byKind(HumanGeneric).copy(kind = k, label = k.replace('_', ' ')))

  def group(kind: String): String = of(kind).group
  def icon(kind: String): String  = of(kind).icon
  def isNatural(kind: String): Boolean = group(kind) == Natural

  /** Pure: the kinds a filter value selects — `natural` / `human` (a group), or one kind. */
  def matches(filter: String, kind: String): Boolean =
    val f = filter.trim.toLowerCase(java.util.Locale.ROOT)
    f.isEmpty || f == group(kind) || canonical(f) == canonical(kind)

  /** A condition on the STORED kind: `kind = ANY(kinds)`, or (negated) `kind <> ALL(kinds)`. */
  final case class KindCondition(kinds: List[String], negate: Boolean)

  /** Pure: the stored kinds a filter value means, for SQL: `natural` is every natural kind of the table
    * (and the aliases that resolve to one); `human` is every OTHER kind, so a kind outside the table
    * counts as human, as [[of]] says; a kind is itself plus its aliases. `None` = no condition. */
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
