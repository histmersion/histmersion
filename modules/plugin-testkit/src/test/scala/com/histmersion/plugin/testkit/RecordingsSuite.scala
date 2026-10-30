package com.histmersion.plugin.testkit

import com.histmersion.plugin.testkit.internal.{ProjectPaths, RecordedExchange, Scrubber}

import java.nio.file.{Files, Path}

/** The recording format and how a test's mode is chosen. Pure but for a temporary directory. */
class RecordingsSuite extends munit.FunSuite:

  test("an exchange renders as readable lines and parses back; notes and blank lines are skipped"):
    val e = RecordedExchange(
      "GET",
      "https://x.example/a?q=1",
      Some(200),
      List("content-type" -> "application/json"),
      Some("001-x.example.json"),
      None
    )
    assertEquals(
      RecordedExchange.render(e),
      "GET https://x.example/a?q=1\nstatus: 200\ncontent-type: application/json\nbody: 001-x.example.json\n"
    )
    assertEquals(RecordedExchange.parse("# body trimmed to its first 2 KiB\n\n" + RecordedExchange.render(e)), Right(e))
    val failed = RecordedExchange("GET", "https://x.example/b", None, Nil, None, Some("x.example unreachable"))
    assertEquals(RecordedExchange.parse(RecordedExchange.render(failed)), Right(failed))
    assert(RecordedExchange.parse("GET https://x.example/c\nstatus: soon").isLeft)
    assert(RecordedExchange.parse("GET https://x.example/c\n").isLeft)
    assert(RecordedExchange.parse("nothing").isLeft)

  test("a body's file is named by its type; a URL's host by what a file name may hold"):
    assertEquals(
      List(
        Some("application/json; charset=utf-8"),
        Some("text/html"),
        Some("application/xml"),
        Some("application/pdf"),
        Some("image/jpeg"),
        Some("text/plain"),
        None
      ).map(RecordedExchange.extension),
      List("json", "html", "xml", "pdf", "jpeg", "txt", "bin")
    )
    assertEquals(RecordedExchange.hostOf("https://Library.OAPEN.org/x"), "library.oapen.org")
    assertEquals(RecordedExchange.hostOf("not a url"), "request")

  test("a secret's value never reaches a recording; short values are left alone"):
    val s = Scrubber(Set("tok-1234", "ab"))
    assertEquals(s("https://x.example/a?key=tok-1234&ab=1"), "https://x.example/a?key={secret}&ab=1")

  test("the mode comes from the environment, else from whether there are recordings"):
    val dir = Files.createTempDirectory("recordings")
    try
      assertEquals(HttpMode.fromEnv(dir, Map("HISTMERSION_PLUGIN_HTTP" -> "record")), Right(HttpMode.Record(dir)))
      assertEquals(HttpMode.fromEnv(dir, Map("HISTMERSION_PLUGIN_HTTP" -> "LIVE")), Right(HttpMode.Live))
      assert(HttpMode.fromEnv(dir, Map("HISTMERSION_PLUGIN_HTTP" -> "maybe")).isLeft)
      assert(HttpMode.fromEnv(dir, Map.empty).left.exists(_.contains("HISTMERSION_PLUGIN_LIVE=1")))
      assertEquals(HttpMode.fromEnv(dir, Map("HISTMERSION_PLUGIN_LIVE" -> "1")), Right(HttpMode.Live))
      Files.writeString(dir.resolve("001-x.example.http"), "GET https://x.example/\nstatus: 200\n")
      assertEquals(HttpMode.fromEnv(dir, Map("HISTMERSION_PLUGIN_LIVE" -> "1")), Right(HttpMode.Replay(dir)))
      assertEquals(
        HttpMode.fromEnv(Path.of("/nonexistent/recordings"), Map("HISTMERSION_PLUGIN_HTTP" -> "replay")),
        Right(HttpMode.Replay(Path.of("/nonexistent/recordings")))
      )
    finally Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))

  test("a relative recordings directory is the project's, wherever the build was started"):
    val relative = Path.of("recordings", "known-work")
    assertEquals(
      ProjectPaths.resolve(relative, Some("/work/my-plugin")),
      Path.of("/work/my-plugin/recordings/known-work")
    )
    assertEquals(
      ProjectPaths.resolve(Path.of("./recordings/../recordings/x"), Some("/work/p/")),
      Path.of("/work/p/recordings/x")
    )
    assertEquals(ProjectPaths.resolve(relative, None), relative.toAbsolutePath.normalize)
    assertEquals(ProjectPaths.resolve(relative, Some("  ")), relative.toAbsolutePath.normalize)
    assertEquals(ProjectPaths.resolve(Path.of("/elsewhere/rec"), Some("/work/p")), Path.of("/elsewhere/rec"))

  test("the reason to skip names the directory it looked in, resolved"):
    val why = HttpMode.fromEnv(Path.of("recordings", "never-recorded"), Map.empty)
    assert(why.left.exists(_.contains(Path.of("recordings", "never-recorded").toAbsolutePath.toString)), why)
