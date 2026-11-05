package com.histmersion.upstreams.http
import sttp.model.Uri

/** URL assembly that keeps query values escaped. `Uri`'s interpolator would try to parse the value as a URI; building
  * the query from pairs is what makes `q=sea peoples & co` safe.
  */
object Url:
  type ApplyResult = ApplyResult.T
  object ApplyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyBase = ApplyBase.T
  object ApplyBase extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPath = ApplyPath.T
  object ApplyPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(base: com.histmersion.upstreams.http.Url.ApplyBase, path: com.histmersion.upstreams.http.Url.ApplyPath = "", params: (String, String)*): com.histmersion.upstreams.http.Url.ApplyResult =
    val u = Uri.unsafeParse(base + path)
    u.addParams(params.filter(_._2.nonEmpty)*).toString
