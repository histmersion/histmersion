package com.histmersion
package config
import com.histmersion.util.nonempty.NonEmptyVector

import com.histmersion.util.problem.{External, Problem, Problems, Raised}
import scala.annotation.tailrec
import org.ekrich.config.{
  Config,
  ConfigException,
  ConfigFactory,
  ConfigObject,
  ConfigOrigin,
  ConfigParseOptions,
  ConfigUtil,
  ConfigValue,
  ConfigValueFactory,
  ConfigValueType
}
import com.histmersion.config.edit.SettingKey
import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.config.edit.SettingKind.valueTypeEquality
import com.histmersion.lint.{atBoundary, movesTo, BoundaryKind, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

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
@atBoundary(BoundaryKind.Config, "layers reference.conf, the user file and the environment as HOCON")
@movesTo(FeaturePlace.Config)
private[config] object ConfigLayers:
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
  private def leaves(obj: ConfigObject, prefix: List[SettingKey.Segment]): List[(List[SettingKey.Segment], Boolean)] =
    obj.keySet.asScala.toList.sorted.flatMap { k =>
      val p = prefix :+ SettingKey.Segment(k)
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
    object Settings extends Newtype[String, Pure]
    type Unknown = Unknown.T
    object Unknown extends Newtype[String, Pure]

  /** Blocks whose keys are the user's own (a map, e.g. the variables of the `claude` CLI's environment), not a fixed
    * set of settings: any key under one is known, and its value — any string, or null — is left to the reader.
    */
  val OpenBlocks: List[List[SettingKey.Segment]] = List(
    words("histmersion", "agents", "providers", "claude-code", "env"),
    // each plugin's block is checked by the plugin host against THAT plugin's defaults resource
    // (com.histmersion.pluginhost.PluginHost.typeCheck), so `order` and every `<id> { … }` are open here
    words("histmersion", "plugins"),
    // one allowlist per media collection a plugin registers, by the collection's id
    words("histmersion", "media", "collections", "per-collection")
  )

  private def words(ws: String*): List[SettingKey.Segment] = ws.toList.map(SettingKey.Segment(_))

  private def inOpenBlock(keys: List[SettingKey.Segment]): Boolean =
    OpenBlocks.exists(b => keys.length > b.length && keys.startsWith(b))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def check(user: Config, known: Config): Either[Problem, Keys] =
    @tailrec def loop(
        rest: List[(List[SettingKey.Segment], Boolean)],
        settings: List[Keys.Settings],
        unknown: List[Keys.Unknown]
    ): Either[Problem, Keys] =
      rest match
        case Nil                                    => Right(Keys(settings, unknown))
        case (keys, _) :: tail if inOpenBlock(keys) => loop(tail, settings, unknown)
        case (keys, isBlock) :: tail                =>
          val path = ConfigUtil.joinPath(keys.map(_.value).asJava)
          if !Try(known.hasPathOrNull(path)).getOrElse(false) then loop(tail, settings, unknown :+ Keys.Unknown(path))
          else if Try(known.getValue(path).valueType == ConfigValueType.OBJECT).getOrElse(false) then
            if isBlock then loop(tail, settings, unknown)
            else Left(Problem.Config.Setting(Problem.SettingKey(s"$path"), Problem.Config.Fault.Block))
          else loop(tail, settings :+ Keys.Settings(path), unknown)
    loop(leaves(user.root, Nil), Nil, Nil)

  /** Each setting the user file sets against the KIND of value reference.conf has there. The derived readers do not say
    * which key a wrong type is at, so the key is found here first. A numeric or boolean string passes, as the readers
    * accept it (that is how env values arrive). Finer mismatches (a fraction for a whole number) are left to the
    * readers.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def typeCheck(settings: List[Keys.Settings], merged: Config, known: Config): Either[Problems, Unit] =
    NonEmptyVector
      .from(
        settings.toVector.flatMap(path =>
          (Try(known.getValue(path.value)).toOption, Try(merged.getValue(path.value)).toOption) match
            case (Some(k), Some(v)) => mismatch(path, k, v)
            case _                  => None
        )
      )
      .toLeft(())

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def mismatch(path: Keys.Settings, k: ConfigValue, v: ConfigValue): Option[Problem] =
    import ConfigValueType.*
    def text = Option(v.unwrapped).map(_.toString.trim).getOrElse("")
    val (ok, want) = k.valueType match
      case NUMBER  => (v.valueType == NUMBER || v.valueType == STRING && text.toDoubleOption.isDefined, kindOf(k))
      case BOOLEAN => (v.valueType == BOOLEAN || v.valueType == STRING && text.toBooleanOption.isDefined, kindOf(k))
      case STRING  => (v.valueType == STRING || v.valueType == NUMBER || v.valueType == BOOLEAN, kindOf(k))
      case LIST    => (v.valueType == LIST, kindOf(k))
      case _       => (true, kindOf(k))
    Option.unless(ok)(
      Problem.Config.Setting(Problem.SettingKey(s"$path"), Problem.Config.Fault.WrongKind(want, kindOf(v)))
    )

  /** The kind of value HOCON holds. */
  def kindOf(v: ConfigValue): Problem.Config.ValueKind = v.valueType match
    case ConfigValueType.NUMBER  => Problem.Config.ValueKind.Number
    case ConfigValueType.BOOLEAN => Problem.Config.ValueKind.Flag
    case ConfigValueType.STRING  => Problem.Config.ValueKind.Text
    case ConfigValueType.LIST    => Problem.Config.ValueKind.List
    case ConfigValueType.OBJECT  => Problem.Config.ValueKind.Block
    case _                       => Problem.Config.ValueKind.Null

  /** `file` as HOCON; `None` when there is no such file. */
  def parse(file: Path): Either[Problem, Option[Config]] =
    if !Files.exists(file) then Right(None)
    else if !Files.isRegularFile(file) then Left(Problem.Io.Wrong(Problem.Io.Path(s"$file"), Problem.Io.Kind.File))
    else
      try Right(Some(ConfigFactory.parseFile(file.toFile, ConfigParseOptions.defaults.setAllowMissing(false))))
      catch case e: ConfigException => Left(unreadable(e, Problem.Where(s"$file")))

  /** Settings that do not read: the HOCON at `where`, and what its reader said. */
  def unreadable(e: ConfigException, where: Problem.Where): Problem =
    Problem.Io.Corrupt(Problem.Io.Format.Hocon, where, Problem.Io.Damage.Unreadable(External.Reported.of(e)))

  /** Is `o` the origin of `file` itself, not of a file it includes? */
  def sameFile(o: ConfigOrigin, file: Path): Boolean =
    Option(o.filename).exists(f =>
      Try(Path.of(f).toAbsolutePath.normalize).toOption.contains(file.toAbsolutePath.normalize)
    )

  /** What a decoding error says, at the key it names (spelled as it is in the file): the problems a reader of ours
    * raised ([[Raised]]), else the reader library's own words about the key.
    */
  def describe(err: ConfigDecodingError): Problems =
    err match
      case ConfigDecodingError.Multiple(errs) =>
        NonEmptyVector.of(errs.head, errs.tail*).map(describe).toVector.reduce(_ ++ _)
      case ConfigDecodingError.CannotConvert(path, _, Some(raised: Raised)) =>
        raised.problems.at(Problem.Field(s"${keyOf(path)}"))
      // a derived reader reports a missing key as KeyNotFound, at the object that lacks it; a hand-written one as Missing
      case e @ (_: ConfigDecodingError.KeyNotFound | _: ConfigDecodingError.Missing) =>
        val at = e match
          case ConfigDecodingError.KeyNotFound(path, key, _) => path :+ key
          case ConfigDecodingError.Missing(path, key)        => ending(path, key)
          case other                                         => other.path
        Problem.Invalid.Missing.here(Problem.Field(s"${keyOf(at)}"))
      case ConfigDecodingError.UnknownKey(path, key) =>
        Problem.Config.Setting(Problem.SettingKey(s"${keyOf(ending(path, key))}"), Problem.Config.Fault.Undeclared)
      case e =>
        Problem.Config
          .Setting(
            Problem.SettingKey(s"${keyOf(e.path)}"),
            Problem.Config.Fault.Unreadable(External.Reported.of(e.message))
          )

  private def keyOf(path: List[String]): String = ("histmersion" :: path).mkString(".")

  private def ending(path: List[String], key: String): List[String] =
    if path.lastOption.contains(key) then path else path :+ key

  /** A reader of a setting that checks what it read: a `Left` is raised to [[describe]], which places it at the key. */
  def checking[A, B](reader: ConfigReader[A])(check: A => Either[Problems, B]): ConfigReader[B] =
    new ConfigReader[B]:
      def from(v: ConfigValue) =
        reader
          .from(v)
          .flatMap(a =>
            check(a).left.map { problems =>
              val raised = Raised(problems)
              ConfigDecodingError.CannotConvert(Nil, raised.getMessage, Some(raised))
            }
          )

  /** The three layers resolved: env overrides > `user` > reference.conf, with the environment also available to
    * substitutions (`${?VAR}`) in the user file.
    */
  def merged(reference: Config, user: Config, env: Config): Config =
    EnvOverrides.textAsLists(
      reference,
      envOverrides(reference, env).withFallback(user).withFallback(reference).withFallback(env).resolve()
    )
