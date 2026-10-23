package com.histmersion.core

import CodexRetention.FileInfo

/** What a retention pass of the runs' Codex home may remove (#2582). Pure. */
class CodexRetentionSuite extends munit.FunSuite:

  private val Day = 24L * 60 * 60 * 1000
  private val now = 100 * Day

  test("only transcripts under sessions/ or archived_sessions/ older than keep-days, oldest first"):
    val files = List(
      FileInfo("sessions/2026/09/01/rollout-a.jsonl", 10, now - 40 * Day),
      FileInfo("sessions/2026/10/20/rollout-b.jsonl", 10, now - 3 * Day),
      FileInfo("archived_sessions/rollout-c.jsonl", 10, now - 60 * Day),
      FileInfo("auth.json", 1, now - 400 * Day),
      FileInfo("state_5.sqlite", 1, now - 400 * Day),
      FileInfo("extractor.config.toml", 1, now - 400 * Day),
      FileInfo("sessions", 1, now - 400 * Day))
    assertEquals(CodexRetention.stale(files, now, 14).map(_.rel),
      List("archived_sessions/rollout-c.jsonl", "sessions/2026/09/01/rollout-a.jsonl"))
    assertEquals(CodexRetention.stale(files, now, 0), Nil, "0 keeps every transcript")
    assert(!CodexRetention.inSessions("sessions/../auth.json"))

  test("the SQLite files at the top of the home, not their journals; a pass is due once per interval"):
    assertEquals(CodexRetention.sqliteFiles(List("state_5.sqlite-wal", "thread_history_1.sqlite", "state_5.sqlite", "auth.json")),
      List("state_5.sqlite", "thread_history_1.sqlite"))
    assert(CodexRetention.due(None, now, 24))
    assert(!CodexRetention.due(Some(now - Day + 1), now, 24))
    assert(CodexRetention.due(Some(now - Day), now, 24))
    assert(!CodexRetention.due(None, now, 0), "0 = no automatic pass")

  test("the configured defaults, and About's shrink hint"):
    val c = AppConfig.load().agents.providers.codex
    assertEquals((c.keepSessionsDays, c.vacuum, c.retentionEveryHours, c.sqlite), (14, true, 24, "sqlite3"))
    assert(StorageAdvice.shrink("codex-home", 0).exists(_.contains("keep-sessions-days")))
