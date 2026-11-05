# Primitive concept review queue

Name/base groups are discovery aids, not approved equivalence classes. Each occurrence still needs a purpose, unit, owner, layer, constraint and generic/phantom decision. Complete rows and evidence are in model-candidates.tsv, positions.tsv, leaves.tsv, types.tsv and uses.tsv.

Review pagination-review.tsv together with parameterization.tsv. A page number, offset, cursor and page of results are different concepts. Reusing a page size across entities may be intentional; a phantom parameter is warranted only when it prevents a meaningful error.

## Largest production-member groups (first 80)

| Name | Base | Leaves | Owners | Modules |
|---|---|---:|---:|---|
| id | java/lang/String# | 755 | 740 | api, cli, core, db, files, plugin-api, plugin-host, plugin-testkit, plugins/ai-http, plugins/events, plugins/fetchers, plugins/gazetteers, plugins/media, server, upstreams, values, web |
| citekey | java/lang/String# | 515 | 513 | api, cli, core, db, files, plugins/calibre, server, web |
| kind | java/lang/String# | 406 | 404 | api, cli, config, core, db, files, plugin-api, plugin-host, plugin-testkit, plugins/ai-http, plugins/events, server, upstreams, web |
| s | java/lang/String# | 378 | 373 | api, cli, config, core, db, files, plugin-host, plugin-vaults, plugins/calibre, plugins/events, plugins/media, server, upstreams, values, web |
| name | java/lang/String# | 370 | 368 | api, chimney-iron, cli, config, core, db, files, plugin-api, plugin-fixtures/hello-ai, plugin-host, plugin-resolver, plugin-testkit, plugin-vaults, plugins/ai-http, plugins/calibre, plugins/gazetteers, plugins/media, server, upstreams, values, web |
| limit | scala/Int# | 362 | 360 | api, cli, config, core, db, plugin-api, plugins/calibre, plugins/media, server, upstreams, web |
| slug | java/lang/String# | 326 | 324 | api, cli, core, db, server, web |
| text | java/lang/String# | 326 | 303 | api, cli, config, core, db, files, plugin-api, plugin-fixtures/hello-ai, plugin-fixtures/hello-lib, plugin-host, plugin-resolver, plugin-testkit, plugin-vaults, plugins/ai-http, plugins/gazetteers, plugins/media, server, upstreams, web |
| title | java/lang/String# | 260 | 258 | api, cli, core, db, plugin-api, plugin-fixtures/hello-ai, plugin-host, plugins/ai-http, plugins/calibre, plugins/events, plugins/fetchers, plugins/gazetteers, plugins/media, server, upstreams, web |
| goal | java/lang/String# | 233 | 231 | api, core, db, server, web |
| label | java/lang/String# | 225 | 220 | api, cli, config, core, db, files, plugin-api, plugin-host, plugins/calibre, plugins/media, server, upstreams, web |
| wire | java/lang/String# | 198 | 198 | api, config, core, db, server, values, web |
| key | java/lang/String# | 195 | 189 | api, cli, config, core, db, files, plugin-api, plugin-host, plugin-testkit, plugins/ai-http, plugins/calibre, plugins/fetchers, plugins/gazetteers, plugins/media, server, upstreams, web |
| doi | java/lang/String# | 155 | 155 | api, cli, core, db, plugin-api, plugin-host, plugin-testkit, plugins/calibre, plugins/fetchers, server, upstreams, web |
| url | java/lang/String# | 144 | 143 | api, cli, config, core, db, files, plugin-api, plugin-host, plugin-testkit, plugins/ai-http, plugins/events, plugins/fetchers, plugins/gazetteers, plugins/media, server, upstreams, values, web |
| status | java/lang/String# | 135 | 134 | api, cli, core, db, server, upstreams, web |
| body | java/lang/String# | 116 | 108 | api, cli, core, db, files, plugin-api, plugin-host, plugin-testkit, plugins/ai-http, server, web |
| note | java/lang/String# | 114 | 110 | api, core, db, files, plugin-api, server, values, web |
| target | java/lang/String# | 113 | 112 | api, cli, core, db, server, web |
| why | java/lang/String# | 113 | 98 | api, cli, config, core, db, plugin-api, plugin-host, plugin-testkit, plugin-vaults, server, upstreams, web |
| run | java/lang/String# | 111 | 98 | api, cli, core, db, plugin-api, plugin-host, plugin-testkit, plugin-vaults, plugins/calibre, server |
| headers | java/lang/String# | 108 | 53 | core, plugin-api, plugin-host, plugin-testkit, plugins/ai-http, plugins/fetchers, plugins/media, server |
| raw | java/lang/String# | 99 | 99 | api, cli, config, core, db, files, plugin-api, plugin-resolver, plugins/calibre, plugins/media, server, upstreams, values, web |
| ids | java/lang/String# | 98 | 84 | api, cli, core, db, files, plugin-api, plugin-host, plugins/calibre, plugins/media, server, upstreams, web |
| id | scala/Long# | 98 | 98 | api, cli, core, db, plugins/media, server, web |
| provider | java/lang/String# | 93 | 93 | api, db, plugin-host, plugin-testkit, plugins/media, server, web |
| env | java/lang/String# | 90 | 46 | cli, config, plugin-api, plugin-host, plugin-testkit, plugin-vaults, server |
| offset | scala/Int# | 88 | 88 | api, core, db, server, web |
| authors | java/lang/String# | 87 | 84 | api, cli, core, db, plugin-api, plugins/calibre, server, upstreams, web |
| tags | java/lang/String# | 86 | 86 | api, cli, core, db, plugin-api, plugins/calibre, server, web |
| out | java/lang/String# | 83 | 49 | api, cli, plugin-api, plugin-host, plugin-vaults, plugins/calibre, server |
| asOf | scala/Long# | 80 | 80 | api, cli, db, server, web |
| managerId | java/lang/String# | 79 | 79 | api, core, db, server |
| ck | java/lang/String# | 79 | 79 | api, cli, core, db, server, web |
| file | java/lang/String# | 77 | 74 | api, cli, config, db, files, plugin-api, plugin-host, plugin-vaults, plugins/calibre, plugins/fetchers, plugins/media, server, upstreams, values |
| span | scala/Long# | 75 | 38 | api, plugins/media, web |
| dryRun | scala/Boolean# | 72 | 72 | api, cli, core, db, server, web |
| year | scala/Int# | 71 | 71 | api, cli, core, db, plugins/calibre, server, upstreams, web |
| reason | java/lang/String# | 71 | 64 | api, cli, core, db, plugin-api, plugins/calibre, server, web |
| summary | java/lang/String# | 69 | 67 | api, cli, core, db, plugin-api, plugins/calibre, server, web |
| lat | scala/Double# | 67 | 67 | api, cli, config, core, db, server, values, web |
| lon | scala/Double# | 67 | 67 | api, cli, config, core, db, server, values, web |
| jobId | java/lang/String# | 65 | 65 | api, db, server, web |
| parse | java/lang/String# | 65 | 62 | api, cli, config, core, db, files, plugin-api, plugin-resolver, plugin-testkit, plugin-vaults, plugins/ai-http, plugins/events, server, upstreams, values |
| n | scala/Int# | 65 | 65 | api, cli, config, core, db, server, web |
| resolve | java/lang/String# | 65 | 39 | api, cli, core, db, plugin-api, plugin-host, plugin-resolver, plugin-testkit, plugin-vaults, server |
| to | scala/Long# | 64 | 64 | api, cli, db, plugin-host, plugins/events, server, web |
| from | scala/Long# | 64 | 64 | api, cli, db, plugin-host, plugins/events, server, web |
| total | scala/Int# | 64 | 64 | api, cli, core, server, web |
| source | java/lang/String# | 63 | 61 | api, cli, config, core, db, files, plugin-api, plugin-testkit, plugins/fetchers, server, web |
| uid | java/lang/String# | 63 | 63 | api, cli, db, server |
| query | java/lang/String# | 63 | 63 | api, cli, core, plugins/calibre, plugins/events, plugins/media, server, upstreams, web |
| role | java/lang/String# | 63 | 63 | api, core, db, plugin-api, plugins/ai-http, plugins/media, server, values, web |
| placeId | java/lang/String# | 63 | 60 | api, core, db, server, values |
| q | java/lang/String# | 62 | 62 | api, cli, core, db, plugin-host, server, web |
| state | java/lang/String# | 61 | 61 | api, cli, core, db, server, values, web |
| sha256 | java/lang/String# | 60 | 60 | api, cli, core, db, files, plugin-resolver, server |
| notes | java/lang/String# | 60 | 56 | api, config, core, db, server, web |
| what | java/lang/String# | 60 | 57 | api, cli, config, core, db, plugin-host, plugin-vaults, server, web |
| bytes | scala/Byte# | 60 | 60 | api, cli, core, db, files, plugin-api, plugin-host, plugin-testkit, server |
| line | java/lang/String# | 59 | 59 | api, core, db, files, plugin-api, plugin-host, plugin-testkit, server, web |
| updatedAt | java/lang/String# | 58 | 58 | api, core, db, server |
| search | java/lang/String# | 57 | 43 | api, cli, plugin-api, plugin-host, plugins/calibre, plugins/fetchers, plugins/media, server, upstreams, web |
| periodEnd | scala/Long# | 57 | 57 | api, core, db, server, web |
| periodStart | scala/Long# | 57 | 57 | api, core, db, server, web |
| error | java/lang/String# | 56 | 56 | api, core, db, plugin-testkit, plugins/ai-http, server, web |
| count | scala/Int# | 55 | 55 | api, cli, config, core, db, plugin-host, server, web |
| model | java/lang/String# | 55 | 54 | api, core, db, plugin-api, plugin-fixtures/hello-ai, plugin-host, plugins/ai-http, server, web |
| describe | java/lang/String# | 54 | 53 | api, config, core, db, plugin-host, plugins/ai-http, server, values, web |
| citekeys | java/lang/String# | 54 | 51 | api, cli, core, db, plugins/calibre, server |
| names | java/lang/String# | 53 | 48 | api, cli, core, db, plugin-api, server, values, web |
| keys | java/lang/String# | 53 | 51 | api, config, core, db, plugin-api, plugins/gazetteers, server, upstreams, web |
| id | scala/Int# | 53 | 50 | api, cli, db, plugins/calibre, server, web |
| itemId | scala/Long# | 52 | 52 | api, core, db, server |
| targetType | java/lang/String# | 49 | 49 | api, db, server |
| value | java/lang/String# | 49 | 47 | api, cli, config, console, core, db, plugin-vaults, server, web |
| to | java/lang/String# | 49 | 45 | api, cli, core, db, plugin-api, plugins/calibre, server, web |
| current | java/lang/String# | 48 | 38 | api, cli, core, db, plugins/calibre, server, web |
| message | java/lang/String# | 48 | 45 | api, cli, config, core, db, plugin-api, plugin-host, plugin-resolver, plugin-testkit, plugins/ai-http, server, upstreams, values, web |
| check | java/lang/String# | 47 | 39 | api, config, core, db, plugin-api, plugin-fixtures/hello-ai, plugin-host, plugin-testkit, plugins/ai-http, plugins/media, server, values |
