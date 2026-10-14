package com.histmersion.core

import io.circe.{ACursor, Json}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

/** The event catalogues' APIs (#1449): how to ask each, and how to read its answer into
  * [[CatalogRecord]]s. Pure — the requests are sent by the server's polite client; a test feeds
  * recorded answers. Documented public APIs only:
  *
  *  - GVP: the Smithsonian's WFS (`GVP-VOTW:Smithsonian_VOTW_Holocene_Eruptions`), CQL-filtered, paged;
  *  - NOAA: NCEI's hazard service (`/hazel/hazard-service/api/v1/{earthquakes,volcanoes,tsunamis/events}`);
  *  - Wikidata: the query service (one SPARQL query per class) and `wbgetentities` for labels/aliases. */
object EventProviders:

  private def enc(s: String): String = URLEncoder.encode(s, UTF_8)

  private def str(c: ACursor, k: String): Option[String] =
    c.downField(k).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)
  private def long(c: ACursor, k: String): Option[Long] =
    c.downField(k).focus.flatMap(j => j.asNumber.map(n => n.toLong.getOrElse(n.toDouble.toLong))
      .orElse(j.asString.flatMap(_.trim.toLongOption)))
  private def dbl(c: ACursor, k: String): Option[Double] =
    c.downField(k).focus.flatMap(j => j.asNumber.map(_.toDouble).orElse(j.asString.flatMap(_.trim.toDoubleOption)))

  /** Pure: "SYRIA:  UGARIT" → "Syria: Ugarit" — NOAA's upper-case place names in title case. */
  def titleCase(s: String): String =
    s.trim.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).split("(?<=[\\s(/-])").map(_.capitalize).mkString

  // ── GVP ─────────────────────────────────────────────────────────────────────────────────────

  object Gvp:
    val TypeName = "GVP-VOTW:Smithsonian_VOTW_Holocene_Eruptions"

    /** Pure: the eruptions that started in `from`…`to` (signed years, both inclusive) — one request
      * per window of years, unpaged: the service's paging (`startIndex`) took 45 s and more a page on
      * 2026-10-14, a window of a thousand years a few seconds. */
    def url(base: String, from: Long, to: Long, max: Int): String =
      val cql = s"StartDateYear>=$from AND StartDateYear<=$to"
      s"$base?service=WFS&version=2.0.0&request=GetFeature&typeNames=${enc(TypeName)}" +
        s"&outputFormat=${enc("application/json")}&count=$max&CQL_FILTER=${enc(cql)}"

    /** Pure: `from`…`to` cut into windows of `years` (the last one shorter). */
    def windows(from: Long, to: Long, years: Long): List[(Long, Long)] =
      if from > to || years <= 0 then Nil
      else Iterator.iterate(from)(_ + years).takeWhile(_ <= to).map(a => (a, (a + years - 1).min(to))).toList

    /** Pure: the records of one page and how many match in all. "Discredited" eruptions are dropped;
      * "Uncertain" ones are `contested`. Years are GVP's signed years (BCE negative), ± its stated
      * uncertainty. */
    def parse(json: Json): Either[String, (List[CatalogRecord], Option[Int])] =
      val c = json.hcursor
      c.downField("features").focus.flatMap(_.asArray) match
        case None => Left(c.downField("exceptions").focus.map(_.noSpaces.take(200)).getOrElse("no features in the answer"))
        case Some(fs) =>
          val total = c.get[Int]("numberMatched").toOption.orElse(c.get[Int]("totalFeatures").toOption)
          Right((fs.toList.flatMap(record), total))

    private def record(f: Json): Option[CatalogRecord] =
      val p = f.hcursor.downField("properties")
      val coords = f.hcursor.downField("geometry").downField("coordinates").focus.flatMap(_.asArray).getOrElse(Vector.empty)
      val (lon, lat) = (coords.headOption.flatMap(_.asNumber).map(_.toDouble), coords.lift(1).flatMap(_.asNumber).map(_.toDouble))
      val activity = str(p, "Activity_Type").getOrElse("")
      for
        num   <- long(p, "Eruption_Number")
        year  <- long(p, "StartDateYear")
        if !activity.toLowerCase(Locale.ROOT).contains("discredited")
      yield
        val volcano = str(p, "Volcano_Name").getOrElse("unnamed volcano")
        val vnum    = long(p, "Volcano_Number")
        val start   = CatalogDating.year(year, long(p, "StartDateYearUncertainty"))
        val end     = long(p, "EndDateYear").map(y => CatalogDating.year(y, long(p, "EndDateYearUncertainty")))
        val vei     = long(p, "ExplosivityIndexMax").map(_.toInt)
        val method  = str(p, "StartEvidenceMethod")
        val doubt   = activity.toLowerCase(Locale.ROOT).contains("uncertain") || str(p, "StartDateYearModifier").contains("?")
        CatalogRecord(CatalogProvider.Gvp, num.toString,
          vnum.fold("https://volcano.si.edu")(v => s"https://volcano.si.edu/volcano.cfm?vn=$v"),
          s"Eruption of $volcano", EventTaxonomy.Eruption, CatalogDating.span(Some(start), end), lat, lon,
          if doubt then Historicity.Contested else Historicity.Attested, vei = vei,
          names = List(s"$volcano eruption"),
          description = Some((List(s"$activity at $volcano") ++ vei.map(v => s"VEI $v") ++
            str(p, "ActivityArea").map(a => s"area: $a") ++ method.map(m => s"dated by $m")).mkString("; ")),
          group = vnum.map(v => s"volcano:$v"))

  // ── NOAA NCEI ───────────────────────────────────────────────────────────────────────────────

  object Noaa:
    val Earthquakes = "earthquakes"
    val Volcanoes   = "volcanoes"
    val Tsunamis    = "tsunamis"
    val datasets: List[String] = List(Earthquakes, Volcanoes, Tsunamis)

    private def path(ds: String): String = if ds == Tsunamis then "tsunamis/events" else ds

    /** Pure: one page of a dataset's events up to `to` (and from `from`). */
    def url(base: String, dataset: String, from: Option[Long], to: Long, page: Int, perPage: Int): String =
      s"${base.stripSuffix("/")}/${path(dataset)}?maxYear=$to${from.fold("")(f => s"&minYear=$f")}" +
        s"&itemsPerPage=$perPage&page=$page"

    /** Pure: one page's records and the number of pages. Tsunamis NOAA marks erroneous (validity −1) or
      * a mere seiche (0) are dropped; very doubtful (1) and questionable (2) ones are `contested`. */
    def parse(dataset: String, json: Json): Either[String, (List[CatalogRecord], Int)] =
      val c = json.hcursor
      c.downField("items").focus.flatMap(_.asArray) match
        case None => Left("no items in the answer")
        case Some(items) => Right((items.toList.flatMap(i => record(dataset, i.hcursor)), c.get[Int]("totalPages").getOrElse(1)))

    private def record(dataset: String, i: ACursor): Option[CatalogRecord] =
      val kind = dataset match
        case Earthquakes => EventTaxonomy.Earthquake
        case Volcanoes   => EventTaxonomy.Eruption
        case _           => EventTaxonomy.Tsunami
      val validity = long(i, "eventValidity")
      for
        id   <- long(i, "id")
        year <- long(i, "year")
        if dataset != Tsunamis || validity.forall(_ >= 1)
      yield
        val where = str(i, "locationName").orElse(str(i, "name")).map(titleCase).getOrElse("unknown place")
        val one   = singular(dataset)
        val label = dataset match
          case Volcanoes => s"Eruption of ${str(i, "name").getOrElse(where)}"
          case _         => s"${one.capitalize}, $where"
        val deaths = long(i, "deathsTotal").orElse(long(i, "deaths"))
        CatalogRecord(CatalogProvider.Noaa, s"$one:$id",
          s"https://www.ngdc.noaa.gov/hazel/view/hazards/${if dataset == Tsunamis then "tsunami" else one}/event-more-info/$id",
          label, kind, CatalogDating.span(Some(CatalogDating.year(year)), None), dbl(i, "latitude"), dbl(i, "longitude"),
          if validity.exists(_ <= 2) then Historicity.Contested else Historicity.Attested,
          vei = long(i, "vei").map(_.toInt), magnitude = dbl(i, "eqMagnitude"), deaths = deaths,
          names = Option.when(dataset == Volcanoes)(str(i, "name").map(n => s"$n eruption")).flatten.toList,
          description = Some((List(s"$where") ++ dbl(i, "eqMagnitude").map(m => s"magnitude $m") ++
            long(i, "vei").map(v => s"VEI $v") ++ deaths.map(d => s"$d deaths recorded") ++
            dbl(i, "maxWaterHeight").map(h => s"max. water height $h m")).mkString("; ")),
          group = if dataset == Volcanoes then long(i, "volcanoLocationNewNum").map(v => s"volcano:$v") else None)

    private def singular(ds: String): String = ds match
      case Earthquakes => "earthquake"
      case Volcanoes   => "volcano"
      case _           => "tsunami"

  // ── Wikidata ────────────────────────────────────────────────────────────────────────────────

  object WikidataEvents:
    /** Items that are only told of, never shown to have happened: a mythical event, a legendary battle,
      * a narrative event (2026-10-14, checked against their labels). */
    val Mythical  = "Q24336466"
    val Legendary: Set[String] = Set("Q26913948", "Q122397289")

    /** `Q178561:battle,Q188055:siege` (config) → `(Q178561, battle)` …, the kind canonical. */
    def classes(s: String): List[(String, String)] =
      Option(s).toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).flatMap { e =>
        e.split(":", 2).map(_.trim) match
          case Array(q, k) if q.matches("Q[1-9][0-9]*") && k.nonEmpty => Some(q -> EventTaxonomy.canonical(k))
          case _ => None
      }.distinctBy(_._1)

    /** Pure: the SPARQL of one class — its instances (and its subclasses'), dated (point in time, else
      * start/end) before `to`, with their precision, their own coordinates or their location's, whether
      * they are legendary or mythical, and the deaths. */
    def sparql(qid: String, from: Option[Long], to: Long, lang: String): String =
      // the query service compares astronomical years; `to`/`from` are historical
      val toA   = Years.toAstronomical(to)
      val fromF = from.fold("")(f => s" && YEAR(?t) >= ${Years.toAstronomical(f)}")
      s"""SELECT ?item ?itemLabel ?itemDescription ?date ?datePrec ?start ?startPrec ?end ?endPrec ?coord ?locCoord ?legend ?deaths WHERE {
         |  ?item wdt:P31/wdt:P279* wd:$qid .
         |  OPTIONAL { ?item p:P585/psv:P585 [ wikibase:timeValue ?date ; wikibase:timePrecision ?datePrec ] }
         |  OPTIONAL { ?item p:P580/psv:P580 [ wikibase:timeValue ?start ; wikibase:timePrecision ?startPrec ] }
         |  OPTIONAL { ?item p:P582/psv:P582 [ wikibase:timeValue ?end ; wikibase:timePrecision ?endPrec ] }
         |  BIND(COALESCE(?date, ?start, ?end) AS ?t)
         |  FILTER(BOUND(?t) && YEAR(?t) <= $toA$fromF)
         |  OPTIONAL { ?item wdt:P625 ?coord }
         |  OPTIONAL { ?item wdt:P276/wdt:P625 ?locCoord }
         |  OPTIONAL { ?item wdt:P31 ?legend . VALUES ?legend { wd:$Mythical ${Legendary.toList.sorted.map("wd:" + _).mkString(" ")} } }
         |  OPTIONAL { ?item wdt:P1120 ?deaths }
         |  SERVICE wikibase:label { bd:serviceParam wikibase:language "$lang". }
         |}""".stripMargin

    def sparqlUrl(endpoint: String, query: String): String = s"$endpoint?format=json&query=${enc(query)}"

    private def binding(b: ACursor, k: String): Option[String] =
      b.downField(k).get[String]("value").toOption.map(_.trim).filter(_.nonEmpty)

    /** Pure: `Point(4.5 47.53)` → (lat 47.53, lon 4.5). */
    def point(wkt: String): Option[(Double, Double)] =
      "Point\\(\\s*([-0-9.eE]+)\\s+([-0-9.eE]+)\\s*\\)".r.findFirstMatchIn(wkt).flatMap { m =>
        for lon <- m.group(1).toDoubleOption; lat <- m.group(2).toDoubleOption yield (lat, lon)
      }

    /** Pure: the records of one class's answer, one per item (its rows joined: the first coordinates,
      * its own before its location's; the most precise dating; the most deaths). Items with no label
      * of their own (the label service then answers the id) keep it. */
    def parse(json: Json, kind: String): Either[String, List[CatalogRecord]] =
      json.hcursor.downField("results").downField("bindings").focus.flatMap(_.asArray) match
        case None => Left("no results in the answer")
        case Some(rows) =>
          val bs = rows.toList.map(_.hcursor)
          Right(bs.groupBy(b => binding(b, "item").getOrElse("")).toList.filter(_._1.nonEmpty).sortBy(_._1).flatMap { (uri, rs) =>
            val qid   = uri.reverse.takeWhile(_ != '/').reverse
            def first(k: String) = rs.flatMap(binding(_, k)).headOption
            def bound(dateKey: String, precKey: String): Option[CatalogDating.Bound] =
              rs.flatMap(r => binding(r, dateKey).flatMap(CatalogDating.isoYear).map(y =>
                CatalogDating.wikidata(y, binding(r, precKey).flatMap(_.toIntOption).getOrElse(9))))
                .sortBy(b => b.hi - b.lo).headOption
            val at    = bound("date", "datePrec")
            val start = bound("start", "startPrec").orElse(at)
            val end   = bound("end", "endPrec").orElse(at)
            val pos   = first("coord").flatMap(point).orElse(first("locCoord").flatMap(point))
            val legends = rs.flatMap(binding(_, "legend")).map(u => u.reverse.takeWhile(_ != '/').reverse).toSet
            val hist =
              if legends(Mythical) then Historicity.Mythic
              else if legends.exists(Legendary) then Historicity.Legendary
              else Historicity.Attested
            val label = first("itemLabel").getOrElse(qid)
            Option.when(start.isDefined || end.isDefined)(CatalogRecord(CatalogProvider.Wikidata, qid,
              s"https://www.wikidata.org/wiki/$qid", label, kind, CatalogDating.span(start, end),
              pos.map(_._1), pos.map(_._2), hist,
              deaths = rs.flatMap(binding(_, "deaths")).flatMap(d => d.toDoubleOption.map(_.toLong)).maxOption,
              wikidata = Some(qid), description = first("itemDescription")))
          })

    /** Pure: the `wbgetentities` URL for the labels and aliases of up to 50 items in `langs`. */
    def namesUrl(api: String, ids: List[String], langs: List[String]): String =
      s"$api?action=wbgetentities&format=json&formatversion=2&props=labels%7Caliases" +
        s"&languages=${enc(langs.mkString("|"))}&ids=${enc(ids.mkString("|"))}"

    /** Pure: each item's labels and aliases in the asked languages, distinct. */
    def names(json: Json): Either[String, Map[String, List[String]]] =
      val c = json.hcursor
      c.downField("error").focus match
        case Some(err) => Left(err.hcursor.get[String]("info").getOrElse("error"))
        case None =>
          val ents = c.downField("entities").focus.flatMap(_.asObject).map(_.toList).getOrElse(Nil)
          Right(ents.map { (id, e) =>
            val labels = e.hcursor.downField("labels").focus.flatMap(_.asObject).toList.flatMap(_.values)
              .flatMap(_.hcursor.get[String]("value").toOption)
            val aliases = e.hcursor.downField("aliases").focus.flatMap(_.asObject).toList.flatMap(_.values)
              .flatMap(_.asArray.toList.flatten).flatMap(_.hcursor.get[String]("value").toOption)
            id -> (labels ++ aliases).map(_.trim).filter(_.nonEmpty).distinct
          }.toMap)

    /** Pure: the P31 classes of each item of a `wbgetentities` (props=claims) answer — what a site's
      * type is refined from ([[SiteTypes.wikidata]]); P279 likewise for a class. */
    def claims(json: Json, property: String): Either[String, Map[String, List[String]]] =
      val c = json.hcursor
      c.downField("error").focus match
        case Some(err) => Left(err.hcursor.get[String]("info").getOrElse("error"))
        case None =>
          val ents = c.downField("entities").focus.flatMap(_.asObject).map(_.toList).getOrElse(Nil)
          Right(ents.map { (id, e) =>
            val sts = e.hcursor.downField("claims").downField(property).focus.flatMap(_.asArray).toList.flatten
            id -> sts.filterNot(_.hcursor.get[String]("rank").toOption.contains("deprecated")).flatMap(st =>
              st.hcursor.downField("mainsnak").downField("datavalue").downField("value").get[String]("id").toOption)
          }.toMap)

  /** Pure: does a record pass the configured thresholds? Inside the research `region` everything (at
    * the region's own minimum); outside it an eruption needs `outsideVei`, an earthquake
    * `outsideMagnitude`. A record with no position never passes (it cannot be drawn or matched in
    * space). `Left` is why not. */
  final case class Thresholds(region: Option[BBox], regionVei: Int, outsideVei: Int, regionMagnitude: Double,
      outsideMagnitude: Double, area: Option[BBox] = None)

  def keep(r: CatalogRecord, t: Thresholds): Either[String, Unit] =
    (r.lat, r.lon) match
      case (Some(la), Some(lo)) =>
        def in(b: BBox) =
          la >= b.minLat && la <= b.maxLat &&
            (if b.crossesAntimeridian then lo >= b.minLon || lo <= b.maxLon else lo >= b.minLon && lo <= b.maxLon)
        val inRegion = t.region.forall(in)
        if t.area.exists(b => !in(b)) then Left("outside --bbox")
        else r.kind match
          case EventTaxonomy.Eruption =>
            val min = if inRegion then t.regionVei else t.outsideVei
            if min > 0 && r.vei.forall(_ < min) then Left(s"VEI below $min${if inRegion then "" else " outside the region"}")
            else Right(())
          case EventTaxonomy.Earthquake =>
            val min = if inRegion then t.regionMagnitude else t.outsideMagnitude
            if min > 0 && r.magnitude.forall(_ < min) then Left(s"magnitude below $min${if inRegion then "" else " outside the region"}")
            else Right(())
          case _ => Right(())
      case _ => Left("no position")
