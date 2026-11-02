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

object Fetch:

  /** What the host lends a fetcher for ONE fetch of one work. */
  trait Context:
    /** A value computed at most once per fetch of this work and shared by every fetcher of the chain — an index record
      * two steps both read. `compute` runs on the first call for `key`; later calls return that value. Name keys
      * `<plugin id>:<what>:<identifier>`: the value is stored untyped, so a key reused with another type fails.
      */
    def memo[A](key: String)(compute: => A): A

    /** The host's log, with the fetcher's id on every line. */
    def log: Plugin.Log

    /** Whether the plugin `pluginId` is registered in the generation this fetch runs in: a fetcher that reads, on
      * another plugin's behalf, what that plugin's upstream holds asks it only while that plugin is on. `false` from a
      * host that does not say.
      */
    def registered(pluginId: String): Boolean = false
