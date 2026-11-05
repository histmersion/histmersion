package com.histmersion.plugins.media

/** A reference of a Pleiades place record that names a picture: the Commons file (`Right("File:…")`) or why it is not
  * taken, and the reference's CiTO type (`seeFurther`, `citesAsRelated`).
  */
final case class PleiadesLink(url: String, citation: String, file: Either[String, String])
