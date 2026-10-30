# Primitive parameters and fields, measured

How it was measured: every `name: T`, `name: Option[T]`, `name: List[T]` (also `Set`, `Vector`, `Seq`) with `T` one of `String Int Long Double Boolean UUID Instant` in `modules/*/src/main/**/*.scala` at master 58de5489, comment lines skipped. Parameters, case-class fields and local `val`s with an ascription all count; pattern bindings and inferred types do not. The scanner is a throwaway regex, not part of the build; counts move by a few percent with the regex, the ranking does not.

## By newtype the design introduces

"all" counts every module; "no web" leaves out the Scala.js page, which converts at its edge last (slice 12). The name patterns are the ones each newtype replaces; a name is counted for one newtype only where its meaning is unambiguous in the code (for example `id: String` is NOT counted anywhere: it is a different identifier in each of its 521 uses and is typed per feature by the slices).

| newtype | all | no web | by module (top 4) |
|---|---:|---:|---|
| Citekey | 426 | 393 | server=109 api=106 db=105 cli=41 |
| Slug | 241 | 198 | server=73 db=49 web=43 api=33 |
| PlaceId (placeId only) | 55 | 55 | db=23 api=22 server=7 core=2 |
| EventId (eventId only) | 17 | 17 | api=9 core=5 db=3 |
| JobId (jobId only) | 25 | 23 | db=13 server=9 web=2 api=1 |
| GoalSlug (goal, goals) | 159 | 143 | server=52 db=41 api=29 core=21 |
| Doi | 129 | 122 | server=49 api=32 db=14 core=10 |
| Latitude/Longitude | 186 | 160 | api=64 server=42 db=32 web=26 |
| SourceUid | 157 | 157 | db=62 api=47 core=25 server=16 |
| JobId (jobId, job) | 40 | 36 | db=15 server=13 api=7 web=4 |
| PlaceId (placeId, place, parentId) | 89 | 86 | api=33 db=30 server=13 core=6 |
| EventId (eventId, event) | 34 | 28 | api=12 web=6 server=6 core=5 |
| ManagerId (managerId, manager) | 53 | 51 | db=33 api=10 server=6 core=2 |
| RunId (runId, run) | 48 | 48 | core=22 server=14 db=6 api=6 |
| AssetId | 4 | 4 | server=2 cli=1 db=1 |
| QueueItemId | 21 | 21 | db=16 core=2 api=2 server=1 |
| Year (from Long/Int) | 450 | 398 | api=185 db=88 server=79 web=52 |
| Tag/Topic/Region | 191 | 179 | api=70 server=37 db=37 core=21 |
| AuthorName | 80 | 75 | api=30 server=12 db=12 core=10 |
| Title | 227 | 193 | api=93 server=43 web=34 core=20 |
| Summary/Body | 215 | 209 | api=80 server=56 core=41 db=16 |
| Claim | 22 | 22 | core=10 api=7 db=5 |
| Venue | 22 | 21 | api=10 db=6 core=2 server=2 |
| Sha256 | 39 | 36 | api=15 db=14 server=4 web=3 |
| Url | 133 | 128 | server=35 core=19 plugin-host=18 config=12 |
| WikidataId | 18 | 18 | db=6 server=5 api=4 core=3 |
| Cron | 26 | 26 | db=9 core=6 api=6 server=4 |
| ModelId | 49 | 48 | api=18 server=17 db=5 core=4 |
| ProviderName | 60 | 55 | api=19 db=18 server=13 web=5 |
| Licence | 42 | 41 | api=19 db=13 cli=3 server=2 |
| Limit/PageSize | 268 | 241 | server=114 db=57 web=27 api=23 |
| Offset | 70 | 61 | api=29 server=21 web=9 db=8 |
| Seconds | 33 | 33 | config=10 server=9 plugin-api=4 plugin-vaults=4 |
| ByteCount | 36 | 36 | api=21 server=6 files=5 core=2 |
| Zoom | 98 | 74 | server=40 web=24 api=15 core=8 |
| Kilometres | 43 | 43 | db=27 server=6 core=5 api=3 |
| CharCount | 22 | 22 | db=15 core=4 server=2 config=1 |
| Timestamp(ISO text) | 175 | 164 | api=77 db=44 server=25 core=11 |
| EpochMillis | 71 | 71 | server=48 api=10 db=10 core=2 |
| ReviewRound | 20 | 20 | core=15 server=2 api=2 db=1 |
| GraphDepth | 40 | 31 | api=19 web=9 server=9 cli=2 |
| Priority | 20 | 20 | api=7 core=5 db=5 server=3 |

## The 400 most frequent name:type pairs

| name: type | uses | by module |
|---|---:|---|
| `id:String` | 521 | server=135 web=113 db=104 api=69 core=44 cli=27 plugin-host=13 plugin-api=6 plugin-testkit=6 files=4 |
| `s:String` | 359 | api=139 core=58 web=47 values=40 server=22 cli=19 db=13 config=7 files=5 plugin-vaults=4 plugin-host=4 plugin-testkit=1 |
| `citekey:String` | 324 | api=96 server=86 db=84 core=25 web=17 cli=12 files=4 |
| `kind:String` | 320 | api=121 db=70 server=56 core=31 web=29 cli=6 files=3 config=1 plugin-api=1 plugin-host=1 plugin-testkit=1 |
| `text:String` | 248 | api=59 core=42 web=41 server=36 db=19 config=14 files=14 cli=10 plugin-api=5 plugin-testkit=4 plugin-fixtures=2 plugin-vaults=1 plugin-host=1 |
| `name:String` | 244 | server=65 api=48 web=36 db=33 core=27 cli=9 plugin-api=6 files=5 plugin-vaults=3 plugin-host=3 config=2 plugin-resolver=2 chimney-iron=2 values=1 plugin-testkit=1 plugin-fixtures=1 |
| `slug:String` | 236 | server=72 db=45 web=43 api=33 core=25 cli=18 |
| `limit:Int` | 236 | server=104 db=56 web=23 cli=20 api=19 core=12 config=2 |
| `title:String` | 227 | api=93 server=43 web=34 core=20 db=20 cli=12 plugin-api=5 |
| `label:String` | 197 | api=70 web=66 core=20 db=20 server=10 cli=5 plugin-api=2 files=2 config=1 plugin-host=1 |
| `key:String` | 155 | web=56 server=38 api=28 db=9 core=7 config=6 cli=5 plugin-host=3 plugin-api=2 plugin-testkit=1 |
| `goal:String` | 152 | server=50 db=40 api=27 core=19 web=16 |
| `doi:String` | 125 | server=49 api=31 db=12 plugin-host=10 core=9 web=7 cli=5 plugin-testkit=2 |
| `url:String` | 116 | server=35 plugin-host=18 config=11 core=10 api=10 plugin-testkit=9 plugin-api=7 db=6 web=5 cli=3 files=2 |
| `wire:String` | 111 | api=53 values=20 core=13 web=13 server=8 config=4 |
| `status:String` | 106 | api=38 db=24 server=19 core=15 web=8 cli=1 plugin-host=1 |
| `now:Instant` | 105 | server=74 core=27 api=2 plugin-host=2 |
| `why:String` | 103 | core=33 api=21 server=16 db=8 config=7 web=6 plugin-api=3 plugin-testkit=3 cli=2 plugin-vaults=2 plugin-host=2 |
| `body:String` | 93 | api=29 core=26 server=22 db=4 plugin-testkit=4 plugin-api=3 web=2 cli=2 plugin-host=1 |
| `ck:String` | 81 | cli=25 server=19 web=16 db=12 api=6 core=3 |
| `note:String` | 78 | server=30 api=22 db=10 core=8 web=7 values=1 |
| `raw:String` | 77 | api=33 core=10 server=10 web=7 cli=5 config=3 plugin-resolver=3 values=2 files=2 plugin-api=1 db=1 |
| `dryRun:Boolean` | 71 | server=31 api=25 cli=6 db=4 web=3 core=2 |
| `lat:Double` | 71 | api=24 db=14 web=13 server=11 core=4 values=2 cli=2 config=1 |
| `lon:Double` | 71 | api=24 db=14 web=13 server=11 core=4 values=2 cli=2 config=1 |
| `offset:Int` | 70 | api=29 server=21 web=9 db=8 core=3 |
| `tags:String` | 69 | api=24 db=16 server=13 core=10 cli=4 web=2 |
| `from:Long` | 66 | api=24 web=15 server=15 db=6 core=4 cli=1 plugin-host=1 |
| `to:Long` | 66 | api=24 web=15 server=15 db=6 core=4 cli=1 plugin-host=1 |
| `target:String` | 64 | api=22 db=16 core=13 server=9 web=4 |
| `what:String` | 64 | server=26 web=19 api=8 core=3 cli=3 db=2 plugin-vaults=2 config=1 |
| `n:Int` | 63 | web=19 api=13 core=9 server=8 db=8 plugin-host=3 config=2 cli=1 |
| `id:Long` | 62 | db=19 server=17 api=15 core=6 cli=4 web=1 |
| `authors:String` | 62 | api=25 server=12 cli=8 core=6 db=6 web=4 plugin-api=1 |
| `provider:String` | 60 | api=19 db=18 server=13 web=5 plugin-host=3 core=1 plugin-testkit=1 |
| `year:Int` | 59 | api=33 cli=7 server=6 db=6 core=4 web=3 |
| `reason:String` | 58 | api=23 core=19 cli=6 db=6 server=3 web=1 |
| `periodStart:Long` | 58 | api=27 db=19 server=10 core=1 web=1 |
| `periodEnd:Long` | 58 | api=27 db=19 server=10 core=1 web=1 |
| `k:String` | 57 | web=16 core=15 files=11 server=9 api=3 cli=2 plugin-testkit=1 |
| `updatedAt:String` | 56 | api=31 db=20 server=3 core=2 |
| `total:Int` | 55 | api=33 web=12 core=4 server=4 cli=2 |
| `placeId:String` | 55 | db=23 api=22 server=7 core=2 values=1 |
| `query:String` | 52 | server=32 api=9 core=6 cli=4 web=1 |
| `q:String` | 51 | db=15 server=14 web=7 cli=5 api=5 core=4 plugin-host=1 |
| `summary:String` | 49 | api=19 server=10 db=8 core=5 cli=4 web=3 |
| `model:String` | 49 | api=18 server=17 db=5 core=4 plugin-api=2 web=1 plugin-host=1 plugin-fixtures=1 |
| `source:String` | 49 | api=18 server=7 cli=6 db=6 core=5 web=3 config=1 plugin-api=1 files=1 plugin-testkit=1 |
| `asOf:Long` | 49 | db=16 api=12 web=9 server=7 cli=5 |
| `notes:String` | 46 | api=17 server=12 core=10 db=4 config=2 web=1 |
| `managerId:String` | 46 | db=33 api=6 server=5 core=2 |
| `at:Instant` | 45 | server=37 core=4 db=2 api=2 |
| `file:String` | 45 | api=19 server=10 cli=6 files=4 config=2 db=2 core=1 values=1 |
| `createdAt:String` | 44 | api=27 db=14 server=2 core=1 |
| `role:String` | 43 | server=14 db=13 api=11 web=2 core=1 values=1 plugin-api=1 |
| `ids:String` | 42 | db=21 server=9 web=5 core=2 api=2 plugin-host=2 files=1 |
| `state:String` | 42 | api=15 server=12 db=7 web=4 core=2 values=1 cli=1 |
| `keys:String` | 42 | server=12 db=11 api=6 plugin-host=5 core=4 config=2 web=2 |
| `i:Int` | 41 | server=15 web=8 api=7 core=6 config=4 db=1 |
| `now:Long` | 41 | server=33 api=8 |
| `depth:Int` | 40 | api=19 web=9 server=9 cli=2 core=1 |
| `value:String` | 40 | web=14 api=11 server=6 db=4 config=3 core=1 cli=1 |
| `topics:String` | 39 | server=11 api=11 core=7 db=5 cli=3 web=1 files=1 |
| `id:Int` | 39 | cli=19 web=7 server=5 db=4 api=4 |
| `uid:String` | 39 | server=16 db=16 api=5 cli=2 |
| `uid:UUID` | 38 | db=17 api=15 core=3 files=3 |
| `message:String` | 38 | server=11 web=5 core=4 plugin-api=4 plugin-host=4 cli=3 api=3 config=1 values=1 plugin-resolver=1 plugin-testkit=1 |
| `v:String` | 37 | web=9 db=8 core=6 api=5 server=4 cli=3 files=1 plugin-host=1 |
| `error:String` | 37 | api=13 server=9 db=7 core=4 web=2 plugin-host=1 plugin-testkit=1 |
| `maxZoom:Int` | 36 | server=12 config=7 api=6 web=5 core=4 db=2 |
| `line:String` | 35 | server=15 api=5 plugin-host=5 core=3 web=2 files=2 plugin-testkit=2 plugin-vaults=1 |
| `sha256:String` | 35 | api=15 db=14 server=3 files=2 cli=1 |
| `prefix:String` | 34 | server=9 core=5 web=5 db=4 api=4 config=3 cli=3 plugin-host=1 |
| `z:Int` | 34 | server=20 web=5 api=5 core=4 |
| `by:String` | 33 | api=19 db=7 server=4 web=2 core=1 |
| `before:String` | 33 | server=13 db=6 core=5 web=5 config=3 cli=1 |
| `path:String` | 33 | server=6 api=6 config=5 web=5 files=5 cli=3 core=1 plugin-vaults=1 plugin-host=1 |
| `regions:String` | 33 | api=14 db=8 server=4 core=3 web=2 cli=2 |
| `all:String` | 32 | api=23 values=6 core=2 server=1 |
| `ref:String` | 32 | db=8 server=6 plugin-vaults=6 api=5 plugin-api=2 plugin-testkit=2 core=1 web=1 cli=1 |
| `start:Long` | 32 | api=17 db=8 core=4 web=3 |
| `quote:String` | 32 | core=22 api=8 server=1 db=1 |
| `x:Double` | 32 | web=29 core=2 api=1 |
| `run:String` | 32 | core=15 server=12 api=3 db=2 |
| `end:Long` | 31 | api=17 db=8 core=4 web=2 |
| `y:Long` | 31 | server=16 core=6 api=4 web=3 plugin-host=2 |
| `timeoutSeconds:Int` | 31 | config=10 server=8 plugin-api=4 plugin-host=4 plugin-vaults=3 plugin-testkit=2 |
| `targetId:String` | 31 | api=12 server=10 db=9 |
| `refresh:Boolean` | 31 | server=17 db=7 api=4 cli=3 |
| `targetType:String` | 30 | server=11 api=10 db=9 |
| `detail:String` | 29 | api=13 server=5 core=4 web=3 db=3 plugin-api=1 |
| `on:Boolean` | 29 | web=16 api=7 server=3 db=3 |
| `width:Int` | 28 | db=9 api=8 server=6 core=5 |
| `a:String` | 28 | core=17 db=3 web=2 cli=2 api=2 config=1 server=1 |
| `apply:Boolean` | 28 | server=15 cli=6 api=4 db=3 |
| `count:Int` | 27 | api=22 core=2 web=2 cli=1 |
| `max:Int` | 27 | server=10 core=9 web=3 api=3 config=1 db=1 |
| `u:UUID` | 27 | core=19 db=8 |
| `md:String` | 27 | api=15 server=12 |
| `version:String` | 27 | api=12 server=7 db=5 files=2 plugin-api=1 |
| `b:String` | 26 | core=15 server=3 api=3 cli=2 db=2 config=1 |
| `cron:String` | 26 | db=9 core=6 api=6 server=4 config=1 |
| `names:String` | 25 | api=9 db=6 server=4 core=3 web=1 cli=1 plugin-api=1 |
| `enabled:Boolean` | 25 | api=9 config=8 db=5 core=1 server=1 plugin-host=1 |
| `siteType:String` | 25 | server=9 db=9 api=4 web=3 |
| `subject:String` | 25 | api=9 db=6 web=5 server=5 |
| `jobId:String` | 25 | db=13 server=9 web=2 api=1 |
| `list:String` | 25 | server=9 cli=6 web=4 api=4 db=1 plugin-host=1 |
| `toleranceKm:Double` | 25 | db=20 server=5 |
| `origin:UUID` | 25 | api=13 db=12 |
| `problems:String` | 24 | api=13 core=4 server=4 db=2 files=1 |
| `t:String` | 24 | server=6 core=5 web=4 api=4 config=3 cli=1 db=1 |
| `since:String` | 24 | api=8 server=6 core=3 web=3 db=2 cli=1 files=1 |
| `action:String` | 24 | server=9 web=6 api=6 cli=2 core=1 |
| `license:String` | 24 | db=11 api=10 web=1 server=1 plugin-host=1 |
| `since:Long` | 24 | server=14 db=9 cli=1 |
| `description:String` | 24 | api=10 server=8 plugin-api=4 db=2 |
| `findings:Int` | 23 | api=8 core=7 db=5 web=2 config=1 |
| `last:Boolean` | 23 | core=21 server=2 |
| `field:String` | 23 | server=6 api=5 core=3 web=3 cli=2 db=2 plugin-vaults=2 |
| `vs:String` | 23 | web=18 db=2 api=2 core=1 |
| `region:String` | 23 | api=10 db=6 server=4 config=1 web=1 cli=1 |
| `kinds:String` | 23 | db=12 api=5 web=3 server=2 cli=1 |
| `height:Int` | 22 | db=9 api=8 server=3 core=2 |
| `claim:String` | 22 | core=10 api=7 db=5 |
| `venue:String` | 22 | api=10 db=6 core=2 server=2 web=1 cli=1 |
| `bytes:Long` | 22 | api=15 server=3 db=2 core=1 files=1 |
| `y:Double` | 22 | web=22 |
| `force:Boolean` | 22 | server=8 api=7 cli=5 web=1 db=1 |
| `alias:String` | 22 | db=16 api=6 |
| `outcome:String` | 21 | core=9 db=7 api=4 server=1 |
| `itemId:Long` | 21 | db=16 core=2 api=2 server=1 |
| `p:String` | 21 | core=5 server=5 db=4 api=3 web=2 config=1 plugin-vaults=1 |
| `citekeys:String` | 21 | db=9 server=4 cli=4 api=4 |
| `sourceUid:UUID` | 21 | api=10 db=9 files=2 |
| `to:String` | 20 | cli=7 api=5 db=3 core=2 web=2 server=1 |
| `describe:String` | 20 | api=8 core=4 server=3 config=2 values=2 db=1 |
| `round:Int` | 20 | core=15 server=2 api=2 db=1 |
| `chunkChars:Int` | 20 | db=15 core=2 server=2 config=1 |
| `l:String` | 20 | config=7 core=6 api=4 web=1 server=1 db=1 |
| `priority:Int` | 20 | api=7 core=5 db=5 server=3 |
| `w:Double` | 20 | web=18 core=1 server=1 |
| `base:String` | 20 | core=10 files=5 web=2 api=2 plugin-host=1 |
| `place:String` | 20 | server=5 core=4 web=3 cli=3 api=3 db=2 |
| `known:String` | 20 | api=11 server=6 values=2 core=1 |
| `item:String` | 20 | server=9 web=5 db=2 api=2 core=1 plugin-vaults=1 |
| `x:Long` | 20 | server=16 core=2 api=2 |
| `zoom:Double` | 20 | web=14 server=3 config=1 cli=1 api=1 |
| `html:String` | 19 | core=10 web=6 api=2 server=1 |
| `all:Boolean` | 19 | server=5 cli=4 api=4 db=3 web=2 core=1 |
| `mime:String` | 18 | db=8 api=7 server=2 core=1 |
| `author:String` | 18 | db=6 api=5 core=4 web=1 cli=1 plugin-api=1 |
| `licence:String` | 18 | api=9 cli=3 db=2 plugin-testkit=2 core=1 server=1 |
| `seen:String` | 18 | api=7 core=4 server=4 config=1 web=1 files=1 |
| `verdict:String` | 18 | api=8 core=7 db=3 |
| `km:Double` | 18 | db=7 core=5 api=3 config=2 server=1 |
| `at:String` | 18 | api=11 web=3 db=2 config=1 server=1 |
| `topic:String` | 18 | api=7 web=5 server=2 cli=2 db=2 |
| `seq:Int` | 18 | server=7 db=7 web=2 api=2 |
| `historicity:String` | 18 | api=9 db=6 server=2 plugin-api=1 |
| `col:String` | 18 | db=16 server=2 |
| `from:String` | 17 | api=4 core=3 cli=3 files=3 db=2 web=1 server=1 |
| `api:String` | 17 | core=17 |
| `lang:String` | 17 | api=10 db=3 web=2 core=1 server=1 |
| `from:Int` | 17 | server=6 core=5 api=2 config=1 web=1 cli=1 plugin-vaults=1 |
| `unit:String` | 17 | api=7 server=4 db=4 core=1 web=1 |
| `eventId:String` | 17 | api=9 core=5 db=3 |
| `dir:String` | 17 | config=6 api=5 server=3 cli=3 |
| `modernFrom:Long` | 17 | db=15 config=1 api=1 |
| `env:String` | 17 | server=8 config=5 cli=4 |
| `hint:String` | 17 | web=10 server=6 cli=1 |
| `upTo:String` | 17 | server=7 db=6 web=4 |
| `bundle:String` | 17 | api=6 server=4 db=4 web=3 |
| `event:String` | 17 | web=6 server=6 api=3 db=2 |
| `progress:String` | 17 | server=9 db=4 cli=2 api=2 |
| `credit:String` | 16 | db=5 core=4 api=4 web=1 cli=1 plugin-api=1 |
| `runId:String` | 16 | core=7 db=4 api=3 server=2 |
| `until:String` | 16 | api=9 web=3 server=3 core=1 |
| `v:Double` | 16 | web=10 api=3 core=1 server=1 db=1 |
| `filename:String` | 16 | server=14 api=2 |
| `provenance:String` | 16 | api=8 db=4 server=2 cli=2 |
| `out:String` | 16 | plugin-vaults=6 cli=4 plugin-resolver=4 server=1 api=1 |
| `code:String` | 15 | api=8 core=3 web=2 server=2 |
| `heading:String` | 15 | core=4 server=4 api=3 web=2 db=2 |
| `failures:Int` | 15 | core=8 db=3 api=3 server=1 |
| `rev:String` | 15 | api=12 files=2 core=1 |
| `job:String` | 15 | api=6 server=4 web=2 db=2 cli=1 |
| `budgetUsd:Double` | 15 | api=9 server=5 web=1 |
| `quality:Boolean` | 15 | server=11 cli=2 web=1 api=1 |
| `layer:String` | 15 | web=9 api=5 server=1 |
| `xs:String` | 15 | api=9 server=5 files=1 |
| `since:Int` | 15 | server=13 cli=1 api=1 |
| `calibreId:String` | 15 | api=9 db=4 cli=2 |
| `size:Long` | 14 | api=6 files=4 server=3 core=1 |
| `v:Long` | 14 | api=6 core=3 server=3 db=2 |
| `rows:Int` | 14 | server=6 core=3 api=3 cli=1 db=1 |
| `n:Long` | 14 | api=7 core=3 server=2 files=2 |
| `minRatio:Double` | 14 | core=14 |
| `lines:String` | 14 | server=5 api=5 core=1 web=1 plugin-resolver=1 files=1 |
| `sha:String` | 14 | server=8 db=3 core=1 web=1 cli=1 |
| `turns:Int` | 14 | server=7 api=3 core=2 db=2 |
| `item:Int` | 14 | api=4 core=3 server=3 db=3 cli=1 |
| `m:String` | 14 | server=11 cli=2 core=1 |
| `user:String` | 14 | server=11 config=2 db=1 |
| `warnings:String` | 14 | api=7 server=3 config=2 db=1 plugin-testkit=1 |
| `tier:String` | 14 | api=7 web=4 db=3 |
| `size:Int` | 14 | server=6 web=5 db=2 api=1 |
| `contested:Boolean` | 14 | api=8 web=4 db=2 |
| `strict:Boolean` | 14 | server=9 cli=4 api=1 |
| `parentId:String` | 14 | api=8 db=5 server=1 |
| `sleep:Long` | 14 | server=5 plugin-host=5 cli=2 plugin-testkit=2 |
| `sources:Int` | 13 | api=7 core=2 cli=2 db=2 |
| `isEmpty:Boolean` | 13 | api=8 core=1 web=1 cli=1 plugin-api=1 db=1 |
| `pipeline:String` | 13 | api=4 core=3 web=2 server=2 config=1 db=1 |
| `asked:String` | 13 | server=5 api=5 db=2 core=1 |
| `section:String` | 13 | api=5 core=3 server=3 config=2 |
| `command:String` | 13 | server=5 config=3 cli=2 web=1 api=1 plugin-vaults=1 |
| `h:Double` | 13 | web=13 |
| `origin:String` | 13 | web=7 api=3 db=2 server=1 |
| `acquireItem:Int` | 13 | db=5 api=4 server=3 cli=1 |
| `planHash:String` | 13 | api=8 db=4 server=1 |
| `json:String` | 13 | api=4 cli=3 server=2 db=2 plugin-host=2 |
| `cap:Int` | 12 | core=8 server=2 db=1 api=1 |
| `code:Int` | 12 | server=7 core=2 plugin-host=2 cli=1 |
| `rounds:Int` | 12 | core=5 api=4 server=2 config=1 |
| `batch:Int` | 12 | config=6 server=2 db=2 core=1 cli=1 |
| `days:Int` | 12 | api=4 server=3 core=2 db=2 web=1 |
| `current:String` | 12 | server=3 api=3 core=2 web=2 cli=1 db=1 |
| `host:String` | 12 | core=5 server=4 api=2 config=1 |
| `samples:Int` | 12 | api=9 server=2 config=1 |
| `msg:String` | 12 | server=4 api=3 config=2 cli=2 web=1 |
| `timer:Int` | 12 | web=12 |
| `w:Int` | 12 | web=4 server=4 api=4 |
| `except:String` | 12 | db=8 server=3 web=1 |
| `anchor:String` | 12 | api=10 server=1 db=1 |
| `h:Int` | 12 | server=8 api=4 |
| `offset:Long` | 12 | server=12 |
| `r:Int` | 12 | server=11 api=1 |
| `err:String` | 12 | plugin-vaults=8 plugin-resolver=4 |
| `table:String` | 12 | db=9 api=3 |
| `attempts:Int` | 11 | core=4 api=4 db=3 |
| `r:String` | 11 | core=6 server=3 api=1 plugin-host=1 |
| `approval:Long` | 11 | core=4 server=4 api=2 db=1 |
| `minSources:Int` | 11 | core=8 api=2 config=1 |
| `after:String` | 11 | config=3 core=2 cli=2 db=2 web=1 server=1 |
| `a:Long` | 11 | web=4 api=4 core=2 plugin-host=1 |
| `maxRuns:Int` | 11 | core=6 config=3 api=2 |
| `property:String` | 11 | server=5 core=2 db=2 api=2 |
| `places:String` | 11 | server=4 api=4 core=1 cli=1 db=1 |
| `isCitekey:String` | 11 | core=10 server=1 |
| `dropped:String` | 11 | api=7 core=3 server=1 |
| `osName:String` | 11 | config=3 server=3 cli=3 core=1 plugin-host=1 |
| `periodName:String` | 11 | api=5 core=3 db=3 |
| `citation:String` | 11 | api=6 db=2 core=1 server=1 plugin-api=1 |
| `generation:String` | 11 | server=5 core=4 db=2 |
| `caption:String` | 11 | api=5 db=4 web=1 server=1 |
| `failed:String` | 11 | api=6 web=2 cli=2 server=1 |
| `next:Int` | 11 | api=10 web=1 |
| `words:String` | 11 | server=10 web=1 |
| `animate:Boolean` | 11 | web=11 |
| `minLon:Double` | 11 | server=5 api=4 db=1 plugin-host=1 |
| `minLat:Double` | 11 | server=5 api=4 db=1 plugin-host=1 |
| `maxLon:Double` | 11 | server=5 api=4 db=1 plugin-host=1 |
| `maxLat:Double` | 11 | server=5 api=4 db=1 plugin-host=1 |
| `sort:String` | 11 | server=8 api=2 cli=1 |
| `arxiv:String` | 11 | plugin-host=5 api=3 server=2 plugin-api=1 |
| `notBefore:Long` | 11 | db=7 api=4 |
| `beginBy:Long` | 11 | db=7 api=4 |
| `endFrom:Long` | 11 | db=7 api=4 |
| `notAfter:Long` | 11 | db=7 api=4 |
| `parent:String` | 10 | db=4 api=3 core=2 web=1 |
| `places:Int` | 10 | api=6 core=2 web=1 db=1 |
| `dataset:String` | 10 | api=4 core=3 server=3 |
| `qid:String` | 10 | server=4 db=4 core=2 |
| `costUsd:Double` | 10 | api=6 core=3 server=1 |
| `plusMinus:Long` | 10 | api=6 db=2 core=1 plugin-api=1 |
| `n:String` | 10 | server=5 core=3 api=2 |
| `token:String` | 10 | server=7 core=1 cli=1 plugin-vaults=1 |
| `d:String` | 10 | server=3 core=2 cli=1 files=1 db=1 api=1 plugin-host=1 |
| `filter:String` | 10 | core=4 api=3 server=2 db=1 |
| `href:String` | 10 | web=8 core=1 api=1 |
| `basis:String` | 10 | server=3 api=3 web=2 core=1 db=1 |
| `b:Int` | 10 | server=6 core=1 web=1 files=1 api=1 |
| `fix:Boolean` | 10 | server=4 api=3 core=2 cli=1 |
| `show:String` | 10 | web=5 server=3 core=1 api=1 |
| `mode:String` | 10 | api=5 server=2 config=1 files=1 db=1 |
| `sourceUrl:String` | 10 | db=4 api=4 config=1 web=1 |
| `changed:String` | 10 | server=4 config=3 cli=1 files=1 api=1 |
| `literalConsent:Boolean` | 10 | config=4 server=4 web=1 api=1 |
| `changed:Boolean` | 10 | web=9 api=1 |
| `cmd:String` | 10 | cli=6 server=3 web=1 |
| `geometry:String` | 10 | api=6 db=2 web=1 server=1 |
| `links:String` | 10 | server=10 |
| `xywh:String` | 10 | api=7 server=2 db=1 |
| `usable:Boolean` | 10 | api=8 server=2 |
| `itemKey:String` | 10 | server=9 api=1 |
| `isbn:String` | 10 | cli=5 db=2 api=2 plugin-api=1 |
| `vei:Int` | 10 | api=5 db=4 plugin-api=1 |
| `magnitude:Double` | 10 | api=5 db=4 plugin-api=1 |
| `checkedAt:String` | 10 | api=7 db=3 |
| `pNull:Boolean` | 10 | db=10 |
| `pLo:Long` | 10 | db=10 |
| `pHi:Long` | 10 | db=10 |
| `into:String` | 9 | server=3 core=2 db=2 api=2 |
| `pageUrl:String` | 9 | core=7 cli=1 plugin-api=1 |
| `suffix:String` | 9 | api=4 core=2 server=1 cli=1 db=1 |
| `rationale:String` | 9 | core=6 api=3 |
| `cited:String` | 9 | api=5 core=2 server=2 |
| `rank:Int` | 9 | server=5 core=3 db=1 |
| `open:Int` | 9 | api=7 core=2 |
| `b:Long` | 9 | web=3 api=3 core=2 plugin-host=1 |
| `years:Long` | 9 | core=4 config=2 db=2 api=1 |
| `foreignRev:String` | 9 | core=7 db=1 api=1 |
| `f:UUID` | 9 | core=9 |
| `models:String` | 9 | db=3 api=3 core=1 web=1 server=1 |
| `attempt:Int` | 9 | server=3 core=2 db=2 api=1 plugin-host=1 |
| `updated:Int` | 9 | api=6 core=1 server=1 db=1 |
| `result:String` | 9 | db=6 core=1 values=1 api=1 |
| `number:Int` | 9 | server=3 core=2 cli=2 web=1 api=1 |
| `tag:String` | 9 | api=4 server=3 core=1 web=1 |
| `problem:String` | 9 | api=5 core=4 |
| `attribution:String` | 9 | config=5 api=2 web=1 server=1 |
| `port:Int` | 9 | server=7 config=1 api=1 |
| `maxTokens:Int` | 9 | server=7 config=1 plugin-api=1 |
| `bbox:String` | 9 | api=7 config=1 web=1 |
| `reduced:Boolean` | 9 | web=9 |
| `group:String` | 9 | api=3 web=2 server=2 plugin-api=1 files=1 |
| `d:Double` | 9 | api=7 web=1 server=1 |
| `cls:String` | 9 | web=9 |
| `page:String` | 9 | api=4 web=3 server=1 db=1 |
| `src:String` | 9 | api=4 web=3 db=2 |
| `licenseUrl:String` | 9 | db=5 api=3 web=1 |
| `g:String` | 9 | server=3 api=3 db=2 web=1 |
| `maxZoom:Double` | 9 | web=9 |
| `rules:String` | 9 | server=6 cli=3 |
| `scale:String` | 9 | api=5 server=2 db=2 |
| `args:String` | 9 | server=3 cli=3 plugin-resolver=2 api=1 |
| `sql:String` | 9 | api=4 db=3 server=1 cli=1 |
| `format:String` | 9 | api=4 server=3 cli=1 plugin-api=1 |
| `ok:Boolean` | 9 | api=6 server=1 files=1 db=1 |
| `citedByCount:Int` | 9 | api=7 server=1 db=1 |
| `calibreId:Int` | 9 | cli=9 |
| `eventUid:UUID` | 9 | db=5 api=4 |
| `start:Int` | 8 | server=3 api=3 core=2 |
| `end:Int` | 8 | server=3 api=3 core=2 |
| `score:Double` | 8 | core=3 api=3 db=2 |
| `page:Int` | 8 | web=6 core=1 server=1 |
| `part:String` | 8 | server=2 core=1 cli=1 plugin-api=1 db=1 api=1 plugin-host=1 |
| `chunkOrd:Int` | 8 | api=4 core=2 server=1 db=1 |
| `issue:String` | 8 | web=3 api=3 core=2 |
| `firstCallTokens:Long` | 8 | api=3 core=2 db=2 server=1 |
| `seconds:Long` | 8 | api=4 core=2 web=1 db=1 |
| `rest:String` | 8 | core=2 server=2 api=2 config=1 plugin-resolver=1 |
| `pos:Int` | 8 | db=5 server=2 core=1 |
| `proposed:Int` | 8 | core=3 server=2 api=2 db=1 |
| `applied:String` | 8 | core=4 api=4 |
| `isOa:Boolean` | 8 | api=3 core=2 db=2 plugin-host=1 |
| `settings:String` | 8 | config=3 server=3 core=1 api=1 |
| `wikidata:String` | 8 | api=4 db=2 core=1 server=1 |
| `entry:String` | 8 | plugin-vaults=5 core=1 web=1 api=1 |
| `queued:Int` | 8 | api=4 server=3 core=1 |
| `a:Int` | 8 | server=6 core=1 web=1 |
| `toString:String` | 8 | plugin-resolver=2 core=1 config=1 server=1 plugin-api=1 files=1 plugin-testkit=1 |
| `parts:String` | 8 | web=2 core=1 config=1 server=1 files=1 api=1 plugin-testkit=1 |
| `where:String` | 8 | core=4 api=2 web=1 cli=1 |
| `fix:String` | 8 | core=4 api=2 server=1 cli=1 |
| `one:String` | 8 | web=6 core=1 server=1 |
| `requestIntervalMs:Int` | 8 | config=6 plugin-api=2 |
| `floors:String` | 8 | config=2 db=2 api=2 web=1 server=1 |
| `maxBytes:Long` | 8 | db=3 server=2 config=1 cli=1 api=1 |
| `singular:String` | 8 | web=4 api=4 |
| `icon:String` | 8 | web=6 api=2 |
| `alpha:Double` | 8 | web=8 |
| `undated:Boolean` | 8 | web=5 api=3 |
| `period:String` | 8 | web=3 cli=3 db=1 api=1 |
| `pool:String` | 8 | api=4 web=2 server=2 |
| `z:Double` | 8 | web=8 |
| `search:String` | 8 | web=7 server=1 |
| `byline:String` | 8 | web=8 |
| `help:String` | 8 | cli=6 web=2 |
| `setupDue:Boolean` | 8 | server=6 api=2 |
| `off:Int` | 8 | files=5 server=3 |
| `l:Int` | 8 | server=8 |
| `requested:String` | 8 | db=5 api=2 server=1 |
| `chars:Int` | 8 | server=4 api=4 |
| `resolve:String` | 8 | api=5 server=1 cli=1 db=1 |
| `minZoom:Int` | 8 | server=5 api=3 |
| `length:Long` | 8 | server=8 |
| `ext:String` | 8 | db=4 server=3 files=1 |
| `applied:Boolean` | 8 | api=5 server=1 cli=1 db=1 |
| `decision:String` | 8 | api=4 server=3 db=1 |
| `ids:Long` | 8 | server=3 api=3 db=2 |
| `reservePct:Int` | 8 | api=5 db=2 server=1 |
| `plugin:String` | 8 | server=5 plugin-resolver=2 api=1 |
| `legalOnly:Boolean` | 8 | plugin-host=5 server=1 api=1 plugin-testkit=1 |
| `ifUpdatedAt:String` | 8 | server=3 api=3 cli=2 |
| `targetLabel:String` | 8 | server=7 api=1 |
| `targetKind:String` | 8 | api=5 db=2 server=1 |
| `json:Boolean` | 8 | cli=8 |
| `spiVersion:Int` | 8 | api=4 files=3 plugin-api=1 |
