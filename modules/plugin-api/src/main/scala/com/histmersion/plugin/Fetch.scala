package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One fetcher's answer for one work. */
enum Fetch:
  /** A copy. */
  case Found(file: FetchedFile)

  /** No copy here (no identifier this fetcher reads, nothing found, the upstream did not answer). */
  case Miss(why: Fetch.Miss.Why)

  /** A copy exists but may not be taken (a lending-only item, a bot challenge, no entitlement). The host logs it and
    * moves on; it never asks the same fetcher again for this work.
    */
  case Refused(why: Fetch.Refused.Why)

object Fetch:

  /** What the host lends a fetcher for ONE fetch of one work. */
  object Miss:
    type Why = Why.T
    object Why extends RefinedType[String, Pure]

  object Refused:
    type Why = Why.T
    object Why extends RefinedType[String, Pure]

  trait Context:
    /** A value computed at most once per fetch of this work and shared by every fetcher of the chain — an index record
      * two steps both read. `compute` runs on the first call for `key`; later calls return that value. Name keys
      * `<plugin id>:<what>:<identifier>`: the value is stored untyped, so a key reused with another type fails.
      */
    def memo[A](key: Context.MemoKey)(compute: => A): A

    /** The host's log, with the fetcher's id on every line. */
    def log: Plugin.Log

    /** Whether the plugin `pluginId` is registered in the generation this fetch runs in: a fetcher that reads, on
      * another plugin's behalf, what that plugin's upstream holds asks it only while that plugin is on. `false` from a
      * host that does not say.
      */
    def registered(pluginId: Context.RegisteredPluginId): Context.RegisteredResult = Context.RegisteredResult(false)

  object Context:
    type MemoKey = MemoKey.T
    object MemoKey extends RefinedType[String, Pure]
    type RegisteredResult = RegisteredResult.T
    object RegisteredResult extends RefinedType[Boolean, Pure]
    type RegisteredPluginId = RegisteredPluginId.T
    object RegisteredPluginId extends RefinedType[String, Pure]
