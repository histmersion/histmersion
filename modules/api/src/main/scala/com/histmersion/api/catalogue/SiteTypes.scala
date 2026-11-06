package com.histmersion.api.catalogue
import com.histmersion.api.given
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

/** Each type comes from what the site's gazetteer says: Pleiades' `feature_types` (comma-separated, `temple-2` a
  * variant of `temple`), the GeoNames feature code (`P.PPL` a populated place, `S.RUIN` ruins, `S.TMB` a tomb),
  * Wikidata's P31 classes walked up P279 through [[wikidataClasses]].
  */
object SiteTypes:

  type ByIdPart1 = ByIdPart1.T
  object ByIdPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetId = GetId.T
  object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IconResult = _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon
  val IconResult: _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon.type = _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon
  type IconId = _root_.com.histmersion.api.catalogue.SiteTypes.GetId
  val IconId: _root_.com.histmersion.api.catalogue.SiteTypes.GetId.type = _root_.com.histmersion.api.catalogue.SiteTypes.GetId
  type PleiadesTypesPart1 = PleiadesTypesPart1.T
  object PleiadesTypesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesTypesPart2 = PleiadesTypesPart2.T
  object PleiadesTypesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Preference = Preference.T
  object Preference extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BestResult = BestResult.T
  object BestResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BestTypes = BestTypes.T
  object BestTypes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesResult = PleiadesResult.T
  object PleiadesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesFeatureTypes = PleiadesFeatureTypes.T
  object PleiadesFeatureTypes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeonamesCodesPart1 = GeonamesCodesPart1.T
  object GeonamesCodesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeonamesCodesPart2 = GeonamesCodesPart2.T
  object GeonamesCodesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeonamesResult = GeonamesResult.T
  object GeonamesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeonamesFcode = GeonamesFcode.T
  object GeonamesFcode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataClassesPart1 = WikidataClassesPart1.T
  object WikidataClassesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataClassesPart2 = WikidataClassesPart2.T
  object WikidataClassesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataResult = WikidataResult.T
  object WikidataResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataP31 = _root_.com.histmersion.api.catalogue.SiteTypes.WikidataOwnP31
  val WikidataP31: _root_.com.histmersion.api.catalogue.SiteTypes.WikidataOwnP31.type = _root_.com.histmersion.api.catalogue.SiteTypes.WikidataOwnP31
  type WikidataSuperclasses = WikidataSuperclasses.T
  object WikidataSuperclasses extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataImportClass = WikidataImportClass.T
  object WikidataImportClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataOwnResult = WikidataOwnResult.T
  object WikidataOwnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataOwnP31 = WikidataOwnP31.T
  object WikidataOwnP31 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type P31OfResult = P31OfResult.T
  object P31OfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type P31OfJson = P31OfJson.T
  object P31OfJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResult = OfResult.T
  object OfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfPlaceId = OfPlaceId.T
  object OfPlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfPlaceKind = OfPlaceKind.T
  object OfPlaceKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfStored = OfStored.T
  object OfStored extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfFeatureTypes = OfFeatureTypes.T
  object OfFeatureTypes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfFcode = OfFcode.T
  object OfFcode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfWikidataClass = OfWikidataClass.T
  object OfWikidataClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfP31 = _root_.com.histmersion.api.catalogue.SiteTypes.WikidataOwnP31
  val OfP31: _root_.com.histmersion.api.catalogue.SiteTypes.WikidataOwnP31.type = _root_.com.histmersion.api.catalogue.SiteTypes.WikidataOwnP31

  final case class SiteType(id: SiteType.Id, label: SiteType.Label, icon: SiteType.Icon)

  object SiteType:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Icon = Icon.T
    object Icon extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Settlement = "settlement"
  val Villa = "villa"
  val Fortification = "fortification"
  val Sanctuary = "sanctuary"
  val Burial = "burial"
  val Mine = "mine"
  val Port = "port"
  val Infrastructure = "infrastructure"
  val Theatre = "theatre"
  val Findspot = "findspot"
  val Cave = "cave"
  val Palace = "palace"
  val Monument = "monument"
  val Shipwreck = "shipwreck"
  val Tower = "tower"
  val Bridge = "bridge"
  val Aqueduct = "aqueduct"

  val all: List[SiteType] = List(
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Settlement), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("settlement"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("building-2")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Villa), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("villa, house"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("house")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Fortification), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("fortification"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("castle")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Sanctuary), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("sanctuary, temple"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("landmark")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Burial), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("burial, tomb"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("pyramid")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Mine), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("mine, quarry"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("pickaxe")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Port), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("port, harbour"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("anchor")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Infrastructure), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("road, canal, cistern"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("route")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Bridge), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("bridge"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("waves")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Aqueduct), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("aqueduct"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("droplets")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Tower), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("tower"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("shield")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Theatre), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("theatre, amphitheatre"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("venetian-mask")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Findspot), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("find-spot, excavation"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("shovel")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Cave), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("cave"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("door-open")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Palace), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("palace"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("crown")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Monument), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("monument, megalith"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("milestone")),
    SiteType(_root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Shipwreck), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Label("shipwreck"), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon("ship"))
  )

  private val byId: Map[com.histmersion.api.catalogue.SiteTypes.ByIdPart1, SiteType] = all.map(t => t.id -> t).toMap

  def get(id: com.histmersion.api.catalogue.SiteTypes.GetId): Option[SiteType] = byId.get(_root_.com.histmersion.api.catalogue.SiteTypes.ByIdPart1((id).value.trim.toLowerCase(java.util.Locale.ROOT)))
  def icon(id: com.histmersion.api.catalogue.SiteTypes.IconId): Option[com.histmersion.api.catalogue.SiteTypes.IconResult] = get(id).map(_.icon)

  val pleiadesTypes: Map[com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1, com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2] = Map(
    { val refactorTuple = "settlement" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple._2)) },
    { val refactorTuple205 = "urban" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple205._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple205._2)) },
    { val refactorTuple3 = "town" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple3._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple3._2)) },
    { val refactorTuple4 = "city" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple4._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple4._2)) },
    { val refactorTuple5 = "village" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple5._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple5._2)) },
    { val refactorTuple209 = "settlement-modern" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple209._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple209._2)) },
    { val refactorTuple7 = "polis" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple7._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple7._2)) },
    { val refactorTuple211 = "tell" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple211._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple211._2)) },
    { val refactorTuple212 = "townhouse" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple212._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple212._2)) },
    { val refactorTuple213 = "villa" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple213._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple213._2)) },
    { val refactorTuple11 = "farmstead" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple11._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple11._2)) },
    { val refactorTuple215 = "estate" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple215._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple215._2)) },
    { val refactorTuple216 = "fort" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple216._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple216._2)) },
    { val refactorTuple14 = "fortlet" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple14._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple14._2)) },
    { val refactorTuple218 = "tower-defensive" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple218._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple218._2)) },
    { val refactorTuple219 = "fortified-settlement" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple219._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple219._2)) },
    { val refactorTuple17 = "city-gate" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple17._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple17._2)) },
    { val refactorTuple221 = "city-wall" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple221._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple221._2)) },
    { val refactorTuple222 = "fortress" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple222._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple222._2)) },
    { val refactorTuple20 = "castle" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple20._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple20._2)) },
    { val refactorTuple224 = "camp" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple224._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple224._2)) },
    { val refactorTuple225 = "hillfort" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple225._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple225._2)) },
    { val refactorTuple23 = "wall" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple23._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple23._2)) },
    { val refactorTuple24 = "acropolis" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple24._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple24._2)) },
    { val refactorTuple228 = "temple" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple228._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple228._2)) },
    { val refactorTuple26 = "sanctuary" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple26._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple26._2)) },
    { val refactorTuple27 = "church" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple27._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple27._2)) },
    { val refactorTuple231 = "shrine" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple231._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple231._2)) },
    { val refactorTuple232 = "monastery" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple232._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple232._2)) },
    { val refactorTuple233 = "mosque" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple233._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple233._2)) },
    { val refactorTuple234 = "synagogue" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple234._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple234._2)) },
    { val refactorTuple32 = "mithraeum" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple32._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple32._2)) },
    { val refactorTuple236 = "basilica" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple236._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple236._2)) },
    { val refactorTuple237 = "altar" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple237._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple237._2)) },
    { val refactorTuple35 = "oracle" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple35._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple35._2)) },
    { val refactorTuple36 = "tomb" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple36._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple36._2)) },
    { val refactorTuple240 = "tumulus" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple240._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple240._2)) },
    { val refactorTuple241 = "cemetery" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple241._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple241._2)) },
    { val refactorTuple39 = "necropolis" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple39._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple39._2)) },
    { val refactorTuple40 = "pyramid" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple40._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple40._2)) },
    { val refactorTuple41 = "mausoleum" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple41._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple41._2)) },
    { val refactorTuple42 = "catacomb" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple42._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple42._2)) },
    { val refactorTuple43 = "mine" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple43._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple43._2)) },
    { val refactorTuple247 = "quarry" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple247._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple247._2)) },
    { val refactorTuple45 = "port" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple45._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple45._2)) },
    { val refactorTuple46 = "harbor" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple46._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple46._2)) },
    { val refactorTuple47 = "harbour" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple47._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple47._2)) },
    { val refactorTuple251 = "anchorage" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple251._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple251._2)) },
    { val refactorTuple252 = "lighthouse" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple252._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple252._2)) },
    { val refactorTuple253 = "road" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple253._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple253._2)) },
    { val refactorTuple51 = "bridge" -> Bridge; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple51._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple51._2)) },
    { val refactorTuple255 = "aqueduct" -> Aqueduct; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple255._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple255._2)) },
    { val refactorTuple256 = "station" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple256._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple256._2)) },
    { val refactorTuple54 = "milestone" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple54._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple54._2)) },
    { val refactorTuple258 = "canal" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple258._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple258._2)) },
    { val refactorTuple259 = "dam" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple259._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple259._2)) },
    { val refactorTuple57 = "cistern" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple57._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple57._2)) },
    { val refactorTuple261 = "pass" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple261._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple261._2)) },
    { val refactorTuple262 = "bath" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple262._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple262._2)) },
    { val refactorTuple263 = "theatre" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple263._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple263._2)) },
    { val refactorTuple61 = "theater" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple61._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple61._2)) },
    { val refactorTuple265 = "amphitheatre" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple265._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple265._2)) },
    { val refactorTuple63 = "amphitheater" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple63._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple63._2)) },
    { val refactorTuple64 = "odeon" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple64._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple64._2)) },
    { val refactorTuple65 = "circus" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple65._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple65._2)) },
    { val refactorTuple66 = "stadium" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple66._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple66._2)) },
    { val refactorTuple67 = "hippodrome" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple67._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple67._2)) },
    { val refactorTuple271 = "findspot" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple271._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple271._2)) },
    { val refactorTuple272 = "archaeological-site" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple272._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple272._2)) },
    { val refactorTuple70 = "rock-art" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple70._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple70._2)) },
    { val refactorTuple71 = "inscription" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple71._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple71._2)) },
    { val refactorTuple275 = "hoard" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple275._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple275._2)) },
    { val refactorTuple73 = "cave" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple73._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple73._2)) },
    { val refactorTuple277 = "grotto" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple277._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple277._2)) },
    { val refactorTuple75 = "palace" -> Palace; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple75._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple75._2)) },
    { val refactorTuple76 = "monument" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple76._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple76._2)) },
    { val refactorTuple77 = "megalith" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple77._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple77._2)) },
    { val refactorTuple281 = "arch" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple281._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple281._2)) },
    { val refactorTuple282 = "stele" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple282._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple282._2)) },
    { val refactorTuple283 = "obelisk" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple283._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple283._2)) },
    { val refactorTuple81 = "statue" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple81._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple81._2)) },
    { val refactorTuple82 = "column" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple82._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple82._2)) },
    { val refactorTuple83 = "shipwreck" -> Shipwreck; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple83._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple83._2)) },
    { val refactorTuple287 = "wreck" -> Shipwreck; (_root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart1(refactorTuple287._1), _root_.com.histmersion.api.catalogue.SiteTypes.PleiadesTypesPart2(refactorTuple287._2)) }
  )

  val preference: List[com.histmersion.api.catalogue.SiteTypes.Preference] = List(
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Palace),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Sanctuary),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Theatre),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Burial),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Fortification),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Tower),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Mine),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Port),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Shipwreck),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Cave),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Monument),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Villa),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Aqueduct),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Bridge),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Infrastructure),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Settlement),
    _root_.com.histmersion.api.catalogue.SiteTypes.Preference(Findspot)
  )

  private def best(types: Iterable[com.histmersion.api.catalogue.SiteTypes.BestTypes]): Option[com.histmersion.api.catalogue.SiteTypes.BestResult] =
    types.toList.distinct
      .sortBy(t => preference.indexOf(t) match { case -1 => preference.size; case i => i })
      .headOption

  def pleiades(featureTypes: com.histmersion.api.catalogue.SiteTypes.PleiadesFeatureTypes): Option[com.histmersion.api.catalogue.SiteTypes.PleiadesResult] =
    best(
      Option(featureTypes).toList
        .flatMap((_).value.split(","))
        .map(_.trim.toLowerCase(java.util.Locale.ROOT))
        .filter(_.nonEmpty)
        .map(_.replaceAll("-\\d+$", ""))
        .flatMap(pleiadesTypes.get)
    )

  val geonamesCodes: Map[com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1, com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2] = Map(
    { val refactorTuple288 = "S.RUIN" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple288._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple288._2)) },
    { val refactorTuple289 = "S.ANS" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple289._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple289._2)) },
    { val refactorTuple290 = "S.HSTS" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple290._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple290._2)) },
    { val refactorTuple88 = "S.TMB" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple88._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple88._2)) },
    { val refactorTuple89 = "S.CMTY" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple89._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple89._2)) },
    { val refactorTuple293 = "S.PYR" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple293._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple293._2)) },
    { val refactorTuple91 = "S.PYRS" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple91._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple91._2)) },
    { val refactorTuple92 = "S.GRVE" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple92._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple92._2)) },
    { val refactorTuple296 = "S.CSTL" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple296._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple296._2)) },
    { val refactorTuple297 = "S.FT" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple297._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple297._2)) },
    { val refactorTuple298 = "S.WALLA" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple298._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple298._2)) },
    { val refactorTuple299 = "S.TOWR" -> Tower; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple299._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple299._2)) },
    { val refactorTuple97 = "S.TMPL" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple97._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple97._2)) },
    { val refactorTuple301 = "S.CH" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple301._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple301._2)) },
    { val refactorTuple99 = "S.MSTY" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple99._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple99._2)) },
    { val refactorTuple303 = "S.MSQE" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple303._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple303._2)) },
    { val refactorTuple101 = "S.SHRN" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple101._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple101._2)) },
    { val refactorTuple305 = "S.CTRR" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple305._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple305._2)) },
    { val refactorTuple306 = "S.PAL" -> Palace; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple306._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple306._2)) },
    { val refactorTuple104 = "S.MNMT" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple104._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple104._2)) },
    { val refactorTuple105 = "S.MNA" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple105._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple105._2)) },
    { val refactorTuple309 = "S.MN" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple309._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple309._2)) },
    { val refactorTuple107 = "S.MNQR" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple107._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple107._2)) },
    { val refactorTuple311 = "S.MNAU" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple311._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple311._2)) },
    { val refactorTuple109 = "S.MNCU" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple109._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple109._2)) },
    { val refactorTuple313 = "S.MNFE" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple313._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple313._2)) },
    { val refactorTuple314 = "S.QUAR" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple314._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple314._2)) },
    { val refactorTuple315 = "L.PRT" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple315._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple315._2)) },
    { val refactorTuple316 = "S.HBR" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple316._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple316._2)) },
    { val refactorTuple114 = "H.HBR" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple114._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple114._2)) },
    { val refactorTuple115 = "S.LTHSE" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple115._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple115._2)) },
    { val refactorTuple116 = "S.BDG" -> Bridge; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple116._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple116._2)) },
    { val refactorTuple117 = "S.AQDT" -> Aqueduct; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple117._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple117._2)) },
    { val refactorTuple321 = "R.RD" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple321._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple321._2)) },
    { val refactorTuple322 = "R.RDA" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple322._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple322._2)) },
    { val refactorTuple120 = "S.AMTH" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple120._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple120._2)) },
    { val refactorTuple121 = "S.THTR" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple121._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple121._2)) },
    { val refactorTuple122 = "S.STDM" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple122._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple122._2)) },
    { val refactorTuple326 = "H.WRCK" -> Shipwreck; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple326._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple326._2)) },
    { val refactorTuple327 = "S.WRCK" -> Shipwreck; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple327._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple327._2)) },
    { val refactorTuple125 = "S.CAVE" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple125._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple125._2)) },
    { val refactorTuple329 = "H.CAVE" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple329._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple329._2)) },
    { val refactorTuple330 = "T.CAVE" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(refactorTuple330._1), _root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart2(refactorTuple330._2)) }
  )

  def geonames(fcode: com.histmersion.api.catalogue.SiteTypes.GeonamesFcode): Option[com.histmersion.api.catalogue.SiteTypes.GeonamesResult] =
    Option(fcode).map((_).value.trim.toUpperCase(java.util.Locale.ROOT)).filter(_.nonEmpty).flatMap { c =>
      geonamesCodes.get(_root_.com.histmersion.api.catalogue.SiteTypes.GeonamesCodesPart1(c)).orElse(Option.when(c.startsWith("P."))(Settlement))
    }

  val ArchaeologicalSite = "Q839954"
  val HumanSettlement = "Q486972"

  /** The comment beside each id is its Wikidata label. */
  val wikidataClasses: Map[com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1, com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2] = Map(
    { val refactorTuple331 = HumanSettlement -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple331._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple331._2)) },
    { val refactorTuple129 = "Q15661340" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple129._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple129._2)) } /* ancient city */,
    { val refactorTuple333 = "Q515" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple333._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple333._2)) } /* city */,
    { val refactorTuple131 = "Q15253706" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple131._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple131._2)) } /* city */,
    { val refactorTuple132 = "Q755017" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple132._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple132._2)) } /* tell */,
    { val refactorTuple336 = "Q532" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple336._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple336._2)) } /* village */,
    { val refactorTuple337 = "Q3957" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple337._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple337._2)) } /* town */,
    { val refactorTuple338 = "Q1549591" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple338._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple338._2)) } /* big city */,
    { val refactorTuple339 = "Q5084" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple339._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple339._2)) } /* hamlet */,
    { val refactorTuple340 = "Q148837" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple340._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple340._2)) } /* polis */,
    { val refactorTuple138 = "Q3947" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple138._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple138._2)) } /* house */,
    { val refactorTuple139 = "Q3950" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple139._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple139._2)) } /* villa */,
    { val refactorTuple140 = "Q918230" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple140._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple140._2)) } /* Roman villa */,
    { val refactorTuple141 = "Q131596" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple141._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple141._2)) } /* farm */,
    { val refactorTuple142 = "Q57821" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple142._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple142._2)) } /* fortification */,
    { val refactorTuple143 = "Q1785071" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple143._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple143._2)) } /* fort */,
    { val refactorTuple347 = "Q23413" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple347._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple347._2)) } /* castle */,
    { val refactorTuple348 = "Q744099" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple348._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple348._2)) } /* hillfort */,
    { val refactorTuple349 = "Q88291" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple349._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple349._2)) } /* citadel */,
    { val refactorTuple350 = "Q16748868" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple350._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple350._2)) } /* city walls */,
    { val refactorTuple351 = "Q192601" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple351._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple351._2)) } /* oppidum */,
    { val refactorTuple149 = "Q57831" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple149._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple149._2)) } /* fortress */,
    { val refactorTuple150 = "Q82117" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple150._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple150._2)) } /* city gate */,
    { val refactorTuple151 = "Q57346" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple151._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple151._2)) } /* defensive wall */,
    { val refactorTuple152 = "Q947103" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple152._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple152._2)) } /* watchtower */,
    { val refactorTuple356 = "Q20034791" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple356._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple356._2)) } /* defensive tower */,
    { val refactorTuple154 = "Q81917" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple154._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple154._2)) } /* fortified tower */,
    { val refactorTuple155 = "Q91312" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple155._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple155._2)) } /* tower house */,
    { val refactorTuple156 = "Q12518" -> Tower; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple156._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple156._2)) } /* tower */,
    { val refactorTuple360 = "Q853854" -> Tower; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple360._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple360._2)) } /* clock tower */,
    { val refactorTuple361 = "Q200334" -> Tower; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple361._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple361._2)) } /* bell tower */,
    { val refactorTuple362 = "Q48356" -> Tower; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple362._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple362._2)) } /* minaret */,
    { val refactorTuple363 = "Q12280" -> Bridge; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple363._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple363._2)) } /* bridge */,
    { val refactorTuple161 = "Q474" -> Aqueduct; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple161._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple161._2)) } /* aqueduct */,
    { val refactorTuple365 = "Q34442" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple365._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple365._2)) } /* road */,
    { val refactorTuple163 = "Q44539" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple163._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple163._2)) } /* temple */,
    { val refactorTuple164 = "Q29553" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple164._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple164._2)) } /* sanctuary */,
    { val refactorTuple165 = "Q16970" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple165._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple165._2)) } /* church building */,
    { val refactorTuple166 = "Q697295" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple166._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple166._2)) } /* shrine */,
    { val refactorTuple370 = "Q44613" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple370._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple370._2)) } /* monastery */,
    { val refactorTuple371 = "Q32815" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple371._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple371._2)) } /* mosque */,
    { val refactorTuple372 = "Q34627" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple372._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple372._2)) } /* synagogue */,
    { val refactorTuple373 = "Q1088552" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple373._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple373._2)) } /* Catholic church building */,
    { val refactorTuple374 = "Q24398318" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple374._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple374._2)) } /* religious building */,
    { val refactorTuple172 = "Q267596" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple172._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple172._2)) } /* ancient Greek temple */,
    { val refactorTuple376 = "Q108325" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple376._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple376._2)) } /* chapel */,
    { val refactorTuple377 = "Q2031836" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple377._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple377._2)) } /* Eastern Orthodox church building */,
    { val refactorTuple175 = "Q2977" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple175._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple175._2)) } /* cathedral */,
    { val refactorTuple176 = "Q381885" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple176._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple176._2)) } /* tomb */,
    { val refactorTuple177 = "Q39614" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple177._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple177._2)) } /* cemetery */,
    { val refactorTuple381 = "Q200141" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple381._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple381._2)) } /* necropolis */,
    { val refactorTuple179 = "Q34023" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple179._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple179._2)) } /* tumulus (a kurgan is one) */,
    { val refactorTuple180 = "Q5737" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple180._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple180._2)) } /* burial mound */,
    { val refactorTuple384 = "Q12516" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple384._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple384._2)) } /* pyramid */,
    { val refactorTuple182 = "Q173387" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple182._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple182._2)) } /* grave */,
    { val refactorTuple386 = "Q162875" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple386._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple386._2)) } /* mausoleum */,
    { val refactorTuple184 = "Q820477" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple184._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple184._2)) } /* mine */,
    { val refactorTuple185 = "Q188040" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple185._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple185._2)) } /* quarry */,
    { val refactorTuple186 = "Q44782" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple186._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple186._2)) } /* port */,
    { val refactorTuple390 = "Q283202" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple390._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple390._2)) } /* harbor */,
    { val refactorTuple391 = "Q852190" -> Shipwreck; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple391._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple391._2)) } /* shipwreck */,
    { val refactorTuple392 = "Q35509" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple392._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple392._2)) } /* cave */,
    { val refactorTuple190 = "Q1149405" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple190._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple190._2)) } /* rock shelter */,
    { val refactorTuple394 = "Q16560" -> Palace; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple394._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple394._2)) } /* palace */,
    { val refactorTuple395 = "Q4989906" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple395._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple395._2)) } /* monument */,
    { val refactorTuple193 = "Q164240" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple193._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple193._2)) } /* megalith */,
    { val refactorTuple397 = "Q10521078" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple397._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple397._2)) } /* megalithic tomb */,
    { val refactorTuple195 = "Q101659" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple195._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple195._2)) } /* dolmen */,
    { val refactorTuple399 = "Q193475" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple399._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple399._2)) } /* menhir */,
    { val refactorTuple197 = "Q1935728" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple197._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple197._2)) } /* stone circle */,
    { val refactorTuple401 = "Q170980" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple401._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple401._2)) } /* obelisk */,
    { val refactorTuple402 = "Q179700" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple402._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple402._2)) } /* statue */,
    { val refactorTuple200 = "Q54831" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple200._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple200._2)) } /* amphitheatre */,
    { val refactorTuple404 = "Q24354" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple404._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple404._2)) } /* theatre building */,
    { val refactorTuple405 = "Q1211146" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple405._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple405._2)) } /* rock art */,
    { val refactorTuple203 = ArchaeologicalSite -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart1(refactorTuple203._1), _root_.com.histmersion.api.catalogue.SiteTypes.WikidataClassesPart2(refactorTuple203._2)) }
  )

  def wikidata(p31: List[com.histmersion.api.catalogue.SiteTypes.WikidataP31], superclasses: List[com.histmersion.api.catalogue.SiteTypes.WikidataSuperclasses], importClass: Option[com.histmersion.api.catalogue.SiteTypes.WikidataImportClass]): Option[com.histmersion.api.catalogue.SiteTypes.WikidataResult] =
    wikidataOwn(p31)
      .orElse(superclasses.flatMap(wikidataClasses.get).headOption)
      .orElse(importClass.flatMap(wikidataClasses.get))

  def wikidataOwn(p31: List[com.histmersion.api.catalogue.SiteTypes.WikidataOwnP31]): Option[com.histmersion.api.catalogue.SiteTypes.WikidataOwnResult] = best(p31.map((_).value.trim).flatMap(wikidataClasses.get))

  def p31Of(json: Option[com.histmersion.api.catalogue.SiteTypes.P31OfJson]): List[com.histmersion.api.catalogue.SiteTypes.P31OfResult] =
    json.toList.flatMap(s => "Q[0-9]+".r.findAllIn((s).value).toList)

  def of(
      placeId: com.histmersion.api.catalogue.SiteTypes.OfPlaceId,
      placeKind: com.histmersion.api.catalogue.SiteTypes.OfPlaceKind,
      stored: Option[com.histmersion.api.catalogue.SiteTypes.OfStored],
      featureTypes: Option[com.histmersion.api.catalogue.SiteTypes.OfFeatureTypes],
      fcode: Option[com.histmersion.api.catalogue.SiteTypes.OfFcode],
      wikidataClass: Option[com.histmersion.api.catalogue.SiteTypes.OfWikidataClass],
      p31: List[com.histmersion.api.catalogue.SiteTypes.OfP31] = Nil
  ): Option[com.histmersion.api.catalogue.SiteTypes.OfResult] =
    if placeKind != _root_.com.histmersion.api.catalogue.SiteTypes.OfPlaceKind(com.histmersion.api.catalogue.Place.Kind.Site) && placeKind != _root_.com.histmersion.api.catalogue.SiteTypes.OfPlaceKind(com.histmersion.api.catalogue.Place.Kind.Feature)
    then None
    else
      stored
        .filter(byId.contains)
        .orElse(featureTypes.flatMap(pleiades))
        .orElse(fcode.flatMap(geonames))
        .orElse(wikidataOwn(p31))
        .orElse(wikidataClass.flatMap(c => wikidataClasses.get(c.trim)))

  final case class Report(
      dryRun: Report.DryRun,
      pending: Report.Pending = _root_.com.histmersion.api.catalogue.SiteTypes.Report.Pending(0),
      asked: Report.Asked = _root_.com.histmersion.api.catalogue.SiteTypes.Report.Asked(0),
      typed: List[EventCount] = Nil,
      untyped: Report.Untyped = _root_.com.histmersion.api.catalogue.SiteTypes.Report.Untyped(0),
      requests: Report.Requests = _root_.com.histmersion.api.catalogue.SiteTypes.Report.Requests(0),
      failed: List[Report.Failed] = Nil
  ) derives hearth.kindlings.circederivation.KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
  object Report:

    type SummaryResult = SummaryResult.T
    object SummaryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pending = Pending.T
    object Pending extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Asked = Asked.T
    object Asked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Untyped = Untyped.T
    object Untyped extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Requests = Requests.T
    object Requests extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Failed = Failed.T
    object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def summary(r: Report): com.histmersion.api.catalogue.SiteTypes.Report.SummaryResult =
      if (r.dryRun).value then _root_.com.histmersion.api.catalogue.SiteTypes.Report.SummaryResult(s"${r.pending} Wikidata site(s) on the map would be asked about")
      else
        _root_.com.histmersion.api.catalogue.SiteTypes.Report.SummaryResult((List(
          s"${r.asked} site(s) asked",
          s"typed: ${if r.typed.isEmpty then "none" else r.typed.map(c => s"${c.key} ${c.n}").mkString(", ")}",
          s"${r.untyped} kept the import's class",
          s"${r.pending} left",
          s"${r.requests} request(s)"
        ) ++
          Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}"))
          .mkString(" · "))

  final case class Request(dryRun: Request.DryRun = _root_.com.histmersion.api.catalogue.SiteTypes.Request.DryRun(false), limit: Option[Request.Limit] = None, refresh: Request.Refresh = _root_.com.histmersion.api.catalogue.SiteTypes.Request.Refresh(false))
      derives hearth.kindlings.circederivation.KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Request:
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Refresh = Refresh.T
    object Refresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
