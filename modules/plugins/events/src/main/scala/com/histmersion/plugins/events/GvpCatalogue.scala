package com.histmersion
package plugins.events

import com.histmersion.plugin.*
import com.histmersion.upstreams.dating.CatalogDating
import io.circe.Json

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import io.github.iltotore.iron.constraint.any.Pure

/** The Smithsonian Global Volcanism Program's Holocene eruptions: its WFS
  * (`GVP-VOTW:Smithsonian_VOTW_Holocene_Eruptions`), CQL-filtered, one request per window of start years — the
  * service's paging took 45 s and more a page on 2026-10-14, a window of a thousand years a few seconds. A part is a
  * window, `years:<from>..<to>`.
  */
final class GvpCatalogue(cfg: GvpEvents) extends EventCatalogue:
  // volcano.si.edu (its terms page) did not answer on 2026-10-14; the Smithsonian's general Terms of
  // Use allow non-commercial, educational use with attribution, and the GVP asks to be cited as below.
  def title = EventCatalogue.TitleResult("Smithsonian Global Volcanism Program")
  def licence = Licence("Smithsonian Terms of Use (non-commercial use with attribution)")
  def citation =
    EventCatalogue.CitationResult(
      "Global Volcanism Program, Volcanoes of the World (v. 5.x), Smithsonian Institution, compiled by Venzke, E. " +
        "https://doi.org/10.5479/si.GVP.VOTW5-2024.5.2"
    )
  def home = EventCatalogue.HomeResult("https://volcano.si.edu")
  override def requestIntervalMs: EventCatalogue.RequestIntervalMsResult =
    EventCatalogue.RequestIntervalMsResult(cfg.gvp.requestIntervalMs.value)
  override def floorYear: Option[Year] = Dated.year(CatalogDating.HistoricalYear(cfg.gvp.floorYear.value))

  def parts(query: CatalogueQuery): List[EventCatalogue.PartsResult] =
    GvpCatalogue
      .windows(
        GvpCatalogue.Year(query.from.fold(cfg.minYear.value)(_.value.toLong)),
        GvpCatalogue.Year(query.to.value.toLong),
        cfg.gvp.windowYears
      )
      .map((a, b) => EventCatalogue.PartsResult(s"years:${a.value}..${b.value}"))

  def read(
      part: EventCatalogue.ReadPart,
      query: CatalogueQuery,
      http: Plugin.Http
  ): Either[EventCatalogue.ReadResultPart1, CataloguePage] =
    part.value match
      case GvpCatalogue.Window(a, b) =>
        CatalogueJson(http)(
          GvpCatalogue.url(cfg.gvp.url, GvpCatalogue.Year(a.toLong), GvpCatalogue.Year(b.toLong), cfg.gvp.pageSize)
        ).left
          .map(e => EventCatalogue.ReadResultPart1(e.value))
          .flatMap(j => GvpCatalogue.parse(j).left.map(e => EventCatalogue.ReadResultPart1(e.value)))
          .flatMap((rs, total) =>
            Either.cond(
              total.forall(_.value <= rs.size),
              CataloguePage(rs),
              EventCatalogue.ReadResultPart1(
                s"${total.map(_.value).getOrElse(0)} eruptions in $a..$b, more than one request takes (events.gvp.page-size)"
              )
            )
          )
      case other =>
        Left(EventCatalogue.ReadResultPart1(s"'$other' is no window of years of the GVP's"))

  /** The feature read again from its `raw`; a record stored before the central year and the ± were kept (V088, no
    * `raw`), read back from its span and the "dated by …" of its description.
    */
  override def reread(stored: CatalogueEvent): Option[CatalogueEvent] =
    stored.raw
      .flatMap(t => io.circe.parser.parse(t.value).toOption)
      .flatMap(GvpCatalogue.record)
      .map(n => n.copy(names = (stored.names ++ n.names).distinct.filterNot(_.value == n.label.value)))
      .orElse(Option.when(stored.central.isEmpty)(GvpCatalogue.fromBefore088(stored)))

object GvpCatalogue:
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Year = Year.T
  object Year extends Newtype[Long, Pure]

  val TypeName = "GVP-VOTW:Smithsonian_VOTW_Holocene_Eruptions"

  private val Window = "years:(-?\\d+)\\.\\.(-?\\d+)".r

  private def enc(s: String): String = URLEncoder.encode(s, UTF_8)

  /** Pure: the eruptions that started in `from`…`to` (signed years, both inclusive), unpaged. */
  def url(base: GvpSettings.Url, from: Year, to: Year, max: GvpSettings.PageSize): Plugin.Http.GetUrl =
    val cql = s"StartDateYear>=${from.value} AND StartDateYear<=${to.value}"
    Plugin.Http.GetUrl(
      s"${base.value}?service=WFS&version=2.0.0&request=GetFeature&typeNames=${enc(TypeName)}" +
        s"&outputFormat=${enc("application/json")}&count=${max.value}&CQL_FILTER=${enc(cql)}"
    )

  /** Pure: `from`…`to` cut into windows of `years` (the last one shorter). */
  def windows(from: Year, to: Year, years: GvpSettings.WindowYears): List[(Year, Year)] =
    if from > to || years.value <= 0 then Nil
    else
      Iterator
        .iterate[Long](from.value)(_ + years.value)
        .takeWhile(_ <= to.value)
        .map(a => (Year(a), Year((a + years.value - 1).min(to.value))))
        .toList

  /** Pure: the records of one page and how many match in all. "Discredited" eruptions are dropped; "Uncertain" ones are
    * `contested`. Years are GVP's signed years (BCE negative), ± its stated uncertainty.
    */
  def parse(json: Json): Either[Problem, (List[CatalogueEvent], Option[Count])] =
    val c = json.hcursor
    c.downField("features").focus.flatMap(_.asArray) match
      case None =>
        Left(Problem(c.downField("exceptions").focus.map(_.noSpaces.take(200)).getOrElse("no features in the answer")))
      case Some(fs) =>
        val total = c.get[Int]("numberMatched").toOption.orElse(c.get[Int]("totalFeatures").toOption)
        Right((fs.toList.flatMap(record), total.map(Count(_))))

  /** Pure: one feature of the WFS's answer as an event (`None`: a discredited eruption, or no eruption number or year).
    * The stated year and ± are kept (`central`, `plusMinus`), as is the dating method (`StartEvidenceMethod`) and the
    * feature itself (`raw`).
    */
  def record(f: Json): Option[CatalogueEvent] =
    import JsonFields.*
    val p = f.hcursor.downField("properties")
    val coords =
      f.hcursor.downField("geometry").downField("coordinates").focus.flatMap(_.asArray).getOrElse(Vector.empty)
    val (lon, lat) =
      (coords.headOption.flatMap(_.asNumber).map(_.toDouble), coords.lift(1).flatMap(_.asNumber).map(_.toDouble))
    val activity = str(p, JsonFields.Key("Activity_Type")).map(_.value).getOrElse("")
    for
      num <- long(p, JsonFields.Key("Eruption_Number"))
      year <- long(p, JsonFields.Key("StartDateYear"))
      if !activity.toLowerCase(Locale.ROOT).contains("discredited")
    yield
      val volcano = str(p, JsonFields.Key("Volcano_Name")).map(_.value).getOrElse("unnamed volcano")
      val vnum = long(p, JsonFields.Key("Volcano_Number"))
      val pm = long(p, JsonFields.Key("StartDateYearUncertainty")).filter(_.value > 0)
      val start =
        CatalogDating.year(CatalogDating.HistoricalYear(year.value), pm.map(m => CatalogDating.YearMargin(m.value)))
      val end = long(p, JsonFields.Key("EndDateYear")).map(y =>
        CatalogDating.year(
          CatalogDating.HistoricalYear(y.value),
          long(p, JsonFields.Key("EndDateYearUncertainty")).map(m => CatalogDating.YearMargin(m.value))
        )
      )
      val vei = long(p, JsonFields.Key("ExplosivityIndexMax")).map(_.value.toInt)
      val method = str(p, JsonFields.Key("StartEvidenceMethod"))
      val doubt =
        activity.toLowerCase(Locale.ROOT).contains("uncertain") ||
          str(p, JsonFields.Key("StartDateYearModifier")).map(_.value).contains("?")
      val d = Dated.of(CatalogDating.span(Some(start), end))
      CatalogueEvent(
        id = CatalogueEvent.Id(num.value.toString),
        url = CatalogueEvent.Url(
          vnum.fold("https://volcano.si.edu")(v => s"https://volcano.si.edu/volcano.cfm?vn=${v.value}")
        ),
        label = CatalogueEvent.Label(s"Eruption of $volcano"),
        kind = CatalogueEvent.Kind("eruption"),
        notBefore = d.notBefore,
        notAfter = d.notAfter,
        beginBy = d.beginBy,
        endFrom = d.endFrom,
        lat = latitude(lat.map(JsonFields.Decimal(_))),
        lon = longitude(lon.map(JsonFields.Decimal(_))),
        historicity = CatalogueEvent.Historicity(if doubt then "contested" else "attested"),
        vei = vei.map(CatalogueEvent.Vei(_)),
        names = List(CatalogueEvent.Names(s"$volcano eruption")),
        description = Some(
          CatalogueEvent.Description(
            (List(s"$activity at $volcano") ++ vei.map(v => s"VEI $v") ++
              str(p, JsonFields.Key("ActivityArea")).map(a => s"area: ${a.value}")).mkString("; ")
          )
        ),
        group = vnum.map(v => CatalogueEvent.Group(s"volcano:${v.value}")),
        central = Dated.year(CatalogDating.HistoricalYear(year.value)),
        plusMinus = pm.map(m => CatalogueEvent.PlusMinus(m.value)),
        datingMethod = method.map(m => CatalogueEvent.DatingMethod(m.value)),
        raw = Some(CatalogueEvent.Raw(f.noSpaces))
      )

  private val Dated088 = "(?:^|;\\s*)dated by ([^;]+)".r

  /** Pure: a record stored before V088, its central year and ± read back from its span ([[CatalogDating.centralOf]])
    * and its dating method from the "dated by …" of its description.
    */
  def fromBefore088(r: CatalogueEvent): CatalogueEvent =
    val method = r.description.flatMap(d => Dated088.findFirstMatchIn(d.value).map(_.group(1).trim))
    val desc = r.description
      .map(d => Dated088.replaceAllIn(d.value, "").trim.stripSuffix(";").trim)
      .filter(_.nonEmpty)
      .map(CatalogueEvent.Description(_))
    val c = CatalogDating.centralOf(Dated.span(r))
    r.copy(
      central = c.flatMap(x => Dated.year(x._1)),
      plusMinus = c.map(_._2).filter(_.value > 0).map(m => CatalogueEvent.PlusMinus(m.value)),
      datingMethod = r.datingMethod.orElse(method.map(CatalogueEvent.DatingMethod(_))),
      description = desc
    )
