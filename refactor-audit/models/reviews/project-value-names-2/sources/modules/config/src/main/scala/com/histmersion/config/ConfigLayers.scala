package com.histmersion.config

import scala.annotation.tailrec
import org.ekrich.config.{
  Config,
  ConfigException,
  ConfigFactory,
  ConfigObject,
  ConfigOrigin,
  ConfigParseOptions,
  ConfigRenderOptions,
  ConfigUtil,
  ConfigValue,
  ConfigValueFactory,
  ConfigValueType
}
import hearth.kindlings.sconfigderivation.ConfigDecodingError

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** Reading `reference.conf`, the user config file and the environment into one HOCON tree, with the precedence
  * ENVIRONMENT > USER FILE > reference.conf.
  *
  * The difficulty: reference.conf carries its env overrides as substitutions on the same key (`max = 8` then
  * `max = ${?HISTMERSION_DB_POOL_MAX}`). Laying the user file over it with `file.withFallback(reference)` replaces that
  * whole stack for every key the file sets, so the env line is lost and the file would beat the environment. So the
  * environment is turned into a layer of its own that goes ON TOP of the file: reference.conf is resolved against a
  * MARKER environment (each set variable's value replaced by a unique marker naming it), and every key whose resolved
  * value IS exactly a marker is a key the environment overrides directly — it gets the real variable's value in the
  * layer. A value that merely CONTAINS a marker (a future `"${?X}/sub"` concatenation) is derived, not overridden, and
  * stays out, so a file's explicit setting of it is not beaten by an env var that was meant for another key. This reads
  * only the public sconfig API (no internal value classes) and needs no list of variables kept in step with
  * reference.conf.
  */
private[config] object ConfigLayers:

  type CheckLoopResultPart1 = CheckLoopResultPart1.T
  object CheckLoopResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckLoopRestPart1 = CheckLoopRestPart1.T
  object CheckLoopRestPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckLoopRestPart2 = CheckLoopRestPart2.T
  object CheckLoopRestPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckLoopSettings = CheckLoopSettings.T
  object CheckLoopSettings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckLoopUnknown = CheckLoopUnknown.T
  object CheckLoopUnknown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type LeavesResultPart1 = LeavesResultPart1.T
  object LeavesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeavesResultPart2 = LeavesResultPart2.T
  object LeavesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeavesPrefix = LeavesPrefix.T
  object LeavesPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenBlocks = OpenBlocksValue
  type OpenBlocksValue = OpenBlocksValue.T
  object OpenBlocksValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InOpenBlockResult = InOpenBlockResult.T
  object InOpenBlockResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InOpenBlockKeys = InOpenBlockKeys.T
  object InOpenBlockKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart1 = CheckResultPart1.T
  object CheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypeCheckResultPart1 = TypeCheckResultPart1.T
  object TypeCheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypeCheckSettings = TypeCheckSettings.T
  object TypeCheckSettings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MismatchResult = MismatchResult.T
  object MismatchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MismatchPath = MismatchPath.T
  object MismatchPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SameFileResult = SameFileResult.T
  object SameFileResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribeResult = DescribeResult.T
  object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def reference: Config = ConfigFactory.parseResources("reference.conf")

  /** The env-override layer: `histmersion.<key> = <value of its variable>` for every key of `reference` whose `${?VAR}`
    * the environment sets.
    */
  def envOverrides(reference: Config, env: Config): Config =
    val layer = EnvOverrides.layer(reference, env)
    if !layer.hasPath("histmersion") then ConfigFactory.empty
    else layer.withOnlyPath("histmersion")

  /** Every key the user file sets (full paths, sorted), each with whether its value is a block. Unresolved values are
    * leaves; an object that cannot be listed (a delayed merge) is one too.
    */
  private def leaves(obj: ConfigObject, prefix: List[com.histmersion.config.ConfigLayers.LeavesPrefix]): List[(List[com.histmersion.config.ConfigLayers.LeavesResultPart1], com.histmersion.config.ConfigLayers.LeavesResultPart2)] =
    obj.keySet.asScala.toList.sorted.flatMap { k =>
      val p = prefix :+ k
      obj.get(k) match
        case o: ConfigObject =>
          Try(leaves(o, p)).toOption match
            case Some(Nil) | None => List(p -> true)
            case Some(ls)         => ls
        case _ => List(p -> false)
    }

  /** The keys a user file sets, split against the known settings (`known` = reference.conf, resolved): the settings it
    * sets (single values), and the keys that are no setting. A single value where a block of settings belongs is an
    * error — it would hide every setting of the block.
    */
  final case class Keys(settings: List[Keys.Settings], unknown: List[Keys.Unknown])

  object Keys:
    type Settings = Settings.T
    object Settings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unknown = Unknown.T
    object Unknown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Blocks whose keys are the user's own (a map, e.g. the variables of the `claude` CLI's environment), not a fixed
    * set of settings: any key under one is known, and its value — any string, or null — is left to the reader.
    */
  val OpenBlocks: List[List[com.histmersion.config.ConfigLayers.OpenBlocks]] = List(
    List("histmersion", "agents", "providers", "claude-code", "env"),
    // each plugin's block is checked by the plugin host against THAT plugin's defaults resource
    // (com.histmersion.pluginhost.PluginHost.typeCheck), so `order` and every `<id> { … }` are open here
    List("histmersion", "plugins"),
    // one allowlist per media collection a plugin registers, by the collection's id
    List("histmersion", "media", "collections", "per-collection")
  )

  private def inOpenBlock(keys: List[com.histmersion.config.ConfigLayers.InOpenBlockKeys]): com.histmersion.config.ConfigLayers.InOpenBlockResult =
    OpenBlocks.exists(b => keys.length > b.length && keys.startsWith(b))

  def check(user: Config, known: Config): Either[com.histmersion.config.ConfigLayers.CheckResultPart1, Keys] =
    @tailrec def loop(
        rest: List[(List[com.histmersion.config.ConfigLayers.CheckLoopRestPart1], com.histmersion.config.ConfigLayers.CheckLoopRestPart2)],
        settings: List[com.histmersion.config.ConfigLayers.CheckLoopSettings],
        unknown: List[com.histmersion.config.ConfigLayers.CheckLoopUnknown]
    ): Either[com.histmersion.config.ConfigLayers.CheckLoopResultPart1, Keys] =
      rest match
        case Nil                                    => Right(Keys(settings, unknown))
        case (keys, _) :: tail if inOpenBlock(keys) => loop(tail, settings, unknown)
        case (keys, isBlock) :: tail                =>
          val path = ConfigUtil.joinPath(keys.asJava)
          if !Try(known.hasPathOrNull(path)).getOrElse(false) then loop(tail, settings, unknown :+ path)
          else if Try(known.getValue(path).valueType == ConfigValueType.OBJECT).getOrElse(false) then
            if isBlock then loop(tail, settings, unknown)
            else Left(s"'$path' is a block of settings ($path { … }), not a single value")
          else loop(tail, settings :+ path, unknown)
    loop(leaves(user.root, Nil), Nil, Nil)

  /** Each setting the user file sets against the KIND of value reference.conf has there. The derived readers do not say
    * which key a wrong type is at, so the key is found here first. A numeric or boolean string passes, as the readers
    * accept it (that is how env values arrive). Finer mismatches (a fraction for a whole number) are left to the
    * readers.
    */
  def typeCheck(settings: List[com.histmersion.config.ConfigLayers.TypeCheckSettings], merged: Config, known: Config): Either[com.histmersion.config.ConfigLayers.TypeCheckResultPart1, Unit] =
    settings.iterator
      .flatMap(path =>
        (Try(known.getValue(path)).toOption, Try(merged.getValue(path)).toOption) match
          case (Some(k), Some(v)) => mismatch(path, k, v)
          case _                  => None
      )
      .nextOption()
      .toLeft(())

  private def mismatch(path: com.histmersion.config.ConfigLayers.MismatchPath, k: ConfigValue, v: ConfigValue): Option[com.histmersion.config.ConfigLayers.MismatchResult] =
    import ConfigValueType.*
    def text = Option(v.unwrapped).map(_.toString.trim).getOrElse("")
    def shown = v.render(ConfigRenderOptions.concise)
    val (ok, want) = k.valueType match
      case NUMBER  => (v.valueType == NUMBER || v.valueType == STRING && text.toDoubleOption.isDefined, "a number")
      case BOOLEAN =>
        (v.valueType == BOOLEAN || v.valueType == STRING && text.toBooleanOption.isDefined, "true or false")
      case STRING => (v.valueType == STRING || v.valueType == NUMBER || v.valueType == BOOLEAN, "a string")
      case LIST   => (v.valueType == LIST, "a list [ … ]")
      case _      => (true, "")
    Option.unless(ok)(s"'$path' must be $want, not $shown")

  /** `file` as HOCON; `None` when there is no such file. */
  def parse(file: Path): Either[com.histmersion.config.ConfigLayers.ParseResultPart1, Option[Config]] =
    if !Files.exists(file) then Right(None)
    else if !Files.isRegularFile(file) then Left(s"$file is not a file")
    else
      try Right(Some(ConfigFactory.parseFile(file.toFile, ConfigParseOptions.defaults.setAllowMissing(false))))
      catch case e: ConfigException => Left(e.getMessage)

  /** Is `o` the origin of `file` itself, not of a file it includes? */
  def sameFile(o: ConfigOrigin, file: Path): com.histmersion.config.ConfigLayers.SameFileResult =
    Option(o.filename).exists(f =>
      Try(Path.of(f).toAbsolutePath.normalize).toOption.contains(file.toAbsolutePath.normalize)
    )

  /** A decoding error as `<key>: <what is wrong>`, the key spelled as it is in the file. */
  def describe(err: ConfigDecodingError): com.histmersion.config.ConfigLayers.DescribeResult =
    err match
      case ConfigDecodingError.Multiple(errs) => errs.toList.map(describe).mkString("; ")
      case e                                  =>
        val key = ("histmersion" :: e.path).mkString(".")
        s"$key: ${e.message}"

  /** The three layers resolved: env overrides > `user` > reference.conf, with the environment also available to
    * substitutions (`${?VAR}`) in the user file.
    */
  def merged(reference: Config, user: Config, env: Config): Config =
    envOverrides(reference, env).withFallback(user).withFallback(reference).withFallback(env).resolve()
