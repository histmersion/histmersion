package com.histmersion
package upstreams.http
import sttp.model.Uri
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.upstreams.landing.RepositoryLanding

/** URL assembly that keeps query values escaped. `Uri`'s interpolator would try to parse the value as a URI; building
  * the query from pairs is what makes `q=sea peoples & co` safe.
  */
object Url:
  type UrlPath = UrlPath.T
  object UrlPath extends Newtype[String, Pure]

  def apply(
      base: RepositoryLanding.Candidate.Url,
      path: UrlPath = UrlPath(""),
      params: (String, String)*
  ): RepositoryLanding.Candidate.Url =
    val u = Uri.unsafeParse((base).value + path)
    RepositoryLanding.Candidate.Url(u.addParams(params.filter(_._2.nonEmpty)*).toString)
