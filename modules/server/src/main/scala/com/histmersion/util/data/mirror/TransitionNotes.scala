package com.histmersion.util.data.mirror

import com.histmersion.api.ApiError

/** Today's per-kind note rendering, which the composition root hands the mirror until each owner registers a note of
  * its own: a source's note (its frontmatter and its notes) and an article's (with its generated coverage sections).
  */
trait TransitionNotes:
  def sourceNote(citekey: String): Either[ApiError, String]
  def articleNote(slug: String): Either[ApiError, String]
