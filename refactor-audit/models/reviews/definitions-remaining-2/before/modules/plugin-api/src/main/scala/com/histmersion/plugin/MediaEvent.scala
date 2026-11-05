package com.histmersion.plugin

/** An event the host asks a collection about.
  *
  * @param id
  *   the event's id (`battle_of_kadesh`)
  * @param label
  *   its name, as the timeline shows it
  * @param wikidata
  *   its Wikidata item (`Q203729`), when it has one; the host asks only about events that have one
  * @param period
  *   its span, when it is dated
  */
final case class MediaEvent(
    id: String,
    label: String,
    wikidata: Option[WikidataId] = None,
    period: Option[Period] = None
)
