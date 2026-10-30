package com.histmersion.api

/** One dataset Histmersion draws or reads, with its licence and the credit it asks for: the full list behind the map's
  * "Data sources" link (Settings › Credits). `onMap`: drawn on the map (the base map, places, boundaries, events), not
  * only searched.
  */
final case class DataCredit(
    name: String,
    usedFor: String,
    licence: String,
    licenceUrl: Option[String],
    credit: String,
    url: String,
    onMap: Boolean = true
)
