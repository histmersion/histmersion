package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The user-added plugins a binary was linked with (docs/research-plugins.md §7.3): what a relink writes into the image
  * (`META-INF/histmersion/plugin-set.json`, in a small generated jar on the image class path) and beside the binary,
  * what the server reports in `/api/about` and `--self-check`, and what `histmersion plugin status` recomputes over the
  * plugins directory. First-party plugins are not in it: they are in every binary (§4.2 option B).
  */

/** @param digest
  *   SHA-256 over the sorted `<file> <sha256>` lines of the jars (order-independent); the empty set's digest for a
  *   binary linked with first-party plugins only
  * @param spiVersion
  *   the plugin SPI the binary speaks
  * @param mode
  *   `native` (linked into the image) or `jvm` (on the JVM launcher's class path)
  * @param kit
  *   the kit (Histmersion version and commit) it was linked from
  * @param linkedAt
  *   when (ISO-8601)
  * @param dependencies
  *   the POMs and `lib/` jars it was linked with; `None` in a record written before they were recorded
  */
final case class PluginSet(
    digest: String,
    spiVersion: Int,
    jars: List[PluginJar] = Nil,
    mode: String = "native",
    kit: Option[String] = None,
    linkedAt: Option[String] = None,
    dependencies: Option[List[PluginFile]] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PluginSet:
  given Schema[PluginSet] = summon[KindlingsSchema[PluginSet]].schema

  /** The file name of the resource inside the image, and of the record beside the binary. */
  val Resource = "META-INF/histmersion/plugin-set.json"

  /** The digest of the empty set: SHA-256 of no bytes. A first-party-only binary reports it. */
  val EmptyDigest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

  /** One line for a report: `<digest> · 2 jars: a-1.0.jar, b-0.1.jar`, or first-party only. */
  def describe(s: Option[PluginSet]): String = s match
    case None | Some(PluginSet(_, _, Nil, _, _, _, _)) =>
      s"first-party plugins only (digest ${s.fold(EmptyDigest)(_.digest).take(12)}…)"
    case Some(set) =>
      s"${set.digest.take(12)}… · ${Plural.count(set.jars.size, "jar")} (${set.mode}): " +
        set.jars.map(j => j.file).mkString(", ")
