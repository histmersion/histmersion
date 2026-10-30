package com.histmersion.plugin

/** One fetcher's answer for one work. */
enum Fetch:
  /** A copy. */
  case Found(file: FetchedFile)

  /** No copy here (no identifier this fetcher reads, nothing found, the upstream did not answer). */
  case Miss(why: String)

  /** A copy exists but may not be taken (a lending-only item, a bot challenge, no entitlement). The host logs it and
    * moves on; it never asks the same fetcher again for this work.
    */
  case Refused(why: String)
