package com.histmersion
package config.edit

import com.histmersion.config.{ConfigLayers, ConfigReload}
import org.ekrich.config.{Config, ConfigFactory, ConfigObject, ConfigResolveOptions, ConfigValue}

import scala.jdk.CollectionConverters.*
import scala.util.Try
import com.histmersion.config.HotPlan

/** Every setting of the config file, from reference.conf: its kind, default, documentation, environment variable,
  * whether it waits for a restart and whether it is a credential — what Settings' "Advanced" form is generated from and
  * what a write is checked against. Pure: built from reference.conf's text.
  */
final class ConfigSchema private (val fields: List[SettingField], blocks: Map[SettingKey, SettingField.Doc]):

  private val byKey: Map[SettingKey, SettingField] = fields.map(f => f.key -> f).toMap

  /** A block's documentation (`histmersion.backups`), empty when it has none or is no block. */
  def blockDoc(key: SettingKey): SettingField.Doc = blocks.getOrElse(key, SettingField.Doc(""))

  def isBlock(key: SettingKey): Boolean = blocks.contains(key) && !byKey.contains(key)

  /** The setting `key`: one of reference.conf's, or a key of an open block (a plugin's own settings, the variables of
    * an AI tool's environment), which takes any single value. `None`: no such setting.
    */
  def field(key: SettingKey): Option[SettingField] =
    byKey
      .get(key)
      .orElse(
        Option.when(ConfigSchema.inOpenBlock(key))(
          SettingField(
            key,
            ConfigSchema.openKind(key),
            None,
            SettingField.Doc(""),
            None,
            ConfigSchema.restartOf(key),
            SecretRule.of(key)
          )
        )
      )

  /** The known settings named most like `key`, for "did you mean". */
  def closest(key: SettingKey, n: Int = 3): List[SettingKey] =
    val k = key.relative
    fields
      .map(f => f.key -> ConfigSchema.distance(k.value, f.key.relative.value))
      .filter(_._2 <= math.max(2, k.value.length / 3))
      .sortBy(_._2)
      .take(n)
      .map(_._1)

  /** The top-level blocks, in reference.conf's order, with the settings in each. */
  def sections: List[(SettingField.Section, List[SettingField])] =
    val order = fields.map(_.section).distinct
    order.map(s => s -> fields.filter(_.section == s))

object ConfigSchema:
  /** The schema of the reference.conf on the class path. */
  lazy val reference: ConfigSchema = of(referenceText)

  def referenceText: ReferenceDocs.Text =
    val is = Option(getClass.getClassLoader.getResourceAsStream("reference.conf"))
      .getOrElse(throw IllegalStateException("reference.conf is missing from the class path"))
    try ReferenceDocs.Text(String(is.readAllBytes, java.nio.charset.StandardCharsets.UTF_8))
    finally is.close()

  /** The schema of a reference.conf's text. */
  def of(text: ReferenceDocs.Text): ConfigSchema =
    val raw = ConfigFactory.parseString(text.value)
    val resolved = raw.resolve(ConfigResolveOptions.noSystem)
    val docs = ReferenceDocs(text)
    val nodes = walk(raw.root, Nil)
    val fields = nodes.collect { case (path, v, false) =>
      SettingKey.either(path.mkString(".")).toOption.map { key =>
        val line = v.origin.lineNumber
        val dflt = Try(resolved.getValue(key.hoconPath.value)).toOption.flatMap(SettingValue.fromConfig)
        val kind = Try(resolved.getValue(key.hoconPath.value)).toOption.map(SettingKind.of).getOrElse(SettingKind.Text)
        SettingField(
          key,
          kind,
          dflt,
          SettingField.Doc(docs.doc(line, key.leaf.value)),
          docs.envVar(line, key.leaf.value),
          restartOf(key),
          SecretRule.of(key)
        )
      }
    }.flatten
    val blocks = nodes
      .collect { case (path, v, true) =>
        SettingKey
          .either(path.mkString("."))
          .toOption
          .map(k => k -> SettingField.Doc(docs.doc(v.origin.lineNumber, k.leaf.value)))
      }
      .flatten
      .toMap
    new ConfigSchema(fields, blocks)

  /** Every node of the tree in the file's order (`isBlock` for an object), the open blocks' contents left out. */
  private def walk(
      o: ConfigObject,
      prefix: List[SettingKey.Segment]
  ): List[(List[SettingKey.Segment], ConfigValue, Boolean)] =
    o.keySet.asScala.toList.sortBy(k => o.get(k).origin.lineNumber).flatMap { k =>
      val p = prefix :+ SettingKey.Segment(k)
      o.get(k) match
        case child: ConfigObject if openBlock(p) => List((p, child, true))
        case child: ConfigObject                 =>
          // an object merged with a substitution cannot be listed before it is resolved: one value, then
          Try(walk(child, p)).fold(_ => List((p, child, false)), inside => (p, child, true) :: inside)
        case v => List((p, v, false))
    }

  private def openBlock(path: List[SettingKey.Segment]): Boolean = ConfigLayers.OpenBlocks.contains(path)

  /** The kind of an open block's key: a plugin's `enabled` is true or false (the host's own key); any other takes any
    * single value.
    */
  def openKind(key: SettingKey): SettingKind =
    if key.leaf == SettingKey.Segment("enabled") && key.within(SettingKey.applyUnsafe("histmersion.plugins")) then
      SettingKind.Flag
    else SettingKind.Any

  def inOpenBlock(key: SettingKey): Boolean =
    val p = key.segments
    ConfigLayers.OpenBlocks.exists(b => p.length > b.length && p.startsWith(b))

  def restartOf(key: SettingKey): Option[SettingField.Restart] =
    ConfigReload.RestartOnly.collectFirst {
      case (k, why) if HotPlan.keyMatches(ConfigReload.Key(key.value), k) => SettingField.Restart(why.legacyText)
    }

  /** Levenshtein distance, for "did you mean". */
  private[edit] def distance(a: String, b: String): Int =
    val prev = Array.tabulate(b.length + 1)(identity)
    val cur = Array.ofDim[Int](b.length + 1)
    var i = 1
    while i <= a.length do
      cur(0) = i
      var j = 1
      while j <= b.length do
        cur(j) = math.min(math.min(cur(j - 1) + 1, prev(j) + 1), prev(j - 1) + (if a(i - 1) == b(j - 1) then 0 else 1))
        j += 1
      System.arraycopy(cur, 0, prev, 0, cur.length)
      i += 1
    prev(b.length)

  /** The user file's own settings (no reference.conf): whether it sets anything at all — the first-run setup is offered
    * only to a file that does not.
    */
  def setsAnything(user: Config): Boolean =
    user.hasPath(SettingKey.Root) && Try(!user.getConfig(SettingKey.Root).isEmpty).getOrElse(true)
