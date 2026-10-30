package com.histmersion.api

/** An event's TIER (docs/visualization-guide.md §1): `attested` when a source in the corpus attests it (its own
  * `citekey`, or an attestation row), `catalogue` when only a catalogue knows it.
  */
object EventTier:
  val Attested = "attested"
  val Catalogue = "catalogue"
  val all: List[String] = List(Attested, Catalogue)
