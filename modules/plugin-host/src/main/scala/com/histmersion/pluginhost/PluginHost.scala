package com.histmersion
package pluginhost
import com.histmersion.util.nonempty.NonEmptyVector

import com.histmersion.util.problem.{External, Problem, Problems}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.config.EnvOverrides
import com.histmersion.api.Sha256
import com.histmersion.api.plugins.{PluginJar, PluginSet, PluginSetting, PluginsReport, PluginState, PluginStatus}
import com.histmersion.plugin.*
import hearth.kindlings.sconfigderivation.ConfigDecodingError
import org.ekrich.config.{
  Config,
  ConfigException,
  ConfigFactory,
  ConfigList,
  ConfigObject,
  ConfigParseOptions,
  ConfigRenderOptions,
  ConfigResolveOptions,
  ConfigUtil,
  ConfigValue,
  ConfigValueFactory,
  ConfigValueType
}

import java.util.ServiceLoader
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.control.NonFatal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.plugins.PluginKit
import com.histmersion.config.edit.SettingKind.valueTypeEquality
import com.histmersion.lint.{tolerated, LintRule}

/** The host side of the SPI (docs/research-plugins.md §2–§3.7): ServiceLoader discovery, the config contract (each
  * plugin's block layered over its own defaults resource, type-checked by key, decoded with the plugin's own reader),
  * secret resolution, and registration into STAGED registries that are committed per plugin only when its `register`
  * succeeded.
  */
object PluginHost:
  type ClassName = ClassName.T
  object ClassName extends Newtype[String, Pure]
  type ConfigPath = ConfigPath.T
  object ConfigPath extends Newtype[String, Pure]
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  type SecretValue = SecretValue.T
  object SecretValue extends Newtype[String, Pure]
  type Warning = Warning.T
  object Warning extends Newtype[String, Pure]

  val Root = "histmersion.plugins"

  /** The SPI generation this host speaks: what a plugin the host supplies itself is built against. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val SpiGeneration: Plugin.Info.SpiVersion = Plugin.Info.SpiVersion(Spi.Version)

  /** Keys of `histmersion.plugins` that are the host's, not a plugin's block. */
  val OrderKey = "order"
  private val Reserved = Set(OrderKey)

  /** What a plugin's id is, as a problem names it. */
  private val PluginIdType: Problem.TypeName = Problem.TypeName.of(Plugin.Info.Id)

  /** Why `raw` is no [[RegistryName]], found in `field`; `None` when it is one. */
  private def named(raw: String, field: Problem.Field): Option[Problem] =
    RegistryName.checked(raw).left.toOption.map(r => Problem.Invalid.malformed(r).within(field))

  // ── discovery ───────────────────────────────────────────────────────────────────────────────

  /** Every `Plugin` provider on the class path. A provider that cannot be instantiated is reported, not fatal (the
    * others still load).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def discover(loader: ClassLoader): (List[Plugin], List[Problem]) =
    val it = ServiceLoader.load(classOf[Plugin], loader).iterator
    // ServiceLoader's iterator is lazy and may throw per provider: walk it by hand, keeping going
    @annotation.tailrec
    def loop(acc: List[Plugin], errs: List[Problem]): (List[Plugin], List[Problem]) =
      val step =
        try Right(if it.hasNext then Some(it.next()) else None)
        catch
          case e: LinkageError => Left(e)
          case NonFatal(e)     => Left(e)
      val loaded: Either[(Option[Provider], Throwable), Option[Plugin]] =
        step.left.map(e => providerOf(e) -> e).flatMap {
          case Some(p) => described(p).left.map(e => Some(Provider(p.getClass.getName)) -> e).map(Some(_))
          case None    => Right(None)
        }
      loaded match
        case Right(Some(p))      => loop(p :: acc, errs)
        case Right(None)         => (acc.reverse, errs.reverse)
        case Left((provider, e)) =>
          loop(
            acc,
            Problem.Plugin.Load(Problem.Plugin.Id(provider.fold(UnnamedProvider)(c => s"$c")), failure(e)) :: errs
          )
    loop(Nil, Nil)

  private val ProviderClass = "Provider ([\\w.$]+)".r.unanchored

  /** How a provider ServiceLoader named no class of is named in the problem. */
  private val UnnamedProvider = "(a provider ServiceLoader did not name)"

  /** The provider class ServiceLoader names in the error it threw for one it could not instantiate. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def providerOf(e: Throwable): Option[Provider] = e match
    case e: java.util.ServiceConfigurationError =>
      Option(e.getMessage).collect { case ProviderClass(c) => Provider(c) }
    case _ => None

  /** The plugin, once its [[Plugin.info]] could be read: a plugin built against an older SPI fails on it (SPI 1's
    * `Spi.Version` was read at run time, and SPI 2 has no such member).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def described(p: Plugin): Either[Throwable, Plugin] =
    try
      val _ = p.info.id
      Right(p)
    catch
      case e: LinkageError => Left(e)
      case NonFatal(e)     => Left(e)

  /** The plugin SPI generation a plugin that failed to link was built against, when that is why `e` was thrown: a
    * member of `com.histmersion.plugin` it calls is missing, or has another shape. SPI 1 is recognised by name (it read
    * `Spi.Version`, which SPI 2 compiles into the plugin); `None` for another SPI type when the generation cannot be
    * told.
    */
  @tolerated(
    LintRule.G5TextBranch,
    "a plugin that fails to link throws a NoSuchMethodError whose only description of the missing SPI member is the JVM's message"
  )
  def foreignSpi(e: Throwable): Option[Option[Plugin.Info.SpiVersion]] = e match
    case e: (NoSuchMethodError | NoSuchFieldError | AbstractMethodError | IncompatibleClassChangeError) =>
      val message = Option(e.getMessage).getOrElse("").replace('/', '.')
      if message.contains("com.histmersion.plugin.Spi$.Version()") then Some(Some(Plugin.Info.SpiVersion(1)))
      else if message.contains("com.histmersion.plugin.") then Some(None)
      else None
    case e => Option(e.getCause).filter(_ ne e).flatMap(foreignSpi)

  /** Why a plugin built against another SPI generation did not load. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rebuild(built: Option[Plugin.Info.SpiVersion]): Problem.Plugin.Fault =
    val speaks = Problem.Raw(s"${Spi.Version}")
    built.fold(Problem.Plugin.Fault.ForeignSpi(speaks))(v =>
      Problem.Plugin.Fault.SpiMismatch(Problem.Raw(s"$v"), speaks)
    )

  /** The class a plugin needs that no jar on the class path has, when that is why `e` was thrown: the library it
    * belongs to was neither shipped with Histmersion nor installed with the plugin.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def missingClass(e: Throwable): Option[ClassName] = e match
    case e: NoClassDefFoundError   => Some(ClassName(Option(e.getMessage).getOrElse("?").replace('/', '.')))
    case e: ClassNotFoundException => Some(ClassName(Option(e.getMessage).getOrElse("?")))
    case e                         => Option(e.getCause).filter(_ ne e).flatMap(missingClass)

  /** Why plugin code threw: a foreign SPI and a missing class are told apart, anything else is what it threw. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def failure(e: Throwable): Problem.Plugin.Fault =
    foreignSpi(e)
      .map(rebuild)
      .getOrElse(
        missingClass(e).fold(Problem.Plugin.Fault.Threw(External.Reported.of(e)))(name =>
          Problem.Plugin.Fault.MissingClass(Problem.Raw(s"$name"))
        )
      )

  /** The kind of value a setting holds, in the problem's words. */
  private def kindOf(t: ConfigValueType): Problem.Config.ValueKind = t match
    case ConfigValueType.OBJECT  => Problem.Config.ValueKind.Block
    case ConfigValueType.LIST    => Problem.Config.ValueKind.List
    case ConfigValueType.NUMBER  => Problem.Config.ValueKind.Number
    case ConfigValueType.BOOLEAN => Problem.Config.ValueKind.Flag
    case ConfigValueType.NULL    => Problem.Config.ValueKind.Null
    case ConfigValueType.STRING  => Problem.Config.ValueKind.Text

  // ── pure parts of the config contract ───────────────────────────────────────────────────────

  /** Pure: why each refused descriptor is refused — a foreign SPI version, an id used twice (every plugin claiming it
    * is refused: which one the user meant cannot be known), an id that is no config key, or a key the host keeps for
    * itself.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def check(infos: List[Plugin.Info], spi: Plugin.Info.SpiVersion): Map[Plugin.Info.Id, Problem] =
    val dup = infos.groupBy(_.id).collect {
      case (id, is) if is.size > 1 =>
        id -> Problem.Plugin.Load(Problem.Plugin.Id(s"$id"), Problem.Plugin.Fault.Duplicate)
    }
    val bad = infos.flatMap { i =>
      if i.spiVersion != spi then
        Some(
          i.id -> Problem.Plugin
            .Load(
              Problem.Plugin.Id(s"${i.id}"),
              Problem.Plugin.Fault.SpiMismatch(Problem.Raw(s"${i.spiVersion}"), Problem.Raw(s"$spi"))
            )
        )
      else
        named(s"${i.id}", Problem.Field("id"))
          .orElse(
            Option.when(Reserved(s"${i.id}"))(Problem.Conflict.AlreadyExists(PluginIdType, Problem.Raw(s"${i.id}")))
          )
          .map(i.id -> _)
    }.toMap
    bad ++ dup

  /** Pure: registration order — the ids named in `histmersion.plugins.order` first, as listed, then the rest by id;
    * with a warning per listed id that is no plugin here.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def order(ids: List[Plugin.Info.Id], configured: List[Plugin.Info.Id]): (List[Plugin.Info.Id], List[Problem]) =
    val listed = configured.distinct
    val unknown =
      listed
        .filterNot(ids.contains)
        .map(id =>
          Problem.Config
            .Setting(Problem.SettingKey(s"$Root.$OrderKey"), Problem.Config.Fault.NoPlugin(Problem.Raw(s"$id")))
        )
    (listed.filter(ids.contains) ++ ids.filterNot(listed.contains).sorted, unknown)

  /** Pure: one digest of a plugin set — `(jar file name, its SHA-256)` pairs, order-independent. What a relink records
    * in the image and `histmersion plugin status` recomputes over the plugins directory (docs/research-plugins.md §7.3;
    * the one implementation is files' `PluginKit.digest`).
    */
  def digest(entries: List[(PluginJar.File, Sha256)]): PluginSet.Digest = PluginKit.digest(entries)

  /** Pure: `histmersion.plugins.order` of the user's file, or why it cannot be read. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def configuredOrder(user: Config, env: Config): Either[Problem, List[Plugin.Info.Id]] =
    val path = s"$Root.$OrderKey"
    Try(user.withFallback(env).resolve(ConfigResolveOptions.noSystem)).toEither.left
      .map(e =>
        Problem.Config.Setting(Problem.SettingKey(path), Problem.Config.Fault.Unreadable(External.Reported.of(e)))
      )
      .flatMap { c =>
        if !c.hasPath(path) then Right(Nil)
        else
          Try(c.getStringList(path).asScala.toList.map(_.trim).filter(_.nonEmpty).map(Plugin.Info.Id(_))).toEither.left
            .map(_ =>
              Problem.Config.Setting(
                Problem.SettingKey(path),
                Problem.Config.Fault.WrongKind(Problem.Config.ValueKind.List, kindOf(c.getValue(path).valueType))
              )
            )
      }

  /** Pure: blocks under `histmersion.plugins` no plugin here claims — a typo, or a plugin left out of this binary. A
    * warning, never an error: removing a plugin must not break startup.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def unknownBlocks(user: Config, ids: Set[Plugin.Info.Id]): List[Plugin.Info.Id] =
    if !Try(user.hasPath(Root)).getOrElse(false) then Nil
    else
      Try(user.getConfig(Root).root.keySet.asScala.toList.sorted)
        .getOrElse(Nil)
        .filterNot(k => ids(Plugin.Info.Id(k)) || Reserved(k))
        .map(Plugin.Info.Id(_))

  /** Pure: the plugin's effective block — env overrides its defaults declare (`${?VAR}`) > the user's
    * `histmersion.plugins.<id>` > `host`, the settings the host supplies a first-party plugin from its own keys (keys
    * relative to the block; empty for any other plugin) > the plugin's defaults; substitutions resolved against `env`
    * only (never system properties: AGENTS.md). The environment's own keys are not part of the result. The report shows
    * the block without `host`: a plugin's settings are its own block's.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def block(
      user: Config,
      defaults: Config,
      env: Config,
      id: Plugin.Info.Id,
      host: Config = ConfigFactory.empty
  ): Either[Problem, Config] =
    val path = s"$Root.$id"
    try
      val own = if user.hasPath(path) then user.getConfig(path) else ConfigFactory.empty
      val keep = defaults.root.keySet.asScala.toSet ++ own.root.keySet.asScala ++ host.root.keySet.asScala
      val resolved =
        EnvOverrides
          .layer(defaults, env)
          .withFallback(own)
          .withFallback(host)
          .withFallback(defaults)
          .withFallback(env)
          .resolve(ConfigResolveOptions.noSystem)
      val trimmed = resolved.root.keySet.asScala.toList.filterNot(keep).foldLeft(resolved.root)(_.withoutKey(_))
      Right(trimmed.toConfig)
    catch
      case e: ConfigException =>
        Left(Problem.Config.Setting(Problem.SettingKey(path), Problem.Config.Fault.Unreadable(External.Reported.of(e))))

  /** Pure: `enabled` of an effective block — a boolean or a boolean string (env); absent = disabled. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def enabled(block: Config, id: Plugin.Info.Id): Either[Problem, Boolean] =
    if !block.hasPath("enabled") then Right(false)
    else
      val v = block.getValue("enabled")
      Option(v.unwrapped)
        .map(_.toString.trim.toLowerCase(java.util.Locale.ROOT))
        .flatMap(_.toBooleanOption)
        .toRight(
          Problem.Config
            .Setting(
              Problem.SettingKey(s"$Root.$id.enabled"),
              Problem.Config.Fault.WrongKind(Problem.Config.ValueKind.Flag, kindOf(v.valueType))
            )
        )

  /** Pure: each key the USER's block sets against the kind of value the plugin's defaults have there — the derived
    * readers report a wrong type without its key ("Expected NUMBER but got STRING"), so the key is found here first, as
    * `ConfigLayers.typeCheck` does for Histmersion's own settings. A numeric or boolean string passes (env overrides
    * arrive as strings); a key whose default is `null` (an optional value) takes any value and is left to the reader.
    * Keys the defaults do not have are reported as unknown.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def typeCheck(id: Plugin.Info.Id, user: Config, defaults: Config): List[Problem] =
    import ConfigValueType.*
    val path = s"$Root.$id"
    if !Try(user.hasPath(path)).getOrElse(false) then Nil
    else
      leaves(user.getConfig(path).root, Nil).flatMap { (keys, v) =>
        val k = ConfigUtil.joinPath(keys.map(_.value).asJava)
        def text = Option(v.unwrapped).map(_.toString.trim).getOrElse("")
        def wrong(want: Problem.Config.ValueKind) =
          Problem.Config.Setting(
            Problem.SettingKey(s"$path.$k"),
            Problem.Config.Fault.WrongKind(want, kindOf(v.valueType))
          )
        if k == "enabled" then
          Option.unless(v.valueType == BOOLEAN || text.toBooleanOption.isDefined)(wrong(Problem.Config.ValueKind.Flag))
        else if !Try(defaults.hasPathOrNull(k)).getOrElse(false) then
          Some(Problem.Config.Setting(Problem.SettingKey(s"$path.$k"), Problem.Config.Fault.NotInDefaults))
        else if defaults.getIsNull(k) then None
        else
          val (ok, want) = defaults.getValue(k).valueType match
            case NUMBER =>
              (
                v.valueType == NUMBER || v.valueType == STRING && text.toDoubleOption.isDefined,
                Problem.Config.ValueKind.Number
              )
            case BOOLEAN =>
              (
                v.valueType == BOOLEAN || v.valueType == STRING && text.toBooleanOption.isDefined,
                Problem.Config.ValueKind.Flag
              )
            case LIST   => (v.valueType == LIST, Problem.Config.ValueKind.List)
            case STRING => (v.valueType != LIST && v.valueType != OBJECT, Problem.Config.ValueKind.Text)
            case _      => (true, Problem.Config.ValueKind.Text)
          Option.unless(ok)(wrong(want))
      }

  /** Every leaf of a HOCON object (lists and nulls are leaves), with its path. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def leaves(obj: ConfigObject, prefix: List[ConfigPath]): List[(List[ConfigPath], ConfigValue)] =
    obj.keySet.asScala.toList.sorted.flatMap { k =>
      obj.get(k) match
        case o: ConfigObject if !o.isEmpty => leaves(o, prefix :+ ConfigPath(k))
        case v                             => List((prefix :+ ConfigPath(k)) -> v)
    }

  /** Pure: each decoding error as the setting it is at (`histmersion.plugins.<id>[.<key>]`) and what the reader said.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe(id: Plugin.Info.Id, err: ConfigDecodingError): Problems = err match
    case ConfigDecodingError.Multiple(errs) =>
      errs.toList.map(describe(id, _)).reduce(_ ++ _)
    case e =>
      // a derived reader reports a missing key as KeyNotFound, at the object that lacks it; a hand-written one as Missing
      val (at, fault) = e match
        case ConfigDecodingError.KeyNotFound(path, key, _) => (path :+ key, Problem.Config.Fault.Empty)
        case ConfigDecodingError.Missing(path, key)        =>
          (if path.lastOption.contains(key) then path else path :+ key, Problem.Config.Fault.Empty)
        case other => (other.path, Problem.Config.Fault.Unreadable(External.Reported.of(other.message)))
      Problems.of(Problem.Config.Setting(Problem.SettingKey((s"$Root.$id" :: at).mkString(".")), fault))

  /** Decode a plugin's block with the plugin's own reader — the path-dependent type keeps the decoded value tied to the
    * plugin that will receive it, with no cast.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decode(p: Plugin, conf: Config): Either[Problems, p.Config] =
    try p.configReader.from(conf.withoutPath("enabled").root).left.map(e => describe(p.info.id, e))
    catch
      case e: LinkageError => Left(Problem.Plugin.Load(Problem.Plugin.Id(s"${p.info.id}"), failure(e)))
      case NonFatal(e)     => Left(Problem.Plugin.Load(Problem.Plugin.Id(s"${p.info.id}"), failure(e)))

  /** Pure: every [[Secret]] inside a decoded config — case classes, options, collections and maps are walked through
    * `productIterator` (generated code, no reflection), in order, without repeats.
    */
  def secretsIn(value: Any): List[Secret] =
    def walk(v: Any): List[Secret] =
      if v.isInstanceOf[Secret] then List(v.asInstanceOf[Secret])
      else if v.isInstanceOf[scala.collection.Map[?, ?]] then
        v.asInstanceOf[scala.collection.Map[?, ?]].values.toList.flatMap(walk)
      else if v.isInstanceOf[IterableOnce[?]] then v.asInstanceOf[IterableOnce[?]].iterator.toList.flatMap(walk)
      else if v.isInstanceOf[Product] then v.asInstanceOf[Product].productIterator.toList.flatMap(walk)
      else Nil
    walk(value).distinct

  /** Pure: the effective block as reported (`/api/config`, `/api/about`, `--self-check`): every setting but `enabled`,
    * strings unquoted, a literal `value:` secret masked. A secret is held in the config only as its reference, so
    * nothing else needs masking.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def settings(block: Config): List[PluginSetting] =
    leaves(block.root.withoutKey("enabled"), Nil).map { (keys, v) =>
      val shown = v.valueType match
        case ConfigValueType.NULL   => "null"
        case ConfigValueType.STRING => Option(mask(v.unwrapped.toString)).filter(_.nonEmpty).getOrElse("\"\"")
        case _                      => masked(v).render(ConfigRenderOptions.concise)
      PluginSetting(PluginSetting.Key(ConfigUtil.joinPath(keys.map(_.value).asJava)), PluginSetting.Value(shown))
    }

  private def mask(s: String): String =
    if s.trim.startsWith(s"${Secret.Ref.ValueScheme}:") then s"${Secret.Ref.ValueScheme}:${Secret.Ref.Mask}" else s

  /** The value with every literal `value:` string inside it (a list of secrets, an object) masked. */
  private def masked(v: ConfigValue): ConfigValue = v match
    case l: ConfigList   => ConfigValueFactory.fromIterable(l.asScala.map(masked).asJava)
    case o: ConfigObject => ConfigValueFactory.fromMap(o.asScala.view.mapValues(masked).toMap.asJava)
    case s if s.valueType == ConfigValueType.STRING => ConfigValueFactory.fromAnyRef(mask(s.unwrapped.toString))
    case other                                      => other

  /** The plugin's defaults resource, read through ITS class loader; absent = no defaults. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def defaults(p: Plugin): Either[Problem, Config] =
    try
      Right(
        ConfigFactory.parseResources(
          p.getClass.getClassLoader,
          p.defaultsResource.value,
          ConfigParseOptions.defaults.setAllowMissing(true)
        )
      )
    catch
      case e: ConfigException =>
        Left(
          Problem.Io.Corrupt(
            Problem.Io.Format.Hocon,
            Problem.Where(s"${p.info.id}'s defaults ${p.defaultsResource}"),
            Problem.Io.Damage.Unreadable(External.Reported.of(e))
          )
        )

  // ── secrets ─────────────────────────────────────────────────────────────────────────────────

  /** Resolve one reference held by the settings at `key`: `value:` is the literal, `env:` the variable of `env`,
    * `<vault>:` asks the vault registered under that name. The problems name the setting, the variable or the vault and
    * the reference, never a value.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def resolve(
      ref: Secret.Ref,
      env: Config,
      vaults: Map[Secret.Ref.Vault.Value, Secret.Vault],
      key: Problem.SettingKey
  ): Either[Problems, SecretValue] =
    ref match
      case Secret.Ref.Value(v) => Right(SecretValue(v.value))
      case Secret.Ref.Env(n)   =>
        Option(env.root.get(n.value))
          .flatMap(v => Option(v.unwrapped))
          .map(v => SecretValue(v.toString))
          .toRight(Problem.Config.Setting(key, Problem.Config.Fault.Unset(Problem.SettingKey(s"$n"))))
      case Secret.Ref.Vault(name, r) =>
        vaults.get(name) match
          case None    => Left(Problem.Plugin.Vault(Problem.Plugin.Id(s"$name"), Problem.Plugin.Retrieval.NoVault))
          case Some(v) =>
            try v.resolve(Secret.Vault.ResolveRef(r.value)).map(x => SecretValue(x.value))
            catch case NonFatal(e) => Left(vaultThrew(e, s"$name"))

  /** The vault of this name threw instead of answering: logged under its problem's id. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def vaultThrew(e: Throwable, vault: String): Problem =
    Problem.from(e, Problem.Where(s"secret vault $vault"), Problem.Internal.Id.mint())

  /** Resolve several references of ONE vault in one call ([[Secret.Vault.resolveAll]]): each answer as [[resolve]]
    * words it; a reference the vault did not answer is an error, an answer to one not asked is dropped. A `resolveAll`
    * that throws (the SPI's default throws whatever one `resolve` throws) is answered one reference at a time instead,
    * so a throw fails only its own reference.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def resolveAll(
      name: Secret.Ref.Vault.Value,
      refs: List[Secret.Ref.Vault.Ref],
      vault: Secret.Vault
  ): Map[Secret.Ref.Vault.Ref, Either[Problems, SecretValue]] =
    val asked = refs.distinct
    def one(r: Secret.Ref.Vault.Ref) =
      try vault.resolve(Secret.Vault.ResolveRef(r.value)).map(x => SecretValue(x.value))
      catch case NonFatal(e) => Left(vaultThrew(e, s"$name"))
    try
      val answers = Option(vault.resolveAll(asked.map(r => Secret.Vault.ResolveRef(r.value)))).getOrElse(Map.empty)
      asked.map { r =>
        val answer: Either[Problems, SecretValue] =
          answers.get(Secret.Vault.ResolveRef(r.value)).flatMap(Option(_)) match
            case Some(Right(v)) => Right(SecretValue(v.value))
            case Some(Left(e))  => Left(e)
            case None           =>
              Left(
                Problem.Plugin.Vault(Problem.Plugin.Id(s"$name"), Problem.Plugin.Retrieval.Absent(Problem.Raw(s"$r")))
              )
        r -> answer
      }.toMap
    catch case NonFatal(_) => asked.map(r => r -> one(r)).toMap

  /** The vaults of a generation's registries, by the name a reference gives them. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def vaultsOf(regs: Registries): Map[Secret.Ref.Vault.Value, Secret.Vault] =
    regs.secretVaults.iterator.map((k, v) => Secret.Ref.Vault.Value(k.value) -> v).toMap

  /** The secrets of one generation: each vault is asked ONCE for all its references that `wanted` lists — on the first
    * reference of it any plugin needs, so a vault registered earlier in the same build is asked then too — and every
    * answer, value or error, is kept for this generation only (a reload resolves again; nothing outlives the build's
    * registrations).
    */
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final private class GenerationSecrets(env: Config, wanted: Map[Secret.Ref.Vault.Value, List[Secret.Ref.Vault.Ref]]):
    private val known = scala.collection.concurrent.TrieMap.empty[Secret.Ref, Either[Problems, SecretValue]]

    /** The reference's value, or why there is none, held by the settings at `key`. A vault's answers are kept; a
      * literal and a variable are read again, each for the settings that hold it.
      */
    def apply(
        ref: Secret.Ref,
        vaults: Map[Secret.Ref.Vault.Value, Secret.Vault],
        key: Problem.SettingKey
    ): Either[Problems, SecretValue] =
      ref match
        case Secret.Ref.Vault(name, asked) if vaults.contains(name) =>
          known.getOrElse(
            ref, {
              val batch = (wanted.getOrElse(name, Nil) :+ asked).distinct
                .filterNot(r => known.contains(Secret.Ref.Vault(name, r)))
              resolveAll(name, batch, vaults(name)).foreach((r, v) => known.update(Secret.Ref.Vault(name, r), v))
              known.getOrElse(ref, resolve(ref, env, vaults, key))
            }
          )
        case _ => resolve(ref, env, vaults, key)

  // ── a generation ────────────────────────────────────────────────────────────────────────────

  /** One enabled plugin after its config was decoded, waiting to register: `call` is `p.register` applied to the value
    * `p.configReader` decoded — built where that value still has type `p.Config`, so no cast is ever needed.
    */
  final private case class Prepared(
      p: Plugin,
      block: Config,
      secrets: List[Secret],
      call: Plugin.Context => Either[Problems, Unit],
      carried: Option[(PluginStatus, Registries)] = None
  )

  /** A registry whose additions are kept apart until the plugin's `register` returned. Mutable, but owned by ONE
    * register call: `close` ends it, after which any add (a plugin that kept the context) is refused.
    */
  final private class Staged[A](
      plugin: Plugin.Info.Id,
      kind: Problem.TypeName,
      committed: Map[Registry.AddId, A],
      valid: (Registry.AddId, A) => Option[Problem]
  ) extends Registry[A]:
    // the plugin's additions, in order; a var because the plugin calls `add` from inside `register`
    private var added: ListMap[Registry.AddId, A] = ListMap.empty
    private var open = true
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def add(id: Registry.AddId, a: A): Either[Problem, Unit] =
      if !open then Left(Problem.Refused.Policy(Problem.Refused.Rule.RegistrationOver))
      else if committed.contains(id) || added.contains(id) then
        Left(Problem.Conflict.AlreadyExists(kind, Problem.Raw(s"$id")))
      else if Option(a).isEmpty then
        Left(Problem.Plugin.Load(Problem.Plugin.Id(s"$plugin"), Problem.Plugin.Fault.NullComponent))
      else
        valid(id, a) match
          case Some(err) => Left(err)
          case None      => added = added.updated(id, a); Right(())
    def close(): ListMap[Registry.AddId, A] =
      open = false
      added

  /** The context one plugin's `register` is called with: the host's services, its secrets, and the staged registries.
    * The HTTP client is built on the first request.
    */
  final private class RegistrationContext(
      id: Plugin.Info.Id,
      services: HostServices,
      resolved: Secret => Either[Problems, SecretValue],
      vaults: Staged[Secret.Vault],
      fetchers: Staged[SourceFetcher],
      media: Staged[MediaCollection],
      events: Staged[EventCatalogue],
      ai: Staged[AiProvider],
      gazetteers: Staged[GazetteerSource],
      collections: Staged[SourceCollection]
  ) extends Plugin.Context:
    override def files: LocalFiles = services.files
    override def processes: Processes = services.processes
    lazy val http: Plugin.Http = services.http()
    val log: Plugin.Log = new Plugin.Log:
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def info(message: Plugin.Log.Line) =
        services.log(HostServices.PluginId(id.value), false, HostServices.Line(message.value))
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def warn(message: Plugin.Log.Line) =
        services.log(HostServices.PluginId(id.value), true, HostServices.Line(message.value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def env(name: EnvVar.Name) =
      Option(services.env.root.get(name.value))
        .flatMap(v => Option(v.unwrapped))
        .map(v => EnvVar.Value(v.toString))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def secret(s: Secret) =
      resolved(s)
        .map(v => Secret.Resolved(v.value))
    def secretVaults = vaults
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def quota(upstream: Plugin.Context.QuotaUpstream) = services.quota(HostServices.Upstream(upstream.value))
    override def licences = HostLicences
    override def sourceFetchers = fetchers
    override def mediaCollections = media
    override def eventCatalogues = events
    override def aiProviders = ai
    override def gazetteerSources = gazetteers
    override def sourceCollections = collections

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def validFetcher(id: Registry.AddId, f: SourceFetcher): Option[Problem] =
    named(s"$id", Problem.Field(Registries.SourceFetcher))

  /** A media collection: a kebab-case id; a plugin's may not take a name of the host's own providers (an import's name
    * or an alias of one, or a provider pictures are stored under — its pictures would be mistaken for theirs); its role
    * is one a collection's pictures may play. A first-party plugin's is the one the name is reserved for.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def validCollection(firstParty: Boolean)(id: Registry.AddId, c: MediaCollection): Option[Problem] =
    named(s"$id", Problem.Field(Registries.MediaCollection)).orElse {
      if firstParty then None
      else if ReservedNames.mediaCollections(ReservedNames.Name(id.value)) then
        Some(Problem.Conflict.AlreadyExists(Problem.TypeName.named[MediaCollection], Problem.Raw(s"$id")))
      else
        (c.role :: c.roles.toList.sorted).find(r => !MediaCollection.Roles(r)).map { r =>
          Problem.Invalid.Unknown(
            Problem.TypeName.of(MediaItem.Role),
            Problem.Raw(s"$r"),
            MediaCollection.Roles.toVector.sorted.map(known => Problem.Raw(s"$known")),
            Vector(Problem.Field(Registries.MediaCollection), Problem.Field(s"$id"), Problem.Field("role"))
          )
        }
    }

  /** An event catalogue: a kebab-case id; a plugin's may not take a name of the host's own catalogues, unless it is the
    * first-party plugin the name is reserved for.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def validCatalogue(firstParty: Boolean)(id: Registry.AddId, c: EventCatalogue): Option[Problem] =
    named(s"$id", Problem.Field(Registries.EventCatalogue)).orElse(
      Option.when(
        !firstParty && !c.isInstanceOf[HostOwned] && ReservedNames.eventCatalogues(ReservedNames.Name(id.value))
      )(Problem.Conflict.AlreadyExists(Problem.TypeName.named[EventCatalogue], Problem.Raw(s"$id")))
    )

  /** An AI provider: its name is the provider half of `provider:model`, so kebab-case; a plugin's may not take the name
    * of one of the host's own providers (its models would be mistaken for theirs).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def validAiProvider(firstParty: Boolean)(id: Registry.AddId, a: AiProvider): Option[Problem] =
    named(s"$id", Problem.Field(Registries.AiProvider)).orElse(
      Option.when(!firstParty && !a.isInstanceOf[HostOwned] && ReservedNames.aiProviders(ReservedNames.Name(id.value)))(
        Problem.Conflict.AlreadyExists(Problem.TypeName.named[AiProvider], Problem.Raw(s"$id"))
      )
    )

  /** A gazetteer source: a kebab-case id; a plugin's may not take the name of one of the host's own sources (the
    * datasets `places fetch` and `init.fetch-sources` name), unless it is the first-party plugin the name is reserved
    * for.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def validGazetteer(firstParty: Boolean)(id: Registry.AddId, g: GazetteerSource): Option[Problem] =
    named(s"$id", Problem.Field(Registries.GazetteerSource)).orElse(
      Option.when(!firstParty && ReservedNames.gazetteerSources(ReservedNames.Name(id.value)))(
        Problem.Conflict.AlreadyExists(Problem.TypeName.named[GazetteerSource], Problem.Raw(s"$id"))
      )
    )

  /** A source collection: a kebab-case id; a plugin's may not take the name of one of Histmersion's own collections
    * (its items would be taken for theirs), unless it is the first-party plugin the name is reserved for.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def validSourceCollection(firstParty: Boolean)(id: Registry.AddId, c: SourceCollection): Option[Problem] =
    named(s"$id", Problem.Field(Registries.SourceCollection)).orElse(
      Option.when(!firstParty && ReservedNames.sourceCollections(ReservedNames.Name(id.value)))(
        Problem.Conflict.AlreadyExists(Problem.TypeName.named[SourceCollection], Problem.Raw(s"$id"))
      )
    )

  /** A vault: a kebab-case name that is no scheme of a reference (`value`, `env`), registered under its own name. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def validVault(id: Registry.AddId, v: Secret.Vault): Option[Problem] =
    if Set(Secret.Ref.ValueScheme, Secret.Ref.EnvScheme)(id.value) then
      Some(Problem.Conflict.AlreadyExists(Problem.TypeName.named[Secret.Vault], Problem.Raw(s"$id")))
    else
      named(s"$id", Problem.Field(Registries.SecretVault)).orElse(
        Option.when(v.name.value != id.value)(
          Problem.Invalid.Inconsistent(
            NonEmptyVector.of(Problem.Field(s"name ${v.name}"), Problem.Field(s"id $id")),
            Problem.Invalid.Exclusion.Same,
            Vector(Problem.Field(Registries.SecretVault))
          )
        )
      )

  /** A plugin's status in the report. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def status(
      p: Plugin,
      enabled: Boolean,
      state: PluginState,
      block: Option[Config] = None,
      error: Option[Problems] = None,
      provides: List[PluginStatus.Provides] = Nil,
      warns: List[Warning] = Nil
  ): PluginStatus =
    PluginStatus(
      PluginStatus.Id(p.info.id.value),
      PluginStatus.Version(p.info.version.value),
      PluginStatus.SpiVersion(p.info.spiVersion.value),
      PluginStatus.Enabled(enabled),
      state.wire,
      provides,
      block.map(settings).getOrElse(Nil),
      warns.map(w => PluginStatus.Warnings(w.value)),
      error.fold(Nil)(_.toVector.toList)
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def hasState(st: PluginStatus, state: PluginState): Boolean = st.state == state.wire

  /** Build one generation: check, order, layer, type-check, decode, resolve secrets, register.
    *
    * A plugin whose secrets name a vault no plugin has registered YET is set aside and retried after the others (a
    * vault plugin may sort after the plugins that use it); one still waiting when no pass registers anything more
    * fails, naming the vault. Every failure is that plugin's alone.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def build(
      plugins: List[Plugin],
      user: Config,
      env: Config,
      services: HostServices,
      discoveryErrors: List[Problem] = Nil,
      /** Plugins whose block did not change since the previous generation (a config reload): their status and
        * contributions are taken over as they are — not decoded, resolved or registered again. A registered one goes
        * into the registries in its place in the order.
        */
      carried: Map[Plugin.Info.Id, (PluginStatus, Registries)] = Map.empty,
      /** The settings the host supplies a first-party plugin, by id: its fallback block from the host's own keys (keys
        * relative to the plugin's block), between the user's block and the plugin's defaults ([[block]]).
        */
      fallback: Plugin.Info.Id => Config = _ => ConfigFactory.empty,
      /** The first-party plugins: the ones a reserved name of the host's (a catalogue's, a media provider's, an AI
        * provider's) is kept for.
        */
      firstParty: Plugin.Info.Id => Boolean = _ => false
  ): Generation =
    val infos = plugins.map(_.info)
    val refused = check(infos, SpiGeneration)
    val accepted = plugins.filterNot(p => refused.contains(p.info.id))
    val (configured, orderErr) = configuredOrder(user, env) match
      case Right(o) => (o, Nil)
      case Left(e)  => (Nil, List(e))
    val (ordered, orderWarn) = order(accepted.map(_.info.id), configured)
    val unknown = unknownBlocks(user, infos.map(_.id).toSet)
      .map(id => Problem.Config.Setting(Problem.SettingKey(s"$Root.$id"), Problem.Config.Fault.Undeclared))
    val warnings = discoveryErrors ++ orderErr ++ orderWarn ++ unknown
    warnings.foreach(w => services.log(HostServices.PluginId(""), true, HostServices.Line(w.sentence)))

    val byId = accepted.map(p => p.info.id -> p).toMap

    // the config of every accepted plugin, in order
    val prepared: List[(Plugin.Info.Id, Either[PluginStatus, Prepared])] = ordered.map { id =>
      val p = byId(id)
      val r = carried.get(id) match
        case Some((st, _)) if !hasState(st, PluginState.Registered) => Left(st)
        case Some(c) => Right(Prepared(p, ConfigFactory.empty, Nil, _ => Right(()), Some(c)))
        case None    =>
          for
            defs <- defaults(p).left.map(e => status(p, false, PluginState.Failed, error = Some(e)))
            // the defaults resolved on their own: `${?ENV}` lines leave the default standing
            known = Try(defs.resolve(ConfigResolveOptions.noSystem)).getOrElse(defs)
            _ <- Problems.from(typeCheck(id, user, known).toVector).toLeft(()).left.map { errs =>
              status(
                p,
                Try(user.getBoolean(s"$Root.$id.enabled")).getOrElse(false),
                PluginState.Failed,
                error = Some(errs)
              )
            }
            conf <- block(user, defs, env, id).left.map(e => status(p, false, PluginState.Failed, error = Some(e)))
            on <- enabled(conf, id).left.map(e => status(p, false, PluginState.Failed, Some(conf), Some(e)))
            _ <- Either.cond(on, (), status(p, false, PluginState.Disabled, Some(conf)))
            supplied <- block(user, defs, env, id, fallback(id)).left
              .map(e => status(p, true, PluginState.Failed, Some(conf), Some(e)))
            typed <- decode(p, supplied).left.map(e => status(p, true, PluginState.Failed, Some(conf), Some(e)))
          yield Prepared(
            p,
            conf,
            secretsIn(typed),
            ctx => p.register(typed, ctx)
          )
      id -> r
    }

    // registration passes; the secrets are this generation's own (re-resolved on every build), each
    // vault asked once for every reference the plugins about to register hold
    val ready = prepared.collect { case (_, Right(pr)) => pr }
    val wanted = ready
      .flatMap(_.secrets.map(_.ref))
      .collect { case Secret.Ref.Vault(v, r) => v -> r }
      .distinct
      .groupMap(_._1)(_._2)
    val secrets = GenerationSecrets(env, wanted)
    @annotation.tailrec
    def passes(
        waiting: List[Prepared],
        regs: Registries,
        done: Map[Plugin.Info.Id, (PluginStatus, Registries)]
    ): (Registries, Map[Plugin.Info.Id, (PluginStatus, Registries)]) =
      @annotation.tailrec
      def pass(
          rest: List[Prepared],
          rs: Registries,
          ds: Map[Plugin.Info.Id, (PluginStatus, Registries)],
          left: List[Prepared],
          prog: Boolean
      ): (Registries, Map[Plugin.Info.Id, (PluginStatus, Registries)], List[Prepared], Boolean) =
        rest match
          case Nil        => (rs, ds, left, prog)
          case pr :: tail =>
            val missing =
              pr.secrets.map(_.ref).collect { case Secret.Ref.Vault(v, _) if !vaultsOf(rs).contains(v) => v }
            if missing.nonEmpty then pass(tail, rs, ds, left :+ pr, prog)
            else
              val (st, added) = pr.carried.getOrElse(register(pr, rs, services, secrets, firstParty(pr.p.info.id)))
              pass(tail, rs ++ added, ds.updated(pr.p.info.id, (st, added)), left, true)
      val (rs, ds, left, progressed) = pass(waiting, regs, done, Nil, false)
      if left.isEmpty || !progressed then
        val stuck = left.map { pr =>
          val names =
            pr.secrets.map(_.ref).collect { case Secret.Ref.Vault(v, _) if !vaultsOf(rs).contains(v) => v }.distinct
          pr.p.info.id -> (
            status(
              pr.p,
              true,
              PluginState.Failed,
              Some(pr.block),
              Problems.from(
                names.toVector.map(n =>
                  Problem.Plugin.Vault(Problem.Plugin.Id(s"$n"), Problem.Plugin.Retrieval.NoVault)
                )
              )
            ),
            Registries.empty
          )
        }
        (rs, ds ++ stuck)
      else passes(left, rs, ds)

    val (registries, registered) = passes(ready, Registries.empty, Map.empty)
    val statuses = prepared.map {
      case (_, Left(st))  => st
      case (id, Right(_)) => registered(id)._1
    } ++ plugins
      .filter(p => refused.contains(p.info.id))
      .map(p => status(p, false, PluginState.Refused, error = refused.get(p.info.id)))
    statuses
      .filter(hasState(_, PluginState.Failed))
      .foreach(s =>
        services.log(
          HostServices.PluginId(s.id.value),
          true,
          HostServices.Line(if s.errors.isEmpty then "failed" else s.errors.map(_.sentence).mkString("; "))
        )
      )
    val live = registered.collect { case (id, (st, regs)) if hasState(st, PluginState.Registered) => id -> regs }
    val fresh = ready.filter(pr => pr.carried.isEmpty && live.contains(pr.p.info.id))
    Generation(
      registries,
      PluginsReport(
        PluginsReport.SpiVersion(Spi.Version),
        statuses,
        warnings.map(w => PluginsReport.Warnings(w.sentence))
      ),
      live,
      plugins.map(_.info.id).filterNot(carried.contains),
      ready.filter(_.secrets.nonEmpty).map(_.p.info.id).toSet,
      fresh.map(pr => pr.p.info.id -> pr.block).toMap,
      fresh
        .filter(_.secrets.nonEmpty)
        .map(pr =>
          pr.p.info.id -> pr.secrets
            .flatMap(sec =>
              secrets(sec.ref, vaultsOf(registries), Problem.SettingKey(s"$Root.${pr.p.info.id}")).toOption
                .map(sec.ref -> _)
            )
            .toMap
        )
        .toMap
    )

  /** Resolve one prepared plugin's secrets and call its `register` with a staged context. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def register(
      pr: Prepared,
      regs: Registries,
      services: HostServices,
      secrets: GenerationSecrets,
      firstParty: Boolean
  ): (PluginStatus, Registries) =
    val p = pr.p
    val id = p.info.id
    def resolved(s: Secret): Either[Problems, SecretValue] =
      secrets(s.ref, vaultsOf(regs), Problem.SettingKey(s"$Root.$id"))
    val literal = pr.secrets.exists(_.ref.isInstanceOf[Secret.Ref.Value])
    val warns = if !literal then Nil
    else
      List(
        Warning(
          s"$Root.$id: a secret is written into the config (value:${Secret.Ref.Mask}) — " +
            "use env:<NAME> or a vault reference"
        )
      )
    warns.foreach(w => services.log(HostServices.PluginId(id.value), true, HostServices.Line(w.value)))
    def fail(e: Problems) = (status(p, true, PluginState.Failed, Some(pr.block), Some(e), Nil, warns), Registries.empty)
    pr.secrets.iterator.map(resolved).collectFirst { case Left(e) => e } match
      case Some(e) => fail(e)
      case None    =>
        def held[K, A](m: ListMap[K, A])(key: K => String): Map[Registry.AddId, A] =
          m.iterator.map((k, a) => Registry.AddId(key(k)) -> a).toMap
        val staged =
          Staged[Secret.Vault](id, Problem.TypeName.named[Secret.Vault], held(regs.secretVaults)(_.value), validVault)
        val fetchers =
          Staged[SourceFetcher](
            id,
            Problem.TypeName.named[SourceFetcher],
            held(regs.sourceFetchers)(_.value),
            validFetcher
          )
        val media =
          Staged[MediaCollection](
            id,
            Problem.TypeName.named[MediaCollection],
            held(regs.mediaCollections)(_.value),
            validCollection(firstParty)
          )
        val events =
          Staged[EventCatalogue](
            id,
            Problem.TypeName.named[EventCatalogue],
            held(regs.eventCatalogues)(_.value),
            validCatalogue(firstParty)
          )
        val ai = Staged[AiProvider](
          id,
          Problem.TypeName.named[AiProvider],
          held(regs.aiProviders)(_.value),
          validAiProvider(firstParty)
        )
        val gazetteers =
          Staged[GazetteerSource](
            id,
            Problem.TypeName.named[GazetteerSource],
            held(regs.gazetteerSources)(_.value),
            validGazetteer(firstParty)
          )
        val collections =
          Staged[SourceCollection](
            id,
            Problem.TypeName.named[SourceCollection],
            held(regs.sourceCollections)(_.value),
            validSourceCollection(firstParty)
          )
        val ctx =
          RegistrationContext(id, services, resolved, staged, fetchers, media, events, ai, gazetteers, collections)
        val outcome =
          try pr.call(ctx)
          catch
            case e: LinkageError => Left(Problem.Plugin.Load(Problem.Plugin.Id(s"$id"), failure(e)))
            case NonFatal(e)     => Left(Problem.Plugin.Load(Problem.Plugin.Id(s"$id"), failure(e)))
        val added = Registries(
          staged.close().map((k, v) => Registries.SecretVaultsKey(k.value) -> v),
          fetchers.close().map((k, v) => Registries.SourceFetchersKey(k.value) -> v),
          media.close().map((k, v) => Registries.MediaCollectionsKey(k.value) -> v),
          events.close().map((k, v) => Registries.EventCataloguesKey(k.value) -> v),
          ai.close().map((k, v) => Registries.AiProvidersKey(k.value) -> v),
          gazetteerSources = gazetteers.close().map((k, v) => Registries.GazetteerSourcesKey(k.value) -> v),
          sourceCollections = collections.close().map((k, v) => Registries.SourceCollectionsKey(k.value) -> v)
        )
        outcome match
          case Left(e)  => fail(e)
          case Right(_) =>
            (
              status(
                p,
                true,
                PluginState.Registered,
                Some(pr.block),
                None,
                added.provides.map(k => PluginStatus.Provides(k.value)),
                warns
              ),
              added
            )
