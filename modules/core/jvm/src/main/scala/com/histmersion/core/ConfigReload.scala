package com.histmersion.core

import org.ekrich.config.{Config, ConfigFactory, ConfigRenderOptions}

import scala.jdk.CollectionConverters.*

/** The config side of an in-place reload (docs/research-plugins.md §3.5), pure: which settings differ
  * between the running config and the one just read, which of them a running server can take on, and
  * the config it switches to. The server's `hot.HotGraph` rebuilds the components those settings
  * feed ([[HotPlan]]). */
object ConfigReload:

  val Root = "histmersion"

  /** The settings of a merged tree — the `histmersion { … }` block alone (a merged tree also holds the
    * environment's variables at its root, which are no setting). */
  def settings(merged: Config): Config =
    if merged.hasPath(Root) then merged.withOnlyPath(Root) else ConfigFactory.empty

  /** The settings a running server cannot change in place, and why. The running value is kept until
    * a restart (see [[effective]]), and the reload report lists them. */
  val RestartOnly: List[(String, String)] = List(
    "histmersion.server.host"                    -> "the listener is bound once",
    "histmersion.server.port"                    -> "the listener is bound once",
    "histmersion.server.request-timeout-seconds" -> "the listener's request timeout is set when it is bound",
    "histmersion.runtime"                        -> "the bundled PostgreSQL and converter are started once",
    "histmersion.agents.enabled"                 -> "the agent pool is started (or not) at startup",
    "histmersion.agents.max-concurrent-runs"     -> "the size of the agent pool, started once",
    "histmersion.converter.parallelism"          -> "the size of the OCR worker pool, started once")

  def restartOnly(key: String): Boolean = RestartOnly.exists((k, _) => HotPlan.keyMatches(key, k))

  /** Every leaf setting (a value or a list; `null` leaves none) with its value rendered. */
  def leaves(c: Config): Map[String, String] =
    c.entrySet.asScala.iterator.map(e => e.getKey -> e.getValue.render(ConfigRenderOptions.concise)).toMap

  /** The settings that differ — set in one and not the other, or set to another value — sorted. */
  def changed(before: Config, after: Config): List[String] =
    val a = leaves(settings(before))
    val b = leaves(settings(after))
    (a.keySet ++ b.keySet).filter(k => a.get(k) != b.get(k)).toList.sorted

  /** `changed`, split into what a running server applies and what waits for a restart. */
  def split(changed: List[String]): (List[String], List[String]) = changed.partition(k => !restartOnly(k))

  /** The settings the server switches to: `next`, with every restart-only setting that changed put back
    * to its `running` value — so the config the server reports is the one it runs. */
  def effective(running: Config, next: Config, changed: List[String]): Config =
    changed.filter(restartOnly).foldLeft(settings(next)) { (c, k) =>
      if running.hasPath(k) then c.withValue(k, running.getValue(k)) else c.withoutPath(k)
    }
