package com.histmersion
package config

import org.ekrich.config.{Config, ConfigFactory, ConfigRenderOptions}

import scala.jdk.CollectionConverters.*
import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The config side of an in-place reload (docs/research-plugins.md §3.5), pure: which settings differ between the
  * running config and the one just read, which of them a running server can take on, and the config it switches to. The
  * server's `hot.HotGraph` rebuilds the components those settings feed ([[HotPlan]]).
  */
object ConfigReload:
  type ChangedKey = ChangedKey.T
  object ChangedKey extends RefinedType[String, Pure]
  type Key = Key.T
  object Key extends RefinedType[String, Pure]
  type Reason = Reason.T
  object Reason extends RefinedType[String, Pure]

  type Rendered = Rendered.T
  object Rendered extends RefinedType[String, Pure]

  val Root = "histmersion"

  /** The settings of a merged tree — the `histmersion { … }` block alone (a merged tree also holds the environment's
    * variables at its root, which are no setting).
    */
  def settings(merged: org.ekrich.config.Config): org.ekrich.config.Config =
    if merged.hasPath(Root) then merged.withOnlyPath(Root) else ConfigFactory.empty

  /** The settings a running server cannot change in place, and why. The running value is kept until a restart (see
    * [[effective]]), and the reload report lists them.
    */
  val RestartOnly: List[(Key, Reason)] = List(
    Key("histmersion.server.host") -> Reason("the listener is bound once"),
    Key("histmersion.server.port") -> Reason("the listener is bound once"),
    Key("histmersion.server.request-timeout-seconds") -> Reason("the listener's request timeout is set when it is bound"),
    Key("histmersion.server.shutdown-grace-seconds") -> Reason("the listener's shutdown grace is set when it is bound"),
    Key("histmersion.runtime") -> Reason("the bundled PostgreSQL and converter are started once"),
    Key("histmersion.agents.enabled") -> Reason("the agent pool is started (or not) at startup"),
    Key("histmersion.agents.max-concurrent-runs") -> Reason("the size of the agent pool, started once"),
    Key("histmersion.agents.max-llm-calls-in-flight") -> Reason("the LLM-call semaphore, sized once"),
    Key("histmersion.converter.parallelism") -> Reason("the size of the OCR worker pool, started once")
  )

  def restartOnly(key: ChangedKey): Boolean = RestartOnly.exists((k, _) => HotPlan.keyMatches(key, k))

  /** Every leaf setting (a value or a list; `null` leaves none) with its value rendered. */
  def leaves(c: org.ekrich.config.Config): Map[ChangedKey, Rendered] =
    c.entrySet.asScala.iterator.map(e => ChangedKey(e.getKey) -> Rendered(e.getValue.render(ConfigRenderOptions.concise))).toMap

  /** The settings that differ — set in one and not the other, or set to another value — sorted. */
  def changed(before: org.ekrich.config.Config, after: org.ekrich.config.Config): List[ChangedKey] =
    val a = leaves(settings(before))
    val b = leaves(settings(after))
    (a.keySet ++ b.keySet).filter(k => a.get(k) != b.get(k)).toList.sortBy(_.value)

  /** `changed`, split into what a running server applies and what waits for a restart. */
  def split(changed: List[ChangedKey]): (List[ChangedKey], List[ChangedKey]) = changed.partition(k => !restartOnly(k))

  /** The settings the server switches to: `next`, with every restart-only setting that changed put back to its
    * `running` value — so the config the server reports is the one it runs.
    */
  def effective(
      running: org.ekrich.config.Config,
      next: org.ekrich.config.Config,
      changed: List[ChangedKey]
  ): org.ekrich.config.Config =
    changed.filter(restartOnly).foldLeft(settings(next)) { (c, k) =>
      if running.hasPath(k.value) then c.withValue(k.value, running.getValue(k.value)) else c.withoutPath(k.value)
    }

  final case class Config(watch: Config.Watch, watchDebounceMs: Config.WatchDebounceMs, drainSeconds: Config.DrainSeconds) derives ConfigReader

  object Config:
    type Watch = Watch.T
    object Watch extends RefinedType[Boolean, Pure]
    type WatchDebounceMs = WatchDebounceMs.T
    object WatchDebounceMs extends RefinedType[Int, Pure]
    type DrainSeconds = DrainSeconds.T
    object DrainSeconds extends RefinedType[Int, Pure]
