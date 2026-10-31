package com.histmersion.plugins.media

import hearth.kindlings.sconfigderivation.ConfigReader

/** A media plugin's settings: Histmersion's own `media.*`, which the host supplies. */
final case class MediaPluginConfig(media: MediaSettings) derives ConfigReader
