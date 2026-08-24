package histdb.server

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import sttp.model.{Header, MediaType}
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import histdb.core.{Db, GeoHit, Markdown, SourceRepo}
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.util.data.MutableDataSet
import javax.sql.DataSource

/** REST endpoints for the web UI + the static page. Interpreted on the same netty-sync server as MCP. */
object Http:

  /** A geo-located finding, flattened for the map. */
  final case class FindingPoint(
      citekey: String,
      title: String,
      claim: String,
      lon: Double,
      lat: Double,
      periodStart: Option[Long],
      periodEnd: Option[Long],
      confidence: Option[String]
  ) derives KindlingsCodecAsObject
  object FindingPoint:
    given Schema[FindingPoint] = KindlingsSchema.derived[FindingPoint].schema

  final case class FindingsQuery(
      region: Option[String],
      minLon: Option[Double],
      minLat: Option[Double],
      maxLon: Option[Double],
      maxLat: Option[Double],
      from: Option[Long],
      to: Option[Long],
      limit: Int
  )

  private lazy val ds: DataSource = Db.fromConfig()

  private def indexHtml: String =
    val is = getClass.getResourceAsStream("/web/index.html")
    scala.io.Source.fromInputStream(is, "UTF-8").mkString

  private val indexEndpoint: ServerEndpoint[Any, Identity] =
    endpoint.get
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogicSuccess[Identity](_ => indexHtml)

  private val findingsEndpoint: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in("api" / "findings")
      .in(query[Option[String]]("region"))
      .in(query[Option[Double]]("minLon"))
      .in(query[Option[Double]]("minLat"))
      .in(query[Option[Double]]("maxLon"))
      .in(query[Option[Double]]("maxLat"))
      .in(query[Option[Long]]("from"))
      .in(query[Option[Long]]("to"))
      .in(query[Int]("limit").default(3000))
      .mapInTo[FindingsQuery]
      .out(jsonBody[List[FindingPoint]])
      .serverLogicSuccess[Identity] { q =>
        val bbox = (q.minLon, q.minLat, q.maxLon, q.maxLat) match
          case (Some(a), Some(b), Some(c), Some(d)) => Some((a, b, c, d))
          case _                                    => None
        SourceRepo
          .geoWhen(ds, q.region, bbox, q.from, q.to, q.limit)
          .collect { case GeoHit(ck, t, claim, _, Some(lo), Some(la), ps, pe, conf) =>
            FindingPoint(ck, t, claim, lo, la, ps, pe, conf)
          }
          .toList
      }

  // ── Markdown → HTML article pages (flexmark) ─────────────────────────────────────────────────
  private val mdOptions = new MutableDataSet()
  private val mdParser  = Parser.builder(mdOptions).build()
  private val mdRender  = HtmlRenderer.builder(mdOptions).build()

  private def resolveLinks(md: String): String =
    md.replaceAll("""\[\[([a-z][a-z0-9_]+)\|([^\]]+)\]\]""", "[$2](/source/$1)")
      .replaceAll("""\[\[([a-z][a-z0-9_]+)\]\]""", "[$1](/source/$1)")

  private def mdToHtml(md: String): String = mdRender.render(mdParser.parse(resolveLinks(md)))

  private def page(title: String, bodyHtml: String): String =
    s"""<!doctype html><html lang="en"><head><meta charset="utf-8">
       |<meta name="viewport" content="width=device-width,initial-scale=1"><title>$title · hist-db</title>
       |<style>
       |  body{max-width:820px;margin:0 auto;padding:32px 20px;font:15px/1.65 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;color:#e6e8ee;background:#12141a}
       |  a{color:#6ea8fe} h1{font-size:22px;line-height:1.3} h2{font-size:15px;color:#9aa3b2;border-top:1px solid #2a2f3a;padding-top:14px;margin-top:26px}
       |  em{color:#9aa3b2} code{background:#1b1e27;border-radius:4px;padding:1px 4px} pre{background:#1b1e27;border-radius:6px;padding:12px;overflow:auto}
       |  ul{padding-left:20px} .nav{color:#9aa3b2;font-size:13px;margin-bottom:18px}
       |</style></head><body>
       |<div class="nav"><a href="/">← atlas</a></div>
       |$bodyHtml
       |</body></html>""".stripMargin

  private def renderSourcePage(ck: String, full: Boolean): Option[String] =
    SourceRepo.get(ds, ck).map { case (s, fs) =>
      val nb = SourceRepo.neighbors(ds, ck)
      val md = Markdown.source(s, fs, full) + "\n\n" + Markdown.neighbors(ck, nb)
      page(s.title, mdToHtml(md))
    }

  private val sourcePageEndpoint: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in("source" / path[String]("citekey"))
      .in(query[Boolean]("full").default(false))
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogicSuccess[Identity] { case (ck, full) =>
        renderSourcePage(ck, full).getOrElse(page("Not found", s"<p>No source <code>$ck</code>.</p>"))
      }

  val endpoints: List[ServerEndpoint[Any, Identity]] =
    List(findingsEndpoint, sourcePageEndpoint, indexEndpoint)
