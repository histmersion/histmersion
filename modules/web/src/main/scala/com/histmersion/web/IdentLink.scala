package com.histmersion.web

import com.histmersion.web.ArticleLink
import com.raquo.laminar.api.L.*

import scala.scalajs.js
import com.histmersion.api.library.{Label, Source}
import com.histmersion.api.research.Article

/** A bare corpus identifier met in free text (`cline2014_year`, `achilles_origins`): shown readable at once, then —
  * when it names a stored source or article — upgraded to that link ("Cline 2014", the article's title). Lookups are
  * cached; an identifier that names nothing stays readable text.
  */
object IdentLink:
  enum Resolved:
    case Source(ref: com.histmersion.api.library.Source.Ref)
    case Article(slug: String, title: String)

  private val cache =
    BoundedCache[String, scala.concurrent.Future[Option[Resolved]]](com.histmersion.web.ui.Layout.identCacheSize)

  /** The lookups' figures, for [[Diagnostics]]. */
  def cacheStats: BoundedCache.Stats = BoundedCache.stats(cache)

  private def resolve(id: String): scala.concurrent.Future[Option[Resolved]] =
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    cache.getOrElseUpdate(
      id,
      ApiClient
        .resolveId(id)
        .map(r =>
          r.source.map(s => Resolved.Source(s.ref)).orElse(r.article.map(a => Resolved.Article(a.slug, a.title)))
        )
        .recover { case _ => None }
    )

  def apply(id: String): HtmlElement =
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    span(
      cls := "ident",
      child <-- Signal.fromFuture(resolve(id)).map {
        case Some(Some(Resolved.Source(r)))        => SourceLink(r.citekey, r.doi, Some(r.shortCite))
        case Some(Some(Resolved.Article(sl, tit))) => ArticleLink(sl, tit)
        case _                                     => span(Label.display(id))
      }
    )
