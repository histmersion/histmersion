package com.histmersion.web.setup

import com.histmersion.api.instance.MapStartConfig

/** What a person chose in the setup. `area`: the background map's box to download (`None`: none); `finders`: each
  * open-access finder on or off.
  */
final case class SetupChoices(
    email: String = "",
    finders: Map[String, Boolean] = Map.empty,
    area: Option[String] = Some("world"),
    start: MapStartConfig = MapStartConfig(),
    boundaries: Boolean = true,
    events: Boolean = true,
    pictures: Boolean = false,
    morePlaces: Boolean = false,
    agents: Boolean = false,
    calibre: String = ""
)
