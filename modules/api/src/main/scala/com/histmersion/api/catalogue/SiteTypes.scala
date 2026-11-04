package com.histmersion.api.catalogue
import com.histmersion.api.given
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

/** Each type comes from what the site's gazetteer says: Pleiades' `feature_types` (comma-separated, `temple-2` a
  * variant of `temple`), the GeoNames feature code (`P.PPL` a populated place, `S.RUIN` ruins, `S.TMB` a tomb),
  * Wikidata's P31 classes walked up P279 through [[wikidataClasses]].
  */
object SiteTypes:

  final case class SiteType(id: String, label: String, icon: String)

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
    SiteType(Settlement, "settlement", "building-2"),
    SiteType(Villa, "villa, house", "house"),
    SiteType(Fortification, "fortification", "castle"),
    SiteType(Sanctuary, "sanctuary, temple", "landmark"),
    SiteType(Burial, "burial, tomb", "pyramid"),
    SiteType(Mine, "mine, quarry", "pickaxe"),
    SiteType(Port, "port, harbour", "anchor"),
    SiteType(Infrastructure, "road, canal, cistern", "route"),
    SiteType(Bridge, "bridge", "waves"),
    SiteType(Aqueduct, "aqueduct", "droplets"),
    SiteType(Tower, "tower", "shield"),
    SiteType(Theatre, "theatre, amphitheatre", "venetian-mask"),
    SiteType(Findspot, "find-spot, excavation", "shovel"),
    SiteType(Cave, "cave", "door-open"),
    SiteType(Palace, "palace", "crown"),
    SiteType(Monument, "monument, megalith", "milestone"),
    SiteType(Shipwreck, "shipwreck", "ship")
  )

  private val byId: Map[String, SiteType] = all.map(t => t.id -> t).toMap

  def get(id: String): Option[SiteType] = byId.get(id.trim.toLowerCase(java.util.Locale.ROOT))
  def icon(id: String): Option[String] = get(id).map(_.icon)

  val pleiadesTypes: Map[String, String] = Map(
    "settlement" -> Settlement,
    "urban" -> Settlement,
    "town" -> Settlement,
    "city" -> Settlement,
    "village" -> Settlement,
    "settlement-modern" -> Settlement,
    "polis" -> Settlement,
    "tell" -> Settlement,
    "townhouse" -> Villa,
    "villa" -> Villa,
    "farmstead" -> Villa,
    "estate" -> Villa,
    "fort" -> Fortification,
    "fortlet" -> Fortification,
    "tower-defensive" -> Fortification,
    "fortified-settlement" -> Fortification,
    "city-gate" -> Fortification,
    "city-wall" -> Fortification,
    "fortress" -> Fortification,
    "castle" -> Fortification,
    "camp" -> Fortification,
    "hillfort" -> Fortification,
    "wall" -> Fortification,
    "acropolis" -> Fortification,
    "temple" -> Sanctuary,
    "sanctuary" -> Sanctuary,
    "church" -> Sanctuary,
    "shrine" -> Sanctuary,
    "monastery" -> Sanctuary,
    "mosque" -> Sanctuary,
    "synagogue" -> Sanctuary,
    "mithraeum" -> Sanctuary,
    "basilica" -> Sanctuary,
    "altar" -> Sanctuary,
    "oracle" -> Sanctuary,
    "tomb" -> Burial,
    "tumulus" -> Burial,
    "cemetery" -> Burial,
    "necropolis" -> Burial,
    "pyramid" -> Burial,
    "mausoleum" -> Burial,
    "catacomb" -> Burial,
    "mine" -> Mine,
    "quarry" -> Mine,
    "port" -> Port,
    "harbor" -> Port,
    "harbour" -> Port,
    "anchorage" -> Port,
    "lighthouse" -> Port,
    "road" -> Infrastructure,
    "bridge" -> Bridge,
    "aqueduct" -> Aqueduct,
    "station" -> Infrastructure,
    "milestone" -> Infrastructure,
    "canal" -> Infrastructure,
    "dam" -> Infrastructure,
    "cistern" -> Infrastructure,
    "pass" -> Infrastructure,
    "bath" -> Infrastructure,
    "theatre" -> Theatre,
    "theater" -> Theatre,
    "amphitheatre" -> Theatre,
    "amphitheater" -> Theatre,
    "odeon" -> Theatre,
    "circus" -> Theatre,
    "stadium" -> Theatre,
    "hippodrome" -> Theatre,
    "findspot" -> Findspot,
    "archaeological-site" -> Findspot,
    "rock-art" -> Findspot,
    "inscription" -> Findspot,
    "hoard" -> Findspot,
    "cave" -> Cave,
    "grotto" -> Cave,
    "palace" -> Palace,
    "monument" -> Monument,
    "megalith" -> Monument,
    "arch" -> Monument,
    "stele" -> Monument,
    "obelisk" -> Monument,
    "statue" -> Monument,
    "column" -> Monument,
    "shipwreck" -> Shipwreck,
    "wreck" -> Shipwreck
  )

  val preference: List[String] = List(
    Palace,
    Sanctuary,
    Theatre,
    Burial,
    Fortification,
    Tower,
    Mine,
    Port,
    Shipwreck,
    Cave,
    Monument,
    Villa,
    Aqueduct,
    Bridge,
    Infrastructure,
    Settlement,
    Findspot
  )

  private def best(types: Iterable[String]): Option[String] =
    types.toList.distinct
      .sortBy(t => preference.indexOf(t) match { case -1 => preference.size; case i => i })
      .headOption

  def pleiades(featureTypes: String): Option[String] =
    best(
      Option(featureTypes).toList
        .flatMap(_.split(","))
        .map(_.trim.toLowerCase(java.util.Locale.ROOT))
        .filter(_.nonEmpty)
        .map(_.replaceAll("-\\d+$", ""))
        .flatMap(pleiadesTypes.get)
    )

  val geonamesCodes: Map[String, String] = Map(
    "S.RUIN" -> Findspot,
    "S.ANS" -> Findspot,
    "S.HSTS" -> Findspot,
    "S.TMB" -> Burial,
    "S.CMTY" -> Burial,
    "S.PYR" -> Burial,
    "S.PYRS" -> Burial,
    "S.GRVE" -> Burial,
    "S.CSTL" -> Fortification,
    "S.FT" -> Fortification,
    "S.WALLA" -> Fortification,
    "S.TOWR" -> Tower,
    "S.TMPL" -> Sanctuary,
    "S.CH" -> Sanctuary,
    "S.MSTY" -> Sanctuary,
    "S.MSQE" -> Sanctuary,
    "S.SHRN" -> Sanctuary,
    "S.CTRR" -> Sanctuary,
    "S.PAL" -> Palace,
    "S.MNMT" -> Monument,
    "S.MNA" -> Mine,
    "S.MN" -> Mine,
    "S.MNQR" -> Mine,
    "S.MNAU" -> Mine,
    "S.MNCU" -> Mine,
    "S.MNFE" -> Mine,
    "S.QUAR" -> Mine,
    "L.PRT" -> Port,
    "S.HBR" -> Port,
    "H.HBR" -> Port,
    "S.LTHSE" -> Port,
    "S.BDG" -> Bridge,
    "S.AQDT" -> Aqueduct,
    "R.RD" -> Infrastructure,
    "R.RDA" -> Infrastructure,
    "S.AMTH" -> Theatre,
    "S.THTR" -> Theatre,
    "S.STDM" -> Theatre,
    "H.WRCK" -> Shipwreck,
    "S.WRCK" -> Shipwreck,
    "S.CAVE" -> Cave,
    "H.CAVE" -> Cave,
    "T.CAVE" -> Cave
  )

  def geonames(fcode: String): Option[String] =
    Option(fcode).map(_.trim.toUpperCase(java.util.Locale.ROOT)).filter(_.nonEmpty).flatMap { c =>
      geonamesCodes.get(c).orElse(Option.when(c.startsWith("P."))(Settlement))
    }

  val ArchaeologicalSite = "Q839954"
  val HumanSettlement = "Q486972"

  /** The comment beside each id is its Wikidata label. */
  val wikidataClasses: Map[String, String] = Map(
    HumanSettlement -> Settlement,
    "Q15661340" -> Settlement /* ancient city */,
    "Q515" -> Settlement /* city */,
    "Q15253706" -> Settlement /* city */,
    "Q755017" -> Settlement /* tell */,
    "Q532" -> Settlement /* village */,
    "Q3957" -> Settlement /* town */,
    "Q1549591" -> Settlement /* big city */,
    "Q5084" -> Settlement /* hamlet */,
    "Q148837" -> Settlement /* polis */,
    "Q3947" -> Villa /* house */,
    "Q3950" -> Villa /* villa */,
    "Q918230" -> Villa /* Roman villa */,
    "Q131596" -> Villa /* farm */,
    "Q57821" -> Fortification /* fortification */,
    "Q1785071" -> Fortification /* fort */,
    "Q23413" -> Fortification /* castle */,
    "Q744099" -> Fortification /* hillfort */,
    "Q88291" -> Fortification /* citadel */,
    "Q16748868" -> Fortification /* city walls */,
    "Q192601" -> Fortification /* oppidum */,
    "Q57831" -> Fortification /* fortress */,
    "Q82117" -> Fortification /* city gate */,
    "Q57346" -> Fortification /* defensive wall */,
    "Q947103" -> Fortification /* watchtower */,
    "Q20034791" -> Fortification /* defensive tower */,
    "Q81917" -> Fortification /* fortified tower */,
    "Q91312" -> Fortification /* tower house */,
    "Q12518" -> Tower /* tower */,
    "Q853854" -> Tower /* clock tower */,
    "Q200334" -> Tower /* bell tower */,
    "Q48356" -> Tower /* minaret */,
    "Q12280" -> Bridge /* bridge */,
    "Q474" -> Aqueduct /* aqueduct */,
    "Q34442" -> Infrastructure /* road */,
    "Q44539" -> Sanctuary /* temple */,
    "Q29553" -> Sanctuary /* sanctuary */,
    "Q16970" -> Sanctuary /* church building */,
    "Q697295" -> Sanctuary /* shrine */,
    "Q44613" -> Sanctuary /* monastery */,
    "Q32815" -> Sanctuary /* mosque */,
    "Q34627" -> Sanctuary /* synagogue */,
    "Q1088552" -> Sanctuary /* Catholic church building */,
    "Q24398318" -> Sanctuary /* religious building */,
    "Q267596" -> Sanctuary /* ancient Greek temple */,
    "Q108325" -> Sanctuary /* chapel */,
    "Q2031836" -> Sanctuary /* Eastern Orthodox church building */,
    "Q2977" -> Sanctuary /* cathedral */,
    "Q381885" -> Burial /* tomb */,
    "Q39614" -> Burial /* cemetery */,
    "Q200141" -> Burial /* necropolis */,
    "Q34023" -> Burial /* tumulus (a kurgan is one) */,
    "Q5737" -> Burial /* burial mound */,
    "Q12516" -> Burial /* pyramid */,
    "Q173387" -> Burial /* grave */,
    "Q162875" -> Burial /* mausoleum */,
    "Q820477" -> Mine /* mine */,
    "Q188040" -> Mine /* quarry */,
    "Q44782" -> Port /* port */,
    "Q283202" -> Port /* harbor */,
    "Q852190" -> Shipwreck /* shipwreck */,
    "Q35509" -> Cave /* cave */,
    "Q1149405" -> Cave /* rock shelter */,
    "Q16560" -> Palace /* palace */,
    "Q4989906" -> Monument /* monument */,
    "Q164240" -> Monument /* megalith */,
    "Q10521078" -> Burial /* megalithic tomb */,
    "Q101659" -> Monument /* dolmen */,
    "Q193475" -> Monument /* menhir */,
    "Q1935728" -> Monument /* stone circle */,
    "Q170980" -> Monument /* obelisk */,
    "Q179700" -> Monument /* statue */,
    "Q54831" -> Theatre /* amphitheatre */,
    "Q24354" -> Theatre /* theatre building */,
    "Q1211146" -> Findspot /* rock art */,
    ArchaeologicalSite -> Findspot
  )

  def wikidata(p31: List[String], superclasses: List[String], importClass: Option[String]): Option[String] =
    wikidataOwn(p31)
      .orElse(superclasses.flatMap(wikidataClasses.get).headOption)
      .orElse(importClass.flatMap(wikidataClasses.get))

  def wikidataOwn(p31: List[String]): Option[String] = best(p31.map(_.trim).flatMap(wikidataClasses.get))

  def p31Of(json: Option[String]): List[String] =
    json.toList.flatMap(s => "Q[0-9]+".r.findAllIn(s).toList)

  def of(
      placeId: String,
      placeKind: String,
      stored: Option[String],
      featureTypes: Option[String],
      fcode: Option[String],
      wikidataClass: Option[String],
      p31: List[String] = Nil
  ): Option[String] =
    if placeKind != com.histmersion.api.catalogue.Place.Kind.Site && placeKind != com.histmersion.api.catalogue.Place.Kind.Feature
    then None
    else
      stored
        .filter(byId.contains)
        .orElse(featureTypes.flatMap(pleiades))
        .orElse(fcode.flatMap(geonames))
        .orElse(wikidataOwn(p31))
        .orElse(wikidataClass.flatMap(c => wikidataClasses.get(c.trim)))

  final case class Report(
      dryRun: Boolean,
      pending: Int = 0,
      asked: Int = 0,
      typed: List[EventCount] = Nil,
      untyped: Int = 0,
      requests: Int = 0,
      failed: List[String] = Nil
  ) derives hearth.kindlings.circederivation.KindlingsCodecAsObject,
        KindlingsSchema
  object Report:
    given sttp.tapir.Schema[Report] = summon[KindlingsSchema[Report]].schema
    def summary(r: Report): String =
      if r.dryRun then s"${r.pending} Wikidata site(s) on the map would be asked about"
      else
        (List(
          s"${r.asked} site(s) asked",
          s"typed: ${if r.typed.isEmpty then "none" else r.typed.map(c => s"${c.key} ${c.n}").mkString(", ")}",
          s"${r.untyped} kept the import's class",
          s"${r.pending} left",
          s"${r.requests} request(s)"
        ) ++
          Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}"))
          .mkString(" · ")

  final case class Request(dryRun: Boolean = false, limit: Option[Int] = None, refresh: Boolean = false)
      derives hearth.kindlings.circederivation.KindlingsCodecAsObject,
        KindlingsSchema
  object Request:
    given sttp.tapir.Schema[Request] = summon[KindlingsSchema[Request]].schema
