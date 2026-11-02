package com.histmersion.pluginhost

/** The names a plugin's contribution may not take, because Histmersion's own providers answer to them: a plugin's
  * pictures, events or models would be taken for theirs. The server's built-in registries define the same sets; a
  * server suite fails when the two disagree.
  */
object ReservedNames:
  import com.histmersion.api.automation.Agents
  import com.histmersion.api.media.MediaProvider
  import com.histmersion.domain.catalogue.events.CatalogProvider

  /** The built-in media imports (`media import <name>`) and their aliases, and every provider pictures are stored
    * under.
    */
  val mediaCollections: Set[String] =
    Set(
      "wikidata",
      "depicts",
      "met",
      "cleveland",
      "aic",
      "loc",
      "pleiades",
      "opencontext",
      "smithsonian",
      "europeana",
      "commons",
      "depictions",
      "cma",
      "artic",
      "oc",
      "si",
      "smithsonian-open-access"
    ) ++
      MediaProvider.all ++ Set(MediaProvider.CommonsDepicts, MediaProvider.Pleiades)

  /** The built-in event catalogues and their aliases. */
  val eventCatalogues: Set[String] = CatalogProvider.all.toSet ++ Set("smithsonian", "volcano", "ncei", "wd")

  /** The first-party plugins: Histmersion's own, in every image. A plugin is first-party when its id is one of these
    * and it was loaded from the image's own class path, not from the plugin set a relink added; a reserved name of a
    * catalogue, a media provider or an AI provider is kept for it.
    */
  val firstPartyPlugins: Set[String] = Set(
    "archive-org",
    "oa-unpaywall",
    "oa-core",
    "oa-openalex",
    "oa-landing",
    "pmc",
    "arxiv",
    "events-gvp",
    "events-noaa",
    "events-wikidata",
    "media-wikidata",
    "media-depicts",
    "media-met",
    "media-cleveland",
    "media-aic",
    "media-loc",
    "media-pleiades",
    "media-opencontext",
    "media-smithsonian",
    "media-europeana",
    "ai-anthropic",
    "ai-openai",
    "ai-local",
    "ai-claude-code",
    "ai-codex",
    "ai-opencode",
    "gazetteer-cliopatria",
    "gazetteer-awmc",
    "gazetteer-chronontology",
    "keepassxc",
    "1password",
    "bitwarden",
    "calibre"
  )

  /** The network gazetteers `places fetch` and `init.fetch-sources` name, and the other names they take (the coastline,
    * PeriodO, the bundled extracts): a plugin's source under one of them would be taken for the host's own.
    */
  val gazetteerSources: Set[String] =
    Set("cliopatria", "awmc", "chronontology", "land", "periodo") ++
      com.histmersion.api.catalogue.GazetteerKind.values.map(_.wire).toSet

  /** Histmersion's own source collections: the Calibre library and archive.org. */
  val sourceCollections: Set[String] = Set("calibre", "archive-org")

  /** The built-in AI providers. */
  val aiProviders: Set[String] = Agents.ProviderKind.values.map(_.wire).toSet
