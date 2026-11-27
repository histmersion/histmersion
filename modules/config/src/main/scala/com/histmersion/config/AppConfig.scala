package com.histmersion
package config

import com.histmersion.util.problem.{Problem, Problems, Raised}
import com.histmersion.util.mcp.McpConfig
import com.histmersion.util.log.LogConfig
import org.ekrich.config.{ConfigFactory, ConfigResolveOptions}
import hearth.kindlings.sconfigderivation.ConfigReader
import hearth.kindlings.sconfigderivation.annotations.configKey
import com.histmersion.lint.{atBoundary, movesTo, BoundaryKind, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@atBoundary(BoundaryKind.Config, "reads the HOCON layers into the typed application config")
@movesTo(FeaturePlace.Config)
final case class AppConfig(
    server: ServerConfig,
    db: DbConfig,
    converter: ConverterConfig,
    corpus: CorpusConfig,
    backups: BackupsConfig,
    maintenance: MaintenanceConfig,
    log: LogConfig,
    bundles: BundlesConfig,
    storage: StorageConfig,
    merge: MergeConfig,
    limits: Limits,
    ui: UiSettings,
    cli: CliConfig,
    query: QueryConfig,
    lint: LintConfig,
    places: PlacesConfig,
    periods: PeriodsConfig,
    init: InitConfig,
    acquire: AcquireConfig,
    auth: AuthConfig,
    runtime: RuntimeConfig,
    agents: AgentsConfig,
    /** The library's part of the same `agents` block. */
    @configKey("agents") libraryAgents: LibraryAgentsConfig,
    /** Research's part of the same `agents` block. */
    @configKey("agents") researchAgents: ResearchAgentsConfig,
    media: MediaConfig,
    events: EventsConfig,
    config: ConfigReload.Config,
    basemap: BasemapConfig,
    mcp: McpConfig,
    setup: SetupConfig = SetupConfig(completed = SetupConfig.Completed(false))
) derives ConfigReader

object AppConfig:
  /** reference.conf with the environment's overrides — no user file. What tests and `about`-less tools read. Must run
    * on a platform thread (main), not an Ox virtual thread: the env read uses GraalVM StackValue under native-image.
    */

  def load(): AppConfig = load(ConfigFactory.systemEnvironment())

  /** Load with an explicit environment (tests). */
  def load(env: org.ekrich.config.Config): AppConfig =
    decode(ConfigLayers.merged(ConfigLayers.reference, ConfigFactory.empty, env)) match
      case Right(cfg) => cfg
      case Left(err)  => throw Raised(err)

  /** The server's and the CLI's load: reference.conf, then the user config file ([[UserPaths.configFile]]), then the
    * environment — env > file > reference.conf. A missing file is no error; one that does not parse or does not
    * type-check is, naming the file and the key. Main thread only, like [[load]].
    */
  def loadWithUserFile(): Either[Problems, LoadedConfig] =
    val env = ConfigFactory.systemEnvironment()
    loadWithUserFile(env, UserPaths.configFile(Environment.of(env).lookup))

  /** The same with an explicit environment and file (tests; `file = None`: no location). */
  def loadWithUserFile(
      env: org.ekrich.config.Config,
      file: Option[java.nio.file.Path]
  ): Either[Problems, LoadedConfig] =
    // Parse reference.conf and resolve ${?ENV} overrides from the given environment only — deliberately
    // NOT ConfigFactory.load(), which folds ALL system properties into HOCON (dotted keys like
    // `io.netty.allocator.type` then collide and blow up sconfig under native-image).
    val reference = ConfigLayers.reference
    file match
      case None =>
        val merged = ConfigLayers.merged(reference, ConfigFactory.empty, env)
        decode(merged)
          .map(
            LoadedConfig(
              _,
              UserConfigFile(None, loaded = UserConfigFile.Loaded(false)),
              env = env,
              merged = ConfigReload.settings(merged)
            )
          )
      case Some(p) =>
        ConfigLayers.parse(p).flatMap {
          case None =>
            val merged = ConfigLayers.merged(reference, ConfigFactory.empty, env)
            decode(merged)
              .map(
                LoadedConfig(
                  _,
                  UserConfigFile(Some(p), loaded = UserConfigFile.Loaded(false)),
                  env = env,
                  merged = ConfigReload.settings(merged)
                )
              )
          case Some(user) => withUser(reference, user, env, Some(p))
        }

  /** The user config file `file` as a load parses it: HOCON with its includes, unresolved; `None` when there is no such
    * file.
    */
  def parseUserFile(file: java.nio.file.Path): Either[Problem, Option[org.ekrich.config.Config]] =
    ConfigLayers.parse(file)

  /** Is `origin` the user config file `file` itself, not a file it includes? */
  def writtenIn(file: java.nio.file.Path)(origin: org.ekrich.config.ConfigOrigin): Boolean =
    ConfigLayers.sameFile(origin, file)

  /** The same load over the TEXT of a config file (a change checked before it is written, without a file; an include in
    * it is not followed).
    */
  def loadWithUserText(env: org.ekrich.config.Config, text: String): Either[Problems, LoadedConfig] =
    try withUser(ConfigLayers.reference, ConfigFactory.parseString(text), env, None)
    catch case e: org.ekrich.config.ConfigException => Left(ConfigLayers.unreadable(e, MergedSettings))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def withUser(
      reference: org.ekrich.config.Config,
      user: org.ekrich.config.Config,
      env: org.ekrich.config.Config,
      p: Option[java.nio.file.Path]
  ): Either[Problems, LoadedConfig] =
    for
      // the settings and the KIND of each from the defaults alone: an env value is always a
      // string, and would make every key it overrides look like a string setting
      known <- resolved(reference.resolve(ConfigResolveOptions.noSystem))
      keys <- ConfigLayers.check(user, known)
      // the file's OWN values are checked, not what the environment leaves of them: a mistyped
      // setting that an env var happens to shadow today is still a broken file
      asFiled <- resolved(EnvOverrides.textAsLists(reference, user.withFallback(reference).withFallback(env).resolve()))
      _ <- ConfigLayers.typeCheck(keys.settings, asFiled, known)
      merged <- resolved(ConfigLayers.merged(reference, user, env))
      cfg <- decode(merged)
    yield LoadedConfig(
      cfg,
      UserConfigFile(
        p,
        loaded = UserConfigFile.Loaded(true),
        keys.unknown.map(u => UserConfigFile.UnknownKeys(u.value))
      ),
      user,
      env,
      ConfigReload.settings(merged)
    )

  /** An environment `Config` as a lookup (its keys are variable names, not paths). */
  def lookup(env: org.ekrich.config.Config): String => Option[String] =
    name =>
      Option(env.root.get(name)).map(_.unwrapped).collect { case s: String => s; case o if o != null => o.toString }

  /** Where the settings are, as a problem names them: the merged tree, not one file. */
  private val MergedSettings = Problem.Where("the settings")

  private def resolved(c: => org.ekrich.config.Config): Either[Problems, org.ekrich.config.Config] =
    try Right(c.resolve())
    catch case e: org.ekrich.config.ConfigException => Left(ConfigLayers.unreadable(e, MergedSettings))

  /** Decode the settings of a merged tree (`LoadedConfig.merged`, or one [[ConfigReload.effective]] made of it).
    */
  def fromMerged(c: org.ekrich.config.Config): Either[Problems, AppConfig] = decode(c)

  private def decode(c: => org.ekrich.config.Config): Either[Problems, AppConfig] =
    try ConfigReader[AppConfig].from(c.getConfig("histmersion").root).left.map(ConfigLayers.describe)
    catch case e: org.ekrich.config.ConfigException => Left(ConfigLayers.unreadable(e, MergedSettings))
