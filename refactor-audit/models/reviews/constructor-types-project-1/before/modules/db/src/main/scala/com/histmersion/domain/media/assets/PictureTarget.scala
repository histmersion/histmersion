package com.histmersion.domain.media.assets

/** What a picture is shown on: a place, an event, or a source (its cover or illustration). */
enum PictureTarget(val wire: String):
  case Place extends PictureTarget("place")
  case Event extends PictureTarget("event")
  case Source extends PictureTarget("source")
