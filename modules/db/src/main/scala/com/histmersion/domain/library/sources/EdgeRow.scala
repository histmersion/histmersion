package com.histmersion.domain.library.sources

import com.augustnagro.magnum.*

/** An edge's row: the source that makes it, its target, its kind. */
final private[library] case class EdgeRow(src: String, dst: String, kind: String) derives DbCodec
