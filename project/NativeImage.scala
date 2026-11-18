import sbt.*
import sbt.given
import sbt.Keys.*
import sbt.librarymanagement.LibraryManagementCodec.given
import sbt.util.CacheImplicits.given

/** A GraalVM native image of a module (no sbt-2.0 native-image plugin exists): `nativeImage` shells out to the pinned
  * GraalVM's `native-image` over the module's Sloth-patched class path, with the module's `nativeImageOptions`, into
  * `target/<nativeImageName>`. Reachability metadata, captured by the tracing agent, lives in the module's
  * src/main/resources/META-INF/native-image/.
  *
  * Enabled per module (`enablePlugins(NativeImage)`), which also forks `run`/`runMain` under the tracing agent and adds
  * `writeClasspath`. The module sets `Compile / mainClass` and `nativeImageName` inline.
  */
object NativeImage extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  object autoImport {
    val nativeImage = taskKey[Unit]("Build a GraalVM native image for this module")
    val nativeImageName = settingKey[String]("The native binary's file name, in the module's target/")
    val nativeImageOptions = settingKey[Seq[String]]("This module's own native-image flags")
    val writeClasspath = taskKey[Unit]("Write the module classpath to target/classpath.txt")
  }
  import autoImport.*

  override def projectSettings: Seq[Setting[?]] = Seq(
    nativeImageOptions := Nil,
    // UNCACHED: sbt 2 caches task results by default, but the binary this writes is not part of the
    // result — a restored CI cache reported the task done and no binary existed (bundle.yml, 2026-09-25)
    nativeImage := Def.uncached {
      val log = streams.value.log
      val conv = fileConverter.value // sbt 2.0: classpath entries are virtual file refs
      // Runtime's class path: the server's first-party plugins are its runtime dependencies, and % Provided jars
      // (chimney-iron) stay off it
      val entries = (Runtime / fullClasspath).value.map(a => conv.toPath(a.data).toFile)
      val cp = Sloth
        .patch(
          log,
          (ThisBuild / Sloth.autoImport.slothJar).value,
          entries,
          target.value / "sloth",
          (ThisBuild / baseDirectory).value / "target" / "sloth-store"
        )
        .mkString(java.io.File.pathSeparator)
      val mainCls = (Compile / mainClass).value.getOrElse(sys.error("nativeImage: no Compile / mainClass"))
      val out = target.value / nativeImageName.value
      // a killed agent run leaves agent-pid*/ scratch dirs in the merge dir; never let them in
      IO.delete(((Compile / resourceDirectory).value / "META-INF" / "native-image" * "agent-pid*").get())
      checkNoLegacyConfigs((Compile / resourceDirectory).value / "META-INF" / "native-image")
      val ni = (pinnedGraalVmHome() / "bin" / "native-image").getAbsolutePath
      log.info(s"native-image → $out")
      // HISTMERSION_NATIVE_XMX: a cap on the builder's heap (a JVM size, e.g. 12g; default none — the server
      // image needs more than 9 GB of heap, and a smaller cap only fails with OutOfMemoryError)
      val xmx = sys.env.get("HISTMERSION_NATIVE_XMX").map(_.trim).filter(_.nonEmpty).map(x => s"-J-Xmx$x")
      // HISTMERSION_NATIVE_PARALLELISM: the builder's threads (default: every core); fewer threads hold less at once
      val par =
        sys.env.get("HISTMERSION_NATIVE_PARALLELISM").map(_.trim).filter(_.nonEmpty).map(p => s"--parallelism=$p")
      val cmd = Seq(ni) ++ xmx ++ par ++ nativeImageOptions.value ++ Seq("-cp", cp, mainCls, out.getAbsolutePath)
      val rc = scala.sys.process.Process(cmd).!
      if rc != 0 then sys.error(s"native-image failed (exit $rc)")
      log.info(s"native image built → $out")
    },
    // the module's Runtime classpath in target/classpath.txt, so the app can be run under the tracing agent
    // directly (no sbt classes polluting the collected metadata). UNCACHED for the reason nativeImage is: the file
    // is not the task's result, so a cache hit would skip writing it (sbt 2.1 warns that `target` is no cache input)
    writeClasspath := Def.uncached {
      val conv = fileConverter.value
      val cp = (Runtime / fullClasspath).value
        .map(a => conv.toPath(a.data).toFile)
        .mkString(java.io.File.pathSeparator)
      val f = target.value / "classpath.txt"
      IO.write(f, cp)
      streams.value.log.info(s"classpath → $f")
    },
    // `run`/`runMain` fork under the tracing agent, MERGING what it captures into the module's
    // META-INF/native-image: exercising the app keeps the metadata current. Only the pinned JDK's agent
    // writes the one reachability-metadata.json; others write other formats, and the JDK-25 agent EMPTIES a
    // legacy resource-config.json it finds there. Resources are recorded as they are requested, so exercise
    // every web asset and boot once against an empty database.
    Compile / run / fork := true,
    Compile / runMain / fork := true,
    Compile / run / javaOptions += agentOption((Compile / resourceDirectory).value),
    Compile / runMain / javaOptions += agentOption((Compile / resourceDirectory).value)
  )

  private def agentOption(resourceDir: File): String =
    pinnedGraalVmHome()
    val dir = resourceDir / "META-INF" / "native-image"
    IO.createDirectory(dir)
    s"-agentlib:native-image-agent=config-merge-dir=${dir.getAbsolutePath}"

  /** The GraalVM CE version scripts/bundle/graalvm-pins.txt pins: the JAVA_VERSION its `release` file declares. */
  lazy val pinnedGraalVm: String =
    IO.readLines(file("scripts") / "bundle" / "graalvm-pins.txt")
      .map(_.trim)
      .find(line => line.nonEmpty && !line.startsWith("#"))
      .map(_.split("\\s+").head)
      .getOrElse(sys.error("scripts/bundle/graalvm-pins.txt pins no GraalVM version"))

  /** The JDK sbt runs on, which must be the pinned GraalVM CE with native-image — the one .sdkmanrc activates. */
  def pinnedGraalVmHome(): File = {
    val home = file(sys.props("java.home"))
    val release = home / "release"
    val version =
      if release.exists then
        IO.readLines(release).collectFirst { case s"""JAVA_VERSION="$v"""" => v }.getOrElse("unknown")
      else "unknown"
    if version != pinnedGraalVm || !(home / "bin" / "native-image").exists then
      sys.error(
        s"sbt runs on JDK $version ($home), not GraalVM CE $pinnedGraalVm with native-image (scripts/bundle/graalvm-pins.txt).\n" +
          "  In the checkout run `sdk env install && sdk env`, then start sbt again."
      )
    home
  }

  /** The pre-JDK-25 split metadata files. The pinned agent never writes them; one beside reachability-metadata.json
    * means the app was run under an older JDK's agent, and with both formats present the JDK-25 agent empties
    * resource-config.json, silently dropping every resource. The build refuses rather than ship a mixed image (a
    * .gitignore would only hide them; native-image still reads them).
    */
  val legacyConfigs = Seq(
    "jni-config.json",
    "predefined-classes-config.json",
    "proxy-config.json",
    "reflect-config.json",
    "resource-config.json",
    "serialization-config.json"
  )

  def checkNoLegacyConfigs(dir: File): Unit = {
    val found = legacyConfigs.map(dir / _).filter(_.exists)
    if found.nonEmpty then
      sys.error(
        s"legacy native-image metadata beside reachability-metadata.json: ${found.map(_.getName).mkString(", ")} in $dir\n" +
          "  these come from a tracing-agent run under a JDK other than the one pinned in scripts/bundle/graalvm-pins.txt.\n" +
          "  Delete them, then regenerate with the pinned JDK (CLAUDE.md, 'The tracing-agent rule')."
      )
  }
}
