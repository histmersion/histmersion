package com.histmersion.inventoryfixture.visibility

object PublicRepo:
  final case class ResultRow(value: Int)
  def read(): ResultRow = ResultRow(1)

type PublicResult = PublicRepo.ResultRow

opaque type SecretToken = PublicRepo.ResultRow
object SecretToken:
  def from(row: PublicRepo.ResultRow): SecretToken = row

final class ConnectionOwner(connection: java.sql.Connection)

private[visibility] object HiddenRepo
private[inventoryfixture] object BroadRepo

private object Envelope:
  final case class NestedRow(value: Int)

object Storage:
  def read(): Int = 1
