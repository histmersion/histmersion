package com.histmersion.cli

import java.nio.file.{Files, Path}

/** `histmersion plugin new`: the names it derives, the files it writes and what they say. Whether the
  * project compiles and its tests pass is checked with scala-cli outside the build, as a plugin author
  * runs it. */
class PluginTemplateSuite extends munit.FunSuite:

  private val api = ApiJars(Path.of("/opt/Histmersion App/plugin-api/histmersion-plugin-api.jar"),
    Some(Path.of("/opt/Histmersion App/plugin-api/histmersion-plugin-api-sources.jar")))

  private def request(id: String, kind: PluginKind = PluginKind.SourceFetcher, agentNotes: Boolean = false) =
    val pid = PluginId.parse(id).toOption.get
    PluginTemplate.Request(pid, kind, ScalaPackage.parse(pid.packageName).toOption.get, api, agentNotes)

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

  test("the project is two Scala files and the resources a plugin jar carries"):
    assertEquals(PluginTemplate.files(request("mirror")).map(_.path), List(
      "Mirror.scala",
      "Mirror.test.scala",
      "resources/META-INF/services/com.histmersion.plugin.Plugin",
      "resources/META-INF/histmersion/plugins/mirror.conf",
      "resources/META-INF/native-image/histmersion-plugin/mirror/reachability-metadata.json"))

  test("agent notes are written only when asked for"):
    val paths = PluginTemplate.files(request("mirror", agentNotes = true)).map(_.path)
    assert(paths.contains("AGENTS.md") && paths.contains("CLAUDE.md"), paths)
    val notes = file(PluginTemplate.files(request("mirror", agentNotes = true)), "AGENTS.md")
    assert(notes.contains("/opt/Histmersion App/plugin-api/histmersion-plugin-api-sources.jar"), notes)
    assert(notes.contains("histmersion.plugins.mirror { enabled = true }"), notes)
    assert(notes.contains("AGPL-3.0"), notes)
    assert(!notes.contains("@"), "a placeholder is left in AGENTS.md")

  test("the directives name the server's Scala, the API jars (quoted: the path has a space) and the resources"):
    val main = file(PluginTemplate.files(request("mirror")), "Mirror.scala")
    assert(main.startsWith(s"//> using scala ${PluginBuildVersions.scala}\n"), main)
    assert(main.contains("//> using jar \"/opt/Histmersion App/plugin-api/histmersion-plugin-api.jar\"\n"), main)
    assert(main.contains("//> using sourceJar \"/opt/Histmersion App/plugin-api/histmersion-plugin-api-sources.jar\"\n"), main)
    assert(main.contains(s"//> using dep com.kubuszok::kindlings-sconfig-derivation:${PluginBuildVersions.kindlings}\n"), main)
    assert(main.contains("//> using resourceDir resources\n"), main)
    assert(main.contains("package mirror\n"), main)

  test("without a sources jar there is no sourceJar directive"):
    val main = file(PluginTemplate.files(request("mirror").copy(api = api.copy(sources = None))), "Mirror.scala")
    assert(!main.contains("sourceJar"), main)

  test("the test file brings munit and checks the packaging"):
    val tests = file(PluginTemplate.files(request("mirror")), "Mirror.test.scala")
    assert(tests.startsWith(s"//> using test.dep org.scalameta::munit:${PluginBuildVersions.munit}\n"), tests)
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

  test("--api-jar takes the sources jar beside it; a missing jar is a usage error"):
    val root = Files.createTempDirectory("plugin-api")
    try
      val jar = Files.writeString(root.resolve("histmersion-plugin-api-1.0.0.jar"), "jar")
      val noInstall = Left(CliError("no install"))
      assertEquals(PluginAuthoring.apiJars(Some(jar), noInstall), Right(ApiJars(jar, None)))
      val src = Files.writeString(root.resolve("histmersion-plugin-api-1.0.0-sources.jar"), "src")
      assertEquals(PluginAuthoring.apiJars(Some(jar), noInstall), Right(ApiJars(jar, Some(src))))
      assert(PluginAuthoring.apiJars(Some(root.resolve("nope.jar")), noInstall).left.exists(_.exitCode == ExitCode.Usage))
    finally Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(p => { val _ = Files.deleteIfExists(p) })

  test("without --api-jar the install's plugin-api/ is used, and an older install says where to get one"):
    val root = Files.createTempDirectory("plugin-install")
    try
      val install = LocalInstall(root, root.resolve("data"))
      assert(PluginAuthoring.apiJars(None, Right(install)).left.exists(_.message.contains("predates plugin-api/")))
      Files.createDirectories(install.apiJar.getParent)
      Files.writeString(install.apiJar, "jar"); Files.writeString(install.apiSources, "src")
      assertEquals(PluginAuthoring.apiJars(None, Right(install)), Right(ApiJars(install.apiJar, Some(install.apiSources))))
    finally Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(p => { val _ = Files.deleteIfExists(p) })
