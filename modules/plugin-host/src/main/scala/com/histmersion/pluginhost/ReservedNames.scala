package com.histmersion
package pluginhost

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Gazetteer

/** The names a plugin's contribution may not take, because Histmersion's own providers answer to them: a plugin's
  * pictures, events or models would be taken for theirs. The server's built-in registries define the same sets; a
  * server suite fails when the two disagree.
  */
object ReservedNames:
  type Name = Name.T
  object Name extends Newtype[String, Pure]

  import com.histmersion.api.automation.Agents
  import com.histmersion.api.media.MediaProvider
  import com.histmersion.domain.catalogue.events.CatalogProvider

  /** The built-in media imports (`media import <name>`) and their aliases, and every provider pictures are stored
    * under.
    */
  val mediaCollections: Set[Name] =
    Set[String](
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
    ).map(Name(_)) ++
      MediaProvider.all.map(id => Name(id.value)) ++ Set(MediaProvider.CommonsDepicts, MediaProvider.Pleiades).map(
        Name(_)
      )

  /** The built-in event catalogues and their aliases. */
  val eventCatalogues: Set[Name] =
    CatalogProvider.all.map(id => Name(id.value)).toSet ++ Set("smithsonian", "volcano", "ncei", "wd").map(Name(_))

  /** The first-party plugins: Histmersion's own, in every image. A plugin is first-party when its id is one of these
    * and it was loaded from the image's own class path, not from the plugin set a relink added; a reserved name of a
    * catalogue, a media provider or an AI provider is kept for it.
    */
  val firstPartyPlugins: Set[Name] = Set(
    Name("archive-org"),
    Name("oa-unpaywall"),
    Name("oa-core"),
    Name("oa-openalex"),
    Name("oa-landing"),
    Name("pmc"),
    Name("arxiv"),
    Name("events-gvp"),
    Name("events-noaa"),
    Name("events-wikidata"),
    Name("media-wikidata"),
    Name("media-depicts"),
    Name("media-met"),
    Name("media-cleveland"),
    Name("media-aic"),
    Name("media-loc"),
    Name("media-pleiades"),
    Name("media-opencontext"),
    Name("media-smithsonian"),
    Name("media-europeana"),
    Name("ai-anthropic"),
    Name("ai-openai"),
    Name("ai-local"),
    Name("ai-claude-code"),
    Name("ai-codex"),
    Name("ai-opencode"),
    Name("gazetteer-cliopatria"),
    Name("gazetteer-awmc"),
    Name("gazetteer-chronontology"),
    Name("keepassxc"),
    Name("1password"),
    Name("bitwarden"),
    Name("calibre")
  )

  /** The network gazetteers `places fetch` and `init.fetch-sources` name, and the other names they take (the coastline,
    * PeriodO, the bundled extracts): a plugin's source under one of them would be taken for the host's own.
    */
  val gazetteerSources: Set[Name] =
    Set("cliopatria", "awmc", "chronontology", "land", "periodo").map(Name(_)) ++
      Gazetteer.Kind.values.map(k => Name(k.wire.value)).toSet

  /** Histmersion's own source collections: the Calibre library and archive.org. */
  val sourceCollections: Set[Name] = Set(Name("calibre"), Name("archive-org"))

  /** The built-in AI providers. */
  val aiProviders: Set[Name] = Agents.ProviderKind.values.map(k => Name(k.wire.value)).toSet
