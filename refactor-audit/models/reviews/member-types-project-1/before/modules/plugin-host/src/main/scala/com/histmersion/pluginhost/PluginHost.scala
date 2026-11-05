package com.histmersion.pluginhost

import com.histmersion.config.EnvOverrides
import com.histmersion.api.plugins.{PluginSetting, PluginsReport, PluginState, PluginStatus}
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

/** The host side of the SPI (docs/research-plugins.md §2–§3.7): ServiceLoader discovery, the config contract (each
  * plugin's block layered over its own defaults resource, type-checked by key, decoded with the plugin's own reader),
  * secret resolution, and registration into STAGED registries that are committed per plugin only when its `register`
  * succeeded.
  */
object PluginHost:

  val Root = "histmersion.plugins"

  /** The SPI generation this host speaks: what a plugin the host supplies itself is built against. */
  val SpiGeneration: Int = Spi.Version

  /** Keys of `histmersion.plugins` that are the host's, not a plugin's block. */
  val OrderKey = "order"
  private val Reserved = Set(OrderKey)
  private val IdPattern = "[a-z0-9][a-z0-9-]*"

  // ── discovery ───────────────────────────────────────────────────────────────────────────────

  /** Every `Plugin` provider on the class path. A provider that cannot be instantiated is reported, not fatal (the
    * others still load).
    */
  def discover(loader: ClassLoader): (List[Plugin], List[String]) =
    val it = ServiceLoader.load(classOf[Plugin], loader).iterator
    // ServiceLoader's iterator is lazy and may throw per provider: walk it by hand, keeping going
    @annotation.tailrec
    def loop(acc: List[Plugin], errs: List[String]): (List[Plugin], List[String]) =
      val step =
        try Right(if it.hasNext then Some(it.next()) else None)
        catch
          case e: LinkageError => Left(e)
          case NonFatal(e)     => Left(e)
      val loaded: Either[(Option[String], Throwable), Option[Plugin]] = step.left.map(e => providerOf(e) -> e).flatMap {
        case Some(p) => described(p).left.map(e => Some(p.getClass.getName) -> e).map(Some(_))
        case None    => Right(None)
      }
      loaded match
        case Right(Some(p))      => loop(p :: acc, errs)
        case Right(None)         => (acc.reverse, errs.reverse)
        case Left((provider, e)) =>
          loop(acc, s"a plugin provider${provider.fold("")(c => s" ($c)")} could not be loaded: ${failure(e)}" :: errs)
    loop(Nil, Nil)

  private val ProviderClass = "Provider ([\\w.$]+)".r.unanchored

  /** The provider class ServiceLoader names in the error it threw for one it could not instantiate. */
  def providerOf(e: Throwable): Option[String] = e match
    case e: java.util.ServiceConfigurationError =>
      Option(e.getMessage).collect { case ProviderClass(c) => c }
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
  def foreignSpi(e: Throwable): Option[Option[Int]] = e match
    case e: (NoSuchMethodError | NoSuchFieldError | AbstractMethodError | IncompatibleClassChangeError) =>
      val message = Option(e.getMessage).getOrElse("").replace('/', '.')
      if message.contains("com.histmersion.plugin.Spi$.Version()") then Some(Some(1))
      else if message.contains("com.histmersion.plugin.") then Some(None)
      else None
    case e => Option(e.getCause).filter(_ ne e).flatMap(foreignSpi)

  /** What to do about a plugin built against another SPI generation. */
  def rebuild(built: Option[Int]): String =
    s"it was built against ${built.fold("another plugin SPI")(v => s"plugin SPI $v")}; this Histmersion speaks " +
      s"SPI ${Spi.Version} — rebuild the plugin against SPI ${Spi.Version} (`histmersion plugin repo` has its API)"

  /** The class a plugin needs that no jar on the class path has, when that is why `e` was thrown: the library it
    * belongs to was neither shipped with Histmersion nor installed with the plugin.
    */
  def missingClass(e: Throwable): Option[String] = e match
    case e: NoClassDefFoundError   => Some(Option(e.getMessage).getOrElse("?").replace('/', '.'))
    case e: ClassNotFoundException => Some(Option(e.getMessage).getOrElse("?"))
    case e                         => Option(e.getCause).filter(_ ne e).flatMap(missingClass)

  /** Why plugin code threw, in words that say what to do when a class is missing. */
  def failure(e: Throwable): String =
    foreignSpi(e)
      .map(rebuild)
      .getOrElse(
        missingClass(e).fold(s"${e.getClass.getSimpleName}: ${e.getMessage}")(name =>
          s"class $name is missing: the plugin uses a library that is neither part of Histmersion nor installed with it — " +
            "install the plugin with `histmersion plugin add` from a jar published with its POM, which lists its dependencies"
        )
      )

  // ── pure parts of the config contract ───────────────────────────────────────────────────────

  /** Pure: why each refused descriptor is refused — a foreign SPI version, an id used twice (every plugin claiming it
    * is refused: which one the user meant cannot be known), an id that is no config key, or a key the host keeps for
    * itself.
    */
  def check(infos: List[Plugin.Info], spi: Int): Map[String, String] =
    val dup = infos.groupBy(_.id).collect {
      case (id, is) if is.size > 1 =>
        id -> s"plugin id '$id' is claimed by ${is.size} plugins"
    }
    val bad = infos.flatMap { i =>
      if i.spiVersion != spi then
        Some(i.id -> s"${i.id}: built for SPI ${i.spiVersion}, this host speaks $spi — rebuild it against SPI $spi")
      else if !i.id.matches(IdPattern) then Some(i.id -> s"'${i.id}': a plugin id is kebab-case $IdPattern")
      else if Reserved(i.id) then Some(i.id -> s"'${i.id}' is a key of $Root, not a plugin id")
      else None
    }.toMap
    bad ++ dup

  /** Pure: registration order — the ids named in `histmersion.plugins.order` first, as listed, then the rest by id;
    * with a warning per listed id that is no plugin here.
    */
  def order(ids: List[String], configured: List[String]): (List[String], List[String]) =
    val listed = configured.distinct
    val unknown =
      listed.filterNot(ids.contains).map(id => s"$Root.$OrderKey names '$id', which is no plugin in this binary")
    (listed.filter(ids.contains) ++ ids.filterNot(listed.contains).sorted, unknown)

  /** Pure: one digest of a plugin set — `(jar file name, its SHA-256)` pairs, order-independent. What a relink records
    * in the image and `histmersion plugin status` recomputes over the plugins directory (docs/research-plugins.md §7.3;
    * the one implementation is files' `PluginKit.digest`).
    */
  def digest(entries: List[(String, String)]): String = com.histmersion.util.plugins.PluginKit.digest(entries)

  /** Pure: `histmersion.plugins.order` of the user's file, or why it cannot be read. */
  def configuredOrder(user: Config, env: Config): Either[String, List[String]] =
    val path = s"$Root.$OrderKey"
    Try(user.withFallback(env).resolve()).toEither.left.map(e => s"$path: ${e.getMessage}").flatMap { c =>
      if !c.hasPath(path) then Right(Nil)
      else
        Try(c.getStringList(path).asScala.toList.map(_.trim).filter(_.nonEmpty)).toEither.left
          .map(_ => s"$path must be a list of plugin ids [ … ]")
    }

  /** Pure: blocks under `histmersion.plugins` no plugin here claims — a typo, or a plugin left out of this binary. A
    * warning, never an error: removing a plugin must not break startup.
    */
  def unknownBlocks(user: Config, ids: Set[String]): List[String] =
    if !Try(user.hasPath(Root)).getOrElse(false) then Nil
    else
      Try(user.getConfig(Root).root.keySet.asScala.toList.sorted)
        .getOrElse(Nil)
        .filterNot(k => ids(k) || Reserved(k))

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
      id: String,
      host: Config = ConfigFactory.empty
  ): Either[String, Config] =
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
    catch case e: ConfigException => Left(s"$path: ${e.getMessage}")

  /** Pure: `enabled` of an effective block — a boolean or a boolean string (env); absent = disabled. */
  def enabled(block: Config, id: String): Either[String, Boolean] =
    if !block.hasPath("enabled") then Right(false)
    else
      val v = block.getValue("enabled")
      Option(v.unwrapped)
        .map(_.toString.trim.toLowerCase(java.util.Locale.ROOT))
        .flatMap(_.toBooleanOption)
        .toRight(s"$Root.$id.enabled must be true or false")

  /** Pure: each key the USER's block sets against the kind of value the plugin's defaults have there — the derived
    * readers report a wrong type without its key ("Expected NUMBER but got STRING"), so the key is found here first, as
    * `ConfigLayers.typeCheck` does for Histmersion's own settings. A numeric or boolean string passes (env overrides
    * arrive as strings); a key whose default is `null` (an optional value) takes any value and is left to the reader.
    * Keys the defaults do not have are reported as unknown.
    */
  def typeCheck(id: String, user: Config, defaults: Config): List[String] =
    import ConfigValueType.*
    val path = s"$Root.$id"
    if !Try(user.hasPath(path)).getOrElse(false) then Nil
    else
      leaves(user.getConfig(path).root, Nil).flatMap { (keys, v) =>
        val k = ConfigUtil.joinPath(keys.asJava)
        def text = Option(v.unwrapped).map(_.toString.trim).getOrElse("")
        if k == "enabled" then
          Option.unless(v.valueType == BOOLEAN || text.toBooleanOption.isDefined)(
            s"$path.enabled must be true or false"
          )
        else if !Try(defaults.hasPathOrNull(k)).getOrElse(false) then
          Some(s"$path.$k: unknown setting (not in the plugin's defaults)")
        else if defaults.getIsNull(k) then None
        else
          val (ok, want) = defaults.getValue(k).valueType match
            case NUMBER => (v.valueType == NUMBER || v.valueType == STRING && text.toDoubleOption.isDefined, "a number")
            case BOOLEAN =>
              (v.valueType == BOOLEAN || v.valueType == STRING && text.toBooleanOption.isDefined, "true or false")
            case LIST   => (v.valueType == LIST, "a list [ … ]")
            case STRING => (v.valueType != LIST && v.valueType != OBJECT, "a string")
            case _      => (true, "")
          Option.unless(ok)(s"$path.$k must be $want, not ${v.render(ConfigRenderOptions.concise)}")
      }

  /** Every leaf of a HOCON object (lists and nulls are leaves), with its path. */
  private def leaves(obj: ConfigObject, prefix: List[String]): List[(List[String], ConfigValue)] =
    obj.keySet.asScala.toList.sorted.flatMap { k =>
      obj.get(k) match
        case o: ConfigObject if !o.isEmpty => leaves(o, prefix :+ k)
        case v                             => List((prefix :+ k) -> v)
    }

  /** Pure: a decoding error as `histmersion.plugins.<id>[.<key>]: what`. */
  def describe(id: String, err: ConfigDecodingError): String = err match
    case ConfigDecodingError.Multiple(errs) => errs.toList.map(describe(id, _)).mkString("; ")
    case e                                  => (s"$Root.$id" :: e.path).mkString(".") + s": ${e.message}"

  /** Decode a plugin's block with the plugin's own reader — the path-dependent type keeps the decoded value tied to the
    * plugin that will receive it, with no cast.
    */
  def decode(p: Plugin, conf: Config): Either[String, p.Config] =
    try p.configReader.from(conf.withoutPath("enabled").root).left.map(describe(p.info.id, _))
    catch
      case e: LinkageError => Left(s"$Root.${p.info.id}: ${failure(e)}")
      case NonFatal(e)     => Left(s"$Root.${p.info.id}: ${e.getMessage}")

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
      PluginSetting(ConfigUtil.joinPath(keys.asJava), shown)
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
  def defaults(p: Plugin): Either[String, Config] =
    try
      Right(
        ConfigFactory.parseResources(
          p.getClass.getClassLoader,
          p.defaultsResource,
          ConfigParseOptions.defaults.setAllowMissing(true)
        )
      )
    catch case e: ConfigException => Left(s"${p.info.id}: its defaults ${p.defaultsResource}: ${e.getMessage}")

  // ── secrets ─────────────────────────────────────────────────────────────────────────────────

  /** Resolve one reference: `value:` is the literal, `env:` the variable of `env`, `<vault>:` asks the vault registered
    * under that name. Errors name the reference (never a value).
    */
  def resolve(ref: Secret.Ref, env: Config, vaults: Map[String, Secret.Vault]): Either[String, String] =
    ref match
      case Secret.Ref.Value(v) => Right(v)
      case Secret.Ref.Env(n)   =>
        Option(env.root.get(n))
          .flatMap(v => Option(v.unwrapped))
          .map(_.toString)
          .toRight(s"secret ${ref.shown}: environment variable $n is not set")
      case Secret.Ref.Vault(name, r) =>
        vaults.get(name) match
          case None =>
            Left(s"secret ${ref.shown}: no secret vault '$name' is registered (enable the plugin that provides it)")
          case Some(v) =>
            try v.resolve(r).left.map(e => s"secret ${ref.shown}: $e")
            catch case NonFatal(e) => Left(s"secret ${ref.shown}: vault '$name' failed: ${e.getClass.getSimpleName}")

  /** Resolve several references of ONE vault in one call ([[Secret.Vault.resolveAll]]): each answer as [[resolve]]
    * words it; a reference the vault did not answer is an error, an answer to one not asked is dropped. A `resolveAll`
    * that throws (the SPI's default throws whatever one `resolve` throws) is answered one reference at a time instead,
    * so a throw fails only its own reference.
    */
  def resolveAll(name: String, refs: List[String], vault: Secret.Vault): Map[String, Either[String, String]] =
    val asked = refs.distinct
    def one(r: String) = resolve(Secret.Ref.Vault(name, r), ConfigFactory.empty, Map(name -> vault))
    try
      val answers = Option(vault.resolveAll(asked)).getOrElse(Map.empty)
      asked.map { r =>
        r -> answers
          .get(r)
          .flatMap(Option(_))
          .getOrElse(Left(s"vault '$name' gave no answer for it"))
          .left
          .map(e => s"secret ${Secret.Ref.Vault(name, r).shown}: $e")
      }.toMap
    catch case NonFatal(_) => asked.map(r => r -> one(r)).toMap

  /** The secrets of one generation: each vault is asked ONCE for all its references that `wanted` lists — on the first
    * reference of it any plugin needs, so a vault registered earlier in the same build is asked then too — and every
    * answer, value or error, is kept for this generation only (a reload resolves again; nothing outlives the build's
    * registrations).
    */
  final private class GenerationSecrets(env: Config, wanted: Map[String, List[String]]):
    private val known = scala.collection.concurrent.TrieMap.empty[Secret.Ref, Either[String, String]]
    def apply(ref: Secret.Ref, vaults: Map[String, Secret.Vault]): Either[String, String] =
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
      call: Plugin.Context => Either[String, Unit],
      carried: Option[(PluginStatus, Registries)] = None
  )

  /** A registry whose additions are kept apart until the plugin's `register` returned. Mutable, but owned by ONE
    * register call: `close` ends it, after which any add (a plugin that kept the context) is refused.
    */
  final private class Staged[A](kind: String, committed: Map[String, A], valid: (String, A) => Option[String])
      extends Registry[A]:
    // the plugin's additions, in order; a var because the plugin calls `add` from inside `register`
    private var added: ListMap[String, A] = ListMap.empty
    private var open = true
    def add(id: String, a: A): Either[String, Unit] =
      if !open then Left(s"$kind '$id': registration is over (add from inside register)")
      else if committed.contains(id) || added.contains(id) then Left(s"$kind '$id' is already registered")
      else
        valid(id, a) match
          case Some(err) => Left(err)
          case None      => added = added.updated(id, a); Right(())
    def close(): ListMap[String, A] =
      open = false
      added

  /** The context one plugin's `register` is called with: the host's services, its secrets, and the staged registries.
    * The HTTP client is built on the first request.
    */
  final private class RegistrationContext(
      id: String,
      services: HostServices,
      resolved: Secret => Either[String, String],
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
      def info(message: String) = services.log(id, false, message)
      def warn(message: String) = services.log(id, true, message)
    def env(name: String) = Option(services.env.root.get(name)).flatMap(v => Option(v.unwrapped)).map(_.toString)
    def secret(s: Secret) = resolved(s)
    def secretVaults = vaults
    override def quota(upstream: String) = services.quota(upstream)
    override def licences = HostLicences
    override def sourceFetchers = fetchers
    override def mediaCollections = media
    override def eventCatalogues = events
    override def aiProviders = ai
    override def gazetteerSources = gazetteers
    override def sourceCollections = collections

  private def validFetcher(id: String, f: SourceFetcher): Option[String] =
    if !id.matches(IdPattern) then Some(s"'$id': a source fetcher id is $IdPattern")
    else if f == null then Some(s"source fetcher '$id' is null")
    else None

  /** A media collection: a kebab-case id; a plugin's may not take a name of the host's own providers (an import's name
    * or an alias of one, or a provider pictures are stored under — its pictures would be mistaken for theirs); its role
    * is one a collection's pictures may play. A first-party plugin's is the one the name is reserved for.
    */
  private def validCollection(firstParty: Boolean)(id: String, c: MediaCollection): Option[String] =
    if !id.matches(IdPattern) then Some(s"'$id': a media collection id is $IdPattern")
    else if c == null then Some(s"media collection '$id' is null")
    else if firstParty then None
    else if ReservedNames.mediaCollections(id) then
      Some(s"'$id' is the name of one of Histmersion's own media providers")
    else
      (c.role :: c.roles.toList.sorted).find(r => !MediaCollection.Roles(r)).map { r =>
        s"media collection '$id': role '$r' is not one of ${MediaCollection.Roles.toList.sorted.mkString(", ")}"
      }

  /** An event catalogue: a kebab-case id; a plugin's may not take a name of the host's own catalogues, unless it is the
    * first-party plugin the name is reserved for.
    */
  private def validCatalogue(firstParty: Boolean)(id: String, c: EventCatalogue): Option[String] =
    if !id.matches(IdPattern) then Some(s"'$id': an event catalogue id is $IdPattern")
    else if c == null then Some(s"event catalogue '$id' is null")
    else if firstParty || c.isInstanceOf[HostOwned] then None
    else if ReservedNames.eventCatalogues(id) then
      Some(s"'$id' is the name of one of Histmersion's own event catalogues")
    else None

  /** An AI provider: its name is the provider half of `provider:model`, so kebab-case; a plugin's may not take the name
    * of one of the host's own providers (its models would be mistaken for theirs).
    */
  private def validAiProvider(firstParty: Boolean)(id: String, a: AiProvider): Option[String] =
    if !id.matches(IdPattern) then Some(s"'$id': an AI provider name is $IdPattern")
    else if a == null then Some(s"AI provider '$id' is null")
    else if firstParty || a.isInstanceOf[HostOwned] then None
    else if ReservedNames.aiProviders(id) then Some(s"'$id' is the name of one of Histmersion's own AI providers")
    else None

  /** A gazetteer source: a kebab-case id; a plugin's may not take the name of one of the host's own sources (the
    * datasets `places fetch` and `init.fetch-sources` name), unless it is the first-party plugin the name is reserved
    * for.
    */
  private def validGazetteer(firstParty: Boolean)(id: String, g: GazetteerSource): Option[String] =
    if !id.matches(IdPattern) then Some(s"'$id': a gazetteer source id is $IdPattern")
    else if g == null then Some(s"gazetteer source '$id' is null")
    else if firstParty then None
    else if ReservedNames.gazetteerSources(id) then Some(s"'$id' is the name of one of Histmersion's own gazetteers")
    else None

  /** A source collection: a kebab-case id; a plugin's may not take the name of one of Histmersion's own collections
    * (its items would be taken for theirs), unless it is the first-party plugin the name is reserved for.
    */
  private def validSourceCollection(firstParty: Boolean)(id: String, c: SourceCollection): Option[String] =
    if !id.matches(IdPattern) then Some(s"'$id': a source collection id is $IdPattern")
    else if c == null then Some(s"source collection '$id' is null")
    else if firstParty then None
    else if ReservedNames.sourceCollections(id) then
      Some(s"'$id' is the name of one of Histmersion's own source collections")
    else None

  private def validVault(id: String, v: Secret.Vault): Option[String] =
    if Set(Secret.Ref.ValueScheme, Secret.Ref.EnvScheme)(id) then
      Some(s"'$id' is a reserved secret scheme, not a vault name")
    else if !id.matches(IdPattern) then Some(s"'$id': a vault name is $IdPattern")
    else if v.name != id then Some(s"vault '${v.name}' registered under another name, '$id'")
    else None

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
      discoveryErrors: List[String] = Nil,
      /** Plugins whose block did not change since the previous generation (a config reload): their status and
        * contributions are taken over as they are — not decoded, resolved or registered again. A registered one goes
        * into the registries in its place in the order.
        */
      carried: Map[String, (PluginStatus, Registries)] = Map.empty,
      /** The settings the host supplies a first-party plugin, by id: its fallback block from the host's own keys (keys
        * relative to the plugin's block), between the user's block and the plugin's defaults ([[block]]).
        */
      fallback: String => Config = _ => ConfigFactory.empty,
      /** The first-party plugins: the ones a reserved name of the host's (a catalogue's, a media provider's, an AI
        * provider's) is kept for.
        */
      firstParty: String => Boolean = _ => false
  ): Generation =
    val infos = plugins.map(_.info)
    val refused = check(infos, Spi.Version)
    val accepted = plugins.filterNot(p => refused.contains(p.info.id))
    val (configured, orderErr) = configuredOrder(user, env) match
      case Right(o) => (o, Nil)
      case Left(e)  => (Nil, List(e))
    val (ordered, orderWarn) = order(accepted.map(_.info.id), configured)
    val unknown = unknownBlocks(user, infos.map(_.id).toSet)
      .map(id => s"$Root.$id configures no plugin in this binary — ignored")
    val warnings = discoveryErrors ++ orderErr ++ orderWarn ++ unknown
    warnings.foreach(w => services.log("", true, w))

    val byId = accepted.map(p => p.info.id -> p).toMap
    def status(
        p: Plugin,
        enabled: Boolean,
        state: PluginState,
        block: Option[Config] = None,
        error: Option[String] = None,
        provides: List[String] = Nil,
        warns: List[String] = Nil
    ) =
      PluginStatus(
        p.info.id,
        p.info.version,
        p.info.spiVersion,
        enabled,
        state.wire,
        provides,
        block.map(settings).getOrElse(Nil),
        warns,
        error
      )

    // the config of every accepted plugin, in order
    val prepared: List[(String, Either[PluginStatus, Prepared])] = ordered.map { id =>
      val p = byId(id)
      val r = carried.get(id) match
        case Some((st, _)) if st.state != PluginState.Registered.wire => Left(st)
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
                    error = Some(errs.mkString("; "))
                  )
                )
            conf <- block(user, defs, env, id).left.map(e => status(p, false, PluginState.Failed, error = Some(e)))
            on <- enabled(conf, id).left.map(e => status(p, false, PluginState.Failed, Some(conf), Some(e)))
            _ <- Either.cond(on, (), status(p, false, PluginState.Disabled, Some(conf)))
            supplied <- block(user, defs, env, id, fallback(id)).left
              .map(e => status(p, true, PluginState.Failed, Some(conf), Some(e)))
            typed <- decode(p, supplied).left.map(e => status(p, true, PluginState.Failed, Some(conf), Some(e)))
          yield Prepared(p, conf, secretsIn(typed), ctx => p.register(typed, ctx))
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
        done: Map[String, (PluginStatus, Registries)]
    ): (Registries, Map[String, (PluginStatus, Registries)]) =
      @annotation.tailrec
      def pass(
          rest: List[Prepared],
          rs: Registries,
          ds: Map[String, (PluginStatus, Registries)],
          left: List[Prepared],
          prog: Boolean
      ): (Registries, Map[String, (PluginStatus, Registries)], List[Prepared], Boolean) =
        rest match
          case Nil        => (rs, ds, left, prog)
          case pr :: tail =>
            val missing =
              pr.secrets.map(_.ref).collect { case Secret.Ref.Vault(v, _) if !rs.secretVaults.contains(v) => v }
            if missing.nonEmpty then pass(tail, rs, ds, left :+ pr, prog)
            else
              val (st, added) = pr.carried.getOrElse(register(pr, rs, services, secrets, firstParty(pr.p.info.id)))
              pass(tail, rs ++ added, ds.updated(pr.p.info.id, (st, added)), left, true)
      val (rs, ds, left, progressed) = pass(waiting, regs, done, Nil, false)
      if left.isEmpty || !progressed then
        val stuck = left.map { pr =>
          val names =
            pr.secrets.map(_.ref).collect { case Secret.Ref.Vault(v, _) if !rs.secretVaults.contains(v) => v }.distinct
          pr.p.info.id -> (
            status(
              pr.p,
              true,
              PluginState.Failed,
              Some(pr.block),
              Some(
                s"$Root.${pr.p.info.id}: no secret vault ${names.map(n => s"'$n'").mkString(", ")} is registered " +
                  "(enable the plugin that provides it)"
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
      .filter(_.state == PluginState.Failed.wire)
      .foreach(s => services.log(s.id, true, s.error.getOrElse("failed")))
    val live = registered.collect { case (id, (st, regs)) if st.state == PluginState.Registered.wire => id -> regs }
    val fresh = ready.filter(pr => pr.carried.isEmpty && live.contains(pr.p.info.id))
    Generation(
      registries,
      PluginsReport(Spi.Version, statuses, warnings),
      live,
      plugins.map(_.info.id).filterNot(carried.contains),
      ready.filter(_.secrets.nonEmpty).map(_.p.info.id).toSet,
      fresh.map(pr => pr.p.info.id -> pr.block).toMap,
      fresh
        .filter(_.secrets.nonEmpty)
        .map(pr =>
          pr.p.info.id -> pr.secrets
            .flatMap(sec => secrets(sec.ref, registries.secretVaults).toOption.map(sec.ref -> _))
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
    def resolved(s: Secret): Either[String, String] = secrets(s.ref, regs.secretVaults)
    val literal = pr.secrets.exists(_.ref.isInstanceOf[Secret.Ref.Value])
    val warns = if !literal then Nil
    else
      List(
        s"$Root.$id: a secret is written into the config (value:${Secret.Ref.Mask}) — " +
          "use env:<NAME> or a vault reference"
      )
    warns.foreach(w => services.log(id, true, w))
    def fail(e: String) =
      (
        PluginStatus(
          id,
          p.info.version,
          p.info.spiVersion,
          true,
          PluginState.Failed.wire,
          Nil,
          settings(pr.block),
          warns,
          Some(s"$Root.$id: $e")
        ),
        Registries.empty
      )
    pr.secrets.iterator.map(resolved).collectFirst { case Left(e) => e } match
      case Some(e) => fail(e)
      case None    =>
        val staged = Staged[Secret.Vault](Registries.SecretVault, regs.secretVaults, validVault)
        val fetchers = Staged[SourceFetcher](Registries.SourceFetcher, regs.sourceFetchers, validFetcher)
        val media =
          Staged[MediaCollection](Registries.MediaCollection, regs.mediaCollections, validCollection(firstParty))
        val events = Staged[EventCatalogue](Registries.EventCatalogue, regs.eventCatalogues, validCatalogue(firstParty))
        val ai = Staged[AiProvider](Registries.AiProvider, regs.aiProviders, validAiProvider(firstParty))
        val gazetteers =
          Staged[GazetteerSource](Registries.GazetteerSource, regs.gazetteerSources, validGazetteer(firstParty))
        val collections =
          Staged[SourceCollection](
            Registries.SourceCollection,
            regs.sourceCollections,
            validSourceCollection(firstParty)
          )
        val ctx =
          RegistrationContext(id, services, resolved, staged, fetchers, media, events, ai, gazetteers, collections)
        val outcome =
          try pr.call(ctx)
          catch
            case e: LinkageError => Left(s"register threw ${failure(e)}")
            case NonFatal(e)     => Left(s"register threw ${failure(e)}")
        val added = Registries(
          staged.close(),
          fetchers.close(),
          media.close(),
          events.close(),
          ai.close(),
          gazetteerSources = gazetteers.close(),
          sourceCollections = collections.close()
        )
        outcome match
          case Left(e)  => fail(e)
          case Right(_) =>
            (
              PluginStatus(
                id,
                p.info.version,
                p.info.spiVersion,
                true,
                PluginState.Registered.wire,
                added.provides,
                settings(pr.block),
                warns
              ),
              added
            )
