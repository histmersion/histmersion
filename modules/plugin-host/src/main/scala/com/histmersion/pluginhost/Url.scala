package com.histmersion.pluginhost

import sttp.model.Uri

/** URL assembly that keeps query values escaped. `Uri`'s interpolator would try to parse the value as a URI; building
  * the query from pairs is what makes `q=sea peoples & co` safe.
  */
object Url:
  def apply(base: String, path: String = "", params: (String, String)*): String =
    val u = Uri.unsafeParse(base + path)
    u.addParams(params.filter(_._2.nonEmpty)*).toString
