package histdb.server

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import sttp.model.{Header, MediaType}
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import histdb.core.{Db, GeoHit, SourceRepo}
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

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(findingsEndpoint, indexEndpoint)
