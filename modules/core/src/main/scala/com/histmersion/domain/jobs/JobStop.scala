package com.histmersion.domain.jobs

/** Why a running background job stops before its end: a person paused it (to be resumed) or cancelled it. */
enum JobStop:
  case Pause, Cancel
