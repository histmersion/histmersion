package com.histmersion.domain.library.sources

import com.augustnagro.magnum.*
import java.util.UUID

/** A source's assessment as the bundle reads it, with the source's uid. */
final private[sources] case class BAssessmentRow(
    sourceUid: UUID,
    checkedAt: String,
    retracted: Boolean,
    notices: String,
    citedBy: Option[Int],
    influential: Option[Int],
    verdict: String
) derives DbCodec
