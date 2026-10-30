package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Pictures that depict an event (`media.depicts`): Commons files whose structured data says `P180 depicts` the event's
  * item, and the images of the works its item lists as `P1299 depicted by`.
  */
final case class MediaDepictsConfig(perEvent: Int, searchLimit: Int, depictedBy: Boolean, licences: String)
    derives ConfigReader
