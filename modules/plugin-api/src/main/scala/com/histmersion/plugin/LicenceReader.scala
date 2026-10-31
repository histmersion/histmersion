package com.histmersion.plugin

/** The host's reading of licence statements, lent to a plugin that must decide on one before it states it — whom a
  * licence requires to be credited, which of two statements a record means, how to name it in a credit line. The host
  * reads every licence a plugin states again with the same code; what a plugin reads here never makes a licence allowed
  * (the host's allowlist decides).
  */
trait LicenceReader:
  /** A licence as a machine id or a short name (`cc-by-sa-3.0`, `CC BY-SA 3.0 de`, `cc0`, `pd`, `Public domain`). */
  def readName(name: String): LicenceRead

  /** A licence given as its Creative Commons URL (`https://creativecommons.org/licenses/by-sa/4.0`); anything else is
    * unknown.
    */
  def readUrl(url: String): LicenceRead

  /** The first of the statements that reads as a known licence ([[readName]]), else unknown with the first non-empty
    * one.
    */
  def readFirst(statements: List[String]): LicenceRead

object LicenceReader:
  /** A host that lends no reading: every statement is unknown. */
  val Unread: LicenceReader = new LicenceReader:
    def readName(name: String) = LicenceRead.unknown(name)
    def readUrl(url: String) = LicenceRead.unknown(url)
    def readFirst(statements: List[String]) =
      LicenceRead.unknown(statements.map(_.trim).find(_.nonEmpty).getOrElse(""))
