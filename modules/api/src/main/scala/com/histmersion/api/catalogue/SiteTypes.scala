package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.Schema
import com.histmersion.api.automation.Creation

/** Each type comes from what the site's gazetteer says: Pleiades' `feature_types` (comma-separated, `temple-2` a
  * variant of `temple`), the GeoNames feature code (`P.PPL` a populated place, `S.RUIN` ruins, `S.TMB` a tomb),
  * Wikidata's P31 classes walked up P279 through [[wikidataClasses]].
  */
object SiteTypes:
  object Pleiades:
    type FeatureType = FeatureType.T
    object FeatureType extends Newtype[String, Pure]
  object Geonames:
    type Fcode = Fcode.T
    object Fcode extends Newtype[String, Pure]
  type ReportSummary = ReportSummary.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ReportSummary extends Newtype[String, Pure]

  export SiteType.{Icon as IconResult, Id as IconId}
  export CatalogRecord.Wikidata.Class as WikidataP31
  export Place.Kind.Wire as OfPlaceKind
  export CatalogRecord.Wikidata.Class as OfP31

  final case class SiteType(id: SiteType.Id, label: SiteType.Label, icon: SiteType.Icon)

  object SiteType:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Icon = Icon.T
    object Icon extends Newtype[String, Pure]

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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val all: List[SiteType] = List(
    SiteType(SiteType.Id(Settlement), SiteType.Label("settlement"), SiteType.Icon("building-2")),
    SiteType(SiteType.Id(Villa), SiteType.Label("villa, house"), SiteType.Icon("house")),
    SiteType(SiteType.Id(Fortification), SiteType.Label("fortification"), SiteType.Icon("castle")),
    SiteType(SiteType.Id(Sanctuary), SiteType.Label("sanctuary, temple"), SiteType.Icon("landmark")),
    SiteType(SiteType.Id(Burial), SiteType.Label("burial, tomb"), SiteType.Icon("pyramid")),
    SiteType(SiteType.Id(Mine), SiteType.Label("mine, quarry"), SiteType.Icon("pickaxe")),
    SiteType(SiteType.Id(Port), SiteType.Label("port, harbour"), SiteType.Icon("anchor")),
    SiteType(SiteType.Id(Infrastructure), SiteType.Label("road, canal, cistern"), SiteType.Icon("route")),
    SiteType(SiteType.Id(Bridge), SiteType.Label("bridge"), SiteType.Icon("waves")),
    SiteType(SiteType.Id(Aqueduct), SiteType.Label("aqueduct"), SiteType.Icon("droplets")),
    SiteType(SiteType.Id(Tower), SiteType.Label("tower"), SiteType.Icon("shield")),
    SiteType(SiteType.Id(Theatre), SiteType.Label("theatre, amphitheatre"), SiteType.Icon("venetian-mask")),
    SiteType(SiteType.Id(Findspot), SiteType.Label("find-spot, excavation"), SiteType.Icon("shovel")),
    SiteType(SiteType.Id(Cave), SiteType.Label("cave"), SiteType.Icon("door-open")),
    SiteType(SiteType.Id(Palace), SiteType.Label("palace"), SiteType.Icon("crown")),
    SiteType(SiteType.Id(Monument), SiteType.Label("monument, megalith"), SiteType.Icon("milestone")),
    SiteType(SiteType.Id(Shipwreck), SiteType.Label("shipwreck"), SiteType.Icon("ship"))
  )

  private val byId: Map[SiteType.Id, SiteType] = all.map(t => t.id -> t).toMap

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def get(id: SiteType.Id): Option[SiteType] = byId.get(SiteType.Id((id).value.trim.toLowerCase(java.util.Locale.ROOT)))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def icon(id: SiteTypes.IconId): Option[SiteTypes.IconResult] = get(id).map(_.icon)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val pleiadesTypes: Map[Pleiades.FeatureType, SiteType.Id] = Map(
    {
      val refactorTuple = "settlement" -> Settlement;
      (Pleiades.FeatureType(refactorTuple._1), SiteType.Id(refactorTuple._2))
    }, {
      val refactorTuple205 = "urban" -> Settlement;
      (Pleiades.FeatureType(refactorTuple205._1), SiteType.Id(refactorTuple205._2))
    }, {
      val refactorTuple3 = "town" -> Settlement;
      (Pleiades.FeatureType(refactorTuple3._1), SiteType.Id(refactorTuple3._2))
    }, {
      val refactorTuple4 = "city" -> Settlement;
      (Pleiades.FeatureType(refactorTuple4._1), SiteType.Id(refactorTuple4._2))
    }, {
      val refactorTuple5 = "village" -> Settlement;
      (Pleiades.FeatureType(refactorTuple5._1), SiteType.Id(refactorTuple5._2))
    }, {
      val refactorTuple209 = "settlement-modern" -> Settlement;
      (Pleiades.FeatureType(refactorTuple209._1), SiteType.Id(refactorTuple209._2))
    }, {
      val refactorTuple7 = "polis" -> Settlement;
      (Pleiades.FeatureType(refactorTuple7._1), SiteType.Id(refactorTuple7._2))
    }, {
      val refactorTuple211 = "tell" -> Settlement;
      (Pleiades.FeatureType(refactorTuple211._1), SiteType.Id(refactorTuple211._2))
    }, {
      val refactorTuple212 = "townhouse" -> Villa;
      (Pleiades.FeatureType(refactorTuple212._1), SiteType.Id(refactorTuple212._2))
    }, {
      val refactorTuple213 = "villa" -> Villa;
      (Pleiades.FeatureType(refactorTuple213._1), SiteType.Id(refactorTuple213._2))
    }, {
      val refactorTuple11 = "farmstead" -> Villa;
      (Pleiades.FeatureType(refactorTuple11._1), SiteType.Id(refactorTuple11._2))
    }, {
      val refactorTuple215 = "estate" -> Villa;
      (Pleiades.FeatureType(refactorTuple215._1), SiteType.Id(refactorTuple215._2))
    }, {
      val refactorTuple216 = "fort" -> Fortification;
      (Pleiades.FeatureType(refactorTuple216._1), SiteType.Id(refactorTuple216._2))
    }, {
      val refactorTuple14 = "fortlet" -> Fortification;
      (Pleiades.FeatureType(refactorTuple14._1), SiteType.Id(refactorTuple14._2))
    }, {
      val refactorTuple218 = "tower-defensive" -> Fortification;
      (Pleiades.FeatureType(refactorTuple218._1), SiteType.Id(refactorTuple218._2))
    }, {
      val refactorTuple219 = "fortified-settlement" -> Fortification;
      (Pleiades.FeatureType(refactorTuple219._1), SiteType.Id(refactorTuple219._2))
    }, {
      val refactorTuple17 = "city-gate" -> Fortification;
      (Pleiades.FeatureType(refactorTuple17._1), SiteType.Id(refactorTuple17._2))
    }, {
      val refactorTuple221 = "city-wall" -> Fortification;
      (Pleiades.FeatureType(refactorTuple221._1), SiteType.Id(refactorTuple221._2))
    }, {
      val refactorTuple222 = "fortress" -> Fortification;
      (Pleiades.FeatureType(refactorTuple222._1), SiteType.Id(refactorTuple222._2))
    }, {
      val refactorTuple20 = "castle" -> Fortification;
      (Pleiades.FeatureType(refactorTuple20._1), SiteType.Id(refactorTuple20._2))
    }, {
      val refactorTuple224 = "camp" -> Fortification;
      (Pleiades.FeatureType(refactorTuple224._1), SiteType.Id(refactorTuple224._2))
    }, {
      val refactorTuple225 = "hillfort" -> Fortification;
      (Pleiades.FeatureType(refactorTuple225._1), SiteType.Id(refactorTuple225._2))
    }, {
      val refactorTuple23 = "wall" -> Fortification;
      (Pleiades.FeatureType(refactorTuple23._1), SiteType.Id(refactorTuple23._2))
    }, {
      val refactorTuple24 = "acropolis" -> Fortification;
      (Pleiades.FeatureType(refactorTuple24._1), SiteType.Id(refactorTuple24._2))
    }, {
      val refactorTuple228 = "temple" -> Sanctuary;
      (Pleiades.FeatureType(refactorTuple228._1), SiteType.Id(refactorTuple228._2))
    }, {
      val refactorTuple26 = "sanctuary" -> Sanctuary;
      (Pleiades.FeatureType(refactorTuple26._1), SiteType.Id(refactorTuple26._2))
    }, {
      val refactorTuple27 = "church" -> Sanctuary;
      (Pleiades.FeatureType(refactorTuple27._1), SiteType.Id(refactorTuple27._2))
    }, {
      val refactorTuple231 = "shrine" -> Sanctuary;
      (Pleiades.FeatureType(refactorTuple231._1), SiteType.Id(refactorTuple231._2))
    }, {
      val refactorTuple232 = "monastery" -> Sanctuary;
      (Pleiades.FeatureType(refactorTuple232._1), SiteType.Id(refactorTuple232._2))
    }, {
      val refactorTuple233 = "mosque" -> Sanctuary;
      (Pleiades.FeatureType(refactorTuple233._1), SiteType.Id(refactorTuple233._2))
    }, {
      val refactorTuple234 = "synagogue" -> Sanctuary;
      (Pleiades.FeatureType(refactorTuple234._1), SiteType.Id(refactorTuple234._2))
    }, {
      val refactorTuple32 = "mithraeum" -> Sanctuary;
      (Pleiades.FeatureType(refactorTuple32._1), SiteType.Id(refactorTuple32._2))
    }, {
      val refactorTuple236 = "basilica" -> Sanctuary;
      (Pleiades.FeatureType(refactorTuple236._1), SiteType.Id(refactorTuple236._2))
    }, {
      val refactorTuple237 = "altar" -> Sanctuary;
      (Pleiades.FeatureType(refactorTuple237._1), SiteType.Id(refactorTuple237._2))
    }, {
      val refactorTuple35 = "oracle" -> Sanctuary;
      (Pleiades.FeatureType(refactorTuple35._1), SiteType.Id(refactorTuple35._2))
    }, {
      val refactorTuple36 = "tomb" -> Burial;
      (Pleiades.FeatureType(refactorTuple36._1), SiteType.Id(refactorTuple36._2))
    }, {
      val refactorTuple240 = "tumulus" -> Burial;
      (Pleiades.FeatureType(refactorTuple240._1), SiteType.Id(refactorTuple240._2))
    }, {
      val refactorTuple241 = "cemetery" -> Burial;
      (Pleiades.FeatureType(refactorTuple241._1), SiteType.Id(refactorTuple241._2))
    }, {
      val refactorTuple39 = "necropolis" -> Burial;
      (Pleiades.FeatureType(refactorTuple39._1), SiteType.Id(refactorTuple39._2))
    }, {
      val refactorTuple40 = "pyramid" -> Burial;
      (Pleiades.FeatureType(refactorTuple40._1), SiteType.Id(refactorTuple40._2))
    }, {
      val refactorTuple41 = "mausoleum" -> Burial;
      (Pleiades.FeatureType(refactorTuple41._1), SiteType.Id(refactorTuple41._2))
    }, {
      val refactorTuple42 = "catacomb" -> Burial;
      (Pleiades.FeatureType(refactorTuple42._1), SiteType.Id(refactorTuple42._2))
    }, {
      val refactorTuple43 = "mine" -> Mine; (Pleiades.FeatureType(refactorTuple43._1), SiteType.Id(refactorTuple43._2))
    }, {
      val refactorTuple247 = "quarry" -> Mine;
      (Pleiades.FeatureType(refactorTuple247._1), SiteType.Id(refactorTuple247._2))
    }, {
      val refactorTuple45 = "port" -> Port; (Pleiades.FeatureType(refactorTuple45._1), SiteType.Id(refactorTuple45._2))
    }, {
      val refactorTuple46 = "harbor" -> Port;
      (Pleiades.FeatureType(refactorTuple46._1), SiteType.Id(refactorTuple46._2))
    }, {
      val refactorTuple47 = "harbour" -> Port;
      (Pleiades.FeatureType(refactorTuple47._1), SiteType.Id(refactorTuple47._2))
    }, {
      val refactorTuple251 = "anchorage" -> Port;
      (Pleiades.FeatureType(refactorTuple251._1), SiteType.Id(refactorTuple251._2))
    }, {
      val refactorTuple252 = "lighthouse" -> Port;
      (Pleiades.FeatureType(refactorTuple252._1), SiteType.Id(refactorTuple252._2))
    }, {
      val refactorTuple253 = "road" -> Infrastructure;
      (Pleiades.FeatureType(refactorTuple253._1), SiteType.Id(refactorTuple253._2))
    }, {
      val refactorTuple51 = "bridge" -> Bridge;
      (Pleiades.FeatureType(refactorTuple51._1), SiteType.Id(refactorTuple51._2))
    }, {
      val refactorTuple255 = "aqueduct" -> Aqueduct;
      (Pleiades.FeatureType(refactorTuple255._1), SiteType.Id(refactorTuple255._2))
    }, {
      val refactorTuple256 = "station" -> Infrastructure;
      (Pleiades.FeatureType(refactorTuple256._1), SiteType.Id(refactorTuple256._2))
    }, {
      val refactorTuple54 = "milestone" -> Infrastructure;
      (Pleiades.FeatureType(refactorTuple54._1), SiteType.Id(refactorTuple54._2))
    }, {
      val refactorTuple258 = "canal" -> Infrastructure;
      (Pleiades.FeatureType(refactorTuple258._1), SiteType.Id(refactorTuple258._2))
    }, {
      val refactorTuple259 = "dam" -> Infrastructure;
      (Pleiades.FeatureType(refactorTuple259._1), SiteType.Id(refactorTuple259._2))
    }, {
      val refactorTuple57 = "cistern" -> Infrastructure;
      (Pleiades.FeatureType(refactorTuple57._1), SiteType.Id(refactorTuple57._2))
    }, {
      val refactorTuple261 = "pass" -> Infrastructure;
      (Pleiades.FeatureType(refactorTuple261._1), SiteType.Id(refactorTuple261._2))
    }, {
      val refactorTuple262 = "bath" -> Infrastructure;
      (Pleiades.FeatureType(refactorTuple262._1), SiteType.Id(refactorTuple262._2))
    }, {
      val refactorTuple263 = "theatre" -> Theatre;
      (Pleiades.FeatureType(refactorTuple263._1), SiteType.Id(refactorTuple263._2))
    }, {
      val refactorTuple61 = "theater" -> Theatre;
      (Pleiades.FeatureType(refactorTuple61._1), SiteType.Id(refactorTuple61._2))
    }, {
      val refactorTuple265 = "amphitheatre" -> Theatre;
      (Pleiades.FeatureType(refactorTuple265._1), SiteType.Id(refactorTuple265._2))
    }, {
      val refactorTuple63 = "amphitheater" -> Theatre;
      (Pleiades.FeatureType(refactorTuple63._1), SiteType.Id(refactorTuple63._2))
    }, {
      val refactorTuple64 = "odeon" -> Theatre;
      (Pleiades.FeatureType(refactorTuple64._1), SiteType.Id(refactorTuple64._2))
    }, {
      val refactorTuple65 = "circus" -> Theatre;
      (Pleiades.FeatureType(refactorTuple65._1), SiteType.Id(refactorTuple65._2))
    }, {
      val refactorTuple66 = "stadium" -> Theatre;
      (Pleiades.FeatureType(refactorTuple66._1), SiteType.Id(refactorTuple66._2))
    }, {
      val refactorTuple67 = "hippodrome" -> Theatre;
      (Pleiades.FeatureType(refactorTuple67._1), SiteType.Id(refactorTuple67._2))
    }, {
      val refactorTuple271 = "findspot" -> Findspot;
      (Pleiades.FeatureType(refactorTuple271._1), SiteType.Id(refactorTuple271._2))
    }, {
      val refactorTuple272 = "archaeological-site" -> Findspot;
      (Pleiades.FeatureType(refactorTuple272._1), SiteType.Id(refactorTuple272._2))
    }, {
      val refactorTuple70 = "rock-art" -> Findspot;
      (Pleiades.FeatureType(refactorTuple70._1), SiteType.Id(refactorTuple70._2))
    }, {
      val refactorTuple71 = "inscription" -> Findspot;
      (Pleiades.FeatureType(refactorTuple71._1), SiteType.Id(refactorTuple71._2))
    }, {
      val refactorTuple275 = "hoard" -> Findspot;
      (Pleiades.FeatureType(refactorTuple275._1), SiteType.Id(refactorTuple275._2))
    }, {
      val refactorTuple73 = "cave" -> Cave; (Pleiades.FeatureType(refactorTuple73._1), SiteType.Id(refactorTuple73._2))
    }, {
      val refactorTuple277 = "grotto" -> Cave;
      (Pleiades.FeatureType(refactorTuple277._1), SiteType.Id(refactorTuple277._2))
    }, {
      val refactorTuple75 = "palace" -> Palace;
      (Pleiades.FeatureType(refactorTuple75._1), SiteType.Id(refactorTuple75._2))
    }, {
      val refactorTuple76 = "monument" -> Monument;
      (Pleiades.FeatureType(refactorTuple76._1), SiteType.Id(refactorTuple76._2))
    }, {
      val refactorTuple77 = "megalith" -> Monument;
      (Pleiades.FeatureType(refactorTuple77._1), SiteType.Id(refactorTuple77._2))
    }, {
      val refactorTuple281 = "arch" -> Monument;
      (Pleiades.FeatureType(refactorTuple281._1), SiteType.Id(refactorTuple281._2))
    }, {
      val refactorTuple282 = "stele" -> Monument;
      (Pleiades.FeatureType(refactorTuple282._1), SiteType.Id(refactorTuple282._2))
    }, {
      val refactorTuple283 = "obelisk" -> Monument;
      (Pleiades.FeatureType(refactorTuple283._1), SiteType.Id(refactorTuple283._2))
    }, {
      val refactorTuple81 = "statue" -> Monument;
      (Pleiades.FeatureType(refactorTuple81._1), SiteType.Id(refactorTuple81._2))
    }, {
      val refactorTuple82 = "column" -> Monument;
      (Pleiades.FeatureType(refactorTuple82._1), SiteType.Id(refactorTuple82._2))
    }, {
      val refactorTuple83 = "shipwreck" -> Shipwreck;
      (Pleiades.FeatureType(refactorTuple83._1), SiteType.Id(refactorTuple83._2))
    }, {
      val refactorTuple287 = "wreck" -> Shipwreck;
      (Pleiades.FeatureType(refactorTuple287._1), SiteType.Id(refactorTuple287._2))
    }
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val preference: List[SiteType.Id] = List(
    SiteType.Id(Palace),
    SiteType.Id(Sanctuary),
    SiteType.Id(Theatre),
    SiteType.Id(Burial),
    SiteType.Id(Fortification),
    SiteType.Id(Tower),
    SiteType.Id(Mine),
    SiteType.Id(Port),
    SiteType.Id(Shipwreck),
    SiteType.Id(Cave),
    SiteType.Id(Monument),
    SiteType.Id(Villa),
    SiteType.Id(Aqueduct),
    SiteType.Id(Bridge),
    SiteType.Id(Infrastructure),
    SiteType.Id(Settlement),
    SiteType.Id(Findspot)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def best(types: Iterable[SiteType.Id]): Option[SiteType.Id] =
    (types.toList.distinct
      .sortBy(t => preference.indexOf(t) match { case -1 => preference.size; case i => i })
      .headOption)
      .map(refactorValue6 => SiteType.Id((refactorValue6).value))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pleiades(featureTypes: Pleiades.FeatureType): Option[SiteType.Id] =
    best(
      Option(featureTypes).toList
        .flatMap((_).value.split(","))
        .map(_.trim.toLowerCase(java.util.Locale.ROOT))
        .filter(_.nonEmpty)
        .map(_.replaceAll("-\\d+$", ""))
        .flatMap(t => pleiadesTypes.get(Pleiades.FeatureType(t)))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val geonamesCodes: Map[Geonames.Fcode, SiteType.Id] = Map(
    {
      val refactorTuple288 = "S.RUIN" -> Findspot;
      (Geonames.Fcode(refactorTuple288._1), SiteType.Id(refactorTuple288._2))
    }, {
      val refactorTuple289 = "S.ANS" -> Findspot;
      (Geonames.Fcode(refactorTuple289._1), SiteType.Id(refactorTuple289._2))
    }, {
      val refactorTuple290 = "S.HSTS" -> Findspot;
      (Geonames.Fcode(refactorTuple290._1), SiteType.Id(refactorTuple290._2))
    },
    { val refactorTuple88 = "S.TMB" -> Burial; (Geonames.Fcode(refactorTuple88._1), SiteType.Id(refactorTuple88._2)) },
    { val refactorTuple89 = "S.CMTY" -> Burial; (Geonames.Fcode(refactorTuple89._1), SiteType.Id(refactorTuple89._2)) }, {
      val refactorTuple293 = "S.PYR" -> Burial; (Geonames.Fcode(refactorTuple293._1), SiteType.Id(refactorTuple293._2))
    },
    { val refactorTuple91 = "S.PYRS" -> Burial; (Geonames.Fcode(refactorTuple91._1), SiteType.Id(refactorTuple91._2)) },
    { val refactorTuple92 = "S.GRVE" -> Burial; (Geonames.Fcode(refactorTuple92._1), SiteType.Id(refactorTuple92._2)) }, {
      val refactorTuple296 = "S.CSTL" -> Fortification;
      (Geonames.Fcode(refactorTuple296._1), SiteType.Id(refactorTuple296._2))
    }, {
      val refactorTuple297 = "S.FT" -> Fortification;
      (Geonames.Fcode(refactorTuple297._1), SiteType.Id(refactorTuple297._2))
    }, {
      val refactorTuple298 = "S.WALLA" -> Fortification;
      (Geonames.Fcode(refactorTuple298._1), SiteType.Id(refactorTuple298._2))
    }, {
      val refactorTuple299 = "S.TOWR" -> Tower; (Geonames.Fcode(refactorTuple299._1), SiteType.Id(refactorTuple299._2))
    }, {
      val refactorTuple97 = "S.TMPL" -> Sanctuary; (Geonames.Fcode(refactorTuple97._1), SiteType.Id(refactorTuple97._2))
    }, {
      val refactorTuple301 = "S.CH" -> Sanctuary;
      (Geonames.Fcode(refactorTuple301._1), SiteType.Id(refactorTuple301._2))
    }, {
      val refactorTuple99 = "S.MSTY" -> Sanctuary; (Geonames.Fcode(refactorTuple99._1), SiteType.Id(refactorTuple99._2))
    }, {
      val refactorTuple303 = "S.MSQE" -> Sanctuary;
      (Geonames.Fcode(refactorTuple303._1), SiteType.Id(refactorTuple303._2))
    }, {
      val refactorTuple101 = "S.SHRN" -> Sanctuary;
      (Geonames.Fcode(refactorTuple101._1), SiteType.Id(refactorTuple101._2))
    }, {
      val refactorTuple305 = "S.CTRR" -> Sanctuary;
      (Geonames.Fcode(refactorTuple305._1), SiteType.Id(refactorTuple305._2))
    }, {
      val refactorTuple306 = "S.PAL" -> Palace; (Geonames.Fcode(refactorTuple306._1), SiteType.Id(refactorTuple306._2))
    }, {
      val refactorTuple104 = "S.MNMT" -> Monument;
      (Geonames.Fcode(refactorTuple104._1), SiteType.Id(refactorTuple104._2))
    },
    { val refactorTuple105 = "S.MNA" -> Mine; (Geonames.Fcode(refactorTuple105._1), SiteType.Id(refactorTuple105._2)) },
    { val refactorTuple309 = "S.MN" -> Mine; (Geonames.Fcode(refactorTuple309._1), SiteType.Id(refactorTuple309._2)) }, {
      val refactorTuple107 = "S.MNQR" -> Mine; (Geonames.Fcode(refactorTuple107._1), SiteType.Id(refactorTuple107._2))
    }, {
      val refactorTuple311 = "S.MNAU" -> Mine; (Geonames.Fcode(refactorTuple311._1), SiteType.Id(refactorTuple311._2))
    }, {
      val refactorTuple109 = "S.MNCU" -> Mine; (Geonames.Fcode(refactorTuple109._1), SiteType.Id(refactorTuple109._2))
    }, {
      val refactorTuple313 = "S.MNFE" -> Mine; (Geonames.Fcode(refactorTuple313._1), SiteType.Id(refactorTuple313._2))
    }, {
      val refactorTuple314 = "S.QUAR" -> Mine; (Geonames.Fcode(refactorTuple314._1), SiteType.Id(refactorTuple314._2))
    },
    { val refactorTuple315 = "L.PRT" -> Port; (Geonames.Fcode(refactorTuple315._1), SiteType.Id(refactorTuple315._2)) },
    { val refactorTuple316 = "S.HBR" -> Port; (Geonames.Fcode(refactorTuple316._1), SiteType.Id(refactorTuple316._2)) },
    { val refactorTuple114 = "H.HBR" -> Port; (Geonames.Fcode(refactorTuple114._1), SiteType.Id(refactorTuple114._2)) }, {
      val refactorTuple115 = "S.LTHSE" -> Port; (Geonames.Fcode(refactorTuple115._1), SiteType.Id(refactorTuple115._2))
    }, {
      val refactorTuple116 = "S.BDG" -> Bridge; (Geonames.Fcode(refactorTuple116._1), SiteType.Id(refactorTuple116._2))
    }, {
      val refactorTuple117 = "S.AQDT" -> Aqueduct;
      (Geonames.Fcode(refactorTuple117._1), SiteType.Id(refactorTuple117._2))
    }, {
      val refactorTuple321 = "R.RD" -> Infrastructure;
      (Geonames.Fcode(refactorTuple321._1), SiteType.Id(refactorTuple321._2))
    }, {
      val refactorTuple322 = "R.RDA" -> Infrastructure;
      (Geonames.Fcode(refactorTuple322._1), SiteType.Id(refactorTuple322._2))
    }, {
      val refactorTuple120 = "S.AMTH" -> Theatre;
      (Geonames.Fcode(refactorTuple120._1), SiteType.Id(refactorTuple120._2))
    }, {
      val refactorTuple121 = "S.THTR" -> Theatre;
      (Geonames.Fcode(refactorTuple121._1), SiteType.Id(refactorTuple121._2))
    }, {
      val refactorTuple122 = "S.STDM" -> Theatre;
      (Geonames.Fcode(refactorTuple122._1), SiteType.Id(refactorTuple122._2))
    }, {
      val refactorTuple326 = "H.WRCK" -> Shipwreck;
      (Geonames.Fcode(refactorTuple326._1), SiteType.Id(refactorTuple326._2))
    }, {
      val refactorTuple327 = "S.WRCK" -> Shipwreck;
      (Geonames.Fcode(refactorTuple327._1), SiteType.Id(refactorTuple327._2))
    }, {
      val refactorTuple125 = "S.CAVE" -> Cave; (Geonames.Fcode(refactorTuple125._1), SiteType.Id(refactorTuple125._2))
    }, {
      val refactorTuple329 = "H.CAVE" -> Cave; (Geonames.Fcode(refactorTuple329._1), SiteType.Id(refactorTuple329._2))
    },
    { val refactorTuple330 = "T.CAVE" -> Cave; (Geonames.Fcode(refactorTuple330._1), SiteType.Id(refactorTuple330._2)) }
  )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def geonames(fcode: Geonames.Fcode): Option[SiteType.Id] =
    Option(fcode).map((_).value.trim.toUpperCase(java.util.Locale.ROOT)).filter(_.nonEmpty).flatMap { c =>
      geonamesCodes.get(Geonames.Fcode(c)).orElse(Option.when(c.startsWith("P."))(SiteType.Id(Settlement)))
    }

  val ArchaeologicalSite = "Q839954"
  val HumanSettlement = "Q486972"

  /** The comment beside each id is its Wikidata label. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val wikidataClasses: Map[CatalogRecord.Wikidata.Class, SiteType.Id] = Map(
    {
      val refactorTuple331 = HumanSettlement -> Settlement;
      (CatalogRecord.Wikidata.Class(refactorTuple331._1), SiteType.Id(refactorTuple331._2))
    }, {
      val refactorTuple129 = "Q15661340" -> Settlement;
      (CatalogRecord.Wikidata.Class(refactorTuple129._1), SiteType.Id(refactorTuple129._2))
    } /* ancient city */, {
      val refactorTuple333 = "Q515" -> Settlement;
      (CatalogRecord.Wikidata.Class(refactorTuple333._1), SiteType.Id(refactorTuple333._2))
    } /* city */, {
      val refactorTuple131 = "Q15253706" -> Settlement;
      (CatalogRecord.Wikidata.Class(refactorTuple131._1), SiteType.Id(refactorTuple131._2))
    } /* city */, {
      val refactorTuple132 = "Q755017" -> Settlement;
      (CatalogRecord.Wikidata.Class(refactorTuple132._1), SiteType.Id(refactorTuple132._2))
    } /* tell */, {
      val refactorTuple336 = "Q532" -> Settlement;
      (CatalogRecord.Wikidata.Class(refactorTuple336._1), SiteType.Id(refactorTuple336._2))
    } /* village */, {
      val refactorTuple337 = "Q3957" -> Settlement;
      (CatalogRecord.Wikidata.Class(refactorTuple337._1), SiteType.Id(refactorTuple337._2))
    } /* town */, {
      val refactorTuple338 = "Q1549591" -> Settlement;
      (CatalogRecord.Wikidata.Class(refactorTuple338._1), SiteType.Id(refactorTuple338._2))
    } /* big city */, {
      val refactorTuple339 = "Q5084" -> Settlement;
      (CatalogRecord.Wikidata.Class(refactorTuple339._1), SiteType.Id(refactorTuple339._2))
    } /* hamlet */, {
      val refactorTuple340 = "Q148837" -> Settlement;
      (CatalogRecord.Wikidata.Class(refactorTuple340._1), SiteType.Id(refactorTuple340._2))
    } /* polis */, {
      val refactorTuple138 = "Q3947" -> Villa;
      (CatalogRecord.Wikidata.Class(refactorTuple138._1), SiteType.Id(refactorTuple138._2))
    } /* house */, {
      val refactorTuple139 = "Q3950" -> Villa;
      (CatalogRecord.Wikidata.Class(refactorTuple139._1), SiteType.Id(refactorTuple139._2))
    } /* villa */, {
      val refactorTuple140 = "Q918230" -> Villa;
      (CatalogRecord.Wikidata.Class(refactorTuple140._1), SiteType.Id(refactorTuple140._2))
    } /* Roman villa */, {
      val refactorTuple141 = "Q131596" -> Villa;
      (CatalogRecord.Wikidata.Class(refactorTuple141._1), SiteType.Id(refactorTuple141._2))
    } /* farm */, {
      val refactorTuple142 = "Q57821" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple142._1), SiteType.Id(refactorTuple142._2))
    } /* fortification */, {
      val refactorTuple143 = "Q1785071" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple143._1), SiteType.Id(refactorTuple143._2))
    } /* fort */, {
      val refactorTuple347 = "Q23413" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple347._1), SiteType.Id(refactorTuple347._2))
    } /* castle */, {
      val refactorTuple348 = "Q744099" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple348._1), SiteType.Id(refactorTuple348._2))
    } /* hillfort */, {
      val refactorTuple349 = "Q88291" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple349._1), SiteType.Id(refactorTuple349._2))
    } /* citadel */, {
      val refactorTuple350 = "Q16748868" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple350._1), SiteType.Id(refactorTuple350._2))
    } /* city walls */, {
      val refactorTuple351 = "Q192601" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple351._1), SiteType.Id(refactorTuple351._2))
    } /* oppidum */, {
      val refactorTuple149 = "Q57831" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple149._1), SiteType.Id(refactorTuple149._2))
    } /* fortress */, {
      val refactorTuple150 = "Q82117" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple150._1), SiteType.Id(refactorTuple150._2))
    } /* city gate */, {
      val refactorTuple151 = "Q57346" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple151._1), SiteType.Id(refactorTuple151._2))
    } /* defensive wall */, {
      val refactorTuple152 = "Q947103" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple152._1), SiteType.Id(refactorTuple152._2))
    } /* watchtower */, {
      val refactorTuple356 = "Q20034791" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple356._1), SiteType.Id(refactorTuple356._2))
    } /* defensive tower */, {
      val refactorTuple154 = "Q81917" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple154._1), SiteType.Id(refactorTuple154._2))
    } /* fortified tower */, {
      val refactorTuple155 = "Q91312" -> Fortification;
      (CatalogRecord.Wikidata.Class(refactorTuple155._1), SiteType.Id(refactorTuple155._2))
    } /* tower house */, {
      val refactorTuple156 = "Q12518" -> Tower;
      (CatalogRecord.Wikidata.Class(refactorTuple156._1), SiteType.Id(refactorTuple156._2))
    } /* tower */, {
      val refactorTuple360 = "Q853854" -> Tower;
      (CatalogRecord.Wikidata.Class(refactorTuple360._1), SiteType.Id(refactorTuple360._2))
    } /* clock tower */, {
      val refactorTuple361 = "Q200334" -> Tower;
      (CatalogRecord.Wikidata.Class(refactorTuple361._1), SiteType.Id(refactorTuple361._2))
    } /* bell tower */, {
      val refactorTuple362 = "Q48356" -> Tower;
      (CatalogRecord.Wikidata.Class(refactorTuple362._1), SiteType.Id(refactorTuple362._2))
    } /* minaret */, {
      val refactorTuple363 = "Q12280" -> Bridge;
      (CatalogRecord.Wikidata.Class(refactorTuple363._1), SiteType.Id(refactorTuple363._2))
    } /* bridge */, {
      val refactorTuple161 = "Q474" -> Aqueduct;
      (CatalogRecord.Wikidata.Class(refactorTuple161._1), SiteType.Id(refactorTuple161._2))
    } /* aqueduct */, {
      val refactorTuple365 = "Q34442" -> Infrastructure;
      (CatalogRecord.Wikidata.Class(refactorTuple365._1), SiteType.Id(refactorTuple365._2))
    } /* road */, {
      val refactorTuple163 = "Q44539" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple163._1), SiteType.Id(refactorTuple163._2))
    } /* temple */, {
      val refactorTuple164 = "Q29553" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple164._1), SiteType.Id(refactorTuple164._2))
    } /* sanctuary */, {
      val refactorTuple165 = "Q16970" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple165._1), SiteType.Id(refactorTuple165._2))
    } /* church building */, {
      val refactorTuple166 = "Q697295" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple166._1), SiteType.Id(refactorTuple166._2))
    } /* shrine */, {
      val refactorTuple370 = "Q44613" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple370._1), SiteType.Id(refactorTuple370._2))
    } /* monastery */, {
      val refactorTuple371 = "Q32815" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple371._1), SiteType.Id(refactorTuple371._2))
    } /* mosque */, {
      val refactorTuple372 = "Q34627" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple372._1), SiteType.Id(refactorTuple372._2))
    } /* synagogue */, {
      val refactorTuple373 = "Q1088552" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple373._1), SiteType.Id(refactorTuple373._2))
    } /* Catholic church building */, {
      val refactorTuple374 = "Q24398318" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple374._1), SiteType.Id(refactorTuple374._2))
    } /* religious building */, {
      val refactorTuple172 = "Q267596" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple172._1), SiteType.Id(refactorTuple172._2))
    } /* ancient Greek temple */, {
      val refactorTuple376 = "Q108325" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple376._1), SiteType.Id(refactorTuple376._2))
    } /* chapel */, {
      val refactorTuple377 = "Q2031836" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple377._1), SiteType.Id(refactorTuple377._2))
    } /* Eastern Orthodox church building */, {
      val refactorTuple175 = "Q2977" -> Sanctuary;
      (CatalogRecord.Wikidata.Class(refactorTuple175._1), SiteType.Id(refactorTuple175._2))
    } /* cathedral */, {
      val refactorTuple176 = "Q381885" -> Burial;
      (CatalogRecord.Wikidata.Class(refactorTuple176._1), SiteType.Id(refactorTuple176._2))
    } /* tomb */, {
      val refactorTuple177 = "Q39614" -> Burial;
      (CatalogRecord.Wikidata.Class(refactorTuple177._1), SiteType.Id(refactorTuple177._2))
    } /* cemetery */, {
      val refactorTuple381 = "Q200141" -> Burial;
      (CatalogRecord.Wikidata.Class(refactorTuple381._1), SiteType.Id(refactorTuple381._2))
    } /* necropolis */, {
      val refactorTuple179 = "Q34023" -> Burial;
      (CatalogRecord.Wikidata.Class(refactorTuple179._1), SiteType.Id(refactorTuple179._2))
    } /* tumulus (a kurgan is one) */, {
      val refactorTuple180 = "Q5737" -> Burial;
      (CatalogRecord.Wikidata.Class(refactorTuple180._1), SiteType.Id(refactorTuple180._2))
    } /* burial mound */, {
      val refactorTuple384 = "Q12516" -> Burial;
      (CatalogRecord.Wikidata.Class(refactorTuple384._1), SiteType.Id(refactorTuple384._2))
    } /* pyramid */, {
      val refactorTuple182 = "Q173387" -> Burial;
      (CatalogRecord.Wikidata.Class(refactorTuple182._1), SiteType.Id(refactorTuple182._2))
    } /* grave */, {
      val refactorTuple386 = "Q162875" -> Burial;
      (CatalogRecord.Wikidata.Class(refactorTuple386._1), SiteType.Id(refactorTuple386._2))
    } /* mausoleum */, {
      val refactorTuple184 = "Q820477" -> Mine;
      (CatalogRecord.Wikidata.Class(refactorTuple184._1), SiteType.Id(refactorTuple184._2))
    } /* mine */, {
      val refactorTuple185 = "Q188040" -> Mine;
      (CatalogRecord.Wikidata.Class(refactorTuple185._1), SiteType.Id(refactorTuple185._2))
    } /* quarry */, {
      val refactorTuple186 = "Q44782" -> Port;
      (CatalogRecord.Wikidata.Class(refactorTuple186._1), SiteType.Id(refactorTuple186._2))
    } /* port */, {
      val refactorTuple390 = "Q283202" -> Port;
      (CatalogRecord.Wikidata.Class(refactorTuple390._1), SiteType.Id(refactorTuple390._2))
    } /* harbor */, {
      val refactorTuple391 = "Q852190" -> Shipwreck;
      (CatalogRecord.Wikidata.Class(refactorTuple391._1), SiteType.Id(refactorTuple391._2))
    } /* shipwreck */, {
      val refactorTuple392 = "Q35509" -> Cave;
      (CatalogRecord.Wikidata.Class(refactorTuple392._1), SiteType.Id(refactorTuple392._2))
    } /* cave */, {
      val refactorTuple190 = "Q1149405" -> Cave;
      (CatalogRecord.Wikidata.Class(refactorTuple190._1), SiteType.Id(refactorTuple190._2))
    } /* rock shelter */, {
      val refactorTuple394 = "Q16560" -> Palace;
      (CatalogRecord.Wikidata.Class(refactorTuple394._1), SiteType.Id(refactorTuple394._2))
    } /* palace */, {
      val refactorTuple395 = "Q4989906" -> Monument;
      (CatalogRecord.Wikidata.Class(refactorTuple395._1), SiteType.Id(refactorTuple395._2))
    } /* monument */, {
      val refactorTuple193 = "Q164240" -> Monument;
      (CatalogRecord.Wikidata.Class(refactorTuple193._1), SiteType.Id(refactorTuple193._2))
    } /* megalith */, {
      val refactorTuple397 = "Q10521078" -> Burial;
      (CatalogRecord.Wikidata.Class(refactorTuple397._1), SiteType.Id(refactorTuple397._2))
    } /* megalithic tomb */, {
      val refactorTuple195 = "Q101659" -> Monument;
      (CatalogRecord.Wikidata.Class(refactorTuple195._1), SiteType.Id(refactorTuple195._2))
    } /* dolmen */, {
      val refactorTuple399 = "Q193475" -> Monument;
      (CatalogRecord.Wikidata.Class(refactorTuple399._1), SiteType.Id(refactorTuple399._2))
    } /* menhir */, {
      val refactorTuple197 = "Q1935728" -> Monument;
      (CatalogRecord.Wikidata.Class(refactorTuple197._1), SiteType.Id(refactorTuple197._2))
    } /* stone circle */, {
      val refactorTuple401 = "Q170980" -> Monument;
      (CatalogRecord.Wikidata.Class(refactorTuple401._1), SiteType.Id(refactorTuple401._2))
    } /* obelisk */, {
      val refactorTuple402 = "Q179700" -> Monument;
      (CatalogRecord.Wikidata.Class(refactorTuple402._1), SiteType.Id(refactorTuple402._2))
    } /* statue */, {
      val refactorTuple200 = "Q54831" -> Theatre;
      (CatalogRecord.Wikidata.Class(refactorTuple200._1), SiteType.Id(refactorTuple200._2))
    } /* amphitheatre */, {
      val refactorTuple404 = "Q24354" -> Theatre;
      (CatalogRecord.Wikidata.Class(refactorTuple404._1), SiteType.Id(refactorTuple404._2))
    } /* theatre building */, {
      val refactorTuple405 = "Q1211146" -> Findspot;
      (CatalogRecord.Wikidata.Class(refactorTuple405._1), SiteType.Id(refactorTuple405._2))
    } /* rock art */, {
      val refactorTuple203 = ArchaeologicalSite -> Findspot;
      (CatalogRecord.Wikidata.Class(refactorTuple203._1), SiteType.Id(refactorTuple203._2))
    }
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def wikidata(
      p31: List[SiteTypes.WikidataP31],
      superclasses: List[CatalogRecord.Wikidata.Class],
      importClass: Option[CatalogRecord.Wikidata.ImportClass]
  ): Option[SiteType.Id] =
    wikidataOwn(p31)
      .orElse(superclasses.flatMap {
        val refactorFunction4 = wikidataClasses.get;
        (refactorValue8) => refactorFunction4(CatalogRecord.Wikidata.Class((refactorValue8).value))
      }.headOption)
      .orElse(importClass.flatMap {
        val refactorFunction5 = wikidataClasses.get;
        (refactorValue9) => refactorFunction5(CatalogRecord.Wikidata.Class((refactorValue9).value))
      })

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wikidataOwn(p31: List[CatalogRecord.Wikidata.Class]): Option[SiteType.Id] = best(
    p31.map(c => CatalogRecord.Wikidata.Class(c.value.trim)).flatMap(wikidataClasses.get)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def p31Of(json: Option[CatalogRecord.Wikidata.P31Json]): List[CatalogRecord.Wikidata.Class] =
    json.toList.flatMap(s => "Q[0-9]+".r.findAllIn((s).value).toList.map(CatalogRecord.Wikidata.Class(_)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def of(
      placeId: Place.Id,
      placeKind: Place.Kind.Wire,
      stored: Option[SiteType.Id],
      featureTypes: Option[Pleiades.FeatureType],
      fcode: Option[Geonames.Fcode],
      wikidataClass: Option[CatalogRecord.Wikidata.Class],
      p31: List[SiteTypes.OfP31] = Nil
  ): Option[SiteType.Id] =
    if placeKind != Place.Kind.Wire(Place.Kind.Site) && placeKind != Place.Kind.Wire(Place.Kind.Feature)
    then None
    else
      stored
        .filter(byId.contains)
        .orElse(featureTypes.flatMap {
          val refactorFunction3 = pleiades;
          (refactorValue5) => refactorFunction3(Pleiades.FeatureType((refactorValue5).value))
        })
        .orElse(fcode.flatMap {
          val refactorFunction2 = geonames;
          (refactorValue10) => refactorFunction2(Geonames.Fcode((refactorValue10).value))
        })
        .orElse(wikidataOwn(p31))
        .orElse(wikidataClass.flatMap(c => wikidataClasses.get(CatalogRecord.Wikidata.Class((c).value.trim))))

  final case class Report(
      dryRun: Report.DryRun,
      pending: Report.Pending = Report.Pending(0),
      asked: Report.Asked = Report.Asked(0),
      typed: List[EventCount] = Nil,
      untyped: Report.Untyped = Report.Untyped(0),
      requests: Report.Requests = Report.Requests(0),
      failed: List[Report.Failed] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        Schema
  object Report:

    export SiteTypes.ReportSummary as SummaryResult

    type DryRun = DryRun.T
    object DryRun extends Newtype[Boolean, Pure]
    type Pending = Pending.T
    object Pending extends Newtype[Int, Pure]
    type Asked = Asked.T
    object Asked extends Newtype[Int, Pure]
    type Untyped = Untyped.T
    object Untyped extends Newtype[Int, Pure]
    type Requests = Requests.T
    object Requests extends Newtype[Int, Pure]
    type Failed = Failed.T
    object Failed extends Newtype[String, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def summary(r: Report): ReportSummary =
      if (r.dryRun).value then ReportSummary(s"${r.pending} Wikidata site(s) on the map would be asked about")
      else
        ReportSummary(
          (List(
            s"${r.asked} site(s) asked",
            s"typed: ${if r.typed.isEmpty then "none" else r.typed.map(c => s"${c.key} ${c.n}").mkString(", ")}",
            s"${r.untyped} kept the import's class",
            s"${r.pending} left",
            s"${r.requests} request(s)"
          ) ++
            Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}"))
            .mkString(" · ")
        )

  final case class Request(
      dryRun: Request.DryRun = Request.DryRun(false),
      limit: Option[Request.Limit] = None,
      refresh: Request.Refresh = Request.Refresh(false),
      createdBy: Option[Creation.By] = None,
      via: Option[Creation.Via] = None
  ) extends Creation.Requested derives JsonCodec, KindlingsSchema, Schema

  object Request:
    type DryRun = DryRun.T
    object DryRun extends Newtype[Boolean, Pure]
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]
    type Refresh = Refresh.T
    object Refresh extends Newtype[Boolean, Pure]
