package com.histmersion.domain.media.assets

/** One image figure of a source, as the illustration's choice weighs it: uid, name, size. */
final case class SourceFigure(uid: String, name: String, width: Option[Int], height: Option[Int], hasThumb: Boolean)
