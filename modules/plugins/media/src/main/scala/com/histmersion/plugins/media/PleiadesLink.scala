package com.histmersion
package plugins.media

import com.histmersion.plugin.Problem
import io.github.iltotore.iron.constraint.any.Pure

/** A reference of a Pleiades place record that names a picture: the Commons file (`Right("File:…")`) or why it is not
  * taken, and the reference's CiTO type (`seeFurther`, `citesAsRelated`).
  */
final case class PleiadesLink(
    url: PleiadesLink.Url,
    citation: PleiadesLink.Citation,
    file: Either[Problem, Commons.File.Title]
)

object PleiadesLink:
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type Citation = Citation.T
  object Citation extends Newtype[String, Pure]
