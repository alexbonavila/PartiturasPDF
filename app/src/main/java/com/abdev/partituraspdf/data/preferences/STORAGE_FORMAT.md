# C04 application preferences

The future composition root obtains `applicationPreferencesStore(applicationContext)` once and
injects that store into `DataStorePreferencesRepository`. The private top-level AndroidX delegate
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

Backup audit: the current manifest allows backup and the existing backup/extraction rules have no
exclusions. This ordinary `files/` preference file can therefore be backed up/transferred. It
contains only the three non-sensitive settings above. C04 does not alter application-wide rules;
future sensitive persistence and incompatible formats require separately approved backup/migration
policies. Never reset/delete a corrupt file automatically.
