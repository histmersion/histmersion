package com.histmersion.plugin

/** One name of a place: an alternate, a period's or a language's ("Hatti", "Anatolia", "Türkiye").
  *
  * @param name
  *   the name as written
  * @param lang
  *   its language (`en`, `de`; an ISO 639 code), `""` when the dataset does not say
  * @param period
  *   when the name was used; `None`: always
  * @param open
  *   the end of [[period]] the dataset does not know, if one
  */
final case class GazetteerName(
    name: String,
    lang: String = "",
    period: Option[Period] = None,
    open: Option[GazetteerOpenSide] = None
)
