package com.histmersion.plugins.events

import com.histmersion.plugin.*
import com.histmersion.upstreams.dating.CatalogDating
import io.circe.{ACursor, Json}

import java.util.Locale

/** NOAA NCEI's significant earthquakes, eruptions and tsunamis: its hazard service
  * (`/hazel/hazard-service/api/v1/{earthquakes,volcanoes,tsunamis/events}`), page by page. A part is a page of a
  * dataset, `<dataset>:<page>`, the next page named by the one before; the pages of a dataset are of its newest
  * records, so every one is read at every import.
  */
final class NoaaCatalogue(cfg: NoaaSettings) extends EventCatalogue:
  // a U.S. federal work: public domain; NCEI asks to be cited
  def title = "NOAA NCEI Natural Hazards"
  def licence = Licence("public-domain")
  def citation =
    "National Geophysical Data Center / World Data Service: NCEI/WDS Global Significant Earthquake, Tsunami " +
      "and Volcano Databases. NOAA National Centers for Environmental Information. https://doi.org/10.7289/V5TD9V7K"
  def home = "https://www.ngdc.noaa.gov/hazard/"
  override def requestIntervalMs: Int = cfg.requestIntervalMs
  override def everyPartEachImport: Boolean = true

  def parts(query: CatalogueQuery): List[String] = cfg.datasetList.map(ds => s"$ds:1")

  def read(part: String, query: CatalogueQuery, http: Plugin.Http): Either[String, CataloguePage] =
    part match
      case NoaaCatalogue.Page(dataset, page) =>
        val p = page.toInt
        CatalogueJson(http)(
          NoaaCatalogue.url(cfg.url, dataset, query.from.map(_.value.toLong), query.to.value.toLong, p, cfg.pageSize)
        ).flatMap(NoaaCatalogue.parse(dataset, _))
          .map((rs, pages) => CataloguePage(rs, Option.when(p < pages)(s"$dataset:${p + 1}")))
      case other => Left(s"'$other' is no page of NOAA's datasets")

  /** The item read again from its `raw`, its dataset named by its id (`volcano:1351`). */
  override def reread(stored: CatalogueEvent): Option[CatalogueEvent] =
    stored.raw
      .flatMap(t => io.circe.parser.parse(t).toOption)
      .flatMap(j => NoaaCatalogue.datasetOf(stored.id).flatMap(NoaaCatalogue.record(_, j.hcursor)))
      .map(n => n.copy(names = (stored.names ++ n.names).distinct.filterNot(_ == n.label)))

object NoaaCatalogue:
  val Earthquakes = "earthquakes"
  val Volcanoes = "volcanoes"
  val Tsunamis = "tsunamis"
  val Datasets: List[String] = List(Earthquakes, Volcanoes, Tsunamis)

  private val Page = "([a-z]+):(\\d+)".r

  private def path(ds: String): String = if ds == Tsunamis then "tsunamis/events" else ds

  /** Pure: one page of a dataset's events up to `to` (and from `from`). */
  def url(base: String, dataset: String, from: Option[Long], to: Long, page: Int, perPage: Int): String =
    s"${base.stripSuffix("/")}/${path(dataset)}?maxYear=$to${from.fold("")(f => s"&minYear=$f")}" +
      s"&itemsPerPage=$perPage&page=$page"

  /** Pure: one page's events and the number of pages. Tsunamis NOAA marks erroneous (validity −1) or a mere seiche (0)
    * are dropped; very doubtful (1) and questionable (2) ones are `contested`.
    */
  def parse(dataset: String, json: Json): Either[String, (List[CatalogueEvent], Int)] =
    val c = json.hcursor
    c.downField("items").focus.flatMap(_.asArray) match
      case None        => Left("no items in the answer")
      case Some(items) =>
        Right((items.toList.flatMap(i => record(dataset, i.hcursor)), c.get[Int]("totalPages").getOrElse(1)))

  /** Pure: the dataset a NOAA record's id names (`volcano:1351` → volcanoes). */
  def datasetOf(providerId: String): Option[String] =
    providerId.takeWhile(_ != ':') match
      case "earthquake" => Some(Earthquakes)
      case "volcano"    => Some(Volcanoes)
      case "tsunami"    => Some(Tsunamis)
      case _            => None

  /** Pure: "SYRIA:  UGARIT" → "Syria: Ugarit" — NOAA's upper-case place names in title case. */
  def titleCase(s: String): String =
    "(^|[\\s(/,-])(\\p{L})".r.replaceAllIn(
      s.trim.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT),
      m => java.util.regex.Matcher.quoteReplacement(m.group(1) + m.group(2).toUpperCase(Locale.ROOT))
    )

  /** Pure: one item of a dataset's answer as an event. */
  def record(dataset: String, i: ACursor): Option[CatalogueEvent] =
    import JsonFields.*
    val kind = dataset match
      case Earthquakes => "earthquake"
      case Volcanoes   => "eruption"
      case _           => "tsunami"
    val validity = long(i, "eventValidity")
    for
      id <- long(i, "id")
      year <- long(i, "year")
      if dataset != Tsunamis || validity.forall(_ >= 1)
    yield
      val where = str(i, "locationName").orElse(str(i, "name")).map(titleCase).getOrElse("unknown place")
      val one = singular(dataset)
      val label = dataset match
        case Volcanoes => s"Eruption of ${str(i, "name").getOrElse(where)}"
        case _         => s"${one.capitalize}, $where"
      val deaths = long(i, "deathsTotal").orElse(long(i, "deaths"))
      val d = Dated.of(CatalogDating.span(Some(CatalogDating.year(year)), None))
      CatalogueEvent(
        id = s"$one:$id",
        url = s"https://www.ngdc.noaa.gov/hazel/view/hazards/${
            if dataset == Tsunamis then "tsunami" else one
          }/event-more-info/$id",
        label = label,
        kind = kind,
        notBefore = d.notBefore,
        notAfter = d.notAfter,
        beginBy = d.beginBy,
        endFrom = d.endFrom,
        lat = latitude(dbl(i, "latitude")),
        lon = longitude(dbl(i, "longitude")),
        historicity = if validity.exists(_ <= 2) then "contested" else "attested",
        // a tsunami's record carries the magnitude of the earthquake behind it: not the tsunami's size
        vei = long(i, "vei").map(_.toInt).filter(_ => dataset == Volcanoes),
        magnitude = dbl(i, "eqMagnitude").filter(_ => dataset == Earthquakes),
        deaths = deaths,
        names = Option.when(dataset == Volcanoes)(str(i, "name").map(n => s"$n eruption")).flatten.toList,
        description = Some(
          (List(s"$where") ++ dbl(i, "eqMagnitude").map(m =>
            if dataset == Earthquakes then s"magnitude $m" else s"caused by an earthquake of magnitude $m"
          ) ++
            long(i, "vei").map(v => s"VEI $v") ++ deaths.map(d => s"$d deaths recorded") ++
            dbl(i, "maxWaterHeight").map(h => s"max. water height $h m")).mkString("; ")
        ),
        group = if dataset == Volcanoes then long(i, "volcanoLocationNewNum").map(v => s"volcano:$v") else None,
        central = JsonFields.year(year),
        raw = i.focus.map(_.noSpaces)
      )

  private def singular(ds: String): String = ds match
    case Earthquakes => "earthquake"
    case Volcanoes   => "volcano"
    case _           => "tsunami"
