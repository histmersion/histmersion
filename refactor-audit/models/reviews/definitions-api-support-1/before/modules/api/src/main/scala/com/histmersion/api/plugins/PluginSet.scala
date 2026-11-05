package com.histmersion.api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given

/** @param dependencies
  *   `None` in a record written before dependencies were recorded
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
      KindlingsSchema, com.histmersion.api.json.Schema
object PluginSet:


  val Resource = "META-INF/histmersion/plugin-set.json"

  /** SHA-256 of no bytes. */
  val EmptyDigest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

  def describe(s: Option[PluginSet]): String = s match
    case None | Some(PluginSet(_, _, Nil, _, _, _, _)) =>
      s"first-party plugins only (digest ${s.fold(EmptyDigest)(_.digest).take(12)}…)"
    case Some(set) =>
      s"${set.digest.take(12)}… · ${Plural.count(set.jars.size, "jar")} (${set.mode}): " +
        set.jars.map(j => j.file).mkString(", ")
