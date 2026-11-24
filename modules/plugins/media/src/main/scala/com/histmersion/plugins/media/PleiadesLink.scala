package com.histmersion
package plugins.media

import io.github.iltotore.iron.constraint.any.Pure

import PleiadesLink.Untaken
import com.histmersion.lint.{LintRule, tolerated}

/** A reference of a Pleiades place record that names a picture: the Commons file (`Right("File:…")`) or why it is not
  * taken, and the reference's CiTO type (`seeFurther`, `citesAsRelated`).
  */
final case class PleiadesLink(
    url: PleiadesLink.Url,
    citation: PleiadesLink.Citation,
    file: Either[Untaken, Commons.File.Title]
)

object PleiadesLink:
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type Citation = Citation.T
  object Citation extends Newtype[String, Pure]

  /** The site a picture not taken is on, as its URL names it. */
  type Host = Host.T
  object Host extends Newtype[String, Pure]

  /** Why a referenced picture is not taken: words for the person, not a failure. */
  enum Untaken:
    /** On Flickr, whose API needs a key. */
    case OnFlickr

    /** A Commons category, not one file. */
    case Category

    /** A Commons file URL whose file name cannot be read. */
    case UnreadableCommonsUrl

    /** A picture on another site (`host`, empty when the URL names none), which documents no keyless licence API. */
    case Elsewhere(host: Host)

    /** The reason, in words. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def words: String = this match
      case OnFlickr             => "on Flickr (its API needs a key)"
      case Category             => "a Commons category, not a file"
      case UnreadableCommonsUrl => "an unreadable Commons file URL"
      case Elsewhere(host)      =>
        s"a picture on ${if s"$host".isEmpty then "another site" else s"$host"} (no documented keyless licence API)"
