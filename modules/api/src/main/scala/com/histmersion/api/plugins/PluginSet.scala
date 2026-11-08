package com.histmersion
package api.plugins
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** @param dependencies
  *   `None` in a record written before dependencies were recorded
  */
final case class PluginSet(
    digest: PluginSet.Digest,
    spiVersion: PluginSet.SpiVersion,
    jars: List[PluginJar] = Nil,
    mode: PluginSet.Mode = PluginSet.Mode("native"),
    kit: Option[PluginSet.Kit] = None,
    linkedAt: Option[PluginSet.LinkedAt] = None,
    dependencies: Option[List[PluginFile]] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema
object PluginSet:
  type Description = Description.T
  object Description extends Newtype[String, Pure]

  type DescribeResult = Description
  val DescribeResult: Description.type = Description

  type Digest = Digest.T
  object Digest extends Newtype[String, Pure]
  export PluginDescription.SpiVersion
  type Mode = Mode.T
  object Mode extends Newtype[String, Pure]
  type Kit = Kit.T
  object Kit extends Newtype[String, Pure]
  type LinkedAt = LinkedAt.T
  object LinkedAt extends Newtype[String, Pure]

  val Resource = "META-INF/histmersion/plugin-set.json"

  /** SHA-256 of no bytes. */
  val EmptyDigest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

  def describe(s: Option[PluginSet]): Description = s match
    case None | Some(PluginSet(_, _, Nil, _, _, _, _)) =>
      Description(s"first-party plugins only (digest ${s.fold(EmptyDigest)(_.digest.value).take(12)}…)")
    case Some(set) =>
      Description(
        s"${(set.digest).value.take(12)}… · ${Plural.count(Plural.CountN((set.jars.size).toLong), Plural.CountSingular("jar"))} (${set.mode}): " +
          set.jars.map(j => j.file).mkString(", ")
      )
