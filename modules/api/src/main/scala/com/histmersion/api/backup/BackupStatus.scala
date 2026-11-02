package com.histmersion.api.backup
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BackupStatus(
    file: String,
    state: String,
    durationMs: Long,
    bytes: Option[Long] = None,
    sha256: Option[String] = None,
    error: Option[String] = None,
    pruned: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def stateOf: Option[BackupState] = BackupState.parse(state)
  def result: Option[BackupResult] =
    for b <- bytes; h <- sha256 if stateOf.contains(BackupState.Done) yield BackupResult(file, b, h, durationMs, pruned)

object BackupStatus:
  given Schema[BackupStatus] = summon[KindlingsSchema[BackupStatus]].schema
  def running(file: String, elapsedMs: Long): BackupStatus = BackupStatus(file, BackupState.Running.wire, elapsedMs)
  def done(r: BackupResult): BackupStatus =
    BackupStatus(r.file, BackupState.Done.wire, r.durationMs, Some(r.bytes), Some(r.sha256), pruned = r.pruned)
  def failed(file: String, elapsedMs: Long, error: String): BackupStatus =
    BackupStatus(file, BackupState.Failed.wire, elapsedMs, error = Some(error))
