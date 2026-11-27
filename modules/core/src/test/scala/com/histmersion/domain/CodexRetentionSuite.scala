package com.histmersion
package domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import CodexRetention.FileInfo
import com.histmersion.api.maintenance.Storage
import com.histmersion.lint.{tolerated, LintRule}

/** What a retention pass of the runs' Codex home may remove (#2582). Pure. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class CodexRetentionSuite extends munit.FunSuite:

  private val Day = 24L * 60 * 60 * 1000
  private val nowMs = 100 * Day
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val now = CodexRetention.NowMs(nowMs)

  test("only transcripts under sessions/ or archived_sessions/ older than keep-days, oldest first"):
    val files = List(
      FileInfo(
        FileInfo.Rel("sessions/2026/09/01/rollout-a.jsonl"),
        FileInfo.Bytes(10),
        FileInfo.ModifiedMs(nowMs - 40 * Day)
      ),
      FileInfo(
        FileInfo.Rel("sessions/2026/10/20/rollout-b.jsonl"),
        FileInfo.Bytes(10),
        FileInfo.ModifiedMs(nowMs - 3 * Day)
      ),
      FileInfo(
        FileInfo.Rel("archived_sessions/rollout-c.jsonl"),
        FileInfo.Bytes(10),
        FileInfo.ModifiedMs(nowMs - 60 * Day)
      ),
      FileInfo(FileInfo.Rel("auth.json"), FileInfo.Bytes(1), FileInfo.ModifiedMs(nowMs - 400 * Day)),
      FileInfo(FileInfo.Rel("state_5.sqlite"), FileInfo.Bytes(1), FileInfo.ModifiedMs(nowMs - 400 * Day)),
      FileInfo(FileInfo.Rel("extractor.config.toml"), FileInfo.Bytes(1), FileInfo.ModifiedMs(nowMs - 400 * Day)),
      FileInfo(FileInfo.Rel("sessions"), FileInfo.Bytes(1), FileInfo.ModifiedMs(nowMs - 400 * Day))
    )
    assertEquals(
      CodexRetention.stale(files, now, CodexRetention.KeepDays(14)).map(_.rel),
      List("archived_sessions/rollout-c.jsonl", "sessions/2026/09/01/rollout-a.jsonl")
    )
    assertEquals(CodexRetention.stale(files, now, CodexRetention.KeepDays(0)), Nil, "0 keeps every transcript")
    assert(!CodexRetention.inSessions(FileInfo.Rel("sessions/../auth.json")))

  test("the SQLite files at the top of the home, not their journals; a pass is due once per interval"):
    assertEquals(
      CodexRetention.sqliteFiles(
        List(
          CodexRetention.FileName("state_5.sqlite-wal"),
          CodexRetention.FileName("thread_history_1.sqlite"),
          CodexRetention.FileName("state_5.sqlite"),
          CodexRetention.FileName("auth.json")
        )
      ),
      List("state_5.sqlite", "thread_history_1.sqlite")
    )
    assert(CodexRetention.due(None, now, CodexRetention.EveryHours(24)))
    assert(!CodexRetention.due(Some(CodexRetention.LastMs(nowMs - Day + 1)), now, CodexRetention.EveryHours(24)))
    assert(CodexRetention.due(Some(CodexRetention.LastMs(nowMs - Day)), now, CodexRetention.EveryHours(24)))
    assert(!CodexRetention.due(None, now, CodexRetention.EveryHours(0)), "0 = no automatic pass")

  test("the configured defaults, and About's shrink hint"):
    val c = AppConfig.load().agents.providers.codex
    assertEquals((c.keepSessionsDays, c.vacuum, c.retentionEveryHours, c.sqlite), (14, true, 24, "sqlite3"))
    assert(
      Storage.Advice
        .shrink(Storage.Entry.Key("codex-home"), Storage.Advice.Keep(0))
        .exists(_.value.contains("keep-sessions-days"))
    )
