package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** Both bounds inclusive: Postgres keeps an `int8range` as `[lower, upper)`, so `upper − 1` is applied on the way out
  * and undone on the way in.
  */
final case class BundleRange(start: Option[Long] = None, end: Option[Long] = None) derives KindlingsCodecAsObject
