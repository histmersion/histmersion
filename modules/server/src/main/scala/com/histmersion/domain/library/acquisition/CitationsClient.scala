package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.{FollowupRow, WorkRef}

/** Papers CITING a work, with whatever intent signal the backend has. */
trait CitationsClient:
  /** `(backend, rows)` — Semantic Scholar (intents + influence + contexts), OpenAlex as fallback. */
  def citing(work: WorkRef, since: Option[Int], limit: Int): Either[String, (String, List[FollowupRow])]
