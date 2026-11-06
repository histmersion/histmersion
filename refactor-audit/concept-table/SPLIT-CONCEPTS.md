# One concept, two types: to merge after api compiles (bridged with X(y.value) until then)

- `CatalogRecord.Kind/Label/Historicity` and `EventSummary.*`
- `EventPatch.Geometry`, `EventPut.Geometry` and `EventGeometry.Geojson`
- `Paging.Page.facet`'s result, `Facet.Count` and `ListFilters.facetLine`'s `Term`, `RowCount`
- `Place.Label/KindValue/Geometry/Source/Properties` and the `Gaz*` types
- `Place.Match.ImportSource` and `SourceInfo.Key`
- `Bundle.Event.NotBefore/BeginBy/NotAfter/EndFrom` and `catalogue.Year`
- `PeriodODataset.ParentsKey/ParentsValue/PresetsKey`, `SkippedPeriod.Id/Label/Detail`, `HistoricalPeriod.ParentId` and `HistoricalPeriod.Id`
- `PlaceSourcesConfig.pointAreaKindSet` (positional result) and `Gazetteer.PointAreaKinds`
- `Coverage.Reason.list`'s `ListMPart1/2` and `Coverage.Entry.Reason`
- `LintItem.citekey` holds slugs too: it should be a generic subject key
- `ReviewFinding` declares `object Reviewer` twice; `ReviewGoal.Slug` is a self-alias
- `Place.Entry.AsOf`, `Time.AsOf` and `Year`; `Period.Start/End`, `Bucket.Start/End`, `TimeHistogram.From/To`, `TimeBucket.Start/End` and `Year`
- `OverrideSet.PointLat/Lon`, `Geo.Latitude/Longitude` and `Latitude/Longitude`; `Place.Entry.SiteType` and `SiteTypes.SiteType.Id`
- `Years.Astronomical` and `Period.IsoYear` (the second unused); `Place.Kind.Parsed`, `Place.KindValue` and `Candidate.Kind`; `Place.Id` and `Relation.ChildId`; `Move.Asked` and `Resolution.Asked`
- `Source.Regions` and `Label.Text`; `Candidate.State` and `Candidates.State.Wire`; `DeleteCount.Rows` and `RowCount`; `FindingsPatch.Remove` and `Finding.Id`; `Source.Tags` and `Write.ListItem`; `Match.RegionName`, `GeoHit.RegionName` and `Place.Region`
- `Managers.Usd.Amount`, `SpentTodayUsd`, `DailyUsd`, `BudgetUsd`; `Wikirefs.Dois/Known` and `Doi`
- `Card.hasBoundary/findings/asOf/contested` are primitives in the trait and newtypes in `Place.Entry` and `PlaceDetail`

Lost in round 2, to restore: `Source.Write` went back to `.copy` (G19), and `Place.TimeBasis.of` to plain Boolean flags.
