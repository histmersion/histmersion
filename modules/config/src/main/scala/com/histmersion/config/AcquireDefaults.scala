package com.histmersion
package config

import com.histmersion.Year
import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.lint.{FeaturePlace, movesTo}

@movesTo(FeaturePlace.LibraryAcquisition)
final case class AcquireDefaults(
    searchLimit: ItemCount, // discover search page size
    minYear: Year, // discover: oldest publication year considered
    followupsLimit: ItemCount, // followups: max citing papers fetched
    archiveRows: ItemCount, // archive.org search rows
    wellCitedAt: CitationCount, // citations at/above which a source is "current" rather than "low-impact"
    lowCitedAt: CitationCount, // citations below which an OLD source is "low-impact"
    staleBefore: Year, // publication year before which the low-citation rule applies
    titleMatchAt: MatchScore // an import PROPOSES an open acquisition item whose text scores this or more (#970)
) derives ConfigReader
