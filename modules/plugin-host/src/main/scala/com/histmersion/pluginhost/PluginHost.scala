package com.histmersion
package pluginhost

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

/** The host side of the SPI (docs/research-plugins.md §2–§3.7): ServiceLoader discovery, the config contract (each
  * plugin's block layered over its own defaults resource, type-checked by key, decoded with the plugin's own reader),
  * secret resolution, and registration into STAGED registries that are committed per plugin only when its `register`
  * succeeded.
  */
object PluginHost:
  private given CanEqual[ConfigValueType, ConfigValueType] = CanEqual.derived

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
  val SpiGeneration: Plugin.Info.SpiVersion = Plugin.Info.SpiVersion(Spi.Version)

  /** Keys of `histmersion.plugins` that are the host's, not a plugin's block. */
  val OrderKey = "order"
  private val Reserved = Set(OrderKey)
  private val IdPattern = "[a-z0-9][a-z0-9-]*"

  // ── discovery ───────────────────────────────────────────────────────────────────────────────

  /** Every `Plugin` provider on the class path. A provider that cannot be instantiated is reported, not fatal (the
    * others still load).
    */
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
            Problem(s"a plugin provider${provider.fold("")(c => s" ($c)")} could not be loaded: ${failure(e)}") :: errs
          )
    loop(Nil, Nil)

  private val ProviderClass = "Provider ([\\w.$]+)".r.unanchored

  /** The provider class ServiceLoader names in the error it threw for one it could not instantiate. */
  def providerOf(e: Throwable): Option[Provider] = e match
    case e: java.util.ServiceConfigurationError =>
      Option(e.getMessage).collect { case ProviderClass(c) => Provider(c) }
    case _ => None

  /** The plugin, once its [[Plugin.info]] could be read: a plugin built against an older SPI fails on it (SPI 1's
    * `Spi.Version` was read at run time, and SPI 2 has no such member).
    */
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
  def foreignSpi(e: Throwable): Option[Option[Plugin.Info.SpiVersion]] = e match
    case e: (NoSuchMethodError | NoSuchFieldError | AbstractMethodError | IncompatibleClassChangeError) =>
      val message = Option(e.getMessage).getOrElse("").replace('/', '.')
      if message.contains("com.histmersion.plugin.Spi$.Version()") then Some(Some(Plugin.Info.SpiVersion(1)))
      else if message.contains("com.histmersion.plugin.") then Some(None)
      else None
    case e => Option(e.getCause).filter(_ ne e).flatMap(foreignSpi)

  /** What to do about a plugin built against another SPI generation. */
  def rebuild(built: Option[Plugin.Info.SpiVersion]): Problem =
    Problem(
      s"it was built against ${built.fold("another plugin SPI")(v => s"plugin SPI $v")}; this Histmersion speaks " +
        s"SPI ${Spi.Version} — rebuild the plugin against SPI ${Spi.Version} (`histmersion plugin repo` has its API)"
    )

  /** The class a plugin needs that no jar on the class path has, when that is why `e` was thrown: the library it
    * belongs to was neither shipped with Histmersion nor installed with the plugin.
    */
  def missingClass(e: Throwable): Option[ClassName] = e match
    case e: NoClassDefFoundError   => Some(ClassName(Option(e.getMessage).getOrElse("?").replace('/', '.')))
    case e: ClassNotFoundException => Some(ClassName(Option(e.getMessage).getOrElse("?")))
    case e                         => Option(e.getCause).filter(_ ne e).flatMap(missingClass)

  /** Why plugin code threw, in words that say what to do when a class is missing. */
  def failure(e: Throwable): Problem =
    foreignSpi(e)
      .map(rebuild)
      .getOrElse(
        missingClass(e).fold(Problem(s"${e.getClass.getSimpleName}: ${e.getMessage}"))(name =>
          Problem(
            s"class $name is missing: the plugin uses a library that is neither part of Histmersion nor installed with it — " +
              "install the plugin with `histmersion plugin add` from a jar published with its POM, which lists its dependencies"
          )
        )
      )

  // ── pure parts of the config contract ───────────────────────────────────────────────────────

  /** Pure: why each refused descriptor is refused — a foreign SPI version, an id used twice (every plugin claiming it
    * is refused: which one the user meant cannot be known), an id that is no config key, or a key the host keeps for
    * itself.
    */
  def check(infos: List[Plugin.Info], spi: Plugin.Info.SpiVersion): Map[Plugin.Info.Id, Problem] =
    val dup = infos.groupBy(_.id).collect {
      case (id, is) if is.size > 1 =>
        id -> Problem(s"plugin id '$id' is claimed by ${is.size} plugins")
    }
    val bad = infos.flatMap { i =>
      if i.spiVersion != spi then
        Some(
          i.id -> Problem(
            s"${i.id}: built for SPI ${i.spiVersion}, this host speaks $spi — rebuild it against SPI $spi"
          )
        )
      else if !i.id.value.matches(IdPattern) then
        Some(i.id -> Problem(s"'${i.id}': a plugin id is kebab-case $IdPattern"))
      else if Reserved(i.id.value) then Some(i.id -> Problem(s"'${i.id}' is a key of $Root, not a plugin id"))
      else None
    }.toMap
    bad ++ dup

  /** Pure: registration order — the ids named in `histmersion.plugins.order` first, as listed, then the rest by id;
    * with a warning per listed id that is no plugin here.
    */
  def order(ids: List[Plugin.Info.Id], configured: List[Plugin.Info.Id]): (List[Plugin.Info.Id], List[Problem]) =
    val listed = configured.distinct
    val unknown =
      listed
        .filterNot(ids.contains)
        .map(id => Problem(s"$Root.$OrderKey names '$id', which is no plugin in this binary"))
    (listed.filter(ids.contains) ++ ids.filterNot(listed.contains).sorted, unknown)

  /** Pure: one digest of a plugin set — `(jar file name, its SHA-256)` pairs, order-independent. What a relink records
    * in the image and `histmersion plugin status` recomputes over the plugins directory (docs/research-plugins.md §7.3;
    * the one implementation is files' `PluginKit.digest`).
    */
  def digest(entries: List[(PluginJar.File, Sha256)]): PluginSet.Digest = PluginKit.digest(entries)

  /** Pure: `histmersion.plugins.order` of the user's file, or why it cannot be read. */
  def configuredOrder(user: Config, env: Config): Either[Problem, List[Plugin.Info.Id]] =
    val path = s"$Root.$OrderKey"
    Try(user.withFallback(env).resolve()).toEither.left.map(e => Problem(s"$path: ${e.getMessage}")).flatMap { c =>
      if !c.hasPath(path) then Right(Nil)
      else
        Try(c.getStringList(path).asScala.toList.map(_.trim).filter(_.nonEmpty).map(Plugin.Info.Id(_))).toEither.left
          .map(_ => Problem(s"$path must be a list of plugin ids [ … ]"))
    }

  /** Pure: blocks under `histmersion.plugins` no plugin here claims — a typo, or a plugin left out of this binary. A
    * warning, never an error: removing a plugin must not break startup.
    */
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
          .resolve()
      val trimmed = resolved.root.keySet.asScala.toList.filterNot(keep).foldLeft(resolved.root)(_.withoutKey(_))
      Right(trimmed.toConfig)
    catch case e: ConfigException => Left(Problem(s"$path: ${e.getMessage}"))

  /** Pure: `enabled` of an effective block — a boolean or a boolean string (env); absent = disabled. */
  def enabled(block: Config, id: Plugin.Info.Id): Either[Problem, Boolean] =
    if !block.hasPath("enabled") then Right(false)
    else
      val v = block.getValue("enabled")
      Option(v.unwrapped)
        .map(_.toString.trim.toLowerCase(java.util.Locale.ROOT))
        .flatMap(_.toBooleanOption)
        .toRight(Problem(s"$Root.$id.enabled must be true or false"))

  /** Pure: each key the USER's block sets against the kind of value the plugin's defaults have there — the derived
    * readers report a wrong type without its key ("Expected NUMBER but got STRING"), so the key is found here first, as
    * `ConfigLayers.typeCheck` does for Histmersion's own settings. A numeric or boolean string passes (env overrides
    * arrive as strings); a key whose default is `null` (an optional value) takes any value and is left to the reader.
    * Keys the defaults do not have are reported as unknown.
    */
  def typeCheck(id: Plugin.Info.Id, user: Config, defaults: Config): List[Problem] =
    import ConfigValueType.*
    val path = s"$Root.$id"
    if !Try(user.hasPath(path)).getOrElse(false) then Nil
    else
      leaves(user.getConfig(path).root, Nil).flatMap { (keys, v) =>
        val k = ConfigUtil.joinPath(keys.map(_.value).asJava)
        def text = Option(v.unwrapped).map(_.toString.trim).getOrElse("")
        if k == "enabled" then
          Option.unless(v.valueType == BOOLEAN || text.toBooleanOption.isDefined)(
            Problem(s"$path.enabled must be true or false")
          )
        else if !Try(defaults.hasPathOrNull(k)).getOrElse(false) then
          Some(Problem(s"$path.$k: unknown setting (not in the plugin's defaults)"))
        else if defaults.getIsNull(k) then None
        else
          val (ok, want) = defaults.getValue(k).valueType match
            case NUMBER => (v.valueType == NUMBER || v.valueType == STRING && text.toDoubleOption.isDefined, "a number")
            case BOOLEAN =>
              (v.valueType == BOOLEAN || v.valueType == STRING && text.toBooleanOption.isDefined, "true or false")
            case LIST   => (v.valueType == LIST, "a list [ … ]")
            case STRING => (v.valueType != LIST && v.valueType != OBJECT, "a string")
            case _      => (true, "")
          Option.unless(ok)(Problem(s"$path.$k must be $want, not ${v.render(ConfigRenderOptions.concise)}"))
      }

  /** Every leaf of a HOCON object (lists and nulls are leaves), with its path. */
  private def leaves(obj: ConfigObject, prefix: List[ConfigPath]): List[(List[ConfigPath], ConfigValue)] =
    obj.keySet.asScala.toList.sorted.flatMap { k =>
      obj.get(k) match
        case o: ConfigObject if !o.isEmpty => leaves(o, prefix :+ ConfigPath(k))
        case v                             => List((prefix :+ ConfigPath(k)) -> v)
    }

  /** Pure: a decoding error as `histmersion.plugins.<id>[.<key>]: what`. */
  def describe(id: Plugin.Info.Id, err: ConfigDecodingError): Problem = err match
    case ConfigDecodingError.Multiple(errs) => Problem(errs.toList.map(describe(id, _)).mkString("; "))
    case e                                  => Problem((s"$Root.$id" :: e.path).mkString(".") + s": ${e.message}")

  /** Decode a plugin's block with the plugin's own reader — the path-dependent type keeps the decoded value tied to the
    * plugin that will receive it, with no cast.
    */
  def decode(p: Plugin, conf: Config): Either[Problem, p.Config] =
    try p.configReader.from(conf.withoutPath("enabled").root).left.map(describe(p.info.id, _))
    catch
      case e: LinkageError => Left(Problem(s"$Root.${p.info.id}: ${failure(e)}"))
      case NonFatal(e)     => Left(Problem(s"$Root.${p.info.id}: ${e.getMessage}"))

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
  def defaults(p: Plugin): Either[Problem, Config] =
    try
      Right(
        ConfigFactory.parseResources(
          p.getClass.getClassLoader,
          p.defaultsResource.value,
          ConfigParseOptions.defaults.setAllowMissing(true)
        )
      )
    catch case e: ConfigException => Left(Problem(s"${p.info.id}: its defaults ${p.defaultsResource}: ${e.getMessage}"))

  // ── secrets ─────────────────────────────────────────────────────────────────────────────────

  /** Resolve one reference: `value:` is the literal, `env:` the variable of `env`, `<vault>:` asks the vault registered
    * under that name. Errors name the reference (never a value).
    */
  def resolve(
      ref: Secret.Ref,
      env: Config,
      vaults: Map[Secret.Ref.Vault.Value, Secret.Vault]
  ): Either[Problem, SecretValue] =
    ref match
      case Secret.Ref.Value(v) => Right(SecretValue(v.value))
      case Secret.Ref.Env(n)   =>
        Option(env.root.get(n.value))
          .flatMap(v => Option(v.unwrapped))
          .map(v => SecretValue(v.toString))
          .toRight(Problem(s"secret ${ref.shown}: environment variable $n is not set"))
      case Secret.Ref.Vault(name, r) =>
        vaults.get(name) match
          case None =>
            Left(
              Problem(
                s"secret ${ref.shown}: no secret vault '$name' is registered (enable the plugin that provides it)"
              )
            )
          case Some(v) =>
            try
              v.resolve(Secret.Vault.ResolveRef(r.value))
                .left
                .map(e => Problem(s"secret ${ref.shown}: $e"))
                .map(x => SecretValue(x.value))
            catch
              case NonFatal(e) =>
                Left(Problem(s"secret ${ref.shown}: vault '$name' failed: ${e.getClass.getSimpleName}"))

  /** Resolve several references of ONE vault in one call ([[Secret.Vault.resolveAll]]): each answer as [[resolve]]
    * words it; a reference the vault did not answer is an error, an answer to one not asked is dropped. A `resolveAll`
    * that throws (the SPI's default throws whatever one `resolve` throws) is answered one reference at a time instead,
    * so a throw fails only its own reference.
    */
  def resolveAll(
      name: Secret.Ref.Vault.Value,
      refs: List[Secret.Ref.Vault.Ref],
      vault: Secret.Vault
  ): Map[Secret.Ref.Vault.Ref, Either[Problem, SecretValue]] =
    val asked = refs.distinct
    def one(r: Secret.Ref.Vault.Ref) = resolve(Secret.Ref.Vault(name, r), ConfigFactory.empty, Map(name -> vault))
    try
      val answers = Option(vault.resolveAll(asked.map(r => Secret.Vault.ResolveRef(r.value)))).getOrElse(Map.empty)
      asked.map { r =>
        val answer: Either[String, SecretValue] = answers.get(Secret.Vault.ResolveRef(r.value)).flatMap(Option(_)) match
          case Some(Right(v)) => Right(SecretValue(v.value))
          case Some(Left(e))  => Left(e.value)
          case None           => Left(s"vault '$name' gave no answer for it")
        r -> answer.left.map(e => Problem(s"secret ${Secret.Ref.Vault(name, r).shown}: $e"))
      }.toMap
    catch case NonFatal(_) => asked.map(r => r -> one(r)).toMap

  /** The vaults of a generation's registries, by the name a reference gives them. */
  private def vaultsOf(regs: Registries): Map[Secret.Ref.Vault.Value, Secret.Vault] =
    regs.secretVaults.iterator.map((k, v) => Secret.Ref.Vault.Value(k.value) -> v).toMap

  /** The secrets of one generation: each vault is asked ONCE for all its references that `wanted` lists — on the first
    * reference of it any plugin needs, so a vault registered earlier in the same build is asked then too — and every
    * answer, value or error, is kept for this generation only (a reload resolves again; nothing outlives the build's
    * registrations).
    */
  final private class GenerationSecrets(env: Config, wanted: Map[Secret.Ref.Vault.Value, List[Secret.Ref.Vault.Ref]]):
    private val known = scala.collection.concurrent.TrieMap.empty[Secret.Ref, Either[Problem, SecretValue]]
    def apply(ref: Secret.Ref, vaults: Map[Secret.Ref.Vault.Value, Secret.Vault]): Either[Problem, SecretValue] =
      known.getOrElse(
        ref, {
          ref match
            case Secret.Ref.Vault(name, asked) if vaults.contains(name) =>
              val batch = (wanted.getOrElse(name, Nil) :+ asked).distinct
                .filterNot(r => known.contains(Secret.Ref.Vault(name, r)))
              resolveAll(name, batch, vaults(name)).foreach((r, v) => known.update(Secret.Ref.Vault(name, r), v))
            case _ => known.update(ref, resolve(ref, env, vaults))
          known.getOrElse(ref, resolve(ref, env, vaults))
        }
      )

  // ── a generation ────────────────────────────────────────────────────────────────────────────

  /** One enabled plugin after its config was decoded, waiting to register: `call` is `p.register` applied to the value
    * `p.configReader` decoded — built where that value still has type `p.Config`, so no cast is ever needed.
    */
  final private case class Prepared(
      p: Plugin,
      block: Config,
      secrets: List[Secret],
      call: Plugin.Context => Either[Problem, Unit],
      carried: Option[(PluginStatus, Registries)] = None
  )

  /** A registry whose additions are kept apart until the plugin's `register` returned. Mutable, but owned by ONE
    * register call: `close` ends it, after which any add (a plugin that kept the context) is refused.
    */
  final private class Staged[A](
      kind: String,
      committed: Map[Registry.AddId, A],
      valid: (Registry.AddId, A) => Option[Problem]
  ) extends Registry[A]:
    // the plugin's additions, in order; a var because the plugin calls `add` from inside `register`
    private var added: ListMap[Registry.AddId, A] = ListMap.empty
    private var open = true
    def add(id: Registry.AddId, a: A): Either[plugin.Problem, Unit] =
      if !open then Left(plugin.Problem(s"$kind '$id': registration is over (add from inside register)"))
      else if committed.contains(id) || added.contains(id) then
        Left(plugin.Problem(s"$kind '$id' is already registered"))
      else
        valid(id, a) match
          case Some(err) => Left(plugin.Problem(err.value))
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
      resolved: Secret => Either[Problem, SecretValue],
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
      def info(message: Plugin.Log.Line) =
        services.log(HostServices.PluginId(id.value), false, HostServices.Line(message.value))
      def warn(message: Plugin.Log.Line) =
        services.log(HostServices.PluginId(id.value), true, HostServices.Line(message.value))
    def env(name: EnvVar.Name) =
      Option(services.env.root.get(name.value))
        .flatMap(v => Option(v.unwrapped))
        .map(v => EnvVar.Value(v.toString))
    def secret(s: Secret) =
      resolved(s).left
        .map(e => plugin.Problem(e.value))
        .map(v => Secret.Resolved(v.value))
    def secretVaults = vaults
    override def quota(upstream: Plugin.Context.QuotaUpstream) = services.quota(HostServices.Upstream(upstream.value))
    override def licences = HostLicences
    override def sourceFetchers = fetchers
    override def mediaCollections = media
    override def eventCatalogues = events
    override def aiProviders = ai
    override def gazetteerSources = gazetteers
    override def sourceCollections = collections

  private def validFetcher(id: Registry.AddId, f: SourceFetcher): Option[Problem] =
    if !id.value.matches(IdPattern) then Some(Problem(s"'$id': a source fetcher id is $IdPattern"))
    else if f == null then Some(Problem(s"source fetcher '$id' is null"))
    else None

  /** A media collection: a kebab-case id; a plugin's may not take a name of the host's own providers (an import's name
    * or an alias of one, or a provider pictures are stored under — its pictures would be mistaken for theirs); its role
    * is one a collection's pictures may play. A first-party plugin's is the one the name is reserved for.
    */
  private def validCollection(firstParty: Boolean)(id: Registry.AddId, c: MediaCollection): Option[Problem] =
    if !id.value.matches(IdPattern) then Some(Problem(s"'$id': a media collection id is $IdPattern"))
    else if c == null then Some(Problem(s"media collection '$id' is null"))
    else if firstParty then None
    else if ReservedNames.mediaCollections(ReservedNames.Name(id.value)) then
      Some(Problem(s"'$id' is the name of one of Histmersion's own media providers"))
    else
      (c.role :: c.roles.toList.sorted).find(r => !MediaCollection.Roles(r)).map { r =>
        Problem(
          s"media collection '$id': role '$r' is not one of ${MediaCollection.Roles.toList.sorted.mkString(", ")}"
        )
      }

  /** An event catalogue: a kebab-case id; a plugin's may not take a name of the host's own catalogues, unless it is the
    * first-party plugin the name is reserved for.
    */
  private def validCatalogue(firstParty: Boolean)(id: Registry.AddId, c: EventCatalogue): Option[Problem] =
    if !id.value.matches(IdPattern) then Some(Problem(s"'$id': an event catalogue id is $IdPattern"))
    else if c == null then Some(Problem(s"event catalogue '$id' is null"))
    else if firstParty || c.isInstanceOf[HostOwned] then None
    else if ReservedNames.eventCatalogues(ReservedNames.Name(id.value)) then
      Some(Problem(s"'$id' is the name of one of Histmersion's own event catalogues"))
    else None

  /** An AI provider: its name is the provider half of `provider:model`, so kebab-case; a plugin's may not take the name
    * of one of the host's own providers (its models would be mistaken for theirs).
    */
  private def validAiProvider(firstParty: Boolean)(id: Registry.AddId, a: AiProvider): Option[Problem] =
    if !id.value.matches(IdPattern) then Some(Problem(s"'$id': an AI provider name is $IdPattern"))
    else if a == null then Some(Problem(s"AI provider '$id' is null"))
    else if firstParty || a.isInstanceOf[HostOwned] then None
    else if ReservedNames.aiProviders(ReservedNames.Name(id.value)) then
      Some(Problem(s"'$id' is the name of one of Histmersion's own AI providers"))
    else None

  /** A gazetteer source: a kebab-case id; a plugin's may not take the name of one of the host's own sources (the
    * datasets `places fetch` and `init.fetch-sources` name), unless it is the first-party plugin the name is reserved
    * for.
    */
  private def validGazetteer(firstParty: Boolean)(id: Registry.AddId, g: GazetteerSource): Option[Problem] =
    if !id.value.matches(IdPattern) then Some(Problem(s"'$id': a gazetteer source id is $IdPattern"))
    else if g == null then Some(Problem(s"gazetteer source '$id' is null"))
    else if firstParty then None
    else if ReservedNames.gazetteerSources(ReservedNames.Name(id.value)) then
      Some(Problem(s"'$id' is the name of one of Histmersion's own gazetteers"))
    else None

  /** A source collection: a kebab-case id; a plugin's may not take the name of one of Histmersion's own collections
    * (its items would be taken for theirs), unless it is the first-party plugin the name is reserved for.
    */
  private def validSourceCollection(firstParty: Boolean)(id: Registry.AddId, c: SourceCollection): Option[Problem] =
    if !id.value.matches(IdPattern) then Some(Problem(s"'$id': a source collection id is $IdPattern"))
    else if c == null then Some(Problem(s"source collection '$id' is null"))
    else if firstParty then None
    else if ReservedNames.sourceCollections(ReservedNames.Name(id.value)) then
      Some(Problem(s"'$id' is the name of one of Histmersion's own source collections"))
    else None

  private def validVault(id: Registry.AddId, v: Secret.Vault): Option[Problem] =
    if Set(Secret.Ref.ValueScheme, Secret.Ref.EnvScheme)(id.value) then
      Some(Problem(s"'$id' is a reserved secret scheme, not a vault name"))
    else if !id.value.matches(IdPattern) then Some(Problem(s"'$id': a vault name is $IdPattern"))
    else if v.name.value != id.value then Some(Problem(s"vault '${v.name}' registered under another name, '$id'"))
    else None

  /** A plugin's status in the report. */
  private def status(
      p: Plugin,
      enabled: Boolean,
      state: PluginState,
      block: Option[Config] = None,
      error: Option[Problem] = None,
      provides: List[PluginStatus.Provides] = Nil,
      warns: List[Warning] = Nil
  ): PluginStatus =
    PluginStatus(
      PluginStatus.Id(p.info.id.value),
      PluginStatus.Version(p.info.version.value),
      PluginStatus.SpiVersion(p.info.spiVersion.value),
      PluginStatus.Enabled(enabled),
      PluginState.Wire(state.wire.value),
      provides,
      block.map(settings).getOrElse(Nil),
      warns.map(w => PluginStatus.Warnings(w.value)),
      error.map(e => api.Problem(e.value))
    )

  private def hasState(st: PluginStatus, state: PluginState): Boolean = st.state == state.wire

  /** Build one generation: check, order, layer, type-check, decode, resolve secrets, register.
    *
    * A plugin whose secrets name a vault no plugin has registered YET is set aside and retried after the others (a
    * vault plugin may sort after the plugins that use it); one still waiting when no pass registers anything more
    * fails, naming the vault. Every failure is that plugin's alone.
    */
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
      .map(id => Problem(s"$Root.$id configures no plugin in this binary — ignored"))
    val warnings = discoveryErrors ++ orderErr ++ orderWarn ++ unknown
    warnings.foreach(w => services.log(HostServices.PluginId(""), true, HostServices.Line(w.value)))

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
            known = Try(defs.resolve()).getOrElse(defs)
            _ <- typeCheck(id, user, known) match
              case Nil  => Right(())
              case errs =>
                Left(
                  status(
                    p,
                    Try(user.getBoolean(s"$Root.$id.enabled")).getOrElse(false),
                    PluginState.Failed,
                    error = Some(Problem(errs.mkString("; ")))
                  )
                )
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
            ctx => p.register(typed, ctx).left.map(e => Problem(e.value))
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
              Some(
                Problem(
                  s"$Root.${pr.p.info.id}: no secret vault ${names.map(n => s"'$n'").mkString(", ")} is registered " +
                    "(enable the plugin that provides it)"
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
          HostServices.Line(s.error.fold("failed")(_.value))
        )
      )
    val live = registered.collect { case (id, (st, regs)) if hasState(st, PluginState.Registered) => id -> regs }
    val fresh = ready.filter(pr => pr.carried.isEmpty && live.contains(pr.p.info.id))
    Generation(
      registries,
      PluginsReport(
        PluginsReport.SpiVersion(Spi.Version),
        statuses,
        warnings.map(w => PluginsReport.Warnings(w.value))
      ),
      live,
      plugins.map(_.info.id).filterNot(carried.contains),
      ready.filter(_.secrets.nonEmpty).map(_.p.info.id).toSet,
      fresh.map(pr => pr.p.info.id -> pr.block).toMap,
      fresh
        .filter(_.secrets.nonEmpty)
        .map(pr =>
          pr.p.info.id -> pr.secrets
            .flatMap(sec => secrets(sec.ref, vaultsOf(registries)).toOption.map(sec.ref -> _))
            .toMap
        )
        .toMap
    )

  /** Resolve one prepared plugin's secrets and call its `register` with a staged context. */
  private def register(
      pr: Prepared,
      regs: Registries,
      services: HostServices,
      secrets: GenerationSecrets,
      firstParty: Boolean
  ): (PluginStatus, Registries) =
    val p = pr.p
    val id = p.info.id
    def resolved(s: Secret): Either[Problem, SecretValue] = secrets(s.ref, vaultsOf(regs))
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
    def fail(e: Problem) =
      (
        status(p, true, PluginState.Failed, Some(pr.block), Some(Problem(s"$Root.$id: $e")), Nil, warns),
        Registries.empty
      )
    pr.secrets.iterator.map(resolved).collectFirst { case Left(e) => e } match
      case Some(e) => fail(e)
      case None    =>
        def held[K, A](m: ListMap[K, A])(key: K => String): Map[Registry.AddId, A] =
          m.iterator.map((k, a) => Registry.AddId(key(k)) -> a).toMap
        val staged = Staged[Secret.Vault](Registries.SecretVault, held(regs.secretVaults)(_.value), validVault)
        val fetchers = Staged[SourceFetcher](Registries.SourceFetcher, held(regs.sourceFetchers)(_.value), validFetcher)
        val media =
          Staged[MediaCollection](
            Registries.MediaCollection,
            held(regs.mediaCollections)(_.value),
            validCollection(firstParty)
          )
        val events =
          Staged[EventCatalogue](
            Registries.EventCatalogue,
            held(regs.eventCatalogues)(_.value),
            validCatalogue(firstParty)
          )
        val ai = Staged[AiProvider](Registries.AiProvider, held(regs.aiProviders)(_.value), validAiProvider(firstParty))
        val gazetteers =
          Staged[GazetteerSource](
            Registries.GazetteerSource,
            held(regs.gazetteerSources)(_.value),
            validGazetteer(firstParty)
          )
        val collections =
          Staged[SourceCollection](
            Registries.SourceCollection,
            held(regs.sourceCollections)(_.value),
            validSourceCollection(firstParty)
          )
        val ctx =
          RegistrationContext(id, services, resolved, staged, fetchers, media, events, ai, gazetteers, collections)
        val outcome =
          try pr.call(ctx)
          catch
            case e: LinkageError => Left(Problem(s"register threw ${failure(e)}"))
            case NonFatal(e)     => Left(Problem(s"register threw ${failure(e)}"))
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
