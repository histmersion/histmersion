package com.histmersion.plugins.events

import com.histmersion.plugin.*
import com.histmersion.upstreams.wikidata.WikidataClasses
import com.histmersion.upstreams.dating.CatalogDating
import io.circe.{ACursor, Json}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

/** Wikidata's events of the configured classes (battles, sieges, wars, treaties …): the query service, one SPARQL query
  * per class. A part is a class, `class:<qid>`.
  */
final class WikidataCatalogue(cfg: WikidataSettings) extends EventCatalogue:
  def title = "Wikidata"
  def licence = Licence("CC0-1.0")
  def citation = "Wikidata (CC0), https://www.wikidata.org"
  def home = "https://www.wikidata.org"
  override def requestIntervalMs: Int = cfg.requestIntervalMs

  private def classes: List[(com.histmersion.plugins.events.WikidataCatalogue.ClassesResultPart1, com.histmersion.plugins.events.WikidataCatalogue.ClassesResultPart2)] = WikidataClasses.parse(cfg.classes)

  def parts(query: CatalogueQuery): List[com.histmersion.plugins.events.WikidataCatalogue.PartsResult] = classes.map((qid, _) => s"class:$qid")

  def read(part: com.histmersion.plugins.events.WikidataCatalogue.ReadPart, query: CatalogueQuery, http: Plugin.Http): Either[com.histmersion.plugins.events.WikidataCatalogue.ReadResultPart1, CataloguePage] =
    classes.find((qid, _) => part == s"class:$qid") match
      case None              => Left(s"'$part' is no class of the configured events.wikidata.classes")
      case Some((qid, kind)) =>
        val q = WikidataCatalogue.sparql(qid, query.from.map(_.value.toLong), query.to.value.toLong, cfg.labelLanguages)
        CatalogueJson(http)(WikidataCatalogue.sparqlUrl(cfg.sparqlUrl, q))
          .flatMap(WikidataCatalogue.parse(_, kind))
          .map(CataloguePage(_))

  /** The item read again from its `raw` (its rows of the query's answer), in the kind it is stored with. */
  override def reread(stored: CatalogueEvent): Option[CatalogueEvent] =
    stored.raw
      .flatMap(t => io.circe.parser.parse(t).toOption)
      .flatMap(j =>
        WikidataCatalogue
          .parse(Json.obj("results" -> Json.obj("bindings" -> j)), stored.kind)
          .toOption
          .flatMap(_.headOption)
      )
      .map(n => n.copy(names = (stored.names ++ n.names).distinct.filterNot(_ == n.label)))

object WikidataCatalogue:
  /** Items that are only told of, never shown to have happened: a mythical event, a legendary battle, a narrative event
    * (2026-10-14, checked against their labels).
    */
  type ClassesResultPart1 = ClassesResultPart1.T
  object ClassesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassesResultPart2 = ClassesResultPart2.T
  object ClassesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PartsResult = PartsResult.T
  object PartsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadResultPart1 = ReadResultPart1.T
  object ReadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPart = ReadPart.T
  object ReadPart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Legendary = Legendary.T
  object Legendary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SparqlResult = SparqlResult.T
  object SparqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SparqlQid = SparqlQid.T
  object SparqlQid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SparqlFrom = SparqlFrom.T
  object SparqlFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SparqlTo = SparqlTo.T
  object SparqlTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SparqlLang = SparqlLang.T
  object SparqlLang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SparqlUrlResult = SparqlUrlResult.T
  object SparqlUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SparqlUrlEndpoint = SparqlUrlEndpoint.T
  object SparqlUrlEndpoint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SparqlUrlQuery = SparqlUrlQuery.T
  object SparqlUrlQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BindingResult = BindingResult.T
  object BindingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BindingK = BindingK.T
  object BindingK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointResultPart1 = PointResultPart1.T
  object PointResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointResultPart2 = PointResultPart2.T
  object PointResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointWkt = PointWkt.T
  object PointWkt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseKind = ParseKind.T
  object ParseKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Mythical = "Q24336466"
  val Legendary: Set[com.histmersion.plugins.events.WikidataCatalogue.Legendary] = Set("Q26913948", "Q122397289")

  /** Pure: the SPARQL of one class — its instances (and its subclasses'), dated (point in time, else start/end) before
    * `to`, with their precision, their own coordinates or their location's, whether they are legendary or mythical, and
    * the deaths.
    */
  def sparql(qid: com.histmersion.plugins.events.WikidataCatalogue.SparqlQid, from: Option[com.histmersion.plugins.events.WikidataCatalogue.SparqlFrom], to: com.histmersion.plugins.events.WikidataCatalogue.SparqlTo, lang: com.histmersion.plugins.events.WikidataCatalogue.SparqlLang): com.histmersion.plugins.events.WikidataCatalogue.SparqlResult =
    // the query service compares astronomical years; `to`/`from` are historical
    val toA = CatalogDating.toAstronomical(to)
    val fromF = from.fold("")(f => s" && YEAR(?t) >= ${CatalogDating.toAstronomical(f)}")
    s"""SELECT ?item ?itemLabel ?itemDescription ?date ?datePrec ?start ?startPrec ?end ?endPrec ?coord ?locCoord ?legend ?deaths WHERE {
       |  ?item wdt:P31/wdt:P279* wd:$qid .
       |  OPTIONAL { ?item p:P585/psv:P585 [ wikibase:timeValue ?date ; wikibase:timePrecision ?datePrec ] }
       |  OPTIONAL { ?item p:P580/psv:P580 [ wikibase:timeValue ?start ; wikibase:timePrecision ?startPrec ] }
       |  OPTIONAL { ?item p:P582/psv:P582 [ wikibase:timeValue ?end ; wikibase:timePrecision ?endPrec ] }
       |  BIND(COALESCE(?date, ?start, ?end) AS ?t)
       |  FILTER(BOUND(?t) && YEAR(?t) <= $toA$fromF)
       |  OPTIONAL { ?item wdt:P625 ?coord }
       |  OPTIONAL { ?item wdt:P276/wdt:P625 ?locCoord }
       |  OPTIONAL { ?item wdt:P31 ?legend . VALUES ?legend { wd:$Mythical ${Legendary.toList.sorted
        .map("wd:" + _)
        .mkString(" ")} } }
       |  OPTIONAL { ?item wdt:P1120 ?deaths }
       |  SERVICE wikibase:label { bd:serviceParam wikibase:language "$lang". }
       |}""".stripMargin

  def sparqlUrl(endpoint: com.histmersion.plugins.events.WikidataCatalogue.SparqlUrlEndpoint, query: com.histmersion.plugins.events.WikidataCatalogue.SparqlUrlQuery): com.histmersion.plugins.events.WikidataCatalogue.SparqlUrlResult =
    s"$endpoint?format=json&query=${URLEncoder.encode(query, UTF_8)}"

  private def binding(b: ACursor, k: com.histmersion.plugins.events.WikidataCatalogue.BindingK): Option[com.histmersion.plugins.events.WikidataCatalogue.BindingResult] =
    b.downField(k).get[String]("value").toOption.map(_.trim).filter(_.nonEmpty)

  /** Pure: `Point(4.5 47.53)` → (lat 47.53, lon 4.5). */
  def point(wkt: com.histmersion.plugins.events.WikidataCatalogue.PointWkt): Option[(com.histmersion.plugins.events.WikidataCatalogue.PointResultPart1, com.histmersion.plugins.events.WikidataCatalogue.PointResultPart2)] =
    "Point\\(\\s*([-0-9.eE]+)\\s+([-0-9.eE]+)\\s*\\)".r.findFirstMatchIn(wkt).flatMap { m =>
      for lon <- m.group(1).toDoubleOption; lat <- m.group(2).toDoubleOption yield (lat, lon)
    }

  /** Pure: the events of one class's answer, one per item (its rows joined: the first coordinates, its own before its
    * location's; the most precise dating; the most deaths). Items with no label of their own (the label service then
    * answers the id) keep it.
    */
  def parse(json: Json, kind: com.histmersion.plugins.events.WikidataCatalogue.ParseKind): Either[com.histmersion.plugins.events.WikidataCatalogue.ParseResultPart1, List[CatalogueEvent]] =
    json.hcursor.downField("results").downField("bindings").focus.flatMap(_.asArray) match
      case None       => Left("no results in the answer")
      case Some(rows) =>
        val bs = rows.toList.map(_.hcursor)
        Right(bs.groupBy(b => binding(b, "item").getOrElse("")).toList.filter(_._1.nonEmpty).sortBy(_._1).flatMap {
          (uri, rs) =>
            val qid = uri.reverse.takeWhile(_ != '/').reverse
            def first(k: String) = rs.flatMap(binding(_, k)).headOption
            def bound(dateKey: String, precKey: String): Option[CatalogDating.Bound] =
              rs.flatMap(r =>
                binding(r, dateKey)
                  .flatMap(CatalogDating.isoYear)
                  .map(y => CatalogDating.wikidata(y, binding(r, precKey).flatMap(_.toIntOption).getOrElse(9)))
              ).sortBy(b => b.hi - b.lo)
                .headOption
            val at = bound("date", "datePrec")
            val start = bound("start", "startPrec").orElse(at)
            val end = bound("end", "endPrec").orElse(at)
            val pos = first("coord").flatMap(point).orElse(first("locCoord").flatMap(point))
            val legends = rs.flatMap(binding(_, "legend")).map(u => u.reverse.takeWhile(_ != '/').reverse).toSet
            val hist =
              if legends(Mythical) then "mythic"
              else if legends.exists(Legendary) then "legendary"
              else "attested"
            val label = first("itemLabel").getOrElse(qid)
            Option.when(start.isDefined || end.isDefined) {
              val d = Dated.of(CatalogDating.span(start, end))
              CatalogueEvent(
                id = qid,
                url = s"https://www.wikidata.org/wiki/$qid",
                label = label,
                kind = kind,
                notBefore = d.notBefore,
                notAfter = d.notAfter,
                beginBy = d.beginBy,
                endFrom = d.endFrom,
                lat = JsonFields.latitude(pos.map(_._1)),
                lon = JsonFields.longitude(pos.map(_._2)),
                historicity = hist,
                deaths = rs.flatMap(binding(_, "deaths")).flatMap(d => d.toDoubleOption.map(_.toLong)).maxOption,
                wikidata = WikidataId.option(qid),
                description = first("itemDescription"),
                raw = Some(Json.arr(rs.flatMap(_.focus)*).noSpaces)
              )
            }
        })
