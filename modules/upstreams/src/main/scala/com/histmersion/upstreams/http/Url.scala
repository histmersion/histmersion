package com.histmersion.upstreams.http
import sttp.model.Uri

/** URL assembly that keeps query values escaped. `Uri`'s interpolator would try to parse the value as a URI; building
  * the query from pairs is what makes `q=sea peoples & co` safe.
  */
object Url:
  type UrlPath = UrlPath.T
  object UrlPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(base: com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url, path: com.histmersion.upstreams.http.Url.UrlPath = _root_.com.histmersion.upstreams.http.Url.UrlPath(""), params: (String, String)*): com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url =
    val u = Uri.unsafeParse((base).value + path)
    _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url(u.addParams(params.filter(_._2.nonEmpty)*).toString)
