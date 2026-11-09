package com.histmersion
package plugins.vaults

import io.github.iltotore.iron.constraint.any.Pure

/** What KeePassXC's stderr says about a failed attempt: the one place its words are read (the process boundary), so
  * that [[YubiKey.failure]] branches on a case.
  */
enum KeePassXcMessage derives CanEqual:

  /** The password, key file or YubiKey is wrong. */
  case InvalidCredentials

  /** The database has no such entry. */
  case NoEntry

  /** Nobody touched the key in time. */
  case NotTouched

  /** The key is not connected. */
  case KeyAbsent

  /** The challenge-response did not succeed (a slot not configured, a USB error). */
  case ChallengeResponseFailed

  /** Anything else KeePassXC said. */
  case Other

object KeePassXcMessage:

  /** KeePassXC's stderr. */
  type Stderr = Stderr.T
  object Stderr extends Newtype[String, Pure]:
    extension (s: Stderr)
      /** Does the text, in any case, mention one of `words`? */
      def mentionsAny(words: List[String]): Boolean =
        val text = s.value.toLowerCase(java.util.Locale.ROOT)
        words.exists(text.contains)

  private val Credentials = List("invalid credentials", "wrong key", "unable to unlock")
  private val Entry = List("could not find entry")
  private val Touch = List("timed out waiting", "user interaction")
  private val Absent = List(
    "could not find hardware key",
    "could not find interface",
    "please plug it in",
    "please connect it",
    "no yubikey",
    "yubikey not found",
    "no hardware key",
    "no challenge-response key"
  )
  private val Challenge = List(
    "challenge-response",
    "challenge response",
    "slot is not configured",
    "usb error",
    "locked or timed out",
    "hardware key"
  )

  /** The message of an error text; the first kind that matches, in the order a failed attempt is judged. */
  def parse(stderr: Stderr): KeePassXcMessage =
    if stderr.mentionsAny(Credentials) then InvalidCredentials
    else if stderr.mentionsAny(Entry) then NoEntry
    else if stderr.mentionsAny(Touch) then NotTouched
    else if stderr.mentionsAny(Absent) then KeyAbsent
    else if stderr.mentionsAny(Challenge) then ChallengeResponseFailed
    else Other
