package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class AcquireDefaults(
    searchLimit: Int, // discover search page size
    minYear: Int, // discover: oldest publication year considered
    followupsLimit: Int, // followups: max citing papers fetched
    archiveRows: Int, // archive.org search rows
    wellCitedAt: Int, // citations at/above which a source is "current" rather than "low-impact"
    lowCitedAt: Int, // citations below which an OLD source is "low-impact"
    staleBefore: Int, // publication year before which the low-citation rule applies
    titleMatchAt: Double // an import PROPOSES an open acquisition item whose text scores this or more (#970)
) derives ConfigReader
