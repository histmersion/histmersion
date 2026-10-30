package com.histmersion.plugin

/** On what terms a fetcher obtains its copy. The host decides from this alone whether it may run. */
enum Access:
  /** Openly licensed or free to read: an open-access index, a repository, a public archive. */
  case Open

  /** Through the user's own subscription, key or entitlement, within that provider's terms. */
  case Subscription

  /** Without the rights holder's authorisation. Never run on the legal-only path, and on the ordinary path only when
    * `histmersion.acquire.allow-unauthorised = true`.
    */
  case Unauthorised
