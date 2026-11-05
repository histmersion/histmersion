package com.histmersion.plugins.media

/** A reference of a Pleiades place record that names a picture: the Commons file (`Right("File:…")`) or why it is not
  * taken, and the reference's CiTO type (`seeFurther`, `citesAsRelated`).
  */
final case class PleiadesLink(url: PleiadesLink.Url, citation: PleiadesLink.Citation, file: Either[PleiadesLink.FilePart1, PleiadesLink.FilePart2])

object PleiadesLink:
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citation = Citation.T
  object Citation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilePart1 = FilePart1.T
  object FilePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilePart2 = FilePart2.T
  object FilePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
