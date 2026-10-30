package com.histmersion.domain.jobs

/** What answering a [[JobAction]] on one job takes. */
enum JobControlStep:
  /** An agent run: its own pause, resume or cancel. */
  case Run(action: JobAction)

  /** A running background job is asked to stop at its next part boundary. */
  case RequestStop(stop: JobStop)

  /** A paused job runs again, skipping the parts it had done. */
  case Resume

  /** A job that is not running (queued, or paused) is cancelled at once. */
  case CancelNow
