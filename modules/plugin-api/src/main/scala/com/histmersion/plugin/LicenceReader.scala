package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The host's reading of licence statements, lent to a plugin that must decide on one before it states it — whom a
  * licence requires to be credited, which of two statements a record means, how to name it in a credit line. The host
  * reads every licence a plugin states again with the same code; what a plugin reads here never makes a licence allowed
  * (the host's allowlist decides).
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
trait LicenceReader:
  /** A licence as a machine id or a short name (`cc-by-sa-3.0`, `CC BY-SA 3.0 de`, `cc0`, `pd`, `Public domain`). */
  def readName(name: LicenceReader.ReadNameName): Licence.Read

  /** A licence given as its Creative Commons URL (`https://creativecommons.org/licenses/by-sa/4.0`); anything else is
    * unknown.
    */
  def readUrl(url: LicenceReader.ReadUrlUrl): Licence.Read

  /** The first of the statements that reads as a known licence ([[readName]]), else unknown with the first non-empty
    * one.
    */
  def readFirst(statements: List[LicenceReader.ReadFirstStatements]): Licence.Read

object LicenceReader:
  type ReadNameName = ReadNameName.T
  object ReadNameName extends Newtype[String, Pure]
  type ReadUrlUrl = ReadUrlUrl.T
  object ReadUrlUrl extends Newtype[String, Pure]
  type ReadFirstStatements = ReadFirstStatements.T
  object ReadFirstStatements extends Newtype[String, Pure]

  val Unread: LicenceReader = new LicenceReader:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def readName(name: ReadNameName) = Licence.Read.unknown(Licence.Read.UnknownStatement((name).value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def readUrl(url: ReadUrlUrl) = Licence.Read.unknown(Licence.Read.UnknownStatement((url).value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def readFirst(statements: List[ReadFirstStatements]) =
      Licence.Read.unknown(Licence.Read.UnknownStatement(statements.map((_).value.trim).find(_.nonEmpty).getOrElse("")))
