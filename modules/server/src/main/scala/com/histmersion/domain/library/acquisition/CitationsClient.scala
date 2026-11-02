package com.histmersion.domain.library.acquisition

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** Papers CITING a work, with whatever intent signal the backend has. */
trait CitationsClient:
  /** `(backend, rows)` — Semantic Scholar (intents + influence + contexts), OpenAlex as fallback. */
  def citing(work: WorkRef, since: Option[Int], limit: Int): Either[String, (String, List[FollowupRow])]
