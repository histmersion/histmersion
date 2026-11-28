package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.TwoStates
import com.histmersion.api.library.Doi
import com.histmersion.util.json.JsonCodec
import com.histmersion.util.problem.Problem

final case class FailedIngest(doi: Doi, why: Problem, noCopy: FailedIngest.NoCopy) derives JsonCodec

object FailedIngest:
  enum NoCopy derives CanEqual:
    case NoLegalCopy, LegalCopyMaybe
  object NoCopy extends TwoStates[NoCopy]
