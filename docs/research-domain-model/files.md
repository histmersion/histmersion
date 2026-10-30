# Current files → target subdomain and feature

Every Scala source in `server`, `core`, `db`, `api`, `config` and `files` (`src/main`, master 58de5489), grouped by the package the design moves it to: `com.histmersion.domain.<subdomain>.<feature>` for services, repositories, controllers and pure rules (server and core modules), `com.histmersion.api.<subdomain>.<feature>` for wire records, endpoints and renderers, `com.histmersion.config.<subdomain>` for settings records. The first cut was made by name rules, then corrected file by file where the measured references said otherwise (§3.2 of the design and `dependencies.md`); the slice that moves a subdomain re-checks its rows. Files the slices split between several owners (`Handlers`, `OpsHandlers`, `api/Queue`, `db/QueueRepo`) are listed where most of their content goes. `agents/AcquireRecheckService` is listed under `library.acquisition`, where §3.5 moves it (the measurements count it where it is today, in automation). `cli` and `web` keep their own trees.

`atlas` is a placeholder name (Q17); renaming it is one package rename.

| package | files | lines |
|---|---:|---:|
| `platform.identity` | 6 | 121 |
| `platform.jobs` | 16 | 844 |
| `platform.lint` | 9 | 1166 |
| `platform.maintenance` | 1 | 34 |
| `platform.plugins` | 14 | 1127 |
| `platform.worklist` | 2 | 460 |
| `atlas.events` | 57 | 4311 |
| `atlas.evidence` | 28 | 753 |
| `atlas.maptiles` | 23 | 1219 |
| `atlas.periods` | 16 | 600 |
| `atlas.places` | 77 | 7268 |
| `media` | 93 | 7034 |
| `library.acquisition` | 101 | 6810 |
| `library.calibre` | 6 | 89 |
| `library.citations` | 19 | 294 |
| `library.findings` | 6 | 133 |
| `library.ingest` | 21 | 1817 |
| `library.search` | 30 | 1966 |
| `library.sources` | 43 | 2631 |
| `research.articles` | 55 | 3368 |
| `research.articles.lint` | 1 | 1336 |
| `research.articles.review` | 5 | 365 |
| `automation.activity` | 8 | 453 |
| `automation.agents` | 116 | 13643 |
| `automation.approvals` | 12 | 1070 |
| `automation.autopilot` | 7 | 1384 |
| `automation.goals` | 16 | 2150 |
| `automation.managers` | 22 | 5528 |
| `automation.pipelines` | 16 | 4086 |
| `automation.review` | 6 | 2706 |
| `automation.scheduler` | 11 | 1710 |
| `exchange.bundle` | 60 | 4264 |
| `exchange.merge` | 41 | 5329 |
| `operations.backup` | 13 | 701 |
| `operations.basemap` | 48 | 5245 |
| `operations.instance` | 24 | 624 |
| `operations.maintenance` | 18 | 819 |
| `operations.settings` | 39 | 1993 |
| `personal` | 38 | 1736 |
| `api root` | 3 | 1449 |
| `api.ui` | 5 | 162 |
| `config root` | 18 | 657 |
| `domain (shared kernel)` | 21 | 443 |
| `server (composition, adapters)` | 21 | 4904 |
| `server: split across subdomains` | 3 | 2403 |
| `util.*` | 22 | 2403 |
| `util.database` | 5 | 139 |
| `util.files` | 1 | 48 |
| `util.markdown` | 1 | 30 |
| `util.ocr` | 8 | 457 |
| `values` | 10 | 92 |

## platform.identity

| module | file | lines |
|---|---|---:|
| api | `api/AliasKind.scala` | 18 |
| api | `api/Aliases.scala` | 17 |
| api | `api/InstanceInfo.scala` | 13 |
| api | `api/ResolvedId.scala` | 14 |
| api | `api/UidRedirect.scala` | 13 |
| db | `db/AliasRepo.scala` | 46 |

## platform.jobs

| module | file | lines |
|---|---|---:|
| api | `api/JobActions.scala` | 15 |
| api | `api/JobCounts.scala` | 48 |
| api | `api/JobDetail.scala` | 32 |
| api | `api/JobInfo.scala` | 25 |
| api | `api/JobKind.scala` | 22 |
| api | `api/JobState.scala` | 25 |
| core | `domain/jobs/JobAction.scala` | 12 |
| core | `domain/jobs/JobControlRules.scala` | 41 |
| core | `domain/jobs/JobControlStep.scala` | 15 |
| core | `domain/jobs/JobStop.scala` | 5 |
| db | `db/JobRepo.scala` | 271 |
| server | `domain/jobs/JobControlControllers.scala` | 12 |
| server | `domain/jobs/JobControlService.scala` | 98 |
| server | `domain/jobs/JobStopped.scala` | 6 |
| server | `domain/jobs/JobStops.scala` | 35 |
| server | `server/BackgroundJob.scala` | 182 |

## platform.lint

| module | file | lines |
|---|---|---:|
| api | `api/FormatCheck.scala` | 11 |
| api | `api/FormatField.scala` | 17 |
| api | `api/FormatIssue.scala` | 19 |
| api | `api/FormatMarkdown.scala` | 32 |
| api | `api/FormatReport.scala` | 17 |
| api | `api/FormatSeverity.scala` | 247 |
| api | `api/Lint.scala` | 478 |
| config | `config/LintConfig.scala` | 8 |
| db | `db/LintRepo.scala` | 337 |

## platform.maintenance

| module | file | lines |
|---|---|---:|
| server | `server/Maintenance.scala` | 34 |

## platform.plugins

| module | file | lines |
|---|---|---:|
| api | `api/PluginDescription.scala` | 17 |
| api | `api/PluginFile.scala` | 9 |
| api | `api/PluginJar.scala` | 11 |
| api | `api/PluginSet.scala` | 33 |
| api | `api/PluginSetting.scala` | 9 |
| api | `api/PluginState.scala` | 15 |
| api | `api/PluginStatus.scala` | 20 |
| api | `api/PluginsMarkdown.scala` | 38 |
| api | `api/PluginsReport.scala` | 16 |
| files | `files/PluginKit.scala` | 319 |
| server | `server/plugins/LegacyPlugins.scala` | 128 |
| server | `server/plugins/PluginSetResource.scala` | 47 |
| server | `server/plugins/PluginTool.scala` | 312 |
| server | `server/plugins/Plugins.scala` | 153 |

## platform.worklist

| module | file | lines |
|---|---|---:|
| api | `api/Queue.scala` | 323 |
| db | `db/QueueRepo.scala` | 137 |

## atlas.events

| module | file | lines |
|---|---|---:|
| api | `api/ArticleEventLead.scala` | 31 |
| api | `api/CatalogProviderInfo.scala` | 11 |
| api | `api/CatalogRecord.scala` | 45 |
| api | `api/Correction.scala` | 24 |
| api | `api/CorrectionCounts.scala` | 20 |
| api | `api/CorrectionList.scala` | 12 |
| api | `api/CorrectionStatus.scala` | 11 |
| api | `api/CorrectionTarget.scala` | 8 |
| api | `api/Corrections.scala` | 51 |
| api | `api/DatingMethod.scala` | 4 |
| api | `api/EventArticleRef.scala` | 11 |
| api | `api/EventCatalogueRef.scala` | 21 |
| api | `api/EventCount.scala` | 14 |
| api | `api/EventDating.scala` | 16 |
| api | `api/EventDeleted.scala` | 9 |
| api | `api/EventDetail.scala` | 29 |
| api | `api/EventEdge.scala` | 16 |
| api | `api/EventFields.scala` | 50 |
| api | `api/EventGeo.scala` | 40 |
| api | `api/EventGeoList.scala` | 11 |
| api | `api/EventGeometry.scala` | 41 |
| api | `api/EventImportReport.scala` | 84 |
| api | `api/EventImportRequest.scala` | 20 |
| api | `api/EventKind.scala` | 14 |
| api | `api/EventList.scala` | 18 |
| api | `api/EventParticipant.scala` | 15 |
| api | `api/EventPatch.scala` | 27 |
| api | `api/EventPrecision.scala` | 53 |
| api | `api/EventPut.scala` | 26 |
| api | `api/EventRederiveReport.scala` | 41 |
| api | `api/EventRederiveRequest.scala` | 11 |
| api | `api/EventScale.scala` | 86 |
| api | `api/EventSummary.scala` | 28 |
| api | `api/EventTaxonomy.scala` | 130 |
| api | `api/EventTier.scala` | 6 |
| api | `api/EventsApi.scala` | 87 |
| config | `config/EventsConfig.scala` | 24 |
| config | `config/EventsGvpConfig.scala` | 16 |
| config | `config/EventsLeadsConfig.scala` | 12 |
| config | `config/EventsLinksConfig.scala` | 16 |
| config | `config/EventsNoaaConfig.scala` | 12 |
| config | `config/EventsWikidataConfig.scala` | 14 |
| core | `domain/CatalogDating.scala` | 76 |
| core | `domain/CatalogMerge.scala` | 136 |
| core | `domain/CatalogProvider.scala` | 48 |
| core | `domain/EventProviders.scala` | 467 |
| core | `domain/FreeTextDates.scala` | 69 |
| core | `domain/StoredEvent.scala` | 20 |
| db | `db/CorrectionRepo.scala` | 121 |
| db | `db/EventCatalogRepo.scala` | 558 |
| db | `db/EventRepo.scala` | 542 |
| server | `server/events/BuiltinCatalogues.scala` | 67 |
| server | `server/events/EventCatalogHandlers.scala` | 824 |
| server | `server/events/EventCatalogRoutes.scala` | 24 |
| server | `server/events/EventCatalogTools.scala` | 107 |
| server | `server/events/EventsSettings.scala` | 16 |
| server | `server/events/HostEventCatalogue.scala` | 21 |

## atlas.evidence

| module | file | lines |
|---|---|---:|
| api | `api/Attestation.scala` | 27 |
| api | `api/AttestationDeleted.scala` | 14 |
| api | `api/AttestationList.scala` | 19 |
| api | `api/AttestationPatch.scala` | 25 |
| api | `api/AttestationPut.scala` | 28 |
| api | `api/AttestationTarget.scala` | 13 |
| api | `api/EventCandidatesRequest.scala` | 11 |
| api | `api/EventFindingRef.scala` | 18 |
| api | `api/EventLead.scala` | 20 |
| api | `api/EventLeadAction.scala` | 11 |
| api | `api/EventLeadList.scala` | 9 |
| api | `api/EventLeads.scala` | 70 |
| api | `api/EventLinkCandidate.scala` | 20 |
| api | `api/EventLinkList.scala` | 11 |
| api | `api/EventLinksMarkdown.scala` | 74 |
| api | `api/EventLinksRequest.scala` | 12 |
| api | `api/EventLinksResult.scala` | 15 |
| api | `api/EventPassage.scala` | 11 |
| api | `api/EventSourceRef.scala` | 16 |
| api | `api/LeadFacts.scala` | 16 |
| api | `api/LinkMatch.scala` | 16 |
| api | `api/LinkScheme.scala` | 9 |
| api | `api/MatchCounts.scala` | 19 |
| api | `api/MatchSpan.scala` | 9 |
| core | `domain/EventMatcher.scala` | 108 |
| core | `domain/MatchWeights.scala` | 28 |
| db | `db/AttestationRepo.scala` | 101 |
| db | `db/LeadRow.scala` | 23 |

## atlas.maptiles

| module | file | lines |
|---|---|---:|
| api | `api/MapTilesUiConfig.scala` | 12 |
| api | `api/SiteTile.scala` | 14 |
| api | `api/TileLayer.scala` | 16 |
| api | `api/TileProps.scala` | 49 |
| api | `api/TilesApi.scala` | 66 |
| config | `config/UiMapTilesSettings.scala` | 16 |
| core | `domain/TileCell.scala` | 21 |
| core | `domain/TileCoord.scala` | 45 |
| core | `domain/TileFeatures.scala` | 90 |
| core | `domain/TileGrid.scala` | 20 |
| core | `domain/TilePoint.scala` | 4 |
| core | `domain/TileValue.scala` | 8 |
| core | `domain/VectorTileWriter.scala` | 76 |
| core | `domain/maptiles/GridCell.scala` | 25 |
| core | `domain/maptiles/UndatedBuild.scala` | 10 |
| core | `domain/maptiles/UndatedCells.scala` | 64 |
| db | `db/TileRepo.scala` | 232 |
| db | `db/UndatedSiteRepo.scala` | 128 |
| server | `server/MapTileService.scala` | 105 |
| server | `server/MapTiles.scala` | 51 |
| server | `server/TileCache.scala` | 31 |
| server | `server/maptiles/UndatedSite.scala` | 10 |
| server | `server/maptiles/UndatedSites.scala` | 126 |

## atlas.periods

| module | file | lines |
|---|---|---:|
| api | `api/HistoricalPeriod.scala` | 21 |
| api | `api/Period.scala` | 4 |
| api | `api/PeriodDetail.scala` | 14 |
| api | `api/PeriodImportResult.scala` | 17 |
| api | `api/PeriodInput.scala` | 13 |
| api | `api/PeriodO.scala` | 202 |
| api | `api/PeriodOAuthority.scala` | 6 |
| api | `api/PeriodODefinition.scala` | 9 |
| api | `api/PeriodRow.scala` | 3 |
| api | `api/PeriodSkip.scala` | 29 |
| api | `api/PeriodsMarkdown.scala` | 52 |
| api | `api/SkippedPeriod.scala` | 11 |
| config | `config/PeriodsConfig.scala` | 13 |
| db | `db/PeriodRepo.scala` | 103 |
| db | `db/PeriodSql.scala` | 91 |
| server | `server/importers/PeriodOFetch.scala` | 12 |

## atlas.places

| module | file | lines |
|---|---|---:|
| api | `api/GazDescription.scala` | 3 |
| api | `api/GazGeometry.scala` | 5 |
| api | `api/GazLink.scala` | 3 |
| api | `api/GazName.scala` | 3 |
| api | `api/GazRecord.scala` | 21 |
| api | `api/Gazetteer.scala` | 272 |
| api | `api/GazetteerExtract.scala` | 22 |
| api | `api/GazetteerKind.scala` | 24 |
| api | `api/GazetteerLoadCounts.scala` | 65 |
| api | `api/GazetteerManifest.scala` | 9 |
| api | `api/GazetteerSourceInfo.scala` | 26 |
| api | `api/Geocode.scala` | 299 |
| api | `api/Lpf.scala` | 341 |
| api | `api/ManualPlaceName.scala` | 11 |
| api | `api/MapArea.scala` | 11 |
| api | `api/Place.scala` | 13 |
| api | `api/PlaceAdd.scala` | 27 |
| api | `api/PlaceCleanup.scala` | 3 |
| api | `api/PlaceCounts.scala` | 11 |
| api | `api/PlaceDating.scala` | 28 |
| api | `api/PlaceDescription.scala` | 11 |
| api | `api/PlaceDesignation.scala` | 12 |
| api | `api/PlaceDetail.scala` | 44 |
| api | `api/PlaceEdits.scala` | 97 |
| api | `api/PlaceFootprint.scala` | 21 |
| api | `api/PlaceGone.scala` | 18 |
| api | `api/PlaceHit.scala` | 25 |
| api | `api/PlaceImportResult.scala` | 16 |
| api | `api/PlaceInput.scala` | 3 |
| api | `api/PlaceLink.scala` | 35 |
| api | `api/PlaceMatch.scala` | 3 |
| api | `api/PlaceMergeIn.scala` | 14 |
| api | `api/PlaceName.scala` | 9 |
| api | `api/PlaceNameAdd.scala` | 11 |
| api | `api/PlaceNameChanged.scala` | 18 |
| api | `api/PlaceNameRemove.scala` | 11 |
| api | `api/PlaceOverride.scala` | 26 |
| api | `api/PlaceOverrideChanged.scala` | 25 |
| api | `api/PlaceOverrideEdit.scala` | 11 |
| api | `api/PlaceOverrideSet.scala` | 30 |
| api | `api/PlaceOverrides.scala` | 84 |
| api | `api/PlaceRebuildResult.scala` | 17 |
| api | `api/PlaceRecord.scala` | 21 |
| api | `api/PlaceRegister.scala` | 17 |
| api | `api/PlaceRelation.scala` | 23 |
| api | `api/PlaceRemoveIn.scala` | 11 |
| api | `api/PlaceShape.scala` | 14 |
| api | `api/PlaceSummary.scala` | 22 |
| api | `api/PlaceTimeBasis.scala` | 41 |
| api | `api/PlaceTreeRow.scala` | 17 |
| api | `api/PlaceType.scala` | 15 |
| api | `api/PresetMenu.scala` | 39 |
| api | `api/PresetRegion.scala` | 36 |
| api | `api/SiteTypes.scala` | 338 |
| api | `api/SiteTypesReport.scala` | 27 |
| api | `api/SiteTypesRequest.scala` | 9 |
| api | `api/Tree.scala` | 279 |
| config | `config/PlaceSourcesConfig.scala` | 26 |
| config | `config/PlacesConfig.scala` | 35 |
| config | `config/SiteTypesConfig.scala` | 6 |
| core | `domain/PlaceReview.scala` | 221 |
| core | `domain/PointAreas.scala` | 100 |
| db | `db/GazetteerRepo.scala` | 537 |
| db | `db/GazetteerSourceRow.scala` | 27 |
| db | `db/Geocoder.scala` | 258 |
| db | `db/LandRepo.scala` | 56 |
| db | `db/PlaceOverrideRepo.scala` | 232 |
| db | `db/PlaceRepo.scala` | 1710 |
| db | `db/PlaceReviewRepo.scala` | 224 |
| db | `db/PlaceScope.scala` | 28 |
| db | `db/PointAreaRepo.scala` | 246 |
| db | `db/SiteTypesSql.scala` | 47 |
| db | `db/TreeRepo.scala` | 167 |
| files | `files/PlaceSeed.scala` | 39 |
| server | `server/importers/GazetteerLoad.scala` | 503 |
| server | `server/importers/ImportRunner.scala` | 96 |
| server | `server/importers/NaturalEarthLand.scala` | 63 |

## media

| module | file | lines |
|---|---|---:|
| api | `api/AssetDetail.scala` | 25 |
| api | `api/AssetInfo.scala` | 17 |
| api | `api/AssetInput.scala` | 17 |
| api | `api/AssetKind.scala` | 9 |
| api | `api/AssetLink.scala` | 20 |
| api | `api/AssetLinkDeleted.scala` | 14 |
| api | `api/AssetLinkList.scala` | 19 |
| api | `api/AssetLinkPut.scala` | 19 |
| api | `api/AssetLinkTarget.scala` | 9 |
| api | `api/AssetLinks.scala` | 112 |
| api | `api/AssetPatch.scala` | 21 |
| api | `api/AssetRelation.scala` | 8 |
| api | `api/AssetsStored.scala` | 9 |
| api | `api/DataCredit.scala` | 11 |
| api | `api/DataCredits.scala` | 142 |
| api | `api/FigureLicenceFill.scala` | 11 |
| api | `api/Iiif.scala` | 111 |
| api | `api/ImageSize.scala` | 46 |
| api | `api/MediaCount.scala` | 18 |
| api | `api/MediaCoversReport.scala` | 34 |
| api | `api/MediaCoversRequest.scala` | 19 |
| api | `api/MediaFacets.scala` | 11 |
| api | `api/MediaGeoHit.scala` | 29 |
| api | `api/MediaImportReport.scala` | 60 |
| api | `api/MediaImportRequest.scala` | 18 |
| api | `api/MediaItem.scala` | 33 |
| api | `api/MediaList.scala` | 18 |
| api | `api/MediaProvider.scala` | 59 |
| api | `api/MediaProviderInfo.scala` | 18 |
| api | `api/MediaSample.scala` | 20 |
| api | `api/MediaSourceLine.scala` | 12 |
| api | `api/MediaTarget.scala` | 18 |
| api | `api/MediaThumb.scala` | 24 |
| config | `config/MediaCollectionConfig.scala` | 39 |
| config | `config/MediaCommonsConfig.scala` | 5 |
| config | `config/MediaConfig.scala` | 40 |
| config | `config/MediaCoversConfig.scala` | 12 |
| config | `config/MediaDepictsConfig.scala` | 9 |
| config | `config/MediaIllustrationsConfig.scala` | 13 |
| config | `config/MediaPluginCollectionsConfig.scala` | 13 |
| config | `config/MediaWikidataConfig.scala` | 11 |
| core | `domain/ArtInstitute.scala` | 62 |
| core | `domain/ClevelandArt.scala` | 71 |
| core | `domain/Commons.scala` | 177 |
| core | `domain/CommonsDepicts.scala` | 66 |
| core | `domain/Europeana.scala` | 222 |
| core | `domain/FigurePick.scala` | 116 |
| core | `domain/LibraryOfCongress.scala` | 101 |
| core | `domain/MediaLicence.scala` | 104 |
| core | `domain/MediaMatch.scala` | 150 |
| core | `domain/MediaRecord.scala` | 42 |
| core | `domain/MediaRights.scala` | 17 |
| core | `domain/MediaRoles.scala` | 19 |
| core | `domain/MediaText.scala` | 51 |
| core | `domain/MetMuseum.scala` | 64 |
| core | `domain/OpenContext.scala` | 215 |
| core | `domain/PleiadesLinks.scala` | 83 |
| core | `domain/Smithsonian.scala` | 155 |
| core | `domain/Wikidata.scala` | 73 |
| db | `db/AssetLinkRepo.scala` | 51 |
| db | `db/AssetMetaRepo.scala` | 85 |
| db | `db/AssetRepo.scala` | 188 |
| db | `db/Figures.scala` | 78 |
| db | `db/MediaRepo.scala` | 986 |
| db | `db/MediaTargetRow.scala` | 16 |
| files | `files/CoverChoice.scala` | 23 |
| files | `files/OpenLibrary.scala` | 36 |
| server | `server/IiifHandler.scala` | 176 |
| server | `server/media/ArtInstituteCollection.scala` | 18 |
| server | `server/media/BuiltinMedia.scala` | 202 |
| server | `server/media/ClevelandCollection.scala` | 16 |
| server | `server/media/Collection.scala` | 81 |
| server | `server/media/CollectionImport.scala` | 247 |
| server | `server/media/CommonsDepictions.scala` | 135 |
| server | `server/media/DbMediaStore.scala` | 43 |
| server | `server/media/EuropeanaCollection.scala` | 41 |
| server | `server/media/HostMediaCollection.scala` | 26 |
| server | `server/media/KeyRefusal.scala` | 11 |
| server | `server/media/KeyedConfig.scala` | 15 |
| server | `server/media/KeyedEntry.scala` | 48 |
| server | `server/media/KeyedMeta.scala` | 20 |
| server | `server/media/LocCollection.scala` | 37 |
| server | `server/media/MediaHandlers.scala` | 321 |
| server | `server/media/MediaImporter.scala` | 211 |
| server | `server/media/MediaImporters.scala` | 104 |
| server | `server/media/MediaStore.scala` | 54 |
| server | `server/media/MetCollection.scala` | 23 |
| server | `server/media/OpenContextCollection.scala` | 28 |
| server | `server/media/PleiadesImages.scala` | 104 |
| server | `server/media/PluginCollection.scala` | 81 |
| server | `server/media/SmithsonianCollection.scala` | 28 |
| server | `server/media/SourcePictures.scala` | 256 |
| server | `server/media/WikidataCommons.scala` | 304 |

## library.acquisition

| module | file | lines |
|---|---|---:|
| api | `api/Acquire.scala` | 305 |
| api | `api/AcquireApi.scala` | 104 |
| api | `api/AcquireChecks.scala` | 98 |
| api | `api/AcquireChecksMarkdown.scala` | 39 |
| api | `api/AcquireMarkdown.scala` | 259 |
| api | `api/ArchiveFile.scala` | 11 |
| api | `api/ArchiveImportRequest.scala` | 14 |
| api | `api/ArchiveItem.scala` | 22 |
| api | `api/ArchiveSearchResult.scala` | 11 |
| api | `api/CandidateCount.scala` | 9 |
| api | `api/CandidateList.scala` | 17 |
| api | `api/CandidateMark.scala` | 9 |
| api | `api/CandidateMarked.scala` | 9 |
| api | `api/CandidateSweep.scala` | 20 |
| api | `api/CandidateSweepRequest.scala` | 11 |
| api | `api/CandidateWatch.scala` | 19 |
| api | `api/Candidates.scala` | 129 |
| api | `api/CandidatesMarkdown.scala` | 34 |
| api | `api/DiscoverResult.scala` | 11 |
| api | `api/RepairAcquire.scala` | 63 |
| api | `api/RetractionNotice.scala` | 11 |
| api | `api/RetractionsUpdate.scala` | 18 |
| api | `api/SourceCandidate.scala` | 25 |
| api | `api/UpstreamQuota.scala` | 23 |
| api | `api/UpstreamQuotaMarkdown.scala` | 13 |
| api | `api/WikirefsResult.scala` | 17 |
| api | `api/WorkRef.scala` | 30 |
| config | `config/AcquireConfig.scala` | 57 |
| config | `config/AcquireDefaults.scala` | 15 |
| config | `config/CandidatesConfig.scala` | 17 |
| config | `config/CoreServiceConfig.scala` | 36 |
| config | `config/IndexName.scala` | 9 |
| config | `config/KeyedServiceConfig.scala` | 7 |
| config | `config/LandingConfig.scala` | 27 |
| config | `config/MatchScore.scala` | 9 |
| config | `config/RecheckConfig.scala` | 17 |
| config | `config/RetractionWatchConfig.scala` | 6 |
| config | `config/ServiceConfig.scala` | 6 |
| core | `domain/AcquireMatch.scala` | 188 |
| core | `domain/AcquireRecheck.scala` | 220 |
| core | `domain/BrowserDownload.scala` | 77 |
| core | `domain/CoreQuota.scala` | 188 |
| core | `domain/HttpPolicy.scala` | 130 |
| core | `domain/IngestBacklog.scala` | 167 |
| core | `domain/RepositoryLanding.scala` | 324 |
| db | `db/AcquireCheckRepo.scala` | 87 |
| db | `db/AcquireRepo.scala` | 234 |
| db | `db/CandidateRepo.scala` | 148 |
| db | `db/UpstreamQuotaRepo.scala` | 49 |
| server | `server/acquire/AcquireClients.scala` | 24 |
| server | `server/acquire/AcquireHandlers.scala` | 829 |
| server | `server/acquire/AcquireMcp.scala` | 252 |
| server | `server/acquire/AcquireRoutes.scala` | 36 |
| server | `server/acquire/AcquireSettings.scala` | 20 |
| server | `server/acquire/ArchiveClient.scala` | 10 |
| server | `server/acquire/ArchiveOrg.scala` | 49 |
| server | `server/acquire/ArxivIndex.scala` | 28 |
| server | `server/acquire/CandidateSearch.scala` | 141 |
| server | `server/acquire/CitationAlerts.scala` | 15 |
| server | `server/acquire/CitationsClient.scala` | 10 |
| server | `server/acquire/Clients.scala` | 16 |
| server | `server/acquire/CoreApi.scala` | 38 |
| server | `server/acquire/CoreBudget.scala` | 106 |
| server | `server/acquire/CoreFailure.scala` | 13 |
| server | `server/acquire/CoreIndex.scala` | 28 |
| server | `server/acquire/CoreQuotaStore.scala` | 16 |
| server | `server/acquire/CrossrefIndex.scala` | 29 |
| server | `server/acquire/DiscoveryClient.scala` | 18 |
| server | `server/acquire/EuropePmcIndex.scala` | 27 |
| server | `server/acquire/FetcherGeneration.scala` | 33 |
| server | `server/acquire/Http.scala` | 32 |
| server | `server/acquire/HttpPlugin.scala` | 9 |
| server | `server/acquire/Indexes.scala` | 35 |
| server | `server/acquire/IngestJobs.scala` | 20 |
| server | `server/acquire/IngestMeta.scala` | 20 |
| server | `server/acquire/IngestOutcome.scala` | 8 |
| server | `server/acquire/NoAlerts.scala` | 12 |
| server | `server/acquire/OaFetch.scala` | 28 |
| server | `server/acquire/OpenAlexCrossref.scala` | 118 |
| server | `server/acquire/OpenAlexIndex.scala` | 49 |
| server | `server/acquire/RetractionWatch.scala` | 8 |
| server | `server/acquire/RetractionWatchClient.scala` | 8 |
| server | `server/acquire/ScholarlyIndex.scala` | 19 |
| server | `server/acquire/SemanticScholar.scala` | 73 |
| server | `server/acquire/SkeletonIngest.scala` | 88 |
| server | `server/acquire/SttpHttp.scala` | 38 |
| server | `server/acquire/WikiClient.scala` | 8 |
| server | `server/acquire/Wikipedia.scala` | 33 |
| server | `server/acquire/fetchers/ArchiveOrgFetcher.scala` | 27 |
| server | `server/acquire/fetchers/ArxivFetcher.scala` | 13 |
| server | `server/acquire/fetchers/Builtin.scala` | 14 |
| server | `server/acquire/fetchers/BuiltinConfig.scala` | 10 |
| server | `server/acquire/fetchers/BuiltinFetchers.scala` | 82 |
| server | `server/acquire/fetchers/BuiltinWith.scala` | 16 |
| server | `server/acquire/fetchers/CoreConfig.scala` | 16 |
| server | `server/acquire/fetchers/CoreFetcher.scala` | 19 |
| server | `server/acquire/fetchers/Fetching.scala` | 32 |
| server | `server/acquire/fetchers/OaIndex.scala` | 237 |
| server | `server/acquire/fetchers/Pmc.scala` | 100 |
| server | `server/acquire/fetchers/UnpaywallConfig.scala` | 14 |
| server | `server/agents/AcquireRecheckService.scala` | 537 |

## library.calibre

| module | file | lines |
|---|---|---:|
| api | `api/CalibreEntryCheck.scala` | 11 |
| api | `api/CalibreGap.scala` | 24 |
| api | `api/CalibreGaps.scala` | 9 |
| api | `api/CalibrePushRequest.scala` | 9 |
| api | `api/CalibrePushResult.scala` | 11 |
| config | `config/CalibreCliConfig.scala` | 25 |

## library.citations

| module | file | lines |
|---|---|---:|
| api | `api/AssessRequest.scala` | 11 |
| api | `api/Assessment.scala` | 27 |
| api | `api/ContradictionRow.scala` | 22 |
| api | `api/ContradictionsResult.scala` | 11 |
| api | `api/Edge.scala` | 3 |
| api | `api/EdgeInput.scala` | 14 |
| api | `api/EdgesPatch.scala` | 13 |
| api | `api/EdgesResolved.scala` | 10 |
| api | `api/FollowupRow.scala` | 21 |
| api | `api/FollowupsRequest.scala` | 15 |
| api | `api/FollowupsResult.scala` | 17 |
| api | `api/GraphLink.scala` | 15 |
| api | `api/GraphNode.scala` | 18 |
| api | `api/NeighborRow.scala` | 18 |
| api | `api/NeighborTarget.scala` | 19 |
| api | `api/RefRow.scala` | 17 |
| api | `api/RefsRequest.scala` | 9 |
| api | `api/RefsResult.scala` | 18 |
| api | `api/SourceGraph.scala` | 16 |

## library.findings

| module | file | lines |
|---|---|---:|
| api | `api/Finding.scala` | 16 |
| api | `api/FindingInput.scala` | 40 |
| api | `api/FindingMatch.scala` | 22 |
| api | `api/FindingRow.scala` | 23 |
| api | `api/FindingsPatch.scala` | 17 |
| api | `api/SourceWithFindings.scala` | 15 |

## library.ingest

| module | file | lines |
|---|---|---:|
| api | `api/Ingest.scala` | 22 |
| api | `api/IngestAccepted.scala` | 11 |
| api | `api/IngestMarkdown.scala` | 66 |
| api | `api/IngestParams.scala` | 17 |
| api | `api/IngestReport.scala` | 20 |
| api | `api/IngestRequest.scala` | 16 |
| api | `api/IngestStep.scala` | 9 |
| api | `api/PdfInfo.scala` | 11 |
| api | `api/SeedRequest.scala` | 9 |
| api | `api/SeedResult.scala` | 15 |
| config | `config/ConverterConfig.scala` | 32 |
| core | `domain/Jats.scala` | 378 |
| db | `db/Importer.scala` | 69 |
| db | `db/Originals.scala` | 76 |
| server | `server/IngestHandlers.scala` | 591 |
| server | `server/StoredOriginals.scala` | 66 |
| server | `server/acquire/QueuedIngest.scala` | 80 |
| server | `server/importers/AwmcImporter.scala` | 80 |
| server | `server/importers/ChronOntologyImporter.scala` | 118 |
| server | `server/importers/CliopatriaImporter.scala` | 107 |
| server | `server/importers/GazetteerImporter.scala` | 24 |

## library.search

| module | file | lines |
|---|---|---:|
| api | `api/Aggregate.scala` | 91 |
| api | `api/Bucket.scala` | 28 |
| api | `api/BucketSample.scala` | 9 |
| api | `api/Facet.scala` | 15 |
| api | `api/FacetField.scala` | 20 |
| api | `api/Filter.scala` | 105 |
| api | `api/FilterDocs.scala` | 102 |
| api | `api/FilterQuery.scala` | 75 |
| api | `api/GeoHit.scala` | 24 |
| api | `api/GeoHitPage.scala` | 18 |
| api | `api/GeoTime.scala` | 13 |
| api | `api/Hit.scala` | 11 |
| api | `api/InstanceLabel.scala` | 9 |
| api | `api/Label.scala` | 4 |
| api | `api/LabelRename.scala` | 9 |
| api | `api/LabelRenamed.scala` | 13 |
| api | `api/LabelRenames.scala` | 18 |
| api | `api/PassageHit.scala` | 11 |
| api | `api/Query.scala` | 217 |
| api | `api/SearchHit.scala` | 25 |
| api | `api/SearchResult.scala` | 18 |
| api | `api/Sort.scala` | 33 |
| api | `api/TimeBucket.scala` | 9 |
| api | `api/TimeHistogram.scala` | 22 |
| config | `config/QueryConfig.scala` | 14 |
| core | `domain/Highlight.scala` | 123 |
| db | `db/AggregateRepo.scala` | 59 |
| db | `db/FilterSql.scala` | 590 |
| db | `db/QueryRepo.scala` | 141 |
| db | `db/Search.scala` | 140 |

## library.sources

| module | file | lines |
|---|---|---:|
| api | `api/CitekeyInput.scala` | 7 |
| api | `api/CorrespondingSource.scala` | 12 |
| api | `api/DeleteBlocker.scala` | 11 |
| api | `api/DeleteBlockerKind.scala` | 21 |
| api | `api/DeleteCount.scala` | 9 |
| api | `api/DeletePart.scala` | 34 |
| api | `api/ExternalId.scala` | 38 |
| api | `api/MetadataField.scala` | 28 |
| api | `api/MetadataPatch.scala` | 47 |
| api | `api/NoteImport.scala` | 18 |
| api | `api/PassageResult.scala` | 19 |
| api | `api/PassagesMarkdown.scala` | 32 |
| api | `api/Provenance.scala` | 4 |
| api | `api/Rename.scala` | 31 |
| api | `api/RenameCount.scala` | 9 |
| api | `api/RenameCounts.scala` | 17 |
| api | `api/RenameRequest.scala` | 9 |
| api | `api/ResolvedSource.scala` | 19 |
| api | `api/Source.scala` | 27 |
| api | `api/SourceAlias.scala` | 9 |
| api | `api/SourceCoverParams.scala` | 8 |
| api | `api/SourceDelete.scala` | 99 |
| api | `api/SourceDeleteRequest.scala` | 12 |
| api | `api/SourceDeletion.scala` | 27 |
| api | `api/SourceDetail.scala` | 31 |
| api | `api/SourceKinds.scala` | 53 |
| api | `api/SourceMatch.scala` | 23 |
| api | `api/SourcePatch.scala` | 25 |
| api | `api/SourcePut.scala` | 58 |
| api | `api/SourceRef.scala` | 35 |
| api | `api/SourceRenamed.scala` | 17 |
| api | `api/SourceSection.scala` | 14 |
| config | `config/PassageLimits.scala` | 23 |
| config | `config/SeaFloorSource.scala` | 15 |
| core | `domain/Chunk.scala` | 6 |
| core | `domain/Citekeys.scala` | 10 |
| core | `domain/ExternalIds.scala` | 30 |
| core | `domain/PassageFinder.scala` | 244 |
| db | `db/SourceDeleteRepo.scala` | 235 |
| db | `db/SourceRepo.scala` | 994 |
| files | `files/Export.scala` | 71 |
| files | `files/Frontmatter.scala` | 189 |
| server | `server/FullText.scala` | 11 |

## research.articles

| module | file | lines |
|---|---|---:|
| api | `api/Article.scala` | 15 |
| api | `api/ArticleCoverage.scala` | 190 |
| api | `api/ArticleDeleted.scala` | 9 |
| api | `api/ArticleEdits.scala` | 209 |
| api | `api/ArticleFilter.scala` | 93 |
| api | `api/ArticleImport.scala` | 17 |
| api | `api/ArticleKind.scala` | 22 |
| api | `api/ArticleKindCount.scala` | 9 |
| api | `api/ArticleLink.scala` | 3 |
| api | `api/ArticleLinks.scala` | 124 |
| api | `api/ArticleList.scala` | 22 |
| api | `api/ArticleMarkdown.scala` | 142 |
| api | `api/ArticlePatch.scala` | 28 |
| api | `api/ArticlePlans.scala` | 68 |
| api | `api/ArticlePut.scala` | 36 |
| api | `api/ArticleRef.scala` | 3 |
| api | `api/ArticleRow.scala` | 22 |
| api | `api/ArticleSummary.scala` | 21 |
| api | `api/ArticleVersion.scala` | 17 |
| api | `api/ArticleVersionFull.scala` | 17 |
| api | `api/ArticleView.scala` | 16 |
| api | `api/Articles.scala` | 166 |
| api | `api/Backlink.scala` | 11 |
| api | `api/CitedSource.scala` | 18 |
| api | `api/Coverage.scala` | 28 |
| api | `api/CoverageDrift.scala` | 9 |
| api | `api/CoverageEdge.scala` | 9 |
| api | `api/CoverageEntry.scala` | 11 |
| api | `api/CoverageReason.scala` | 35 |
| api | `api/CoverageSearch.scala` | 11 |
| api | `api/CoverageSource.scala` | 17 |
| api | `api/CritiquePair.scala` | 27 |
| api | `api/CritiquePairing.scala` | 170 |
| api | `api/CritiqueReason.scala` | 47 |
| api | `api/CritiqueRef.scala` | 23 |
| api | `api/CritiqueSide.scala` | 16 |
| api | `api/CritiqueState.scala` | 20 |
| api | `api/Critiques.scala` | 191 |
| api | `api/DriftKind.scala` | 21 |
| api | `api/ResolvedArticle.scala` | 9 |
| api | `api/TocEntry.scala` | 9 |
| config | `config/CitationCount.scala` | 9 |
| core | `domain/TextAnchor.scala` | 95 |
| db | `db/ArticleRepo.scala` | 466 |
| db | `db/CoverageRepo.scala` | 50 |
| db | `db/CritiqueRepo.scala` | 94 |
| files | `files/ArticleNote.scala` | 155 |
| server | `server/ArticlePage.scala` | 17 |
| server | `server/Citation.scala` | 16 |
| server | `server/CitedEntry.scala` | 19 |
| server | `server/HtmlRender.scala` | 100 |
| server | `server/PageBuilder.scala` | 83 |
| server | `server/PageKind.scala` | 15 |
| server | `server/Pages.scala` | 291 |
| server | `server/SourcePage.scala` | 27 |

## research.articles.lint

| module | file | lines |
|---|---|---:|
| server | `server/MarkdownLint.scala` | 1336 |

## research.articles.review

| module | file | lines |
|---|---|---:|
| api | `api/ArticleReviewView.scala` | 64 |
| api | `api/ReviewBadge.scala` | 19 |
| api | `api/ReviewState.scala` | 23 |
| core | `domain/ArticleReviewStatus.scala` | 160 |
| db | `db/ArticleReviewRepo.scala` | 99 |

## automation.activity

| module | file | lines |
|---|---|---:|
| api | `api/Activity.scala` | 73 |
| api | `api/ActivityMarkdown.scala` | 53 |
| core | `domain/activity/ActivityCursor.scala` | 20 |
| core | `domain/activity/ActivityQuery.scala` | 16 |
| core | `domain/activity/ActivityRules.scala` | 100 |
| server | `domain/activity/ActivityControllers.scala` | 18 |
| server | `domain/activity/ActivityRepo.scala` | 138 |
| server | `domain/activity/ActivityService.scala` | 35 |

## automation.agents

| module | file | lines |
|---|---|---:|
| api | `api/AgentLeaseInfo.scala` | 14 |
| api | `api/AgentMode.scala` | 31 |
| api | `api/AgentStateDeleted.scala` | 14 |
| api | `api/AgentStateEntry.scala` | 17 |
| api | `api/AgentStatePut.scala` | 15 |
| api | `api/Agents.scala` | 473 |
| api | `api/AgentsApi.scala` | 176 |
| api | `api/AgentsMarkdown.scala` | 514 |
| api | `api/ModelChains.scala` | 308 |
| api | `api/PlanQuota.scala` | 328 |
| api | `api/RunCompare.scala` | 102 |
| api | `api/Stalls.scala` | 174 |
| api | `api/StallsMarkdown.scala` | 18 |
| api | `api/StrictJson.scala` | 76 |
| config | `config/AgentBudgetsConfig.scala` | 32 |
| config | `config/AgentCompileConfig.scala` | 16 |
| config | `config/AgentEnrichConfig.scala` | 16 |
| config | `config/AgentEnrichGuardsConfig.scala` | 14 |
| config | `config/AgentInferFilterConfig.scala` | 10 |
| config | `config/AgentPlanConfig.scala` | 8 |
| config | `config/AgentProviderConfig.scala` | 19 |
| config | `config/AgentProvidersConfig.scala` | 13 |
| config | `config/AgentReviewConfig.scala` | 31 |
| config | `config/AgentRolesConfig.scala` | 18 |
| config | `config/AgentRuntimesConfig.scala` | 10 |
| config | `config/AgentSeedConfig.scala` | 8 |
| config | `config/AgentStepConfig.scala` | 8 |
| config | `config/AgentToolsConfig.scala` | 13 |
| config | `config/AgentTraceConfig.scala` | 10 |
| config | `config/AgentWatchdogConfig.scala` | 26 |
| config | `config/AgentsConfig.scala` | 49 |
| config | `config/ClaudeCodeConfig.scala` | 39 |
| config | `config/ClaudePlanConfig.scala` | 19 |
| config | `config/CodexConfig.scala` | 36 |
| config | `config/FinalPanel.scala` | 13 |
| config | `config/OpencodeConfig.scala` | 9 |
| config | `config/PlanPoolConfig.scala` | 13 |
| config | `config/QuotaConfig.scala` | 17 |
| config | `config/RuntimeConfig.scala` | 17 |
| core | `domain/CodexRetention.scala` | 43 |
| core | `domain/CompareRuns.scala` | 378 |
| core | `domain/SeedPlanDefaults.scala` | 3 |
| db | `db/AgentRepo.scala` | 950 |
| db | `db/AgentStateRepo.scala` | 33 |
| db | `db/ModelSettingsRepo.scala` | 64 |
| db | `db/PlanQuotaRepo.scala` | 118 |
| server | `server/agents/AgentDef.scala` | 27 |
| server | `server/agents/AgentDefinitions.scala` | 252 |
| server | `server/agents/AgentDoctor.scala` | 406 |
| server | `server/agents/AgentFacetsInput.scala` | 15 |
| server | `server/agents/AgentGenerator.scala` | 400 |
| server | `server/agents/AgentGetSourceInput.scala` | 30 |
| server | `server/agents/AgentLease.scala` | 110 |
| server | `server/agents/AgentRuntime.scala` | 29 |
| server | `server/agents/AgentSearchInput.scala` | 44 |
| server | `server/agents/AgentTrees.scala` | 165 |
| server | `server/agents/AgentUpstream.scala` | 101 |
| server | `server/agents/AgentsHandlers.scala` | 1031 |
| server | `server/agents/AgentsRoutes.scala` | 37 |
| server | `server/agents/AgentsSettings.scala` | 79 |
| server | `server/agents/AgentsTools.scala` | 816 |
| server | `server/agents/AnswerSchemas.scala` | 60 |
| server | `server/agents/Audience.scala` | 17 |
| server | `server/agents/BuiltinAi.scala` | 48 |
| server | `server/agents/Checked.scala` | 17 |
| server | `server/agents/ClaudeCode.scala` | 428 |
| server | `server/agents/ClaudeCodeClient.scala` | 121 |
| server | `server/agents/ClaudeCodeEnv.scala` | 15 |
| server | `server/agents/CliOutcome.scala` | 18 |
| server | `server/agents/CliOutcomes.scala` | 75 |
| server | `server/agents/CliProcess.scala` | 229 |
| server | `server/agents/CliRuntime.scala` | 31 |
| server | `server/agents/Codex.scala` | 125 |
| server | `server/agents/CodexExecClient.scala` | 93 |
| server | `server/agents/CodexHome.scala` | 149 |
| server | `server/agents/CompareRunsViews.scala` | 21 |
| server | `server/agents/Engine.scala` | 599 |
| server | `server/agents/FirstCallProbe.scala` | 33 |
| server | `server/agents/FollowupsInput.scala` | 17 |
| server | `server/agents/GenSettings.scala` | 32 |
| server | `server/agents/GeneratePlugin.scala` | 52 |
| server | `server/agents/Halt.scala` | 26 |
| server | `server/agents/HostAiProvider.scala` | 14 |
| server | `server/agents/InteractiveSpec.scala` | 11 |
| server | `server/agents/LlmCall.scala` | 27 |
| server | `server/agents/LlmClient.scala` | 157 |
| server | `server/agents/ModelLimitReached.scala` | 12 |
| server | `server/agents/ModelPicker.scala` | 430 |
| server | `server/agents/OaBacklog.scala` | 454 |
| server | `server/agents/Opencode.scala` | 92 |
| server | `server/agents/OpencodeRunClient.scala` | 60 |
| server | `server/agents/PickFailure.scala` | 17 |
| server | `server/agents/Picked.scala` | 19 |
| server | `server/agents/PlanPools.scala` | 644 |
| server | `server/agents/PluginAiClient.scala` | 125 |
| server | `server/agents/PoolSink.scala` | 17 |
| server | `server/agents/Prompt.scala` | 52 |
| server | `server/agents/Prompts.scala` | 43 |
| server | `server/agents/QuotaGuard.scala` | 56 |
| server | `server/agents/RefreshGate.scala` | 29 |
| server | `server/agents/Refreshed.scala` | 10 |
| server | `server/agents/RunCtx.scala` | 637 |
| server | `server/agents/RunGrant.scala` | 15 |
| server | `server/agents/RunScope.scala` | 8 |
| server | `server/agents/RunTokenGate.scala` | 52 |
| server | `server/agents/RunTokens.scala` | 55 |
| server | `server/agents/SkillDef.scala` | 13 |
| server | `server/agents/StepAgent.scala` | 26 |
| server | `server/agents/StrictJsonSchema.scala` | 130 |
| server | `server/agents/ToolName.scala` | 35 |
| server | `server/agents/ToolResultCap.scala` | 95 |
| server | `server/agents/ToolStyle.scala` | 17 |
| server | `server/agents/Toolbox.scala` | 166 |
| server | `server/agents/Trace.scala` | 35 |
| server | `server/agents/WebAccess.scala` | 15 |
| server | `server/agents/Writes.scala` | 14 |

## automation.approvals

| module | file | lines |
|---|---|---:|
| api | `api/ApprovalDigest.scala` | 122 |
| api | `api/ApprovalDigestMarkdown.scala` | 37 |
| api | `api/ListFilters.scala` | 241 |
| api | `api/ReviewApproval.scala` | 9 |
| config | `config/AgentApprovalsConfig.scala` | 24 |
| config | `config/AutoApprovalsConfig.scala` | 25 |
| core | `domain/ApprovalModes.scala` | 7 |
| server | `server/ListQueries.scala` | 58 |
| server | `server/agents/ApprovalDigestService.scala` | 99 |
| server | `server/agents/Approvals.scala` | 211 |
| server | `server/agents/AutoApprovals.scala` | 221 |
| server | `server/agents/Decided.scala` | 16 |

## automation.autopilot

| module | file | lines |
|---|---|---:|
| api | `api/Autopilot.scala` | 139 |
| api | `api/AutopilotMarkdown.scala` | 102 |
| config | `config/AgentAutopilotConfig.scala` | 54 |
| core | `domain/AutoEnrich.scala` | 60 |
| core | `domain/AutopilotPolicy.scala` | 445 |
| db | `db/AutopilotRepo.scala` | 126 |
| server | `server/agents/AutopilotService.scala` | 458 |

## automation.goals

| module | file | lines |
|---|---|---:|
| api | `api/Goal.scala` | 22 |
| api | `api/GoalDeleted.scala` | 14 |
| api | `api/GoalList.scala` | 19 |
| api | `api/GoalPatch.scala` | 23 |
| api | `api/GoalPlans.scala` | 119 |
| api | `api/GoalPut.scala` | 22 |
| api | `api/GoalStages.scala` | 14 |
| api | `api/GoalsExport.scala` | 97 |
| api | `api/ReviewGoal.scala` | 9 |
| core | `domain/GoalFocus.scala` | 459 |
| core | `domain/PlanGoal.scala` | 189 |
| db | `db/FocusRepo.scala` | 181 |
| db | `db/GoalLinkRepo.scala` | 228 |
| db | `db/GoalRepo.scala` | 158 |
| server | `server/agents/GoalFocusService.scala` | 368 |
| server | `server/agents/PlanGoalPipeline.scala` | 228 |

## automation.managers

| module | file | lines |
|---|---|---:|
| api | `api/Managers.scala` | 533 |
| api | `api/ManagersApi.scala` | 177 |
| api | `api/ManagersMarkdown.scala` | 165 |
| config | `config/AgentManagersConfig.scala` | 18 |
| config | `config/ReviewManagerConfig.scala` | 25 |
| config | `config/SimpleManagerConfig.scala` | 8 |
| config | `config/WatchManagerConfig.scala` | 12 |
| core | `domain/HintProposals.scala` | 121 |
| core | `domain/ManagerDefaults.scala` | 12 |
| core | `domain/ManagerPolicy.scala` | 617 |
| core | `domain/ManagerRunDefaults.scala` | 3 |
| core | `domain/ReviewManagerDefaults.scala` | 15 |
| core | `domain/WatchManagerDefaults.scala` | 3 |
| db | `db/ManagerRepo.scala` | 1138 |
| server | `server/agents/DrainService.scala` | 86 |
| server | `server/agents/GoalSweep.scala` | 68 |
| server | `server/agents/HintEffectPipeline.scala` | 130 |
| server | `server/agents/ManagerDefaultsViews.scala` | 16 |
| server | `server/agents/ManagerSupervisor.scala` | 1325 |
| server | `server/agents/ManagersHandlers.scala` | 911 |
| server | `server/agents/ManagersRoutes.scala` | 41 |
| server | `server/agents/SeedService.scala` | 104 |

## automation.pipelines

| module | file | lines |
|---|---|---:|
| config | `config/AgentVerifyConfig.scala` | 8 |
| core | `domain/AgentProposals.scala` | 164 |
| core | `domain/AgentVerify.scala` | 208 |
| core | `domain/CompileArticle.scala` | 432 |
| core | `domain/EnrichSource.scala` | 603 |
| core | `domain/FilterInference.scala` | 102 |
| core | `domain/PlanArticles.scala` | 161 |
| server | `server/agents/AcquisitionSweepPipeline.scala` | 341 |
| server | `server/agents/CompileArticlePipeline.scala` | 346 |
| server | `server/agents/CurrencyCheckPipeline.scala` | 104 |
| server | `server/agents/EnrichSourcePipeline.scala` | 342 |
| server | `server/agents/InferFilterPipeline.scala` | 179 |
| server | `server/agents/PipelineDef.scala` | 20 |
| server | `server/agents/Pipelines.scala` | 30 |
| server | `server/agents/ProposeArticlesPipeline.scala` | 196 |
| server | `server/agents/ReviewArticlePipeline.scala` | 850 |

## automation.review

| module | file | lines |
|---|---|---:|
| api | `api/LedgerRow.scala` | 22 |
| api | `api/ReviewFinding.scala` | 26 |
| api | `api/ReviewRound.scala` | 16 |
| core | `domain/ReviewArticle.scala` | 2013 |
| core | `domain/ReviewLedger.scala` | 601 |
| db | `db/ReviewLedgerRepo.scala` | 28 |

## automation.scheduler

| module | file | lines |
|---|---|---:|
| api | `api/AgentScheduler.scala` | 111 |
| api | `api/AgentSchedulerMarkdown.scala` | 65 |
| config | `config/AgentSchedulerConfig.scala` | 55 |
| core | `domain/Cron.scala` | 139 |
| core | `domain/PlanReadSchedule.scala` | 28 |
| core | `domain/ReadSchedule.scala` | 7 |
| core | `domain/SchedulerDefaults.scala` | 13 |
| core | `domain/SchedulerPolicy.scala` | 458 |
| core | `domain/Scheduling.scala` | 218 |
| core | `domain/WatchSchedules.scala` | 97 |
| server | `server/agents/SchedulerService.scala` | 519 |

## exchange.bundle

| module | file | lines |
|---|---|---:|
| api | `api/Bundle.scala` | 21 |
| api | `api/BundleAlias.scala` | 7 |
| api | `api/BundleArticle.scala` | 20 |
| api | `api/BundleArticleAlias.scala` | 7 |
| api | `api/BundleArticleVersion.scala` | 16 |
| api | `api/BundleAssessment.scala` | 16 |
| api | `api/BundleAsset.scala` | 24 |
| api | `api/BundleAssetLink.scala` | 19 |
| api | `api/BundleAttestation.scala` | 28 |
| api | `api/BundleBlobs.scala` | 9 |
| api | `api/BundleCorrection.scala` | 20 |
| api | `api/BundleEdge.scala` | 7 |
| api | `api/BundleEvent.scala` | 40 |
| api | `api/BundleEventAlias.scala` | 7 |
| api | `api/BundleEventCandidate.scala` | 18 |
| api | `api/BundleEventCatalogue.scala` | 27 |
| api | `api/BundleEventCheck.scala` | 7 |
| api | `api/BundleEventDating.scala` | 16 |
| api | `api/BundleEventEdge.scala` | 13 |
| api | `api/BundleEventParticipant.scala` | 8 |
| api | `api/BundleEventSets.scala` | 11 |
| api | `api/BundleFileInfo.scala` | 11 |
| api | `api/BundleFiles.scala` | 42 |
| api | `api/BundleFinding.scala` | 24 |
| api | `api/BundleGoal.scala` | 28 |
| api | `api/BundleGoalAlias.scala` | 7 |
| api | `api/BundleGoalLink.scala` | 13 |
| api | `api/BundleGoalSchedule.scala` | 6 |
| api | `api/BundleImportResult.scala` | 26 |
| api | `api/BundleInstance.scala` | 11 |
| api | `api/BundleKindCounts.scala` | 23 |
| api | `api/BundleManifest.scala` | 23 |
| api | `api/BundleMarkdown.scala` | 67 |
| api | `api/BundlePeriod.scala` | 18 |
| api | `api/BundlePlace.scala` | 24 |
| api | `api/BundlePlaceAlias.scala` | 7 |
| api | `api/BundlePlaceLink.scala` | 5 |
| api | `api/BundlePlaceName.scala` | 6 |
| api | `api/BundlePlaceRelation.scala` | 6 |
| api | `api/BundlePlaceType.scala` | 6 |
| api | `api/BundleQueueItem.scala` | 19 |
| api | `api/BundleRange.scala` | 8 |
| api | `api/BundleReference.scala` | 11 |
| api | `api/BundleSiteType.scala` | 6 |
| api | `api/BundleSource.scala` | 35 |
| api | `api/BundleTombstone.scala` | 9 |
| api | `api/BundleUnresolvedEdge.scala` | 7 |
| api | `api/BundleUpload.scala` | 11 |
| api | `api/BundleVerifyResult.scala` | 15 |
| config | `config/BundlesConfig.scala` | 9 |
| core | `domain/BundlePlan.scala` | 182 |
| db | `db/BundleExport.scala` | 108 |
| db | `db/BundleImport.scala` | 1151 |
| db | `db/BundleRepo.scala` | 1015 |
| db | `db/BundleScope.scala` | 17 |
| files | `files/BundleEntities.scala` | 218 |
| files | `files/BundleFormat.scala` | 152 |
| files | `files/BundleReader.scala` | 108 |
| files | `files/BundleTar.scala` | 182 |
| server | `server/BundleHandlers.scala` | 307 |

## exchange.merge

| module | file | lines |
|---|---|---:|
| api | `api/InitPlan.scala` | 30 |
| api | `api/IssueCondition.scala` | 11 |
| api | `api/IssueDetail.scala` | 14 |
| api | `api/IssueField.scala` | 11 |
| api | `api/IssueHunk.scala` | 15 |
| api | `api/IssuePlace.scala` | 15 |
| api | `api/IssueSide.scala` | 13 |
| api | `api/IssueSpan.scala` | 9 |
| api | `api/IssueText.scala` | 11 |
| api | `api/MergeApplyJob.scala` | 58 |
| api | `api/MergeApplyRequest.scala` | 11 |
| api | `api/MergeApplyResult.scala` | 28 |
| api | `api/MergeDecision.scala` | 30 |
| api | `api/MergeDetail.scala` | 16 |
| api | `api/MergeImportRequest.scala` | 14 |
| api | `api/MergeInfo.scala` | 22 |
| api | `api/MergeIssue.scala` | 28 |
| api | `api/MergeIssueDetail.scala` | 101 |
| api | `api/MergeJobState.scala` | 18 |
| api | `api/MergeLedger.scala` | 11 |
| api | `api/MergeMarkdown.scala` | 201 |
| api | `api/MergePlan.scala` | 32 |
| api | `api/MergePlanRequest.scala` | 11 |
| api | `api/MergePolicy.scala` | 11 |
| api | `api/MergeResolution.scala` | 14 |
| api | `api/MergeResolutions.scala` | 23 |
| api | `api/MergeResolutionsText.scala` | 67 |
| api | `api/MergeSession.scala` | 14 |
| api | `api/MergeTally.scala` | 30 |
| config | `config/MergeConfig.scala` | 33 |
| config | `config/MergeWeights.scala` | 30 |
| core | `domain/Diff3.scala` | 118 |
| core | `domain/MergeFold.scala` | 513 |
| core | `domain/MergePlanner.scala` | 2425 |
| core | `domain/MergeSettings.scala` | 12 |
| core | `domain/MergeText.scala` | 299 |
| core | `domain/SourceMatcher.scala` | 240 |
| db | `db/MergeRepo.scala` | 225 |
| db | `db/MergeState.scala` | 401 |
| files | `files/MergeResolutionsFile.scala` | 100 |
| server | `server/MergeApplyJobs.scala` | 64 |

## operations.backup

| module | file | lines |
|---|---|---:|
| api | `api/Backup.scala` | 137 |
| api | `api/BackupInfo.scala` | 11 |
| api | `api/BackupResult.scala` | 16 |
| api | `api/BackupState.scala` | 10 |
| api | `api/BackupStatus.scala` | 27 |
| api | `api/BackupsUsage.scala` | 11 |
| api | `api/RestoreRequest.scala` | 10 |
| api | `api/RestoreResult.scala` | 20 |
| config | `config/BackupsConfig.scala` | 11 |
| db | `db/BackupRepo.scala` | 18 |
| server | `server/BackupJobs.scala` | 68 |
| server | `server/BackupService.scala` | 249 |
| server | `server/PgRelocation.scala` | 113 |

## operations.basemap

| module | file | lines |
|---|---|---:|
| api | `api/BaseMapSource.scala` | 15 |
| api | `api/BaseStyles.scala` | 13 |
| api | `api/Basemap.scala` | 170 |
| api | `api/BasemapApi.scala` | 76 |
| api | `api/BasemapFetchQuery.scala` | 11 |
| api | `api/BasemapFocus.scala` | 20 |
| api | `api/BasemapLayer.scala` | 22 |
| api | `api/BasemapLayerInfo.scala` | 33 |
| api | `api/BasemapMarkdown.scala` | 72 |
| api | `api/BasemapOnDemand.scala` | 19 |
| api | `api/BasemapRegion.scala` | 10 |
| api | `api/BasemapRemoveQuery.scala` | 3 |
| api | `api/BasemapRemoved.scala` | 10 |
| api | `api/BasemapStatus.scala` | 24 |
| api | `api/BasemapStyleState.scala` | 12 |
| config | `config/BasemapBathymetryConfig.scala` | 20 |
| config | `config/BasemapConfig.scala` | 32 |
| config | `config/BasemapGebcoConfig.scala` | 9 |
| config | `config/BasemapLabelsConfig.scala` | 15 |
| config | `config/BasemapOnDemandConfig.scala` | 15 |
| config | `config/BasemapTerrainConfig.scala` | 14 |
| config | `config/BasemapVectorConfig.scala` | 16 |
| server | `server/basemap/BasemapFetch.scala` | 424 |
| server | `server/basemap/BasemapLabels.scala` | 360 |
| server | `server/basemap/BasemapService.scala` | 1151 |
| server | `server/basemap/ByteOffset.scala` | 7 |
| server | `server/basemap/Degrees.scala` | 6 |
| server | `server/basemap/DegreesPerPixel.scala` | 7 |
| server | `server/basemap/DiskLru.scala` | 83 |
| server | `server/basemap/GebcoPart.scala` | 10 |
| server | `server/basemap/GebcoPlan.scala` | 15 |
| server | `server/basemap/GebcoSeaFloor.scala` | 179 |
| server | `server/basemap/GeoTiffGrid.scala` | 182 |
| server | `server/basemap/GridWindow.scala` | 7 |
| server | `server/basemap/JavaRangeHttp.scala` | 131 |
| server | `server/basemap/Mvt.scala` | 343 |
| server | `server/basemap/OnDemandTiles.scala` | 248 |
| server | `server/basemap/PixelCount.scala` | 7 |
| server | `server/basemap/PmTiles.scala` | 303 |
| server | `server/basemap/PmTilesFile.scala` | 126 |
| server | `server/basemap/PmTilesWriter.scala` | 119 |
| server | `server/basemap/Png.scala` | 141 |
| server | `server/basemap/RangeHttp.scala` | 43 |
| server | `server/basemap/RemoteFile.scala` | 6 |
| server | `server/basemap/RowRun.scala` | 6 |
| server | `server/basemap/TerrainResample.scala` | 59 |
| server | `server/basemap/TerrariumPng.scala` | 142 |
| server | `server/basemap/Vp8l.scala` | 509 |

## operations.instance

| module | file | lines |
|---|---|---:|
| api | `api/About.scala` | 29 |
| api | `api/AboutMarkdown.scala` | 50 |
| api | `api/BuildStamp.scala` | 19 |
| api | `api/CorpusCounts.scala` | 19 |
| api | `api/DebounceConfig.scala` | 11 |
| api | `api/EventsUiConfig.scala` | 15 |
| api | `api/Health.scala` | 16 |
| api | `api/MapStartConfig.scala` | 13 |
| api | `api/MapStartMode.scala` | 8 |
| api | `api/TimeConfig.scala` | 11 |
| api | `api/UiApi.scala` | 31 |
| api | `api/UiConfig.scala` | 38 |
| api | `api/UiNotices.scala` | 59 |
| api | `api/UpdateNotice.scala` | 12 |
| api | `api/Version.scala` | 8 |
| api | `api/VersionInfo.scala` | 9 |
| config | `config/UiDebounceSettings.scala` | 8 |
| config | `config/UiEventsSettings.scala` | 13 |
| config | `config/UiMapStartSettings.scala` | 9 |
| config | `config/UiSettings.scala` | 31 |
| config | `config/UiTimeSettings.scala` | 6 |
| db | `db/StatsRepo.scala` | 67 |
| db | `db/UiRepo.scala` | 74 |
| server | `server/TimeZoneCheck.scala` | 68 |

## operations.maintenance

| module | file | lines |
|---|---|---:|
| api | `api/ReindexResult.scala` | 12 |
| api | `api/Storage.scala` | 94 |
| api | `api/StorageAdvice.scala` | 23 |
| api | `api/StorageEntry.scala` | 24 |
| api | `api/StorageMarkdown.scala` | 26 |
| api | `api/StorageReport.scala` | 22 |
| api | `api/StorageVolume.scala` | 11 |
| api | `api/Vacuum.scala` | 39 |
| api | `api/VacuumPlan.scala` | 9 |
| api | `api/VacuumRequest.scala` | 18 |
| config | `config/MaintenanceConfig.scala` | 7 |
| config | `config/StorageConfig.scala` | 19 |
| db | `db/VacuumRepo.scala` | 43 |
| files | `files/DirSize.scala` | 104 |
| server | `server/ReadOnlyPool.scala` | 12 |
| server | `server/StorageCache.scala` | 69 |
| server | `server/StorageProbe.scala` | 225 |
| server | `server/VacuumJobs.scala` | 62 |

## operations.settings

| module | file | lines |
|---|---|---:|
| api | `api/ConfigEdit.scala` | 102 |
| api | `api/ConfigEditApi.scala` | 35 |
| api | `api/ConfigEditMarkdown.scala` | 55 |
| api | `api/ConfigFileInfo.scala` | 24 |
| api | `api/ReloadMarkdown.scala` | 22 |
| api | `api/ReloadReport.scala` | 23 |
| config | `config/ConfigReload.scala` | 57 |
| config | `config/ConfigReloadConfig.scala` | 5 |
| config | `config/UserConfigFile.scala` | 19 |
| config | `config/edit/ConfigFileState.scala` | 30 |
| config | `config/edit/ConfigFileWriter.scala` | 117 |
| config | `config/edit/ConfigSchema.scala` | 139 |
| config | `config/edit/ConfigTextEdit.scala` | 183 |
| config | `config/edit/EditedText.scala` | 6 |
| config | `config/edit/FileVersion.scala` | 22 |
| config | `config/edit/ReferenceDocs.scala` | 88 |
| config | `config/edit/SecretForm.scala` | 59 |
| config | `config/edit/SecretRule.scala` | 29 |
| config | `config/edit/SettingEdit.scala` | 32 |
| config | `config/edit/SettingError.scala` | 65 |
| config | `config/edit/SettingField.scala` | 22 |
| config | `config/edit/SettingKey.scala` | 49 |
| config | `config/edit/SettingKind.scala` | 27 |
| config | `config/edit/SettingPolicy.scala` | 54 |
| config | `config/edit/SettingReading.scala` | 16 |
| config | `config/edit/SettingShown.scala` | 51 |
| config | `config/edit/SettingValue.scala` | 99 |
| config | `config/edit/SettingsReader.scala` | 70 |
| config | `config/edit/WriteOutcome.scala` | 64 |
| config | `config/edit/Written.scala` | 16 |
| server | `domain/settings/RestartService.scala` | 34 |
| server | `domain/settings/SettingsControllers.scala` | 51 |
| server | `domain/settings/SettingsService.scala` | 168 |
| server | `domain/settings/SettingsSnapshot.scala` | 17 |
| server | `domain/settings/SettingsViews.scala` | 60 |
| server | `server/ConfigSource.scala` | 22 |
| server | `server/ConfigViews.scala` | 37 |
| server | `server/HotConfig.scala` | 16 |
| server | `server/MergeSettingsViews.scala` | 8 |

## personal

| module | file | lines |
|---|---|---:|
| api | `api/personal/Bookmark.scala` | 20 |
| api | `api/personal/BookmarkEdit.scala` | 11 |
| api | `api/personal/BookmarkId.scala` | 15 |
| api | `api/personal/BookmarkLabel.scala` | 14 |
| api | `api/personal/BookmarkNew.scala` | 13 |
| api | `api/personal/ItemKey.scala` | 13 |
| api | `api/personal/ItemKind.scala` | 40 |
| api | `api/personal/MapZoom.scala` | 13 |
| api | `api/personal/Note.scala` | 19 |
| api | `api/personal/NoteEdit.scala` | 13 |
| api | `api/personal/NoteId.scala` | 15 |
| api | `api/personal/NoteNew.scala` | 13 |
| api | `api/personal/NoteText.scala` | 14 |
| api | `api/personal/PersonalApi.scala` | 100 |
| api | `api/personal/PersonalIds.scala` | 9 |
| api | `api/personal/PersonalMarkdown.scala` | 51 |
| api | `api/personal/ReadMark.scala` | 19 |
| api | `api/personal/ReadMarkSet.scala` | 13 |
| api | `api/personal/ReadOpened.scala` | 13 |
| api | `api/personal/Target.scala` | 88 |
| api | `api/personal/UnreadPage.scala` | 13 |
| api | `api/personal/UnreadRow.scala` | 21 |
| api | `api/personal/ViewState.scala` | 14 |
| server | `domain/personal/BookmarkRepo.scala` | 97 |
| server | `domain/personal/BookmarkService.scala` | 130 |
| server | `domain/personal/ItemTitles.scala` | 45 |
| server | `domain/personal/NoteEditRefused.scala` | 6 |
| server | `domain/personal/NoteRepo.scala` | 84 |
| server | `domain/personal/NoteService.scala` | 159 |
| server | `domain/personal/PersonalControllers.scala` | 129 |
| server | `domain/personal/PersonalRules.scala` | 83 |
| server | `domain/personal/PersonalState.scala` | 53 |
| server | `domain/personal/PersonalTools.scala` | 67 |
| server | `domain/personal/ReadingRepo.scala` | 141 |
| server | `domain/personal/ReadingService.scala` | 132 |
| server | `domain/personal/ReadingShow.scala` | 15 |
| server | `domain/personal/TargetColumns.scala` | 29 |
| server | `domain/personal/UserId.scala` | 12 |

## api root

| module | file | lines |
|---|---|---:|
| api | `api/Api.scala` | 1307 |
| api | `api/ApiIndex.scala` | 36 |
| api | `api/OpsApi.scala` | 106 |

## api.ui

| module | file | lines |
|---|---|---:|
| api | `api/ConfidenceStyle.scala` | 49 |
| api | `api/MapStack.scala` | 26 |
| api | `api/Palette.scala` | 46 |
| api | `api/Wcag.scala` | 24 |
| api | `api/WebPaths.scala` | 17 |

## config root

| module | file | lines |
|---|---|---:|
| config | `config/AppConfig.scala` | 130 |
| config | `config/AuthConfig.scala` | 9 |
| config | `config/CliConfig.scala` | 6 |
| config | `config/CliPluginsConfig.scala` | 11 |
| config | `config/Config.scala` | 27 |
| config | `config/CorpusConfig.scala` | 5 |
| config | `config/EnvOverrides.scala` | 172 |
| config | `config/HotNode.scala` | 7 |
| config | `config/HotPlan.scala` | 74 |
| config | `config/InitConfig.scala` | 11 |
| config | `config/Limits.scala` | 34 |
| config | `config/LoadedConfig.scala` | 23 |
| config | `config/ScheduledRefresh.scala` | 19 |
| config | `config/ServerConfig.scala` | 24 |
| config | `config/SetupConfig.scala` | 6 |
| config | `config/TlsConfig.scala` | 22 |
| config | `config/UserPaths.scala` | 72 |
| config | `config/edit/SettingSource.scala` | 5 |

## domain (shared kernel)

| module | file | lines |
|---|---|---:|
| api | `api/BBox.scala` | 29 |
| api | `api/Dataset.scala` | 13 |
| api | `api/GeoPick.scala` | 52 |
| api | `api/GeoPoint.scala` | 4 |
| api | `api/ListPatch.scala` | 17 |
| api | `api/LpfDataset.scala` | 10 |
| api | `api/OffsetPage.scala` | 29 |
| api | `api/OpenSide.scala` | 14 |
| api | `api/Paging.scala` | 89 |
| api | `api/PeriodODataset.scala` | 21 |
| api | `api/ServiceState.scala` | 39 |
| api | `api/Span.scala` | 7 |
| api | `api/SpatialHierarchyResult.scala` | 15 |
| api | `api/StatusCount.scala` | 10 |
| api | `api/TableSize.scala` | 9 |
| api | `api/TimeSpan.scala` | 15 |
| api | `api/Validity.scala` | 3 |
| core | `domain/DeepTime.scala` | 17 |
| core | `domain/Read.scala` | 31 |
| core | `domain/ScheduledRead.scala` | 5 |
| server | `server/ChipField.scala` | 14 |

## server (composition, adapters)

| module | file | lines |
|---|---|---:|
| server | `server/ArticlesApi.scala` | 83 |
| server | `server/AuthHandler.scala` | 78 |
| server | `server/Bootstrap.scala` | 320 |
| server | `server/Deps.scala` | 88 |
| server | `server/HttpApi.scala` | 357 |
| server | `server/Jobs.scala` | 133 |
| server | `server/Main.scala` | 350 |
| server | `server/McpOut.scala` | 28 |
| server | `server/McpTools.scala` | 2248 |
| server | `server/OpsRoutes.scala` | 25 |
| server | `server/ServerArgs.scala` | 24 |
| server | `server/ServerGraph.scala` | 286 |
| server | `server/ServerMode.scala` | 17 |
| server | `server/Static.scala` | 15 |
| server | `server/Supervisor.scala` | 335 |
| server | `server/hot/Carried.scala` | 13 |
| server | `server/hot/Hot.scala` | 24 |
| server | `server/hot/HotDataSource.scala` | 38 |
| server | `server/hot/HotGraph.scala` | 402 |
| server | `server/hot/PinRequests.scala` | 24 |
| server | `server/hot/Pinning.scala` | 16 |

## server: split across subdomains

| module | file | lines |
|---|---|---:|
| server | `server/Handlers.scala` | 1891 |
| server | `server/OpsHandlers.scala` | 287 |
| server | `server/OpsTools.scala` | 225 |

## util.*

| module | file | lines |
|---|---|---:|
| api | `api/ApiError.scala` | 15 |
| api | `api/Csv.scala` | 41 |
| api | `api/LatinFold.scala` | 84 |
| api | `api/Markdown.scala` | 913 |
| api | `api/Plural.scala` | 8 |
| api | `api/ValueCodecs.scala` | 30 |
| api | `api/VocabularyCodecs.scala` | 93 |
| api | `api/WireEnum.scala` | 13 |
| api | `api/Wording.scala` | 235 |
| api | `api/Write.scala` | 241 |
| api | `api/YearFmt.scala` | 34 |
| api | `api/Years.scala` | 54 |
| config | `config/ServiceUrl.scala` | 9 |
| db | `db/Codecs.scala` | 59 |
| db | `db/Db.scala` | 223 |
| db | `db/Jsonb.scala` | 30 |
| db | `db/Migrate.scala` | 192 |
| db | `db/Param.scala` | 13 |
| db | `db/Sql.scala` | 61 |
| db | `db/Vocab.scala` | 18 |
| server | `server/MarkdownFlavor.scala` | 25 |
| server | `server/MarkdownHtml.scala` | 12 |

## util.database

| module | file | lines |
|---|---|---:|
| config | `config/DbConfig.scala` | 5 |
| config | `config/DbPoolConfig.scala` | 9 |
| config | `config/PgTuning.scala` | 11 |
| db | `db/Keyset.scala` | 65 |
| db | `db/ListSql.scala` | 49 |

## util.files

| module | file | lines |
|---|---|---:|
| files | `files/SafePath.scala` | 48 |

## util.markdown

| module | file | lines |
|---|---|---:|
| api | `api/HtmlAtoms.scala` | 30 |

## util.ocr

| module | file | lines |
|---|---|---:|
| api | `api/ConvertedDoc.scala` | 15 |
| api | `api/ConvertedImage.scala` | 18 |
| core | `domain/Chunker.scala` | 96 |
| server | `server/ConverterClient.scala` | 57 |
| server | `server/ConverterState.scala` | 33 |
| server | `server/HttpConverterClient.scala` | 163 |
| server | `server/JatsConverter.scala` | 28 |
| server | `server/acquire/Spool.scala` | 47 |

## values

| module | file | lines |
|---|---|---:|
| config | `config/ApiKey.scala` | 9 |
| config | `config/CommaList.scala` | 13 |
| config | `config/Days.scala` | 9 |
| config | `config/Hours.scala` | 9 |
| config | `config/ItemCount.scala` | 9 |
| config | `config/Minutes.scala` | 9 |
| config | `config/Percent.scala` | 9 |
| config | `config/RefreshIntervalMinutes.scala` | 7 |
| config | `config/Seconds.scala` | 9 |
| config | `config/TokenCount.scala` | 9 |
