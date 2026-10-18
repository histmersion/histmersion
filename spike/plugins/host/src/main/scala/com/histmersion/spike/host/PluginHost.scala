package com.histmersion.spike.host

import com.histmersion.plugin.*
import hearth.kindlings.sconfigderivation.ConfigDecodingError
import org.ekrich.config.{Config, ConfigFactory, ConfigParseOptions}

import java.util.ServiceLoader
import scala.jdk.CollectionConverters.*

/** The host side of the SPI (spike): discovery, the config contract and start-up. The pure parts
  * (`block`, `unknownBlocks`, `check`, `digest`) are what the real implementation keeps. */
object PluginHost:

  val Root = "histmersion.plugins"

  /** A plugin the host found, its block decoded and — when enabled — started. */
  final case class Loaded(info: PluginInfo, enabled: Boolean, fetcher: Option[SourceFetcher])

  /** Pure: the plugin's effective block — the user's `histmersion.plugins.<id>` over the plugin's own
    * defaults, `${?ENV}` resolved against `env` only (never system properties: AGENTS.md). */
  def block(user: Config, defaults: Config, env: Config, id: String): Either[String, Config] =
    val path = s"$Root.$id"
    val own  = if user.hasPath(path) then user.getConfig(path) else ConfigFactory.empty
    try Right(own.withFallback(defaults).withFallback(env).resolve())
    catch case e: org.ekrich.config.ConfigException => Left(s"$path: ${e.getMessage}")

  /** Pure: blocks under `histmersion.plugins` no installed plugin claims — a typo, or a plugin that is
    * not in this binary (the native image is closed: a jar added after the build is not here). */
  def unknownBlocks(user: Config, ids: Set[String]): List[String] =
    if !user.hasPath(Root) then Nil
    else user.getConfig(Root).root.keySet.asScala.toList.sorted.filterNot(ids)

  /** Pure: the descriptors the host refuses — a foreign SPI version, an id used twice, an id that is
    * not a config key. */
  def check(infos: List[PluginInfo], spi: Int): List[String] =
    val dup = infos.groupBy(_.id).collect { case (id, is) if is.size > 1 => s"plugin id '$id' is claimed ${is.size} times" }
    val bad = infos.collect {
      case i if i.spiVersion != spi => s"${i.id}: built for SPI ${i.spiVersion}, this host speaks $spi"
      case i if !i.id.matches("[a-z][a-z0-9-]*") => s"'${i.id}': a plugin id is kebab-case [a-z][a-z0-9-]*"
    }
    (dup ++ bad).toList.sorted

  /** Pure: one digest of a plugin set — `(file name, its SHA-256)` pairs, order-independent. What the
    * build records in the image and the CLI recomputes over the plugins directory. */
  def digest(entries: List[(String, String)]): String =
    val text = entries.sortBy(_._1).map((n, h) => s"$n $h\n").mkString
    sha256(text.getBytes("UTF-8"))

  def sha256(b: Array[Byte]): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(b).map(x => f"$x%02x").mkString

  /** Pure: a decoding error as `histmersion.plugins.<id>.<key>: what`. */
  def describe(id: String, err: ConfigDecodingError): String = err match
    case ConfigDecodingError.Multiple(errs) => errs.toList.map(describe(id, _)).mkString("; ")
    case e => (s"$Root.$id" :: e.path).mkString(".") + s": ${e.message}"

  /** Pure: each key the USER's block sets against the kind of value the plugin's defaults have there —
    * the derived readers report a wrong type without its key ("Expected NUMBER but got STRING"), so
    * the key is found here first, as `ConfigLayers.typeCheck` does for Histmersion's own settings. A
    * numeric or boolean string passes (env overrides arrive as strings). Keys the defaults do not
    * have are reported as unknown. */
  def typeCheck(id: String, user: Config, defaults: Config): List[String] =
    import org.ekrich.config.ConfigValueType.*
    val path = s"$Root.$id"
    if !user.hasPath(path) then Nil
    else
      val own = user.getConfig(path)
      own.entrySet.asScala.toList.sortBy(_.getKey).flatMap { e =>
        val k = e.getKey
        val v = e.getValue
        def text = Option(v.unwrapped).map(_.toString.trim).getOrElse("")
        if k == "enabled" then Option.unless(v.valueType == BOOLEAN || text.toBooleanOption.isDefined)(s"$path.enabled must be true or false")
        else if !defaults.hasPath(k) then Some(s"$path.$k: unknown setting (not in the plugin's defaults)")
        else
          val (ok, want) = defaults.getValue(k).valueType match
            case NUMBER  => (v.valueType == NUMBER || v.valueType == STRING && text.toDoubleOption.isDefined, "a number")
            case BOOLEAN => (v.valueType == BOOLEAN || v.valueType == STRING && text.toBooleanOption.isDefined, "true or false")
            case LIST    => (v.valueType == LIST, "a list [ … ]")
            case STRING  => (v.valueType != LIST && v.valueType != OBJECT, "a string")
            case _       => (true, "")
          Option.unless(ok)(s"$path.$k must be $want, not ${v.render(org.ekrich.config.ConfigRenderOptions.concise)}")
      }

  /** The plugin's defaults resource, read through ITS class loader; absent = no defaults. */
  def defaults(p: Plugin): Config =
    ConfigFactory.parseResources(p.getClass.getClassLoader, p.defaultsResource,
      ConfigParseOptions.defaults.setAllowMissing(true))

  /** Decode a plugin's block with the plugin's own reader — the path-dependent type keeps the
    * decoded value tied to the plugin that will receive it, with no cast. */
  def decode(p: SourceFetcherPlugin[?], conf: Config): Either[String, p.Config] =
    p.configReader.from(conf.withoutPath("enabled").root).left.map(describe(p.info.id, _))

  /** Discover, check, decode and start every source-fetcher plugin on the classpath. */
  def load(user: Config, env: Config, host: HostServices): Either[List[String], List[Loaded]] =
    val found = ServiceLoader.load(classOf[SourceFetcherPlugin[?]]).iterator.asScala.toList
    check(found.map(_.info), Spi.Version) match
      case errs if errs.nonEmpty => Left(errs)
      case _ =>
        unknownBlocks(user, found.map(_.info.id).toSet)
          .foreach(id => host.log(s"warning: $Root.$id configures no installed plugin — ignored"))
        val results = found.map { p =>
          val defs = defaults(p)
          for
            // the defaults resolved on their own: `${?ENV}` lines leave the default value standing
            known    = scala.util.Try(defs.resolve()).getOrElse(defs)
            _       <- typeCheck(p.info.id, user, known) match
                         case Nil  => Right(())
                         case errs => Left(errs.mkString("; "))
            conf    <- block(user, defs, env, p.info.id)
            enabled  = conf.hasPath("enabled") && conf.getBoolean("enabled")
            typed   <- decode(p, conf)
            fetcher <- if enabled then p.create(typed, host).map(Some(_)).left.map(e => s"${p.info.id}: $e")
                       else Right(None)
          yield Loaded(p.info, enabled, fetcher)
        }
        val errs = results.collect { case Left(e) => e }
        if errs.nonEmpty then Left(errs) else Right(results.collect { case Right(l) => l })
