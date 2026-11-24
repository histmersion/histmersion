package com.histmersion
package server.agents
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.AppConfig
import com.histmersion.domain.CodexRetention

import java.nio.file.{Files, Path}
import java.nio.file.attribute.FileTime
import java.util.concurrent.{CountDownLatch, TimeUnit}
import com.histmersion.config.CodexConfig
import com.histmersion.lint.{LintRule, tolerated}

/** A retention pass over a TEMPORARY Codex home (#2582) — never the live one: old transcripts go, new ones and the
  * login stay, the SQLite files are vacuumed through the configured command, and nothing happens while a run holds the
  * home.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class CodexHomeSuite extends munit.FunSuite:

  private val Day = 24L * 60 * 60 * 1000
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val now = CodexRetention.NowMs(System.currentTimeMillis())
  private val cfg = AppConfig.load().agents.providers.codex

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def home(): Path =
    val h = Files.createTempDirectory("histmersion-codex-home-")
    def file(rel: String, ageDays: Long, body: String = "x" * 100) =
      val p = h.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, body)
      Files.setLastModifiedTime(p, FileTime.fromMillis(now.value - ageDays * Day))
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
    try
      s.sorted(java.util.Comparator.reverseOrder()).forEach { p =>
        val _ = Files.deleteIfExists(p)
      }
    finally s.close()

  test("a pass deletes old transcripts and their empty folders, vacuums the SQLite files, keeps the login"):
    val h = home()
    try
      val ran = scala.collection.mutable.ListBuffer.empty[List[Codex.Home.Argument]]
      val r = Codex.Home.retain(h, cfg, now, argv => { ran += argv; Right(Codex.Home.ExitCode(0)) })
      assertEquals(r.skipped, None)
      assertEquals((r.deleted, r.freedBytes), (2, 200L))
      assert(!Files.exists(h.resolve("sessions/2026/08")), "the emptied month folder is gone")
      assert(Files.exists(h.resolve("sessions/2026/10/22/rollout-new.jsonl")))
      assert(Files.isDirectory(h.resolve("archived_sessions")), "the transcript folders themselves stay")
      assertEquals(Files.readString(h.resolve("auth.json")), """{"tokens":"secret"}""")
      assert(Files.exists(h.resolve("extractor.config.toml")))
      assertEquals(
        ran.toList,
        List(
          List("sqlite3", h.resolve("state_5.sqlite").toString, "VACUUM;"),
          List("sqlite3", h.resolve("thread_history_1.sqlite").toString, "VACUUM;")
        )
      )
      assertEquals(r.vacuumed.map(_._1), List("state_5.sqlite", "thread_history_1.sqlite"))
      assert(r.summary.value.startsWith("2 transcripts deleted"), r.summary)
    finally cleanup(h)

  test("while a run holds the home the pass does nothing; afterwards it runs"):
    val h = home()
    try
      val inRun = CountDownLatch(1)
      val done = CountDownLatch(1)
      val t = Thread.ofVirtual().start { () =>
        Codex.Home.using(h) { inRun.countDown(); val _ = done.await(10, TimeUnit.SECONDS) }
      }
      assert(inRun.await(10, TimeUnit.SECONDS))
      val busy = Codex.Home.retain(h, cfg, now, _ => fail("no vacuum under a run"))
      assert(busy.skipped.exists(_.sentence.contains("using the home")), busy.toString)
      assert(Files.exists(h.resolve("sessions/2026/08/01/rollout-old.jsonl")))
      done.countDown(); t.join()
      assertEquals(Codex.Home.retain(h, cfg, now, _ => Right(Codex.Home.ExitCode(0))).deleted, 2)
    finally cleanup(h)

  test("keep-sessions-days 0 and vacuum off change nothing; a failing vacuum is reported, not thrown"):
    val h = home()
    try
      val off = Codex.Home.retain(
        h,
        cfg
          .modify(_.keepSessionsDays)
          .setTo(CodexConfig.KeepSessionsDays(0))
          .modify(_.vacuum)
          .setTo(CodexConfig.Vacuum(false)),
        now,
        _ => fail("vacuum is off")
      )
      assertEquals((off.deleted, off.vacuumed), (0, Nil))
      val bad = Codex.Home.retain(
        h,
        cfg.modify(_.keepSessionsDays).setTo(CodexConfig.KeepSessionsDays(0)),
        now,
        _ => Left(Problem.Unclassified(Legacy.Text("sqlite3: not found")))
      )
      assertEquals(bad.problems.size, 2, bad.toString)
      assert(Codex.Home.retain(h.resolve("missing"), cfg, now).skipped.exists(_.sentence.contains("no Codex home")))
    finally cleanup(h)
