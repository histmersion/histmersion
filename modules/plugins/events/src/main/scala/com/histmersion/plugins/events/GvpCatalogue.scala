package com.histmersion.plugins.events

import com.histmersion.plugin.*
import com.histmersion.upstreams.dating.CatalogDating
import io.circe.Json

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

/** The Smithsonian Global Volcanism Program's Holocene eruptions: its WFS
  * (`GVP-VOTW:Smithsonian_VOTW_Holocene_Eruptions`), CQL-filtered, one request per window of start years — the
  * service's paging took 45 s and more a page on 2026-10-14, a window of a thousand years a few seconds. A part is a
  * window, `years:<from>..<to>`.
  */
final class GvpCatalogue(cfg: GvpEvents) extends EventCatalogue:
  // volcano.si.edu (its terms page) did not answer on 2026-10-14; the Smithsonian's general Terms of
  // Use allow non-commercial, educational use with attribution, and the GVP asks to be cited as below.
  def title = com.histmersion.plugin.EventCatalogue.TitleResult("Smithsonian Global Volcanism Program")
  def licence = Licence("Smithsonian Terms of Use (non-commercial use with attribution)")
  def citation =
    "Global Volcanism Program, Volcanoes of the World (v. 5.x), Smithsonian Institution, compiled by Venzke, E. " +
      "https://doi.org/10.5479/si.GVP.VOTW5-2024.5.2"
  def home = com.histmersion.plugin.EventCatalogue.HomeResult("https://volcano.si.edu")
  override def requestIntervalMs: Int = cfg.gvp.requestIntervalMs
  override def floorYear: Option[Year] = JsonFields.year(cfg.gvp.floorYear)

  def parts(query: CatalogueQuery): List[com.histmersion.plugins.events.GvpCatalogue.Part] =
    GvpCatalogue
      .windows(query.from.fold(cfg.minYear)(_.value.toLong), query.to.value.toLong, cfg.gvp.windowYears)
      .map((a, b) => s"years:$a..$b")

  def read(part: com.histmersion.plugins.events.GvpCatalogue.Part, query: CatalogueQuery, http: Plugin.Http): Either[com.histmersion.plugins.events.GvpCatalogue.Problem, CataloguePage] =
    part match
      case GvpCatalogue.Window(a, b) =>
        CatalogueJson(http)(GvpCatalogue.url(cfg.gvp.url, a.toLong, b.toLong, cfg.gvp.pageSize))
          .flatMap(GvpCatalogue.parse)
          .flatMap((rs, total) =>
            Either.cond(
              total.forall(_ <= rs.size),
              CataloguePage(rs),
              s"${total.getOrElse(0)} eruptions in $a..$b, more than one request takes (events.gvp.page-size)"
            )
          )
      case other => Left(s"'$other' is no window of years of the GVP's")

  /** The feature read again from its `raw`; a record stored before the central year and the ± were kept (V088, no
    * `raw`), read back from its span and the "dated by …" of its description.
    */
  override def reread(stored: CatalogueEvent): Option[CatalogueEvent] =
    stored.raw
      .flatMap(t => io.circe.parser.parse(t).toOption)
      .flatMap(GvpCatalogue.record)
      .map(n => n.copy(names = (stored.names ++ n.names).distinct.filterNot(_ == n.label)))
      .orElse(Option.when(stored.central.isEmpty)(GvpCatalogue.fromBefore088(stored)))

object GvpCatalogue:
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Encoded = Encoded.T
  object Encoded extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Part = Part.T
  object Part extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Year = Year.T
  object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val TypeName = "GVP-VOTW:Smithsonian_VOTW_Holocene_Eruptions"

  private val Window = "years:(-?\\d+)\\.\\.(-?\\d+)".r

  private def enc(s: com.histmersion.plugins.events.GvpCatalogue.Text): com.histmersion.plugins.events.GvpCatalogue.Encoded = URLEncoder.encode(s, UTF_8)

  /** Pure: the eruptions that started in `from`…`to` (signed years, both inclusive), unpaged. */
  def url(base: com.histmersion.plugin.Plugin.Http.GetUrl, from: com.histmersion.plugins.events.GvpCatalogue.Year, to: com.histmersion.plugins.events.GvpCatalogue.Year, max: com.histmersion.plugins.events.GvpSettings.PageSize): com.histmersion.plugin.Plugin.Http.GetUrl =
    val cql = s"StartDateYear>=$from AND StartDateYear<=$to"
    s"$base?service=WFS&version=2.0.0&request=GetFeature&typeNames=${enc(TypeName)}" +
      s"&outputFormat=${enc("application/json")}&count=$max&CQL_FILTER=${enc(cql)}"

  /** Pure: `from`…`to` cut into windows of `years` (the last one shorter). */
  def windows(from: com.histmersion.plugins.events.GvpCatalogue.Year, to: com.histmersion.plugins.events.GvpCatalogue.Year, years: com.histmersion.plugins.events.GvpSettings.WindowYears): List[(com.histmersion.plugins.events.GvpCatalogue.Year, com.histmersion.plugins.events.GvpCatalogue.Year)] =
    if from > to || years <= 0 then Nil
    else Iterator.iterate(from)(_ + years).takeWhile(_ <= to).map(a => (a, (a + years - 1).min(to))).toList

  /** Pure: the records of one page and how many match in all. "Discredited" eruptions are dropped; "Uncertain" ones are
    * `contested`. Years are GVP's signed years (BCE negative), ± its stated uncertainty.
    */
  def parse(json: Json): Either[com.histmersion.plugins.events.GvpCatalogue.Problem, (List[CatalogueEvent], Option[com.histmersion.plugins.events.GvpCatalogue.Count])] =
    val c = json.hcursor
    c.downField("features").focus.flatMap(_.asArray) match
      case None =>
        Left(c.downField("exceptions").focus.map(_.noSpaces.take(200)).getOrElse("no features in the answer"))
      case Some(fs) =>
        val total = c.get[Int]("numberMatched").toOption.orElse(c.get[Int]("totalFeatures").toOption)
        Right((fs.toList.flatMap(record), total))

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
    val activity = str(p, com.histmersion.plugins.events.JsonFields.Key("Activity_Type")).getOrElse("")
    for
      num <- long(p, com.histmersion.plugins.events.JsonFields.Key("Eruption_Number"))
      year <- long(p, "StartDateYear")
      if !activity.toLowerCase(Locale.ROOT).contains("discredited")
    yield
      val volcano = str(p, "Volcano_Name").getOrElse("unnamed volcano")
      val vnum = long(p, "Volcano_Number")
      val pm = long(p, "StartDateYearUncertainty").filter(_ > 0)
      val start = CatalogDating.year(year, pm)
      val end = long(p, "EndDateYear").map(y => CatalogDating.year(y, long(p, "EndDateYearUncertainty")))
      val vei = long(p, "ExplosivityIndexMax").map(_.toInt)
      val method = str(p, "StartEvidenceMethod")
      val doubt =
        activity.toLowerCase(Locale.ROOT).contains("uncertain") || str(p, "StartDateYearModifier").contains("?")
      val d = Dated.of(CatalogDating.span(Some(start), end))
      CatalogueEvent(
        id = num.toString,
        url = vnum.fold("https://volcano.si.edu")(v => s"https://volcano.si.edu/volcano.cfm?vn=$v"),
        label = s"Eruption of $volcano",
        kind = "eruption",
        notBefore = d.notBefore,
        notAfter = d.notAfter,
        beginBy = d.beginBy,
        endFrom = d.endFrom,
        lat = latitude(lat),
        lon = longitude(lon),
        historicity = if doubt then "contested" else "attested",
        vei = vei,
        names = List(s"$volcano eruption"),
        description = Some(
          (List(s"$activity at $volcano") ++ vei.map(v => s"VEI $v") ++
            str(p, "ActivityArea").map(a => s"area: $a")).mkString("; ")
        ),
        group = vnum.map(v => s"volcano:$v"),
        central = JsonFields.year(year),
        plusMinus = pm,
        datingMethod = method,
        raw = Some(f.noSpaces)
      )

  private val Dated088 = "(?:^|;\\s*)dated by ([^;]+)".r

  /** Pure: a record stored before V088, its central year and ± read back from its span ([[CatalogDating.centralOf]])
    * and its dating method from the "dated by …" of its description.
    */
  def fromBefore088(r: CatalogueEvent): CatalogueEvent =
    val method = r.description.flatMap(d => Dated088.findFirstMatchIn(d).map(_.group(1).trim))
    val desc = r.description.map(d => Dated088.replaceAllIn(d, "").trim.stripSuffix(";").trim).filter(_.nonEmpty)
    val c = CatalogDating.centralOf(Dated.span(r))
    r.copy(
      central = c.flatMap(x => JsonFields.year(x._1)),
      plusMinus = c.map(_._2).filter(_ > 0),
      datingMethod = r.datingMethod.orElse(method),
      description = desc
    )
