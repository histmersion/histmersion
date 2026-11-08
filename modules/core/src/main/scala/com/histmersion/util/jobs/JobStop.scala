package com.histmersion
package util.jobs

/** Why a running background job stops before its end: a person paused it (to be resumed) or cancelled it. */
enum JobStop derives CanEqual:
  case Pause, Cancel
