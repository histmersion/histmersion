package com.histmersion.plugins.media

import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.media.commons`: the action API, titles per `imageinfo` call, and `maxlag`. */
final case class CommonsSettings(apiUrl: String, batch: Int, maxlag: Int) derives ConfigReader
