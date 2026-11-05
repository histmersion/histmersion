package com.histmersion.plugin

/** The host's reading of licence statements, lent to a plugin that must decide on one before it states it — whom a
  * licence requires to be credited, which of two statements a record means, how to name it in a credit line. The host
  * reads every licence a plugin states again with the same code; what a plugin reads here never makes a licence allowed
  * (the host's allowlist decides).
  */
trait LicenceReader:
  /** A licence as a machine id or a short name (`cc-by-sa-3.0`, `CC BY-SA 3.0 de`, `cc0`, `pd`, `Public domain`). */
  def readName(name: com.histmersion.plugin.LicenceReader.ReadNameName): Licence.Read

  /** A licence given as its Creative Commons URL (`https://creativecommons.org/licenses/by-sa/4.0`); anything else is
    * unknown.
    */
  def readUrl(url: com.histmersion.plugin.LicenceReader.ReadUrlUrl): Licence.Read

  /** The first of the statements that reads as a known licence ([[readName]]), else unknown with the first non-empty
    * one.
    */
  def readFirst(statements: List[com.histmersion.plugin.LicenceReader.ReadFirstStatements]): Licence.Read

object LicenceReader:
  /** A host that lends no reading: every statement is unknown. */
  type ReadNameName = ReadNameName.T
  object ReadNameName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadUrlUrl = ReadUrlUrl.T
  object ReadUrlUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadFirstStatements = ReadFirstStatements.T
  object ReadFirstStatements extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Unread: LicenceReader = new LicenceReader:
    def readName(name: com.histmersion.plugin.LicenceReader.ReadNameName) = Licence.Read.unknown(name)
    def readUrl(url: com.histmersion.plugin.LicenceReader.ReadUrlUrl) = Licence.Read.unknown(url)
    def readFirst(statements: List[com.histmersion.plugin.LicenceReader.ReadFirstStatements]) =
      Licence.Read.unknown(statements.map((_).value.trim).find(_.nonEmpty).getOrElse(""))
