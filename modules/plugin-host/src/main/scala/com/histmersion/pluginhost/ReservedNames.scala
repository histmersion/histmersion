package com.histmersion.pluginhost

/** The names a plugin's contribution may not take, because Histmersion's own providers answer to them: a plugin's
  * pictures, events or models would be taken for theirs. The server's built-in registries define the same sets; a
  * server suite fails when the two disagree.
  */
object ReservedNames:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import com.histmersion.api.automation.Agents
  import com.histmersion.api.media.MediaProvider
  import com.histmersion.domain.catalogue.events.CatalogProvider

  /** The built-in media imports (`media import <name>`) and their aliases, and every provider pictures are stored
    * under.
    */
  val mediaCollections: Set[com.histmersion.pluginhost.ReservedNames.Name] =
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
  val eventCatalogues: Set[com.histmersion.pluginhost.ReservedNames.Name] = CatalogProvider.all.toSet ++ Set("smithsonian", "volcano", "ncei", "wd")

  /** The first-party plugins: Histmersion's own, in every image. A plugin is first-party when its id is one of these
    * and it was loaded from the image's own class path, not from the plugin set a relink added; a reserved name of a
    * catalogue, a media provider or an AI provider is kept for it.
    */
  val firstPartyPlugins: Set[com.histmersion.pluginhost.ReservedNames.Name] = Set(
    com.histmersion.pluginhost.ReservedNames.Name("archive-org"),
    com.histmersion.pluginhost.ReservedNames.Name("oa-unpaywall"),
    com.histmersion.pluginhost.ReservedNames.Name("oa-core"),
    com.histmersion.pluginhost.ReservedNames.Name("oa-openalex"),
    com.histmersion.pluginhost.ReservedNames.Name("oa-landing"),
    com.histmersion.pluginhost.ReservedNames.Name("pmc"),
    com.histmersion.pluginhost.ReservedNames.Name("arxiv"),
    com.histmersion.pluginhost.ReservedNames.Name("events-gvp"),
    com.histmersion.pluginhost.ReservedNames.Name("events-noaa"),
    com.histmersion.pluginhost.ReservedNames.Name("events-wikidata"),
    com.histmersion.pluginhost.ReservedNames.Name("media-wikidata"),
    com.histmersion.pluginhost.ReservedNames.Name("media-depicts"),
    com.histmersion.pluginhost.ReservedNames.Name("media-met"),
    com.histmersion.pluginhost.ReservedNames.Name("media-cleveland"),
    com.histmersion.pluginhost.ReservedNames.Name("media-aic"),
    com.histmersion.pluginhost.ReservedNames.Name("media-loc"),
    com.histmersion.pluginhost.ReservedNames.Name("media-pleiades"),
    com.histmersion.pluginhost.ReservedNames.Name("media-opencontext"),
    com.histmersion.pluginhost.ReservedNames.Name("media-smithsonian"),
    com.histmersion.pluginhost.ReservedNames.Name("media-europeana"),
    com.histmersion.pluginhost.ReservedNames.Name("ai-anthropic"),
    com.histmersion.pluginhost.ReservedNames.Name("ai-openai"),
    com.histmersion.pluginhost.ReservedNames.Name("ai-local"),
    com.histmersion.pluginhost.ReservedNames.Name("ai-claude-code"),
    com.histmersion.pluginhost.ReservedNames.Name("ai-codex"),
    com.histmersion.pluginhost.ReservedNames.Name("ai-opencode"),
    com.histmersion.pluginhost.ReservedNames.Name("gazetteer-cliopatria"),
    com.histmersion.pluginhost.ReservedNames.Name("gazetteer-awmc"),
    com.histmersion.pluginhost.ReservedNames.Name("gazetteer-chronontology"),
    com.histmersion.pluginhost.ReservedNames.Name("keepassxc"),
    com.histmersion.pluginhost.ReservedNames.Name("1password"),
    com.histmersion.pluginhost.ReservedNames.Name("bitwarden"),
    com.histmersion.pluginhost.ReservedNames.Name("calibre")
  )

  /** The network gazetteers `places fetch` and `init.fetch-sources` name, and the other names they take (the coastline,
    * PeriodO, the bundled extracts): a plugin's source under one of them would be taken for the host's own.
    */
  val gazetteerSources: Set[com.histmersion.pluginhost.ReservedNames.Name] =
    Set("cliopatria", "awmc", "chronontology", "land", "periodo") ++
      com.histmersion.api.catalogue.Gazetteer.Kind.values.map(_.wire).toSet

  /** Histmersion's own source collections: the Calibre library and archive.org. */
  val sourceCollections: Set[com.histmersion.pluginhost.ReservedNames.Name] = Set(com.histmersion.pluginhost.ReservedNames.Name("calibre"), com.histmersion.pluginhost.ReservedNames.Name("archive-org"))

  /** The built-in AI providers. */
  val aiProviders: Set[com.histmersion.pluginhost.ReservedNames.Name] = Agents.ProviderKind.values.map(_.wire).toSet
