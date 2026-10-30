package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** A nullable `int8range` read losslessly: `None` bounds are unbounded, both are INCLUSIVE (the `upper − 1` of
  * AGENTS.md is applied on the way out and undone on the way in).
  */
final case class BundleRange(start: Option[Long] = None, end: Option[Long] = None) derives KindlingsCodecAsObject
