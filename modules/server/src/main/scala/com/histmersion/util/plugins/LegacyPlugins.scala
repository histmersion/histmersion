package com.histmersion.util.plugins

import scala.annotation.tailrec
import com.histmersion.pluginhost.PluginHost

import org.ekrich.config.{Config, ConfigValueFactory}

import scala.jdk.CollectionConverters.*
import scala.util.Try

/** Plugin ids that were SPLIT, and how their settings are still honoured (docs/research-plugins.md §20.2).
  *
  * `oa-index` (one built-in plugin registering the `unpaywall`, `core`, `openalex` and `landing` fetchers) became four
  * plugins on 2026-10-18 (D4): `oa-unpaywall`, `oa-core`, `oa-openalex`, `oa-landing`. A config written for it keeps
  * working, with a deprecation warning in every report (`--self-check`, `/api/about`, `histmersion about`):
  *
  *   - `histmersion.plugins.oa-index.enabled` applies to each of the four, unless that plugin's own block sets
  *     `enabled` (`oa-index.enabled = false` switches all four off);
  *   - another key of the block goes to the successor whose defaults have that key, unless it sets the key itself; a
  *     key no successor has is reported and ignored (it was an error in `oa-index` too);
  *   - `HISTMERSION_PLUGIN_OA_INDEX_ENABLED` stands for each successor's `HISTMERSION_PLUGIN_<ID>_ENABLED` that is not
  *     set — with the precedence it always had (the environment over the file);
  *   - `oa-index` in `histmersion.plugins.order` is replaced, in place, by the four.
  *
  * Pure: config in, config and warnings out. Applied before [[PluginHost.build]] by [[Plugins.load]].
  */
object LegacyPlugins:

  /** A former plugin id and the plugins that took its place, in their chain order. */
  final case class Split(id: Split.Id, into: List[Split.Into])

  object Split:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Into = Into.T
    object Into extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val OaIndex: Split = Split("oa-index", List("oa-unpaywall", "oa-core", "oa-openalex", "oa-landing"))

  val All: List[Split] = List(OaIndex)

  /** The former ids: no plugin may take one (its settings would be read as the successors'). */
  def ids: Set[String] = All.map(_.id).toSet

  /** The plugins a key of `id` concerns: the successors of a former id, else the id itself. */
  def expand(id: String): List[String] = All.find(_.id == id).fold(List(id))(_.into)

  /** The environment variable the host reads for a plugin's `enabled` (its defaults declare it). */
  def enabledEnv(id: String): String =
    s"HISTMERSION_PLUGIN_${id.toUpperCase(java.util.Locale.ROOT).replace('-', '_')}_ENABLED"

  final case class Migrated(user: Config, env: Config, warnings: List[Migrated.Warnings])

  object Migrated:
    type Warnings = Warnings.T
    object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Map every former id's settings onto its successors. `known(id)`: the keys of plugin `id`'s defaults (`enabled`
    * aside), which decide where a key other than `enabled` goes.
    */
  def migrate(user: Config, env: Config, known: String => Set[String]): Migrated =
    All.foldLeft(Migrated(user, env, Nil)) { (m, split) =>
      val (u1, w1) = block(m.user, split, known)
      val (u2, w2) = order(u1, m.env, split)
      val (e1, w3) = environment(m.env, split)
      Migrated(u2, e1, m.warnings ++ w1 ++ w2 ++ w3)
    }

  private def path(id: String): String = s"${PluginHost.Root}.$id"

  private def has(c: Config, p: String): Boolean = Try(c.hasPathOrNull(p)).getOrElse(false)

  /** The former block's keys onto the successors; the block itself removed. */
  private def block(user: Config, split: Split, known: String => Set[String]): (Config, List[String]) =
    val from = path(split.id)
    if !has(user, from) then (user, Nil)
    else
      Try(user.getConfig(from)).toOption match
        case None =>
          (
            user.withoutPath(from),
            List(s"$from must be a block { … } — ignored; it is deprecated: " + successors(split))
          )
        case Some(old) =>
          val keys = old.root.keySet.asScala.toList.sorted
          @tailrec def carry(rest: List[String], acc: Config, ign: List[String]): (Config, List[String]) =
            rest match
              case Nil       => (acc, ign)
              case k :: tail =>
                val targets = if k == "enabled" then split.into else split.into.filter(id => known(id)(k))
                if targets.isEmpty then carry(tail, acc, ign :+ k)
                else
                  val v = old.root.get(k)
                  val withK = targets.foldLeft(acc) { (c, id) =>
                    val to = s"${path(id)}.${ConfigQuote(k)}"
                    if has(c, to) then c else c.withValue(to, v)
                  }
                  carry(tail, withK, ign)
          val (out, ignored) = carry(keys, user.withoutPath(from), Nil)
          val applied = keys.filterNot(ignored.contains)
          val warn = s"$from is deprecated: " + successors(split) +
            (if applied.isEmpty then "" else s"; its ${applied.mkString(", ")} applied to them") +
            s" — write ${PluginHost.Root}.<id> instead"
          (out, warn :: ignored.map(k => s"$from.$k: none of ${split.into.mkString(", ")} has this setting — ignored"))

  /** `order`: the former id replaced in place by its successors not listed already. */
  private def order(user: Config, env: Config, split: Split): (Config, List[String]) =
    val at = s"${PluginHost.Root}.${PluginHost.OrderKey}"
    val listed = Try(user.withFallback(env).resolve().getStringList(at).asScala.toList.map(_.trim)).toOption
    listed match
      case Some(ids) if ids.contains(split.id) =>
        val replaced = ids.flatMap(id => if id == split.id then split.into.filterNot(ids.contains) else List(id))
        (
          user.withValue(at, ConfigValueFactory.fromIterable(replaced.asJava)),
          List(s"$at names '${split.id}', which is deprecated: ${successors(split)} — listed in its place")
        )
      case _ => (user, Nil)

  /** The former `enabled` variable for each successor's that is not set. */
  private def environment(env: Config, split: Split): (Config, List[String]) =
    val old = enabledEnv(split.id)
    Option(env.root.get(old)) match
      case None    => (env, Nil)
      case Some(v) =>
        val out = split.into.map(enabledEnv).foldLeft(env) { (e, name) =>
          if e.root.containsKey(name) then e else e.withValue(ConfigQuote(name), v)
        }
        (
          out,
          List(s"$old is deprecated: ${successors(split)} — set ${split.into.map(enabledEnv).mkString(", ")} instead")
        )

  private def successors(split: Split): String =
    s"'${split.id}' is ${split.into.size} plugins now (${split.into.mkString(", ")})"

  /** A key as one HOCON path element (an environment variable's name or a block's key). */
  private object ConfigQuote:
    def apply(k: String): String = org.ekrich.config.ConfigUtil.quoteString(k)
