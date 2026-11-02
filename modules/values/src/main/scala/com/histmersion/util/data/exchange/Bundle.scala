package com.histmersion.util.data.exchange

import com.histmersion.{Period, WireEnumValues}

object Bundle:

  /** The kinds of entity a bundle carries one line per uid of — and what a tombstone says was deleted. Wire names are
    * kebab-case (`article-version`), as the V031 trigger writes them.
    */
  enum Kind:
    case Source, Finding, Article, ArticleVersion, Asset, AssetLink, Attestation, Event, Goal, QueueItem

    /** A place or a named period added HERE (`dataset = 'local'`); the bundled seeds every instance imports itself are
      * not carried (docs/research-merge-import.md §7.8).
      */
    case Place, Period

    def wire: String = Kind.wireOf(this)

    /** The NDJSON file holding this kind's lines. */
    def file: String = s"entities/${Kind.fileStem(this)}.ndjson"

    /** The letter a merge issue about this kind of record starts its id with (`P-1a2b3c4d`). */
    def issueLetter: String = Kind.letterOf(this)

  object Kind:
    def wireOf(k: Kind): String = k match
      case Source         => "source"
      case Finding        => "finding"
      case Article        => "article"
      case ArticleVersion => "article-version"
      case Asset          => "asset"
      case AssetLink      => "asset-link"
      case Attestation    => "attestation"
      case Event          => "event"
      case Goal           => "goal"
      case QueueItem      => "queue-item"
      case Place          => "place"
      case Period         => "period"

    private def fileStem(k: Kind): String = k match
      case Source         => "sources"
      case Finding        => "findings"
      case Article        => "articles"
      case ArticleVersion => "article-versions"
      case Asset          => "assets"
      case AssetLink      => "asset-links"
      case Attestation    => "attestations"
      case Event          => "events"
      case Goal           => "goals"
      case QueueItem      => "queue"
      case Place          => "places"
      case Period         => "periods"

    private def letterOf(k: Kind): String = k match
      case Source         => "S"
      case Finding        => "F"
      case Article        => "A"
      case ArticleVersion => "V"
      case Asset          => "X"
      case AssetLink      => "K"
      case Attestation    => "T"
      case Event          => "E"
      case Goal           => "G"
      case QueueItem      => "Q"
      case Place          => "P"
      case Period         => "N"

    def fromWire(s: String): Option[Kind] = values.find(_.wire == s.trim.toLowerCase)

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): String = wireOf(e)
      def fromWire(s: String): Option[Kind] = Kind.fromWire(s)
