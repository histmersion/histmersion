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
    digest: PluginSet.Digest,
    spiVersion: PluginSet.SpiVersion,
    jars: List[PluginJar] = Nil,
    mode: PluginSet.Mode = _root_.com.histmersion.api.plugins.PluginSet.Mode("native"),
    kit: Option[PluginSet.Kit] = None,
    linkedAt: Option[PluginSet.LinkedAt] = None,
    dependencies: Option[List[PluginFile]] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object PluginSet:
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  type Digest = Digest.T
  object Digest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mode = Mode.T
  object Mode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kit = Kit.T
  object Kit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedAt = LinkedAt.T
  object LinkedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Resource = "META-INF/histmersion/plugin-set.json"

  /** SHA-256 of no bytes. */
  val EmptyDigest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

  def describe(s: Option[PluginSet]): com.histmersion.api.plugins.PluginSet.Description = s match
    case None | Some(PluginSet(_, _, Nil, _, _, _, _)) =>
      s"first-party plugins only (digest ${s.fold(EmptyDigest)(_.digest).take(12)}…)"
    case Some(set) =>
      _root_.com.histmersion.api.plugins.PluginSet.Description(s"${(set.digest).value.take(12)}… · ${Plural.count(_root_.com.histmersion.api.Plural.CountN((set.jars.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("jar"))} (${set.mode}): " +
        set.jars.map(j => j.file).mkString(", "))
