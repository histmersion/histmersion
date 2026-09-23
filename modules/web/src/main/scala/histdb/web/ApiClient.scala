package histdb.web

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import sttp.client4.*
import sttp.client4.fetch.FetchBackend
import sttp.tapir.PublicEndpoint
import sttp.tapir.client.sttp4.SttpClientInterpreter

import histdb.core.*

object ApiClient:

  private val backend = FetchBackend()
  private val interp  = SttpClientInterpreter()

  private def call[I, O](ep: PublicEndpoint[I, ApiError, O, Any])(in: I): Future[O] =
    val req = interp.toRequestThrowDecodeFailures(ep, None).apply(in)
    req.send(backend).map(_.body).flatMap {
      case Right(o) => Future.successful(o)
      case Left(e)  => Future.failed(Exception(e.message))
    }

  def config(): Future[UiConfig]                                       = call(Api.config)(())
  def sources(f: Filter): Future[SearchResult]                         = call(Api.sources)(f)
  def findings(q: Api.FindingsQuery): Future[List[GeoHit]]             = call(Api.findings)(q)
  def aggregate(q: Api.AggregateQuery): Future[TimeHistogram]          = call(Api.aggregate)(q)
  def facets(field: String, prefix: Option[String], f: Filter): Future[List[Facet]] =
    call(Api.facets)((field, prefix, f))
  def getSource(ck: String): Future[SourceWithFindings]                 = call(Api.getSource)(ck)
  def neighbors(ck: String, kinds: List[String] = Nil): Future[List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def articles(q: Api.ArticleQuery): Future[ArticleList]                = call(Api.articles)(q)
  def getArticle(slug: String): Future[ArticleView]                     = call(Api.getArticle)(slug)
  def places(q: Option[String], asOf: Option[Long], limit: Option[Int]): Future[List[PlaceHit]] =
    call(Api.places)((q, asOf, limit))
  def place(id: String, asOf: Option[Long]): Future[PlaceDetail]        = call(Api.place)((id, asOf))

  def queueList(list: String, all: Boolean = false): Future[Queue.QueueListing] =
    call(OpsApi.queueList)((list, all))
  def queuePatch(id: Int, p: Queue.QueuePatch): Future[Queue.QueueItem] =
    call(OpsApi.queuePatch)((id, p))

  def graph(citekey: String, depth: Option[Int] = None): Future[SourceGraph] =
    call(Api.graph)((citekey, depth))

  def periods(q: Option[String] = None, region: Option[String] = None,
      from: Option[Long] = None, to: Option[Long] = None): Future[List[HistoricalPeriod]] =
    call(Api.periods)((q, region, from, to))

  def placesGeo(kind: Option[String] = None, asOf: Option[Long] = None,
      limit: Option[Int] = None): Future[String] =
    call(Api.placesGeo)((kind, asOf, limit))

  def mediaGeo(kind: Option[String] = None, from: Option[Long] = None, to: Option[Long] = None,
      limit: Option[Int] = None): Future[List[MediaGeoHit]] =
    call(Api.mediaGeo)((kind, from, to, limit))

  def events(q: Api.EventQuery): Future[EventList] = call(Api.events)(q)

  def fetchHtml(url: String): Future[String] =
    basicRequest.get(uri"$url").response(asStringAlways).send(backend).map(_.body)
