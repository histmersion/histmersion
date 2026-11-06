package com.histmersion.api.catalogue
import com.histmersion.api.given
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

/** Each type comes from what the site's gazetteer says: Pleiades' `feature_types` (comma-separated, `temple-2` a
  * variant of `temple`), the GeoNames feature code (`P.PPL` a populated place, `S.RUIN` ruins, `S.TMB` a tomb),
  * Wikidata's P31 classes walked up P279 through [[wikidataClasses]].
  */
object SiteTypes:
  object Pleiades:
    type FeatureType = FeatureType.T
    object FeatureType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Geonames:
    type Fcode = Fcode.T
    object Fcode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReportSummary = ReportSummary.T
  object ReportSummary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ByIdPart1 = _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id
  val ByIdPart1: _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id.type = _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id
  type IconResult = _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon
  val IconResult: _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon.type = _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Icon
  type IconId = _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id
  val IconId: _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id.type = _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id
  type WikidataP31 = _root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class
  val WikidataP31: _root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class.type = _root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class
  type OfP31 = _root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class
  val OfP31: _root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class.type = _root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class

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

  def get(id: com.histmersion.api.catalogue.SiteTypes.SiteType.Id): Option[SiteType] = byId.get(_root_.com.histmersion.api.catalogue.SiteTypes.ByIdPart1((id).value.trim.toLowerCase(java.util.Locale.ROOT)))
  def icon(id: com.histmersion.api.catalogue.SiteTypes.IconId): Option[com.histmersion.api.catalogue.SiteTypes.IconResult] = get(id).map(_.icon)

  val pleiadesTypes: Map[com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType, com.histmersion.api.catalogue.SiteTypes.SiteType.Id] = Map(
    { val refactorTuple = "settlement" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple._2)) },
    { val refactorTuple205 = "urban" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple205._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple205._2)) },
    { val refactorTuple3 = "town" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple3._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple3._2)) },
    { val refactorTuple4 = "city" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple4._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple4._2)) },
    { val refactorTuple5 = "village" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple5._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple5._2)) },
    { val refactorTuple209 = "settlement-modern" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple209._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple209._2)) },
    { val refactorTuple7 = "polis" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple7._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple7._2)) },
    { val refactorTuple211 = "tell" -> Settlement; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple211._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple211._2)) },
    { val refactorTuple212 = "townhouse" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple212._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple212._2)) },
    { val refactorTuple213 = "villa" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple213._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple213._2)) },
    { val refactorTuple11 = "farmstead" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple11._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple11._2)) },
    { val refactorTuple215 = "estate" -> Villa; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple215._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple215._2)) },
    { val refactorTuple216 = "fort" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple216._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple216._2)) },
    { val refactorTuple14 = "fortlet" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple14._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple14._2)) },
    { val refactorTuple218 = "tower-defensive" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple218._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple218._2)) },
    { val refactorTuple219 = "fortified-settlement" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple219._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple219._2)) },
    { val refactorTuple17 = "city-gate" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple17._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple17._2)) },
    { val refactorTuple221 = "city-wall" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple221._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple221._2)) },
    { val refactorTuple222 = "fortress" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple222._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple222._2)) },
    { val refactorTuple20 = "castle" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple20._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple20._2)) },
    { val refactorTuple224 = "camp" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple224._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple224._2)) },
    { val refactorTuple225 = "hillfort" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple225._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple225._2)) },
    { val refactorTuple23 = "wall" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple23._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple23._2)) },
    { val refactorTuple24 = "acropolis" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple24._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple24._2)) },
    { val refactorTuple228 = "temple" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple228._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple228._2)) },
    { val refactorTuple26 = "sanctuary" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple26._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple26._2)) },
    { val refactorTuple27 = "church" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple27._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple27._2)) },
    { val refactorTuple231 = "shrine" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple231._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple231._2)) },
    { val refactorTuple232 = "monastery" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple232._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple232._2)) },
    { val refactorTuple233 = "mosque" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple233._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple233._2)) },
    { val refactorTuple234 = "synagogue" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple234._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple234._2)) },
    { val refactorTuple32 = "mithraeum" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple32._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple32._2)) },
    { val refactorTuple236 = "basilica" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple236._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple236._2)) },
    { val refactorTuple237 = "altar" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple237._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple237._2)) },
    { val refactorTuple35 = "oracle" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple35._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple35._2)) },
    { val refactorTuple36 = "tomb" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple36._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple36._2)) },
    { val refactorTuple240 = "tumulus" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple240._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple240._2)) },
    { val refactorTuple241 = "cemetery" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple241._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple241._2)) },
    { val refactorTuple39 = "necropolis" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple39._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple39._2)) },
    { val refactorTuple40 = "pyramid" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple40._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple40._2)) },
    { val refactorTuple41 = "mausoleum" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple41._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple41._2)) },
    { val refactorTuple42 = "catacomb" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple42._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple42._2)) },
    { val refactorTuple43 = "mine" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple43._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple43._2)) },
    { val refactorTuple247 = "quarry" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple247._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple247._2)) },
    { val refactorTuple45 = "port" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple45._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple45._2)) },
    { val refactorTuple46 = "harbor" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple46._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple46._2)) },
    { val refactorTuple47 = "harbour" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple47._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple47._2)) },
    { val refactorTuple251 = "anchorage" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple251._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple251._2)) },
    { val refactorTuple252 = "lighthouse" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple252._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple252._2)) },
    { val refactorTuple253 = "road" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple253._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple253._2)) },
    { val refactorTuple51 = "bridge" -> Bridge; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple51._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple51._2)) },
    { val refactorTuple255 = "aqueduct" -> Aqueduct; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple255._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple255._2)) },
    { val refactorTuple256 = "station" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple256._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple256._2)) },
    { val refactorTuple54 = "milestone" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple54._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple54._2)) },
    { val refactorTuple258 = "canal" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple258._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple258._2)) },
    { val refactorTuple259 = "dam" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple259._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple259._2)) },
    { val refactorTuple57 = "cistern" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple57._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple57._2)) },
    { val refactorTuple261 = "pass" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple261._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple261._2)) },
    { val refactorTuple262 = "bath" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple262._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple262._2)) },
    { val refactorTuple263 = "theatre" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple263._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple263._2)) },
    { val refactorTuple61 = "theater" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple61._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple61._2)) },
    { val refactorTuple265 = "amphitheatre" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple265._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple265._2)) },
    { val refactorTuple63 = "amphitheater" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple63._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple63._2)) },
    { val refactorTuple64 = "odeon" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple64._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple64._2)) },
    { val refactorTuple65 = "circus" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple65._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple65._2)) },
    { val refactorTuple66 = "stadium" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple66._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple66._2)) },
    { val refactorTuple67 = "hippodrome" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple67._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple67._2)) },
    { val refactorTuple271 = "findspot" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple271._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple271._2)) },
    { val refactorTuple272 = "archaeological-site" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple272._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple272._2)) },
    { val refactorTuple70 = "rock-art" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple70._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple70._2)) },
    { val refactorTuple71 = "inscription" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple71._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple71._2)) },
    { val refactorTuple275 = "hoard" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple275._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple275._2)) },
    { val refactorTuple73 = "cave" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple73._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple73._2)) },
    { val refactorTuple277 = "grotto" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple277._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple277._2)) },
    { val refactorTuple75 = "palace" -> Palace; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple75._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple75._2)) },
    { val refactorTuple76 = "monument" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple76._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple76._2)) },
    { val refactorTuple77 = "megalith" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple77._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple77._2)) },
    { val refactorTuple281 = "arch" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple281._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple281._2)) },
    { val refactorTuple282 = "stele" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple282._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple282._2)) },
    { val refactorTuple283 = "obelisk" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple283._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple283._2)) },
    { val refactorTuple81 = "statue" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple81._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple81._2)) },
    { val refactorTuple82 = "column" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple82._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple82._2)) },
    { val refactorTuple83 = "shipwreck" -> Shipwreck; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple83._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple83._2)) },
    { val refactorTuple287 = "wreck" -> Shipwreck; (_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType(refactorTuple287._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple287._2)) }
  )

  val preference: List[com.histmersion.api.catalogue.SiteTypes.SiteType.Id] = List(
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Palace),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Sanctuary),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Theatre),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Burial),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Fortification),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Tower),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Mine),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Port),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Shipwreck),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Cave),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Monument),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Villa),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Aqueduct),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Bridge),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Infrastructure),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Settlement),
    _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(Findspot)
  )

  private def best(types: Iterable[com.histmersion.api.catalogue.SiteTypes.SiteType.Id]): Option[com.histmersion.api.catalogue.SiteTypes.SiteType.Id] =
    (types.toList.distinct
      .sortBy(t => preference.indexOf(t) match { case -1 => preference.size; case i => i })
      .headOption).map(refactorValue6 => _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id((refactorValue6).value))

  def pleiades(featureTypes: com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType): Option[com.histmersion.api.catalogue.SiteTypes.SiteType.Id] =
    best(
      Option(featureTypes).toList
        .flatMap((_).value.split(","))
        .map(_.trim.toLowerCase(java.util.Locale.ROOT))
        .filter(_.nonEmpty)
        .map(_.replaceAll("-\\d+$", ""))
        .flatMap(pleiadesTypes.get)
    )

  val geonamesCodes: Map[com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode, com.histmersion.api.catalogue.SiteTypes.SiteType.Id] = Map(
    { val refactorTuple288 = "S.RUIN" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple288._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple288._2)) },
    { val refactorTuple289 = "S.ANS" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple289._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple289._2)) },
    { val refactorTuple290 = "S.HSTS" -> Findspot; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple290._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple290._2)) },
    { val refactorTuple88 = "S.TMB" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple88._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple88._2)) },
    { val refactorTuple89 = "S.CMTY" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple89._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple89._2)) },
    { val refactorTuple293 = "S.PYR" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple293._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple293._2)) },
    { val refactorTuple91 = "S.PYRS" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple91._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple91._2)) },
    { val refactorTuple92 = "S.GRVE" -> Burial; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple92._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple92._2)) },
    { val refactorTuple296 = "S.CSTL" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple296._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple296._2)) },
    { val refactorTuple297 = "S.FT" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple297._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple297._2)) },
    { val refactorTuple298 = "S.WALLA" -> Fortification; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple298._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple298._2)) },
    { val refactorTuple299 = "S.TOWR" -> Tower; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple299._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple299._2)) },
    { val refactorTuple97 = "S.TMPL" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple97._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple97._2)) },
    { val refactorTuple301 = "S.CH" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple301._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple301._2)) },
    { val refactorTuple99 = "S.MSTY" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple99._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple99._2)) },
    { val refactorTuple303 = "S.MSQE" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple303._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple303._2)) },
    { val refactorTuple101 = "S.SHRN" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple101._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple101._2)) },
    { val refactorTuple305 = "S.CTRR" -> Sanctuary; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple305._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple305._2)) },
    { val refactorTuple306 = "S.PAL" -> Palace; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple306._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple306._2)) },
    { val refactorTuple104 = "S.MNMT" -> Monument; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple104._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple104._2)) },
    { val refactorTuple105 = "S.MNA" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple105._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple105._2)) },
    { val refactorTuple309 = "S.MN" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple309._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple309._2)) },
    { val refactorTuple107 = "S.MNQR" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple107._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple107._2)) },
    { val refactorTuple311 = "S.MNAU" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple311._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple311._2)) },
    { val refactorTuple109 = "S.MNCU" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple109._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple109._2)) },
    { val refactorTuple313 = "S.MNFE" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple313._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple313._2)) },
    { val refactorTuple314 = "S.QUAR" -> Mine; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple314._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple314._2)) },
    { val refactorTuple315 = "L.PRT" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple315._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple315._2)) },
    { val refactorTuple316 = "S.HBR" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple316._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple316._2)) },
    { val refactorTuple114 = "H.HBR" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple114._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple114._2)) },
    { val refactorTuple115 = "S.LTHSE" -> Port; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple115._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple115._2)) },
    { val refactorTuple116 = "S.BDG" -> Bridge; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple116._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple116._2)) },
    { val refactorTuple117 = "S.AQDT" -> Aqueduct; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple117._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple117._2)) },
    { val refactorTuple321 = "R.RD" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple321._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple321._2)) },
    { val refactorTuple322 = "R.RDA" -> Infrastructure; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple322._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple322._2)) },
    { val refactorTuple120 = "S.AMTH" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple120._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple120._2)) },
    { val refactorTuple121 = "S.THTR" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple121._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple121._2)) },
    { val refactorTuple122 = "S.STDM" -> Theatre; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple122._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple122._2)) },
    { val refactorTuple326 = "H.WRCK" -> Shipwreck; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple326._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple326._2)) },
    { val refactorTuple327 = "S.WRCK" -> Shipwreck; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple327._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple327._2)) },
    { val refactorTuple125 = "S.CAVE" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple125._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple125._2)) },
    { val refactorTuple329 = "H.CAVE" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple329._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple329._2)) },
    { val refactorTuple330 = "T.CAVE" -> Cave; (_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(refactorTuple330._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple330._2)) }
  )

  def geonames(fcode: com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode): Option[com.histmersion.api.catalogue.SiteTypes.SiteType.Id] =
    Option(fcode).map((_).value.trim.toUpperCase(java.util.Locale.ROOT)).filter(_.nonEmpty).flatMap { c =>
      geonamesCodes.get(_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode(c)).orElse(Option.when(c.startsWith("P."))(Settlement))
    }

  val ArchaeologicalSite = "Q839954"
  val HumanSettlement = "Q486972"

  /** The comment beside each id is its Wikidata label. */
  val wikidataClasses: Map[com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class, com.histmersion.api.catalogue.SiteTypes.SiteType.Id] = Map(
    { val refactorTuple331 = HumanSettlement -> Settlement; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple331._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple331._2)) },
    { val refactorTuple129 = "Q15661340" -> Settlement; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple129._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple129._2)) } /* ancient city */,
    { val refactorTuple333 = "Q515" -> Settlement; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple333._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple333._2)) } /* city */,
    { val refactorTuple131 = "Q15253706" -> Settlement; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple131._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple131._2)) } /* city */,
    { val refactorTuple132 = "Q755017" -> Settlement; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple132._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple132._2)) } /* tell */,
    { val refactorTuple336 = "Q532" -> Settlement; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple336._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple336._2)) } /* village */,
    { val refactorTuple337 = "Q3957" -> Settlement; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple337._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple337._2)) } /* town */,
    { val refactorTuple338 = "Q1549591" -> Settlement; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple338._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple338._2)) } /* big city */,
    { val refactorTuple339 = "Q5084" -> Settlement; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple339._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple339._2)) } /* hamlet */,
    { val refactorTuple340 = "Q148837" -> Settlement; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple340._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple340._2)) } /* polis */,
    { val refactorTuple138 = "Q3947" -> Villa; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple138._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple138._2)) } /* house */,
    { val refactorTuple139 = "Q3950" -> Villa; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple139._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple139._2)) } /* villa */,
    { val refactorTuple140 = "Q918230" -> Villa; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple140._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple140._2)) } /* Roman villa */,
    { val refactorTuple141 = "Q131596" -> Villa; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple141._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple141._2)) } /* farm */,
    { val refactorTuple142 = "Q57821" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple142._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple142._2)) } /* fortification */,
    { val refactorTuple143 = "Q1785071" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple143._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple143._2)) } /* fort */,
    { val refactorTuple347 = "Q23413" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple347._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple347._2)) } /* castle */,
    { val refactorTuple348 = "Q744099" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple348._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple348._2)) } /* hillfort */,
    { val refactorTuple349 = "Q88291" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple349._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple349._2)) } /* citadel */,
    { val refactorTuple350 = "Q16748868" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple350._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple350._2)) } /* city walls */,
    { val refactorTuple351 = "Q192601" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple351._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple351._2)) } /* oppidum */,
    { val refactorTuple149 = "Q57831" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple149._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple149._2)) } /* fortress */,
    { val refactorTuple150 = "Q82117" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple150._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple150._2)) } /* city gate */,
    { val refactorTuple151 = "Q57346" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple151._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple151._2)) } /* defensive wall */,
    { val refactorTuple152 = "Q947103" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple152._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple152._2)) } /* watchtower */,
    { val refactorTuple356 = "Q20034791" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple356._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple356._2)) } /* defensive tower */,
    { val refactorTuple154 = "Q81917" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple154._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple154._2)) } /* fortified tower */,
    { val refactorTuple155 = "Q91312" -> Fortification; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple155._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple155._2)) } /* tower house */,
    { val refactorTuple156 = "Q12518" -> Tower; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple156._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple156._2)) } /* tower */,
    { val refactorTuple360 = "Q853854" -> Tower; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple360._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple360._2)) } /* clock tower */,
    { val refactorTuple361 = "Q200334" -> Tower; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple361._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple361._2)) } /* bell tower */,
    { val refactorTuple362 = "Q48356" -> Tower; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple362._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple362._2)) } /* minaret */,
    { val refactorTuple363 = "Q12280" -> Bridge; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple363._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple363._2)) } /* bridge */,
    { val refactorTuple161 = "Q474" -> Aqueduct; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple161._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple161._2)) } /* aqueduct */,
    { val refactorTuple365 = "Q34442" -> Infrastructure; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple365._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple365._2)) } /* road */,
    { val refactorTuple163 = "Q44539" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple163._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple163._2)) } /* temple */,
    { val refactorTuple164 = "Q29553" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple164._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple164._2)) } /* sanctuary */,
    { val refactorTuple165 = "Q16970" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple165._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple165._2)) } /* church building */,
    { val refactorTuple166 = "Q697295" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple166._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple166._2)) } /* shrine */,
    { val refactorTuple370 = "Q44613" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple370._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple370._2)) } /* monastery */,
    { val refactorTuple371 = "Q32815" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple371._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple371._2)) } /* mosque */,
    { val refactorTuple372 = "Q34627" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple372._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple372._2)) } /* synagogue */,
    { val refactorTuple373 = "Q1088552" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple373._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple373._2)) } /* Catholic church building */,
    { val refactorTuple374 = "Q24398318" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple374._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple374._2)) } /* religious building */,
    { val refactorTuple172 = "Q267596" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple172._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple172._2)) } /* ancient Greek temple */,
    { val refactorTuple376 = "Q108325" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple376._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple376._2)) } /* chapel */,
    { val refactorTuple377 = "Q2031836" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple377._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple377._2)) } /* Eastern Orthodox church building */,
    { val refactorTuple175 = "Q2977" -> Sanctuary; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple175._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple175._2)) } /* cathedral */,
    { val refactorTuple176 = "Q381885" -> Burial; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple176._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple176._2)) } /* tomb */,
    { val refactorTuple177 = "Q39614" -> Burial; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple177._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple177._2)) } /* cemetery */,
    { val refactorTuple381 = "Q200141" -> Burial; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple381._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple381._2)) } /* necropolis */,
    { val refactorTuple179 = "Q34023" -> Burial; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple179._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple179._2)) } /* tumulus (a kurgan is one) */,
    { val refactorTuple180 = "Q5737" -> Burial; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple180._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple180._2)) } /* burial mound */,
    { val refactorTuple384 = "Q12516" -> Burial; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple384._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple384._2)) } /* pyramid */,
    { val refactorTuple182 = "Q173387" -> Burial; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple182._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple182._2)) } /* grave */,
    { val refactorTuple386 = "Q162875" -> Burial; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple386._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple386._2)) } /* mausoleum */,
    { val refactorTuple184 = "Q820477" -> Mine; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple184._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple184._2)) } /* mine */,
    { val refactorTuple185 = "Q188040" -> Mine; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple185._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple185._2)) } /* quarry */,
    { val refactorTuple186 = "Q44782" -> Port; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple186._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple186._2)) } /* port */,
    { val refactorTuple390 = "Q283202" -> Port; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple390._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple390._2)) } /* harbor */,
    { val refactorTuple391 = "Q852190" -> Shipwreck; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple391._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple391._2)) } /* shipwreck */,
    { val refactorTuple392 = "Q35509" -> Cave; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple392._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple392._2)) } /* cave */,
    { val refactorTuple190 = "Q1149405" -> Cave; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple190._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple190._2)) } /* rock shelter */,
    { val refactorTuple394 = "Q16560" -> Palace; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple394._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple394._2)) } /* palace */,
    { val refactorTuple395 = "Q4989906" -> Monument; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple395._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple395._2)) } /* monument */,
    { val refactorTuple193 = "Q164240" -> Monument; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple193._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple193._2)) } /* megalith */,
    { val refactorTuple397 = "Q10521078" -> Burial; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple397._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple397._2)) } /* megalithic tomb */,
    { val refactorTuple195 = "Q101659" -> Monument; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple195._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple195._2)) } /* dolmen */,
    { val refactorTuple399 = "Q193475" -> Monument; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple399._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple399._2)) } /* menhir */,
    { val refactorTuple197 = "Q1935728" -> Monument; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple197._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple197._2)) } /* stone circle */,
    { val refactorTuple401 = "Q170980" -> Monument; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple401._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple401._2)) } /* obelisk */,
    { val refactorTuple402 = "Q179700" -> Monument; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple402._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple402._2)) } /* statue */,
    { val refactorTuple200 = "Q54831" -> Theatre; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple200._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple200._2)) } /* amphitheatre */,
    { val refactorTuple404 = "Q24354" -> Theatre; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple404._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple404._2)) } /* theatre building */,
    { val refactorTuple405 = "Q1211146" -> Findspot; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple405._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple405._2)) } /* rock art */,
    { val refactorTuple203 = ArchaeologicalSite -> Findspot; (_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class(refactorTuple203._1), _root_.com.histmersion.api.catalogue.SiteTypes.SiteType.Id(refactorTuple203._2)) }
  )

  def wikidata(p31: List[com.histmersion.api.catalogue.SiteTypes.WikidataP31], superclasses: List[com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class], importClass: Option[com.histmersion.api.catalogue.CatalogRecord.Wikidata.ImportClass]): Option[com.histmersion.api.catalogue.SiteTypes.SiteType.Id] =
    wikidataOwn(p31)
      .orElse(superclasses.flatMap({ val refactorFunction4 = wikidataClasses.get; (refactorValue8) => refactorFunction4(_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class((refactorValue8).value)) }).headOption)
      .orElse(importClass.flatMap({ val refactorFunction5 = wikidataClasses.get; (refactorValue9) => refactorFunction5(_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class((refactorValue9).value)) }))

  def wikidataOwn(p31: List[com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class]): Option[com.histmersion.api.catalogue.SiteTypes.SiteType.Id] = best(p31.map((_).value.trim).flatMap(wikidataClasses.get))

  def p31Of(json: Option[com.histmersion.api.catalogue.CatalogRecord.Wikidata.P31Json]): List[com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class] =
    json.toList.flatMap(s => "Q[0-9]+".r.findAllIn((s).value).toList)

  def of(
      placeId: com.histmersion.api.catalogue.Place.Id,
      placeKind: com.histmersion.api.catalogue.Place.Kind.Wire,
      stored: Option[com.histmersion.api.catalogue.SiteTypes.SiteType.Id],
      featureTypes: Option[com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType],
      fcode: Option[com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode],
      wikidataClass: Option[com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class],
      p31: List[com.histmersion.api.catalogue.SiteTypes.OfP31] = Nil
  ): Option[com.histmersion.api.catalogue.SiteTypes.SiteType.Id] =
    if placeKind != _root_.com.histmersion.api.catalogue.Place.Kind.Wire(com.histmersion.api.catalogue.Place.Kind.Site) && placeKind != _root_.com.histmersion.api.catalogue.Place.Kind.Wire(com.histmersion.api.catalogue.Place.Kind.Feature)
    then None
    else
      stored
        .filter(_root_.com.histmersion.api.catalogue.SiteTypes.ByIdPart1((byId).value).contains)
        .orElse(featureTypes.flatMap({ val refactorFunction3 = pleiades; (refactorValue5) => refactorFunction3(_root_.com.histmersion.api.catalogue.SiteTypes.Pleiades.FeatureType((refactorValue5).value)) }))
        .orElse(fcode.flatMap({ val refactorFunction2 = geonames; (refactorValue10) => refactorFunction2(_root_.com.histmersion.api.catalogue.SiteTypes.Geonames.Fcode((refactorValue10).value)) }))
        .orElse(wikidataOwn(p31))
        .orElse(wikidataClass.flatMap(c => wikidataClasses.get(_root_.com.histmersion.api.catalogue.CatalogRecord.Wikidata.Class((c).value.trim))))

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

    def summary(r: Report): com.histmersion.api.catalogue.SiteTypes.ReportSummary =
      if (r.dryRun).value then _root_.com.histmersion.api.catalogue.SiteTypes.ReportSummary(s"${r.pending} Wikidata site(s) on the map would be asked about")
      else
        _root_.com.histmersion.api.catalogue.SiteTypes.ReportSummary((List(
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
