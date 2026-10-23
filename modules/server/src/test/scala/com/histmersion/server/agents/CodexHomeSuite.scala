package com.histmersion.server.agents

import com.histmersion.core.AppConfig

import java.nio.file.{Files, Path}
import java.nio.file.attribute.FileTime
import java.util.concurrent.{CountDownLatch, TimeUnit}

/** A retention pass over a TEMPORARY Codex home (#2582) — never the live one: old transcripts go, new
  * ones and the login stay, the SQLite files are vacuumed through the configured command, and nothing
  * happens while a run holds the home. */
class CodexHomeSuite extends munit.FunSuite:

  private val Day = 24L * 60 * 60 * 1000
  private val now = System.currentTimeMillis()
  private val cfg = AppConfig.load().agents.providers.codex

  private def home(): Path =
    val h = Files.createTempDirectory("histmersion-codex-home-")
    def file(rel: String, ageDays: Long, body: String = "x" * 100) =
      val p = h.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, body)
      Files.setLastModifiedTime(p, FileTime.fromMillis(now - ageDays * Day))
    file("sessions/2026/08/01/rollout-old.jsonl", 60)
    file("sessions/2026/10/22/rollout-new.jsonl", 1)
    file("archived_sessions/rollout-older.jsonl", 90)
    file("auth.json", 400, """{"tokens":"secret"}""")
    file("extractor.config.toml", 400)
    file("state_5.sqlite", 400)
    file("thread_history_1.sqlite", 400)
    h

  private def cleanup(h: Path): Unit =
    val s = Files.walk(h)
    try s.sorted(java.util.Comparator.reverseOrder()).forEach(p => { val _ = Files.deleteIfExists(p) }) finally s.close()

  test("a pass deletes old transcripts and their empty folders, vacuums the SQLite files, keeps the login"):
    val h = home()
    try
      val ran = scala.collection.mutable.ListBuffer.empty[List[String]]
      val r = CodexHome.retain(h, cfg, now, argv => { ran += argv; Right(0) })
      assertEquals(r.skipped, None)
      assertEquals((r.deleted, r.freedBytes), (2, 200L))
      assert(!Files.exists(h.resolve("sessions/2026/08")), "the emptied month folder is gone")
      assert(Files.exists(h.resolve("sessions/2026/10/22/rollout-new.jsonl")))
      assert(Files.isDirectory(h.resolve("archived_sessions")), "the transcript folders themselves stay")
      assertEquals(Files.readString(h.resolve("auth.json")), """{"tokens":"secret"}""")
      assert(Files.exists(h.resolve("extractor.config.toml")))
      assertEquals(ran.toList, List(
        List("sqlite3", h.resolve("state_5.sqlite").toString, "VACUUM;"),
        List("sqlite3", h.resolve("thread_history_1.sqlite").toString, "VACUUM;")))
      assertEquals(r.vacuumed.map(_._1), List("state_5.sqlite", "thread_history_1.sqlite"))
      assert(r.summary.startsWith("2 transcripts deleted"), r.summary)
    finally cleanup(h)

  test("while a run holds the home the pass does nothing; afterwards it runs"):
    val h = home()
    try
      val inRun = CountDownLatch(1)
      val done  = CountDownLatch(1)
      val t = Thread.ofVirtual().start { () =>
        CodexHome.using(h) { inRun.countDown(); val _ = done.await(10, TimeUnit.SECONDS) }
      }
      assert(inRun.await(10, TimeUnit.SECONDS))
      val busy = CodexHome.retain(h, cfg, now, _ => fail("no vacuum under a run"))
      assert(busy.skipped.exists(_.contains("using the home")), busy.toString)
      assert(Files.exists(h.resolve("sessions/2026/08/01/rollout-old.jsonl")))
      done.countDown(); t.join()
      assertEquals(CodexHome.retain(h, cfg, now, _ => Right(0)).deleted, 2)
    finally cleanup(h)

  test("keep-sessions-days 0 and vacuum off change nothing; a failing vacuum is reported, not thrown"):
    val h = home()
    try
      val off = CodexHome.retain(h, cfg.copy(keepSessionsDays = 0, vacuum = false), now, _ => fail("vacuum is off"))
      assertEquals((off.deleted, off.vacuumed), (0, Nil))
      val bad = CodexHome.retain(h, cfg.copy(keepSessionsDays = 0), now, _ => Left("sqlite3: not found"))
      assertEquals(bad.problems.size, 2, bad.toString)
      assert(CodexHome.retain(h.resolve("missing"), cfg, now).skipped.exists(_.contains("no Codex home")))
    finally cleanup(h)
