package com.histmersion
package util.problem

sealed trait Problem

object Problem:
  sealed trait Conflict extends Problem
  object Conflict:
    final case class AlreadyExists(typeName: String, key: String) extends Conflict
    final case class Taken(typeName: String, key: String, clash: Clash) extends Conflict
    final case class Busy(activity: Activity) extends Conflict
    enum Clash:
      case Exists
    enum Activity:
      case Backup, Restore

  sealed trait Run extends Problem
  object Run:
    case object Vanished extends Run
    final case class Stopped(stop: Stop) extends Run
    enum Stop:
      case Vanished
