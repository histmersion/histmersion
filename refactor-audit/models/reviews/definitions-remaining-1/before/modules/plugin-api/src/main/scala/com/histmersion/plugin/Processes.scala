package com.histmersion.plugin

import scala.concurrent.duration.FiniteDuration

/** Argument-vector execution supplied by the host. Implementations never interpret argv through a shell.
  * stdout and stderr remain separate; stdout may contain secrets and must not be included in errors.
  */
trait Processes:
  def run(argv: List[String], env: Map[String, String], stdin: Option[String], timeout: FiniteDuration): Either[String, Processes.Result]
  def start(argv: List[String], env: Map[String, String]): Either[String, Processes.Session]

  /** Capture a command without a time limit, closing stdin immediately. The caller explicitly chooses this mode. */
  def capture(argv: List[String]): Either[String, Processes.Result]

object Processes:
  final case class Result(exit: Int, out: String, err: String)

  /** Owned by the caller, which must close it. Output is kept in memory and never logged by the implementation. */
  trait Session:
    def out: String
    def err: String
    def alive: Boolean
    def send(line: String): Either[String, Unit]
    def awaitOut(timeout: FiniteDuration)(ready: String => Boolean): Boolean
    def awaitErr(length: Int, timeout: FiniteDuration): Unit
    def close(timeout: FiniteDuration): Option[Int]
    def exit(timeout: FiniteDuration): Option[Int]

  object Unavailable extends Processes:
    def run(argv: List[String], env: Map[String, String], stdin: Option[String], timeout: FiniteDuration): Either[String, Result] =
      Left("this host supplies no processes")
    def start(argv: List[String], env: Map[String, String]): Either[String, Session] =
      Left("this host supplies no processes")
    def capture(argv: List[String]): Either[String, Result] = Left("this host supplies no processes")
