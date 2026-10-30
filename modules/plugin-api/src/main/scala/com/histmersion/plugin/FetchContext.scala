package com.histmersion.plugin

/** What the host lends a fetcher for ONE fetch of one work. */
trait FetchContext:
  /** A value computed at most once per fetch of this work and shared by every fetcher of the chain — an index record
    * two steps both read. `compute` runs on the first call for `key`; later calls return that value. Name keys
    * `<plugin id>:<what>:<identifier>`: the value is stored untyped, so a key reused with another type fails.
    */
  def memo[A](key: String)(compute: => A): A

  /** The host's log, with the fetcher's id on every line. */
  def log: PluginLog
