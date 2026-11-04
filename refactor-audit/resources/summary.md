# Resource and build AST inventory

49 configuration resources; 782 declared configuration values; 0 include directives; 0 parse failures.
8 build source files; 154 relevant build AST occurrences; 1367 architecture ledger rows.

HOCON is parsed without resolving environment variables or fetching includes. Includes are inventoried separately, not silently flattened. Configuration ownership and effective include resolution still require policy decisions. Parse failures remain explicit.
Build expressions retain ASTs; actual compile/runtime dependency closures come from BuildInventory. Ledger rows remain obligations when their original file disappears. Live-data protection requires process and integration evidence, not source-scanner approval.
