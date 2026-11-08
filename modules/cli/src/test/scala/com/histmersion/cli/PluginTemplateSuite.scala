package com.histmersion
package cli

import hearth.kindlings.optics.syntax.*

import java.nio.file.{Files, Path}

/** `histmersion plugin new`: the names it derives, the files it writes and what they say. Whether the project compiles
  * and its tests pass is checked with scala-cli outside the build, as a plugin author runs it.
  */
class PluginTemplateSuite extends munit.FunSuite:

  private val repo = TemplateRepository.MavenLocal
  private val remote =
    TemplateRepository.Remote(PluginRepository.parse("https://repo.example.org/m2/", _ => false).toOption.get)

  private def request(
      id: String,
      kind: PluginKind = PluginKind.SourceFetcher,
      agentNotes: PluginTemplate.Request.AgentNotes = PluginTemplate.Request.AgentNotes(false)
  ) =
    val pid = PluginId.parse(id).toOption.get
    PluginTemplate.Request(pid, kind, ScalaPackage.parse(pid.packageName.value).toOption.get, repo, agentNotes)

  private val api = s"${PluginBuildVersions.organization}::histmersion-plugin-api:${PluginBuildVersions.histmersion}"
  private val platform =
    s"${PluginBuildVersions.organization}::histmersion-plugin-platform:${PluginBuildVersions.histmersion}"
  private val testkit =
    s"${PluginBuildVersions.organization}::histmersion-plugin-testkit:${PluginBuildVersions.histmersion}"

  private def file(files: List[TemplateFile], path: TemplateFile.Path): String =
    files.find(_.path == path).map(_.content.value).getOrElse(fail(s"no $path among ${files.map(_.path)}"))

  test("a plugin id is kebab-case words that make a class name"):
    assertEquals(
      PluginId.parse("my-mirror").map(i => (i.typeName, i.packageName)),
      Right((PluginId.TypeName("MyMirror"), PluginId.PackageName("mymirror")))
    )
    assertEquals(PluginId.parse("oa2").map(_.typeName), Right(PluginId.TypeName("Oa2")))
    List("", "My-mirror", "2d-maps", "my--mirror", "my-mirror-", "my_mirror", "my.mirror").foreach(bad =>
      assert(PluginId.parse(bad).isLeft, bad)
    )

  test("a kind is named as the command line names it"):
    assertEquals(PluginKind.parse("event-catalogue"), Right(PluginKind.EventCatalogue))
    assert(PluginKind.parse("fetcher").left.exists(_.contains("source-fetcher, media-collection")))

  test("a package name is dotted lower-case identifiers"):
    assertEquals(ScalaPackage.parse("org.example.mirror").map(_.path), Right(ScalaPackage.Path("org/example/mirror")))
    assert(ScalaPackage.parse("Org.example").isLeft)
    assert(ScalaPackage.parse("org..example").isLeft)
    assertEquals(ScalaPackage.parse("Org"), Left("'Org' is not a package name ([a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*)"))
    assertEquals(
      PluginId.parse("My-Mirror"),
      Left(
        "'My-Mirror' is not a plugin id: lower-case letters and digits in words " +
          "joined by single dashes, starting with a letter ([a-z][a-z0-9]*(-[a-z0-9]+)*)"
      )
    )
    assertEquals(
      PluginRepository.parse("ftp://x", _ => false),
      Left(s"no such directory: ${java.nio.file.Path.of("ftp://x").toAbsolutePath.normalize}")
    )

  test("the plugin class is named after the id, without doubling 'Plugin'"):
    assertEquals(PluginTemplate.pluginClass(PluginId.parse("mirror").toOption.get), PluginTemplate.ClassName("MirrorPlugin"))
    assertEquals(PluginTemplate.pluginClass(PluginId.parse("my-plugin").toOption.get), PluginTemplate.ClassName("MyPlugin"))

  test("the project is its build, two Scala files, a README, a .gitignore and the resources a plugin jar carries"):
    assertEquals(
      PluginTemplate.files(request("mirror")).map(_.path.value),
      List(
        "project.scala",
        "Mirror.scala",
        "Mirror.test.scala",
        "README.md",
        ".gitignore",
        "resources/META-INF/services/com.histmersion.plugin.Plugin",
        "resources/META-INF/histmersion/plugins/mirror.conf",
        "resources/META-INF/native-image/mirror/mirror/reachability-metadata.json"
      )
    )

  test("the native-image metadata sits under the published group and artifact, as GraalVM's convention has it"):
    val files = PluginTemplate.files(request("my-mirror").modify(_.pkg).setTo(ScalaPackage.parse("org.example").toOption.get))
    assert(
      files.exists(_.path.value == "resources/META-INF/native-image/org.example/my-mirror/reachability-metadata.json"),
      files.map(_.path)
    )
    val build = file(files, TemplateFile.Path("project.scala"))
    assert(build.contains("//> using publish.organization org.example\n//> using publish.name my-mirror\n"), build)

  test("agent notes are written only when asked for"):
    val paths = PluginTemplate.files(request("mirror", agentNotes = PluginTemplate.Request.AgentNotes(true))).map(_.path.value)
    assert(paths.contains("AGENTS.md") && paths.contains("CLAUDE.md"), paths)
    val notes = file(
      PluginTemplate.files(request("mirror", agentNotes = PluginTemplate.Request.AgentNotes(true))),
      TemplateFile.Path("AGENTS.md")
    )
    assert(notes.contains("`m2Local`") && notes.contains("histmersion plugin repo --m2"), notes)
    assert(notes.contains("`project.scala`: the build") && notes.contains("test-resources/"), notes)
    assert(notes.contains(s"histmersion-plugin-api_3-${PluginBuildVersions.histmersion}-sources.jar"), notes)
    assert(notes.contains("histmersion plugin add mirror::mirror:0.1.0 --repo ./repo"), notes)
    assert(notes.contains("histmersion.plugins.mirror { enabled = true }"), notes)
    assert(!notes.contains("shade"), "plugins no longer shade their dependencies")
    assert(notes.contains("AGPL-3.0"), notes)
    assert(!notes.contains("@"), "a placeholder is left in AGENTS.md")

  test(
    "the build is project.scala: the server's Scala, m2Local, the API and platform compile-only, the resources, the publication"
  ):
    val files = PluginTemplate.files(request("mirror"))
    val build = file(files, TemplateFile.Path("project.scala"))
    assert(build.contains(s"\n//> using scala ${PluginBuildVersions.scala}\n"), build)
    assert(build.contains("\n//> using repository m2Local\n"), build)
    assert(build.contains(s"\n//> using compileOnly.dep $api\n//> using compileOnly.dep $platform\n"), build)
    assert(build.contains("\n//> using resourceDir resources\n"), build)
    assert(
      build.contains(
        "//> using publish.organization mirror\n//> using publish.name mirror\n//> using publish.version 0.1.0\n"
      ),
      build
    )
    assert(
      build.contains("\n//> using publish.doc false\n"),
      "no scaladoc jar: it warns on every link into the compile-only API"
    )
    assert(!build.contains("//> using jar") && !build.contains("//> using dep "), "no jar paths, no bundled library")
    assert(
      !build.contains("file:") && !build.contains("/Users/") && !build.contains("/home/"),
      "no path of this machine"
    )
    List("Mirror.scala", "Mirror.test.scala").foreach(f =>
      assert(
        !file(files, TemplateFile.Path(f)).contains("//> using"),
        s"$f carries directives: they all belong in project.scala"
      )
    )
    assert(file(files, TemplateFile.Path("Mirror.scala")).startsWith("package mirror\n"))

  test(
    "the tests' directives: munit, the platform and the testkit (which brings the API), fixtures, the project's directory"
  ):
    val build = file(PluginTemplate.files(request("mirror")), TemplateFile.Path("project.scala"))
    assert(
      build.contains(
        s"\n//> using test.dep org.scalameta::munit:${PluginBuildVersions.munit}\n//> using test.dep $platform\n//> using test.dep $testkit\n"
      ),
      build
    )
    assert(!build.contains(s"test.dep $api"), "the testkit depends on the API: it needs no line of its own")
    assert(build.contains("\n//> using test.resourceDir test-resources\n"), build)
    assert(build.contains("\n//> using test.javaProp histmersion.plugin.project=${.}\n"), build)
    assertEquals(build.linesIterator.count(_.contains(s":${PluginBuildVersions.histmersion}")), 4)
    assert(build.contains("four lines"), "the build says where the Histmersion version is repeated")

  test("a repository named with --repo is written as given"):
    val build =
      file(PluginTemplate.files(request("mirror").modify(_.repository).setTo(remote)), TemplateFile.Path("project.scala"))
    assert(build.contains("\n//> using repository https://repo.example.org/m2/\n") && !build.contains("m2Local"), build)

  test("the test file checks the plugin as the server loads it, and the packaging"):
    val tests = file(PluginTemplate.files(request("mirror")), TemplateFile.Path("Mirror.test.scala"))
    assert(tests.contains("class MirrorSuite extends munit.FunSuite"), tests)
    assert(tests.contains("class MirrorPackagingSuite extends munit.FunSuite"), tests)
    assert(tests.contains("PluginHarness.load(\"mirror\""), "the tests load the plugin as the server does")
    assert(
      !tests.contains("extends Plugin.Context") && !tests.contains("extends Plugin.Http"),
      "no hand-made host in the tests"
    )

  test("a fetcher's live check is gated and replays its recordings; the README says how to record them"):
    val files = PluginTemplate.files(request("mirror"))
    val tests = file(files, TemplateFile.Path("Mirror.test.scala"))
    assert(tests.contains("class MirrorLiveSuite extends munit.FunSuite"), tests)
    assert(
      tests.contains("HttpMode.fromEnv(Path.of(\"recordings\", \"known-work\"))") && tests.contains(
        "assume(mode.isRight"
      ),
      tests
    )
    assert(
      tests.contains("mode.left.foreach(why => println(s\"SKIPPED ${getClass.getSimpleName}: $why\"))"),
      "a skip is printed with its reason: munit shows only 'skipped'"
    )
    assert(tests.contains("harness.fetch(work, saveTo = Some(Path.of(\"target\", \"fetched\")))"), tests)
    val readme = file(files, TemplateFile.Path("README.md"))
    assert(
      readme.contains("HISTMERSION_PLUGIN_HTTP=record scala-cli test .") && readme.contains("Trim before committing"),
      readme
    )
    assert(readme.contains("wherever `scala-cli test` runs from"), readme)
    assert(readme.contains("`test-resources/`") && readme.contains("`project.scala` | the build"), readme)

  test("the README says how to install the plugin from its source, update it and remove it"):
    val readme = file(PluginTemplate.files(request("my-mirror")), TemplateFile.Path("README.md"))
    List(
      "1. **Build and test**",
      "scala-cli test .",
      "scala-cli --power publish . --publish-repository ./repo",
      "histmersion plugin add mymirror::my-mirror:0.1.0 --repo ./repo",
      "`--yes`",
      "histmersion.plugins.my-mirror.enabled = true",
      "histmersion agent autopilot drain",
      "DRAINED",
      "histmersion plugin rebuild",
      "--jvm",
      "histmersion agent autopilot undrain",
      "13.5 GB",
      "histmersion plugin status",
      "**Updating.**",
      "histmersion plugin rm my-mirror --rebuild",
      "`enabled = false`",
      "GraalVM CE 25.4",
      "25.4.4.1+1-graalce",
      "histmersion plugin repo --m2"
    ).foreach(step => assert(readme.contains(step), s"the README lacks: $step"))
    assert(
      !file(PluginTemplate.files(request("keys", PluginKind.SecretVault)), TemplateFile.Path("Keys.test.scala"))
        .contains("LiveSuite")
    )

  test("the services file names the plugin class in its package"):
    val files = PluginTemplate.files(request("my-mirror").modify(_.pkg).setTo(ScalaPackage.parse("org.example").toOption.get))
    assertEquals(file(files, TemplateFile.Path(PluginTemplate.ServicesPath)), "org.example.MyMirrorPlugin\n")
    assert(file(files, TemplateFile.Path("MyMirror.scala")).contains("package org.example\n"))

  PluginKind.values.foreach { kind =>
    test(s"${kind.name}: every placeholder is filled, and the plugin registers under its id"):
      val files = PluginTemplate.files(request("my-thing", kind))
      files.foreach(f => assert(!"@[A-Za-z]+@".r.findFirstIn(f.content.value).isDefined, s"${f.path} keeps a placeholder"))
      val main = file(files, TemplateFile.Path("MyThing.scala"))
      assert(main.contains("final class MyThingPlugin extends Plugin:"), main)
      assert(main.contains("Plugin.Info(\"my-thing\", \"0.1.0\", Spi.Version)"), main)
      assert(main.contains(s"final class MyThing${kind.classSuffix}"), main)
      assert(
        file(files, PluginTemplate.defaultsPath(PluginId.parse("my-thing").toOption.get)).contains("enabled = false\n")
      )

    test(s"${kind.name}: the native metadata registers the lazy vals of the generated classes and the defaults"):
      val metadata = file(
        PluginTemplate.files(request("my-thing", kind)),
        PluginTemplate.nativeMetadataPath(
          PluginId.parse("my-thing").toOption.get,
          ScalaPackage.parse("mything").toOption.get
        )
      )
      assert(
        metadata.contains("""{ "type": "mything.MyThingPlugin", "fields": [ { "name": "configReader$lzy1" } ] }"""),
        metadata
      )
      assert(
        metadata.contains("""{ "type": "mything.MyThingConfig$", "fields": [ { "name": "derived$ConfigReader$lzy"""),
        metadata
      )
      assert(metadata.contains("""{ "glob": "META-INF/histmersion/plugins/my-thing.conf" }"""), metadata)
  }

  test("nested settings types are registered too, numbered in the order the file declares them"):
    val metadata = file(
      PluginTemplate.files(request("notes", PluginKind.EventCatalogue)),
      PluginTemplate.nativeMetadataPath(PluginId.parse("notes").toOption.get, ScalaPackage.parse("notes").toOption.get)
    )
    assert(
      metadata.contains("""{ "type": "notes.ListedEvent$", "fields": [ { "name": "derived$ConfigReader$lzy1" } ] }"""),
      metadata
    )
    assert(
      metadata.contains("""{ "type": "notes.NotesConfig$", "fields": [ { "name": "derived$ConfigReader$lzy2" } ] }"""),
      metadata
    )

  test("a project is written into an empty or new directory, never over files"):
    val root = Files.createTempDirectory("plugin-new")
    try
      val files = PluginTemplate.files(request("mirror"))
      val written = PluginAuthoring.write(root.resolve("mirror"), files)
      assertEquals(written.map(_.size), Right(files.size))
      assert(Files.isRegularFile(root.resolve("mirror/resources/META-INF/services/com.histmersion.plugin.Plugin")))
      assert(PluginAuthoring.write(root.resolve("mirror"), files).left.exists(_.message.value.contains("not empty")))
    finally
      Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach { p =>
        val _ = Files.deleteIfExists(p)
      }

  test("a repository: a URL as given, a directory as its file: URL, a missing directory refused"):
    assertEquals(
      PluginRepository.parse("https://repo.example.org/m2", _ => false).map(r => r.value: String),
      Right("https://repo.example.org/m2")
    )
    val root = Files.createTempDirectory("plugin-repo")
    try
      assertEquals(
        PluginRepository.parse(root.toString, Files.isDirectory(_)).map(r => r.value: String),
        Right(root.toUri.toString)
      )
      assert(
        PluginRepository
          .parse(root.resolve("nope").toString, Files.isDirectory(_))
          .left
          .exists(_.contains("no such directory"))
      )
    finally
      Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach { p =>
        val _ = Files.deleteIfExists(p)
      }

  test(
    "without --repo the install's maven/ is copied into the local Maven repository and m2Local is used; an older install says where to get one"
  ):
    val root = Files.createTempDirectory("plugin-install")
    try
      val install = LocalInstall(root, root.resolve("data"))
      val m2 = root.resolve("home/.m2/repository")
      val old = PluginAuthoring.repository(None, Right(install), Right(m2))
      assert(
        old.left.exists(e => e.message.value.contains("predates plugin dependencies") && e.exitCode == ExitCode.Usage),
        old
      )
      Files.createDirectories(install.apiPom.getParent)
      Files.writeString(install.apiPom, "<project/>"); Files.writeString(install.apiJar, "jar");
      Files.writeString(install.apiSources, "src")
      val first = PluginAuthoring.repository(None, Right(install), Right(m2))
      assertEquals(first.map(_._1), Right(TemplateRepository.MavenLocal))
      assertEquals(first.map(_._2.size), Right(3))
      assertEquals(
        Files.readString(m2.resolve(PluginArtifacts.path(PluginArtifacts.PathName(PluginArtifacts.Api), ".jar").value)),
        "jar"
      )
      assertEquals(
        PluginAuthoring.publishToMavenLocal(Right(install), m2),
        Right(Nil),
        "nothing changed: nothing copied"
      )
      Files.writeString(install.apiJar, "a rebuilt SNAPSHOT")
      assertEquals(
        PluginAuthoring.publishToMavenLocal(Right(install), m2).map(_.map(_.getFileName.toString)),
        Right(List(install.apiJar.getFileName.toString))
      )
      assertEquals(
        Files.readString(m2.resolve(PluginArtifacts.path(PluginArtifacts.PathName(PluginArtifacts.Api), ".jar").value)),
        "a rebuilt SNAPSHOT"
      )
      assertEquals(
        PluginAuthoring
          .repository(Some("https://example.org/m2"), Left(Cli.Error(Cli.Error.Message("no install"))), Left(Cli.Error(Cli.Error.Message("no HOME"))))
          .map(_._1),
        Right(TemplateRepository.Remote(PluginRepository.parse("https://example.org/m2", _ => false).toOption.get))
      )
      assertEquals(PluginAuthoring.apiJars(Right(install)), Right((install.apiJar, install.apiSources)))
      assert(
        install.apiJar.toString.endsWith(
          s"maven/com/histmersion/histmersion-plugin-api_3/${PluginBuildVersions.histmersion}/" +
            s"histmersion-plugin-api_3-${PluginBuildVersions.histmersion}.jar"
        ),
        install.apiJar
      )
    finally
      Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach { p =>
        val _ = Files.deleteIfExists(p)
      }

  test("the local Maven repository is under HOME"):
    assertEquals(
      PluginAuthoring.mavenLocal(
        Map(LocalInstall.EnvVariable("HOME") -> LocalInstall.EnvSetting("/home/someone")).get
      ),
      Right(Path.of("/home/someone/.m2/repository"))
    )
    assert(PluginAuthoring.mavenLocal(Map.empty[LocalInstall.EnvVariable, LocalInstall.EnvSetting].get).isLeft)

  test("next steps: test, publish with the POM, add by coordinates from the publish repository"):
    val id = PluginId.parse("my-mirror").toOption.get
    val steps =
      PluginAuthoring.nextSteps(Path.of("/w/my-mirror"), id, ScalaPackage.parse("org.example").toOption.get, Nil)
    assert(
      steps.contains(
        "  scala-cli --power publish . --publish-repository ./repo   # the jar and its POM, which lists its dependencies"
      ),
      steps
    )
    assert(steps.contains("  histmersion plugin add org.example::my-mirror:0.1.0 --repo ./repo"), steps)
    assert(steps.exists(_.contains("the build is project.scala")), steps)
