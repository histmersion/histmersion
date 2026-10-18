// Spike A of docs/research-plugins.md (#2017): a plugin SPI discovered with java.util.ServiceLoader,
// each plugin's config decoded by the host with the plugin's OWN derived HOCON reader, on the JVM and
// in a native image. A standalone build: nothing here is part of the shipped Histmersion build.
//
//   api   — the SPI (what a plugin author compiles against): sconfig + kindlings-sconfig-derivation only
//   host  — loads plugins, cuts `histmersion.plugins.<id>` out of the config, decodes, starts
//   hello — a trivial source-fetcher plugin in its own jar (META-INF/services, defaults, metadata)
//
// The host does NOT depend on hello: hello reaches the host only through the classpath, as a
// user-installed jar would. `hostClasspath` writes the host's classpath; spike/plugins/run.sh does the rest.

ThisBuild / scalaVersion := "3.8.0"
ThisBuild / organization := "com.histmersion.spike"
ThisBuild / version      := "0.1.0"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-source:future")

val kindlings = "0.3.1"
val sconfig   = "2.0.0"

lazy val api = project
  .in(file("api"))
  .settings(
    name := "histmersion-plugin-api",
    libraryDependencies ++= Seq(
      "org.ekrich"   %% "sconfig"                      % sconfig,
      "com.kubuszok" %% "kindlings-sconfig-derivation" % kindlings
    )
  )

val hostClasspath = taskKey[Unit]("Write the host's runtime classpath to target/host-classpath.txt")

lazy val host = project
  .in(file("host"))
  .dependsOn(api)
  .settings(
    name := "plugin-host",
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test,
    hostClasspath := Def.uncached {
      val conv = fileConverter.value
      val cp   = (Runtime / fullClasspath).value.map(a => conv.toPath(a.data).toFile)
      val out  = (ThisBuild / baseDirectory).value / "target" / "host-classpath.txt"
      IO.write(out, cp.mkString(java.io.File.pathSeparator))
    }
  )

lazy val hello = project
  .in(file("hello"))
  .dependsOn(api % Provided) // the host provides the SPI; the plugin jar carries only itself
  .settings(name := "plugin-hello")

lazy val root = project.in(file(".")).aggregate(api, host, hello).settings(publish / skip := true)
