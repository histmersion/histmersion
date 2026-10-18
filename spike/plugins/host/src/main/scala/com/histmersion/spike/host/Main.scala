package com.histmersion.spike.host

import com.histmersion.plugin.*
import org.ekrich.config.{ConfigFactory, ConfigParseOptions}

/** `plugin-host <config.conf>`: loads the plugins on the classpath, decodes their blocks, starts the
  * enabled ones and asks each for one work. Prints what happened; exit 1 on a plugin error. */
object Main:
  def main(args: Array[String]): Unit =
    val runtime = if System.getProperty("org.graalvm.nativeimage.imagecode") != null then "native" else "jvm"
    val user = args.headOption.fold(ConfigFactory.empty)(p =>
      ConfigFactory.parseFile(java.io.File(p), ConfigParseOptions.defaults.setAllowMissing(false)))
    val env  = ConfigFactory.systemEnvironment()
    val host = new HostServices:
      val http = new PluginHttp:
        def get(url: String, headers: Map[String, String]) = Left("the spike host has no network")
      def log(m: String) = println(s"  [$runtime] $m")
    // do resources of the same name from several jars all reach parseResources (reference.conf)?
    val ref = ConfigFactory.parseResources("reference.conf")
    println(s"[$runtime] reference.conf: from-host=${ref.hasPath("spike-reference.from-host")} " +
      s"from-hello=${ref.hasPath("spike-reference.from-hello")}")
    PluginHost.load(user, env, host) match
      case Left(errs) =>
        errs.foreach(e => println(s"[$runtime] plugin error: $e")); sys.exit(1)
      case Right(ls) =>
        println(s"[$runtime] ${ls.size} source-fetcher plugin(s): " +
          ls.map(l => s"${l.info.id} ${l.info.version} (${if l.enabled then "enabled" else "disabled"})").mkString(", "))
        ls.flatMap(l => l.fetcher.map(l.info.id -> _)).foreach { (id, f) =>
          println(s"[$runtime] $id access=${f.access} fetch → ${f.fetch(WorkKey(Some("10.1234/x"), None, None, None))}")
        }
