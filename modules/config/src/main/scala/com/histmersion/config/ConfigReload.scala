package com.histmersion.config

import org.ekrich.config.{Config, ConfigFactory, ConfigRenderOptions}

import scala.jdk.CollectionConverters.*
import hearth.kindlings.sconfigderivation.ConfigReader

/** The config side of an in-place reload (docs/research-plugins.md §3.5), pure: which settings differ between the
  * running config and the one just read, which of them a running server can take on, and the config it switches to. The
  * server's `hot.HotGraph` rebuilds the components those settings feed ([[HotPlan]]).
  */
object ConfigReload:
  type ChangedKey = ChangedKey.T
  object ChangedKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type LeavesResultPart2 = LeavesResultPart2.T
  object LeavesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Root = "histmersion"

  /** The settings of a merged tree — the `histmersion { … }` block alone (a merged tree also holds the environment's
    * variables at its root, which are no setting).
    */
  def settings(merged: org.ekrich.config.Config): org.ekrich.config.Config =
    if merged.hasPath(Root) then merged.withOnlyPath(Root) else ConfigFactory.empty

  /** The settings a running server cannot change in place, and why. The running value is kept until a restart (see
    * [[effective]]), and the reload report lists them.
    */
  val RestartOnly: List[(com.histmersion.config.ConfigReload.Key, com.histmersion.config.ConfigReload.Reason)] = List(
    "histmersion.server.host" -> "the listener is bound once",
    "histmersion.server.port" -> "the listener is bound once",
    "histmersion.server.request-timeout-seconds" -> "the listener's request timeout is set when it is bound",
    "histmersion.server.shutdown-grace-seconds" -> "the listener's shutdown grace is set when it is bound",
    "histmersion.runtime" -> "the bundled PostgreSQL and converter are started once",
    "histmersion.agents.enabled" -> "the agent pool is started (or not) at startup",
    "histmersion.agents.max-concurrent-runs" -> "the size of the agent pool, started once",
    "histmersion.agents.max-llm-calls-in-flight" -> "the LLM-call semaphore, sized once",
    "histmersion.converter.parallelism" -> "the size of the OCR worker pool, started once"
  )

  def restartOnly(key: com.histmersion.config.ConfigReload.Key): Boolean = RestartOnly.exists((k, _) => HotPlan.keyMatches(key, k))

  /** Every leaf setting (a value or a list; `null` leaves none) with its value rendered. */
  def leaves(c: org.ekrich.config.Config): Map[com.histmersion.config.ConfigReload.Key, com.histmersion.config.ConfigReload.LeavesResultPart2] =
    c.entrySet.asScala.iterator.map(e => e.getKey -> e.getValue.render(ConfigRenderOptions.concise)).toMap

  /** The settings that differ — set in one and not the other, or set to another value — sorted. */
  def changed(before: org.ekrich.config.Config, after: org.ekrich.config.Config): List[com.histmersion.config.ConfigReload.ChangedKey] =
    val a = leaves(settings(before))
    val b = leaves(settings(after))
    (a.keySet ++ b.keySet).filter(k => a.get(k) != b.get(k)).toList.sorted

  /** `changed`, split into what a running server applies and what waits for a restart. */
  def split(changed: List[com.histmersion.config.ConfigReload.ChangedKey]): (List[com.histmersion.config.ConfigReload.ChangedKey], List[com.histmersion.config.ConfigReload.ChangedKey]) = changed.partition(k => !restartOnly(k))

  /** The settings the server switches to: `next`, with every restart-only setting that changed put back to its
    * `running` value — so the config the server reports is the one it runs.
    */
  def effective(
      running: org.ekrich.config.Config,
      next: org.ekrich.config.Config,
      changed: List[com.histmersion.config.ConfigReload.ChangedKey]
  ): org.ekrich.config.Config =
    changed.filter(restartOnly).foldLeft(settings(next)) { (c, k) =>
      if running.hasPath(k) then c.withValue(k, running.getValue(k)) else c.withoutPath(k)
    }

  final case class Config(watch: Config.Watch, watchDebounceMs: Config.WatchDebounceMs, drainSeconds: Config.DrainSeconds) derives ConfigReader

  object Config:
    type Watch = Watch.T
    object Watch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WatchDebounceMs = WatchDebounceMs.T
    object WatchDebounceMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DrainSeconds = DrainSeconds.T
    object DrainSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
