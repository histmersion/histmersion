package com.histmersion.plugins.media

/** A reference of a Pleiades place record that names a picture: the Commons file (`Right("File:…")`) or why it is not
  * taken, and the reference's CiTO type (`seeFurther`, `citesAsRelated`).
  */
final case class PleiadesLink(url: PleiadesLink.Url, citation: PleiadesLink.Citation, file: Either[PleiadesLinks.Problem, Commons.File.Title])

object PleiadesLink:
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citation = Citation.T
  object Citation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
