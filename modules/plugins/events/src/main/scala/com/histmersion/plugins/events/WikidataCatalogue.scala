package com.histmersion
package plugins.events

import com.histmersion.plugin.Problem
import com.histmersion.plugin.*
import com.histmersion.upstreams.wikidata.WikidataClasses
import com.histmersion.upstreams.dating.CatalogDating
import com.histmersion.upstreams.wikidata.WikidataItem
import io.circe.{ACursor, Json}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import io.github.iltotore.iron.constraint.any.Pure

/** Wikidata's events of the configured classes (battles, sieges, wars, treaties …): the query service, one SPARQL query
  * per class. A part is a class, `class:<qid>`.
  */
final class WikidataCatalogue(cfg: WikidataSettings) extends EventCatalogue:
  def title = EventCatalogue.Title("Wikidata")
  def licence = Licence("CC0-1.0")
  def citation = EventCatalogue.Citation("Wikidata (CC0), https://www.wikidata.org")
  def home = EventCatalogue.Home("https://www.wikidata.org")
  override def requestIntervalMs: EventCatalogue.RequestIntervalMs =
    EventCatalogue.RequestIntervalMs(cfg.requestIntervalMs.value)

  private def classes: List[(WikidataItem.Id, WikidataClasses.ClassLabel)] =
    WikidataClasses.parse(WikidataClasses.WikidataText(cfg.classes.value))

  def parts(query: CatalogueQuery): List[EventCatalogue.ReadPart] =
    classes.map((qid, _) => EventCatalogue.ReadPart(s"class:$qid"))

  def read(
      part: EventCatalogue.ReadPart,
      query: CatalogueQuery,
      http: Plugin.Http
  ): Either[plugin.Problem, CataloguePage] =
    classes.find((qid, _) => part.value == s"class:$qid") match
      case None =>
        Left(plugin.Problem(s"'$part' is no class of the configured events.wikidata.classes"))
      case Some((qid, kind)) =>
        val q = WikidataCatalogue.sparql(
          qid,
          query.from.map(y => CatalogDating.HistoricalYear(y.value.toLong)),
          CatalogDating.HistoricalYear(query.to.value.toLong),
          cfg.labelLanguages
        )
        CatalogueJson(http)(WikidataCatalogue.sparqlUrl(cfg.sparqlUrl, q)).left
          .map(e => plugin.Problem(e.value))
          .flatMap(j =>
            WikidataCatalogue
              .parse(j, CatalogueEvent.Kind(kind.value))
              .left
              .map(e => plugin.Problem(e.value))
          )
          .map(CataloguePage(_))

  /** The item read again from its `raw` (its rows of the query's answer), in the kind it is stored with. */
  override def reread(stored: CatalogueEvent): Option[CatalogueEvent] =
    stored.raw
      .flatMap(t => io.circe.parser.parse(t.value).toOption)
      .flatMap(j =>
        WikidataCatalogue
          .parse(Json.obj("results" -> Json.obj("bindings" -> j)), stored.kind)
          .toOption
          .flatMap(_.headOption)
      )
      .map(n => n.copy(names = (stored.names ++ n.names).distinct.filterNot(_.value == n.label.value)))

object WikidataCatalogue:
  type Query = Query.T
  object Query extends Newtype[String, Pure]
  type Wkt = Wkt.T
  object Wkt extends Newtype[String, Pure]

  /** Items that are only told of, never shown to have happened: a mythical event, a legendary battle, a narrative event
    * (2026-10-14, checked against their labels).
    */
  val Mythical = "Q24336466"
  val Legendary: Set[String] = Set("Q26913948", "Q122397289")

  /** Pure: the SPARQL of one class — its instances (and its subclasses'), dated (point in time, else start/end) before
    * `to`, with their precision, their own coordinates or their location's, whether they are legendary or mythical, and
    * the deaths.
    */
  def sparql(
      qid: WikidataItem.Id,
      from: Option[CatalogDating.HistoricalYear],
      to: CatalogDating.HistoricalYear,
      lang: WikidataSettings.LabelLanguage
  ): Query =
    // the query service compares astronomical years; `to`/`from` are historical
    val toA = CatalogDating.toAstronomical(to)
    val fromF = from.fold("")(f => s" && YEAR(?t) >= ${CatalogDating.toAstronomical(f)}")
    Query(
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
    )

  def sparqlUrl(endpoint: WikidataSettings.SparqlUrl, query: Query): Plugin.Http.Url =
    Plugin.Http.Url(s"$endpoint?format=json&query=${URLEncoder.encode(query.value, UTF_8)}")

  private def binding(b: ACursor, k: String): Option[String] =
    b.downField(k).get[String]("value").toOption.map(_.trim).filter(_.nonEmpty)

  /** Pure: `Point(4.5 47.53)` → (lat 47.53, lon 4.5). */
  def point(wkt: Wkt): Option[(JsonFields.Decimal, JsonFields.Decimal)] =
    "Point\\(\\s*([-0-9.eE]+)\\s+([-0-9.eE]+)\\s*\\)".r.findFirstMatchIn(wkt.value).flatMap { m =>
      for lon <- m.group(1).toDoubleOption; lat <- m.group(2).toDoubleOption
      yield (JsonFields.Decimal(lat), JsonFields.Decimal(lon))
    }

  /** Pure: the events of one class's answer, one per item (its rows joined: the first coordinates, its own before its
    * location's; the most precise dating; the most deaths). Items with no label of their own (the label service then
    * answers the id) keep it.
    */
  def parse(json: Json, kind: CatalogueEvent.Kind): Either[Problem, List[CatalogueEvent]] =
    json.hcursor.downField("results").downField("bindings").focus.flatMap(_.asArray) match
      case None       => Left(Problem("no results in the answer"))
      case Some(rows) =>
        val bs = rows.toList.map(_.hcursor)
        Right(bs.groupBy(b => binding(b, "item").getOrElse("")).toList.filter(_._1.nonEmpty).sortBy(_._1).flatMap {
          (uri, rs) =>
            val qid = uri.reverse.takeWhile(_ != '/').reverse
            def first(k: String) = rs.flatMap(binding(_, k)).headOption
            def bound(dateKey: String, precKey: String): Option[CatalogDating.Bound] =
              rs.flatMap(r =>
                binding(r, dateKey)
                  .flatMap(d => CatalogDating.isoYear(CatalogDating.XsdYearText(d)))
                  .map(y =>
                    CatalogDating.wikidata(
                      y,
                      CatalogDating.WikidataPrecision(binding(r, precKey).flatMap(_.toIntOption).getOrElse(9))
                    )
                  )
              ).sortBy(b => b.hi.value - b.lo.value)
                .headOption
            val at = bound("date", "datePrec")
            val start = bound("start", "startPrec").orElse(at)
            val end = bound("end", "endPrec").orElse(at)
            val pos = first("coord").flatMap(c => point(Wkt(c))).orElse(first("locCoord").flatMap(c => point(Wkt(c))))
            val legends = rs.flatMap(binding(_, "legend")).map(u => u.reverse.takeWhile(_ != '/').reverse).toSet
            val hist =
              if legends(Mythical) then "mythic"
              else if legends.exists(Legendary) then "legendary"
              else "attested"
            val label = first("itemLabel").getOrElse(qid)
            Option.when(start.isDefined || end.isDefined) {
              val d = Dated.of(CatalogDating.span(start, end))
              CatalogueEvent(
                id = CatalogueEvent.Id(qid),
                url = CatalogueEvent.Url(s"https://www.wikidata.org/wiki/$qid"),
                label = CatalogueEvent.Label(label),
                kind = kind,
                notBefore = d.notBefore,
                notAfter = d.notAfter,
                beginBy = d.beginBy,
                endFrom = d.endFrom,
                lat = JsonFields.latitude(pos.map(_._1)),
                lon = JsonFields.longitude(pos.map(_._2)),
                historicity = CatalogueEvent.Historicity(hist),
                deaths = rs
                  .flatMap(binding(_, "deaths"))
                  .flatMap(d => d.toDoubleOption.map(_.toLong))
                  .maxOption
                  .map(n => CatalogueEvent.Deaths(n)),
                wikidata = Option.when(qid.matches("Q[1-9][0-9]*"))(WikidataId(qid)),
                description = first("itemDescription").map(t => CatalogueEvent.Description(t)),
                raw = Some(CatalogueEvent.Raw(Json.arr(rs.flatMap(_.focus)*).noSpaces))
              )
            }
        })
