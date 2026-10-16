# C04 application preferences

The implemented E application-composition factory obtains `applicationPreferencesStore(application)`
and injects it into `DataStorePreferencesRepository`. The main user interface does not yet
consume this graph; an implemented factory is not a completed settings screen. The private top-level AndroidX delegate
returns the same store across calls and contexts. AndroidX owns its process-lifetime IO scope;
the repository neither launches jobs nor closes the shared store. Do not open the same file with
another delegate or factory. This is single-process storage, not a multi-process contract.

Tests instead use `PreferenceDataStoreFactory` with unique files and explicitly owned scopes.
Cancel and join the scope before reopening a file or deleting its directory.

The initial file is `files/datastore/app_preferences.preferences_pb`, using DataStore's protobuf
format. All three owned values are strings:

| Key | Purpose / allowed domain keys | Absent default |
| --- | --- | --- |
| `theme_selection` | Appearance: `system`, `light`, `dark` | `system` |
| `language_selection` | Language: `system`, `en`, `ca`, `es` | `system` |
| `reader_page_fit` | Reader fit: `page`, `width` | `page` |

Only absent keys imply defaults. Unknown strings, wrong types and corrupted protobuf fail; there
is no replacement corruption handler, format version or speculative migration. Updates validate
all owned fields and change one field inside DataStore's atomic `edit`, retaining unrelated keys.
No ordinals, implicit enum names, localized labels, PDFs, document locations or secrets are stored.

Each observation collector starts with Loading and collects the real store independently.
Operational `IOException` becomes PRIVATE_STORAGE_UNAVAILABLE, but `CorruptionException` is
explicitly excluded. Other failures propagate, including wrong types, invalid values and lifecycle
defects. Cancellation takes precedence over I/O classification. Failed observations terminate;
recollection starts Loading. A cancelled update may have committed: reconcile by observing.

Backup policy (updated after C04): the manifest enables backup but the implemented **Policy B**
uses an exact-file allowlist in `backup_rules.xml` (API 26–30) and
`data_extraction_rules.xml` (API 31+ cloud backup and device transfer). Only
`file:datastore/app_preferences.preferences_pb` is eligible; Room databases,
SQLite sidecars and other private files are excluded. The whole protobuf, including
the three non-sensitive settings above, is selected rather than individual keys.
Policy XML/APK validation passed; actual backup, restore and device-to-device
transfer remain **DEFERRED / NOT RUN**. See `docs/PHASE_C_DEFERRED_ACCEPTANCE.md`.
Do not add secrets or document references to this file without reviewing Policy B.
Future incompatible formats require an approved migration policy. Never reset/delete
a corrupt file automatically.
