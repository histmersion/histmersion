package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import CodexRetention.FileInfo
import com.histmersion.api.maintenance.Storage

/** What a retention pass of the runs' Codex home may remove (#2582). Pure. */
class CodexRetentionSuite extends munit.FunSuite:

  private val Day = 24L * 60 * 60 * 1000
  private val now = 100 * Day

  test("only transcripts under sessions/ or archived_sessions/ older than keep-days, oldest first"):
    val files = List(
      FileInfo(com.histmersion.domain.CodexRetention.FileInfo.Rel("sessions/2026/09/01/rollout-a.jsonl"), com.histmersion.domain.CodexRetention.FileInfo.Bytes(10), now - 40 * Day),
      FileInfo(com.histmersion.domain.CodexRetention.FileInfo.Rel("sessions/2026/10/20/rollout-b.jsonl"), com.histmersion.domain.CodexRetention.FileInfo.Bytes(10), now - 3 * Day),
      FileInfo(com.histmersion.domain.CodexRetention.FileInfo.Rel("archived_sessions/rollout-c.jsonl"), com.histmersion.domain.CodexRetention.FileInfo.Bytes(10), now - 60 * Day),
      FileInfo(com.histmersion.domain.CodexRetention.FileInfo.Rel("auth.json"), com.histmersion.domain.CodexRetention.FileInfo.Bytes(1), now - 400 * Day),
      FileInfo(com.histmersion.domain.CodexRetention.FileInfo.Rel("state_5.sqlite"), com.histmersion.domain.CodexRetention.FileInfo.Bytes(1), now - 400 * Day),
      FileInfo(com.histmersion.domain.CodexRetention.FileInfo.Rel("extractor.config.toml"), com.histmersion.domain.CodexRetention.FileInfo.Bytes(1), now - 400 * Day),
      FileInfo(com.histmersion.domain.CodexRetention.FileInfo.Rel("sessions"), com.histmersion.domain.CodexRetention.FileInfo.Bytes(1), now - 400 * Day)
    )
    assertEquals(
      CodexRetention.stale(files, now, com.histmersion.domain.CodexRetention.KeepDays(14)).map(_.rel),
      List("archived_sessions/rollout-c.jsonl", "sessions/2026/09/01/rollout-a.jsonl")
    )
    assertEquals(CodexRetention.stale(files, now, com.histmersion.domain.CodexRetention.KeepDays(0)), Nil, "0 keeps every transcript")
    assert(!CodexRetention.inSessions(com.histmersion.domain.CodexRetention.FileInfo.Rel("sessions/../auth.json")))

  test("the SQLite files at the top of the home, not their journals; a pass is due once per interval"):
    assertEquals(
      CodexRetention.sqliteFiles(List(com.histmersion.domain.CodexRetention.FileName("state_5.sqlite-wal"), com.histmersion.domain.CodexRetention.FileName("thread_history_1.sqlite"), com.histmersion.domain.CodexRetention.FileName("state_5.sqlite"), com.histmersion.domain.CodexRetention.FileName("auth.json"))),
      List("state_5.sqlite", "thread_history_1.sqlite")
    )
    assert(CodexRetention.due(None, now, com.histmersion.domain.CodexRetention.EveryHours(24)))
    assert(!CodexRetention.due(Some(now - Day + 1), now, com.histmersion.domain.CodexRetention.EveryHours(24)))
    assert(CodexRetention.due(Some(now - Day), now, com.histmersion.domain.CodexRetention.EveryHours(24)))
    assert(!CodexRetention.due(None, now, com.histmersion.domain.CodexRetention.EveryHours(0)), "0 = no automatic pass")

  test("the configured defaults, and About's shrink hint"):
    val c = AppConfig.load().agents.providers.codex
    assertEquals((c.keepSessionsDays, c.vacuum, c.retentionEveryHours, c.sqlite), (14, true, 24, "sqlite3"))
    assert(Storage.Advice.shrink(com.histmersion.api.maintenance.Storage.Entry.Key("codex-home"), com.histmersion.api.maintenance.Storage.Advice.Keep(0)).exists(_.contains("keep-sessions-days")))
