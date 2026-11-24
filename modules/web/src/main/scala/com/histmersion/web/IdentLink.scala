package com.histmersion
package web

import com.raquo.laminar.api.L.*

import scala.scalajs.js
import com.histmersion.api.Api
import com.histmersion.api.library.{Label, Source}
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.web.ui.Layout
import com.histmersion.api.UiLabels
import com.histmersion.lint.{LintRule, tolerated}

/** A bare corpus identifier met in free text (`cline2014_year`, `achilles_origins`): shown readable at once, then —
  * when it names a stored source or article — upgraded to that link ("Cline 2014", the article's title). Lookups are
  * cached; an identifier that names nothing stays readable text.
  */
object IdentLink:
  type Id = Id.T
  object Id extends Newtype[String, Pure]

  enum Resolved:
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    case Source(ref: com.histmersion.api.library.Source.Ref)
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    case Article(slug: com.histmersion.api.research.Article.Slug, title: com.histmersion.api.research.Article.Title)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val cache =
    BoundedCache[String, scala.concurrent.Future[Option[Resolved]]](
      BoundedCache.MaxEntries(Layout.identCacheSize.value)
    )

  /** The lookups' figures, for [[Diagnostics]]. */
  def cacheStats: BoundedCache.Stats = BoundedCache.stats(cache)

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def resolve(id: Id): scala.concurrent.Future[Option[Resolved]] =
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    cache.getOrElseUpdate(
      id.value,
      ApiClient
        .resolveId(Api.Lookup.Text(id.value))
        .map(r =>
          r.source.map(s => Resolved.Source(s.ref)).orElse(r.article.map(a => Resolved.Article(a.slug, a.title)))
        )
        .recover { case _ => None }
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(id: Id): HtmlElement =
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    span(
      cls := "ident",
      child <-- Signal.fromFuture(resolve(id)).map {
        case Some(Some(Resolved.Source(r))) => SourceLink(r.citekey, r.doi, Some(UiLabels.Text(r.shortCite.value)))
        case Some(Some(Resolved.Article(sl, tit))) => ArticleLink(sl, tit.value)
        case _                                     => span(Label.display(Label.Text(id.value)))
      }
    )
