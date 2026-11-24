package com.histmersion
package util.problem

sealed trait Problem

object Problem:
  type Field = String

  sealed trait Invalid extends Problem
  object Invalid:
    final case class Missing(field: Field, at: Vector[Field]) extends Invalid
    object Missing:
      def here(field: Field): Missing = Missing(field, Vector.empty)
    final case class Bounds(least: Option[String], greatest: Option[String])

  sealed trait Config extends Problem
  object Config:
    enum Fault:
      case Empty
      case Rejected(why: String)
      case Unbuilt
    final case class Setting(key: String, fault: Fault) extends Config
    object Setting:
      def emptied(key: String): Setting = Setting(key, Fault.Empty)

  sealed trait Run extends Problem
  object Run:
    case object Interrupted extends Run
    final case class Failed(all: Vector[Problem]) extends Run
    enum Actor:
      case Person, Server

  final case class Relayed(sentence: String) extends Problem

  val examples: Vector[Problem] = Vector(Run.Failed(Vector.empty), Config.Setting("k", Config.Fault.Unbuilt))
