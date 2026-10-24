package com.histmersion.cli

import java.nio.file.{Files, Path}

/** `histmersion plugin new`: the names it derives, the files it writes and what they say. Whether the
  * project compiles and its tests pass is checked with scala-cli outside the build, as a plugin author
  * runs it. */
class PluginTemplateSuite extends munit.FunSuite:

  private val repo = PluginRepository.parse("file:///opt/Histmersion%20App/maven/", _ => false).toOption.get

  private def request(id: String, kind: PluginKind = PluginKind.SourceFetcher, agentNotes: Boolean = false) =
    val pid = PluginId.parse(id).toOption.get
    PluginTemplate.Request(pid, kind, ScalaPackage.parse(pid.packageName).toOption.get, repo, agentNotes)

  private val api      = s"${PluginBuildVersions.organization}::histmersion-plugin-api:${PluginBuildVersions.histmersion}"
  private val platform = s"${PluginBuildVersions.organization}::histmersion-plugin-platform:${PluginBuildVersions.histmersion}"

  private def file(files: List[TemplateFile], path: String): String =
    files.find(_.path == path).map(_.content).getOrElse(fail(s"no $path among ${files.map(_.path)}"))

  test("a plugin id is kebab-case words that make a class name"):
    assertEquals(PluginId.parse("my-mirror").map(i => (i.typeName, i.packageName)), Right(("MyMirror", "mymirror")))
    assertEquals(PluginId.parse("oa2").map(_.typeName), Right("Oa2"))
    List("", "My-mirror", "2d-maps", "my--mirror", "my-mirror-", "my_mirror", "my.mirror").foreach(bad =>
      assert(PluginId.parse(bad).isLeft, bad))

  test("a kind is named as the command line names it"):
    assertEquals(PluginKind.parse("event-catalogue"), Right(PluginKind.EventCatalogue))
    assert(PluginKind.parse("fetcher").left.exists(_.contains("source-fetcher, media-collection")))

  test("a package name is dotted lower-case identifiers"):
    assertEquals(ScalaPackage.parse("org.example.mirror").map(_.path), Right("org/example/mirror"))
    assert(ScalaPackage.parse("Org.example").isLeft)
    assert(ScalaPackage.parse("org..example").isLeft)

  test("the plugin class is named after the id, without doubling 'Plugin'"):
    assertEquals(PluginTemplate.pluginClass(PluginId.parse("mirror").toOption.get), "MirrorPlugin")
    assertEquals(PluginTemplate.pluginClass(PluginId.parse("my-plugin").toOption.get), "MyPlugin")

  test("the project is two Scala files, a .gitignore and the resources a plugin jar carries"):
    assertEquals(PluginTemplate.files(request("mirror")).map(_.path), List(
      "Mirror.scala",
      "Mirror.test.scala",
      ".gitignore",
      "resources/META-INF/services/com.histmersion.plugin.Plugin",
      "resources/META-INF/histmersion/plugins/mirror.conf",
      "resources/META-INF/native-image/histmersion-plugin/mirror/reachability-metadata.json"))

  test("agent notes are written only when asked for"):
    val paths = PluginTemplate.files(request("mirror", agentNotes = true)).map(_.path)
    assert(paths.contains("AGENTS.md") && paths.contains("CLAUDE.md"), paths)
    val notes = file(PluginTemplate.files(request("mirror", agentNotes = true)), "AGENTS.md")
    assert(notes.contains("file:///opt/Histmersion%20App/maven/"), notes)
    assert(notes.contains(s"histmersion-plugin-api_3-${PluginBuildVersions.histmersion}-sources.jar"), notes)
    assert(notes.contains("histmersion plugin add mirror::mirror:0.1.0 --repo ./repo"), notes)
    assert(notes.contains("histmersion.plugins.mirror { enabled = true }"), notes)
    assert(!notes.contains("shade"), "plugins no longer shade their dependencies")
    assert(notes.contains("AGPL-3.0"), notes)
    assert(!notes.contains("@"), "a placeholder is left in AGENTS.md")

  test("the directives: the server's Scala, the repository, the API and platform compile-only, the resources, the publication"):
    val main = file(PluginTemplate.files(request("mirror")), "Mirror.scala")
    assert(main.startsWith(s"//> using scala ${PluginBuildVersions.scala}\n"), main)
    assert(main.contains("//> using repository file:///opt/Histmersion%20App/maven/\n"), main)
    assert(main.contains(s"//> using compileOnly.dep $api\n"), main)
    assert(main.contains(s"//> using compileOnly.dep $platform\n"), main)
    assert(main.contains("//> using resourceDir resources\n"), main)
    assert(main.contains("//> using publish.organization mirror\n//> using publish.name mirror\n//> using publish.version 0.1.0\n"), main)
    assert(!main.contains("//> using jar") && !main.contains("//> using dep "), "no jar paths, no bundled library")
    assert(main.contains("package mirror\n"), main)

  test("the test file brings munit, the API and the platform at run time, and checks the packaging"):
    val tests = file(PluginTemplate.files(request("mirror")), "Mirror.test.scala")
    assert(tests.startsWith(s"//> using test.dep org.scalameta::munit:${PluginBuildVersions.munit}\n"), tests)
    assert(tests.contains(s"//> using test.dep $api\n//> using test.dep $platform\n"), tests)
    assert(tests.contains("class MirrorSuite extends munit.FunSuite"), tests)
    assert(tests.contains("class MirrorPackagingSuite extends munit.FunSuite"), tests)

  test("the services file names the plugin class in its package"):
    val files = PluginTemplate.files(request("my-mirror").copy(pkg = ScalaPackage.parse("org.example").toOption.get))
    assertEquals(file(files, PluginTemplate.ServicesPath), "org.example.MyMirrorPlugin\n")
    assert(file(files, "MyMirror.scala").contains("package org.example\n"))

  PluginKind.values.foreach { kind =>
    test(s"${kind.name}: every placeholder is filled, and the plugin registers under its id"):
      val files = PluginTemplate.files(request("my-thing", kind))
      files.foreach(f => assert(!"@[A-Za-z]+@".r.findFirstIn(f.content).isDefined, s"${f.path} keeps a placeholder"))
      val main = file(files, "MyThing.scala")
      assert(main.contains("final class MyThingPlugin extends Plugin:"), main)
      assert(main.contains("PluginInfo(\"my-thing\", \"0.1.0\", Spi.Version)"), main)
      assert(main.contains(s"final class MyThing${kind.classSuffix}"), main)
      assert(file(files, PluginTemplate.defaultsPath(PluginId.parse("my-thing").toOption.get)).contains("enabled = false\n"))

    test(s"${kind.name}: the native metadata registers the lazy vals of the generated classes and the defaults"):
      val metadata = file(PluginTemplate.files(request("my-thing", kind)), PluginTemplate.nativeMetadataPath(PluginId.parse("my-thing").toOption.get))
      assert(metadata.contains("""{ "type": "mything.MyThingPlugin", "fields": [ { "name": "configReader$lzy1" } ] }"""), metadata)
      assert(metadata.contains("""{ "type": "mything.MyThingConfig$", "fields": [ { "name": "derived$ConfigReader$lzy"""), metadata)
      assert(metadata.contains("""{ "glob": "META-INF/histmersion/plugins/my-thing.conf" }"""), metadata)
  }

  test("nested settings types are registered too, numbered in the order the file declares them"):
    val metadata = file(PluginTemplate.files(request("notes", PluginKind.EventCatalogue)),
      PluginTemplate.nativeMetadataPath(PluginId.parse("notes").toOption.get))
    assert(metadata.contains("""{ "type": "notes.ListedEvent$", "fields": [ { "name": "derived$ConfigReader$lzy1" } ] }"""), metadata)
    assert(metadata.contains("""{ "type": "notes.NotesConfig$", "fields": [ { "name": "derived$ConfigReader$lzy2" } ] }"""), metadata)

  test("a project is written into an empty or new directory, never over files"):
    val root = Files.createTempDirectory("plugin-new")
    try
      val files = PluginTemplate.files(request("mirror"))
      val written = PluginAuthoring.write(root.resolve("mirror"), files)
      assertEquals(written.map(_.size), Right(files.size))
      assert(Files.isRegularFile(root.resolve("mirror/resources/META-INF/services/com.histmersion.plugin.Plugin")))
      assert(PluginAuthoring.write(root.resolve("mirror"), files).left.exists(_.message.contains("not empty")))
    finally Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(p => { val _ = Files.deleteIfExists(p) })

  test("a repository: a URL as given, a directory as its file: URL, a missing directory refused"):
    assertEquals(PluginRepository.parse("https://repo.example.org/m2", _ => false).map(_.url), Right("https://repo.example.org/m2"))
    val root = Files.createTempDirectory("plugin-repo")
    try
      assertEquals(PluginRepository.parse(root.toString, Files.isDirectory(_)).map(_.url), Right(root.toUri.toString))
      assert(PluginRepository.parse(root.resolve("nope").toString, Files.isDirectory(_)).left.exists(_.contains("no such directory")))
    finally Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(p => { val _ = Files.deleteIfExists(p) })

  test("without --repo the install's maven/ is used, and an older install says where to get one"):
    val root = Files.createTempDirectory("plugin-install")
    try
      val install = LocalInstall(root, root.resolve("data"))
      val old = PluginAuthoring.repository(None, Right(install))
      assert(old.left.exists(e => e.message.contains("predates plugin dependencies") && e.exitCode == ExitCode.Usage), old)
      Files.createDirectories(install.apiPom.getParent)
      Files.writeString(install.apiPom, "<project/>"); Files.writeString(install.apiJar, "jar"); Files.writeString(install.apiSources, "src")
      assertEquals(PluginAuthoring.repository(None, Right(install)).map(_.url), Right(install.mavenRepo.toUri.toString))
      assertEquals(PluginAuthoring.repository(Some("https://example.org/m2"), Left(CliError("no install"))).map(_.url),
        Right("https://example.org/m2"))
      assertEquals(PluginAuthoring.apiJars(Right(install)), Right((install.apiJar, install.apiSources)))
      assert(install.apiJar.toString.endsWith(s"maven/com/kubuszok/histmersion-plugin-api_3/${PluginBuildVersions.histmersion}/" +
        s"histmersion-plugin-api_3-${PluginBuildVersions.histmersion}.jar"), install.apiJar)
    finally Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(p => { val _ = Files.deleteIfExists(p) })

  test("next steps: test, publish with the POM, add by coordinates from the publish repository"):
    val id    = PluginId.parse("my-mirror").toOption.get
    val steps = PluginAuthoring.nextSteps(Path.of("/w/my-mirror"), id, ScalaPackage.parse("org.example").toOption.get, Nil)
    assert(steps.contains("  scala-cli --power publish . --publish-repository ./repo   # the jar and its POM, which lists its dependencies"), steps)
    assert(steps.contains("  histmersion plugin add org.example::my-mirror:0.1.0 --repo ./repo"), steps)
