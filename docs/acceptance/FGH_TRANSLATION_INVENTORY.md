# Complete resource inventory — 2026-10-19

Source delivery: `46c1b117dcee26902e675426755de551bbb68e01`.
Main: **147 IDs in each of values/values-es/values-ca/values-en** (3 starter + 72 F + 48 G + 24 H).
Debug-only SAF acceptance: **20 additional IDs in each locale**, also migrated to Spanish default
with original English preserved explicitly. All Spanish values are preserved by value. The name,
type and argument contracts match; there are no plurals/arrays in this revision's user copy.
No existing resource ID or formatting contract was removed. Six staged files were replaced by
complete feature XMLs only after in-memory key/format parity checks. No MissingTranslation
suppression remains on those feature resources.

Author review checked pending versus confirmed writes, registered versus temporary documents,
revoked versus missing READ permissions, metadata preservation and visual-only zoom. English and
Catalan are independently written translations, not Spanish clones. Product names remain unchanged.
**Independent native-speaker/human linguistic review: pending**; validator success proves structure,
not linguistic quality or on-device clipping/TalkBack. Resource-context Android tests are compiled
but NOT RUN in this cloud. Activity locale application/recreation remains Phase I.

Each ID below exists once per listed locale; the validator checks their values and format contracts.
The default/es equality check includes XML contents, not just name counts.


## main IDs (same exact set in all four locales)

```text
app_name
library_action_error
library_back
library_change_folder
library_empty
library_enter_folder
library_error_conflict
library_error_deleted
library_error_invalid
library_error_not_registered
library_error_permission_denied
library_error_permission_revoked
library_error_private_storage
library_error_provider_io
library_error_provider_unavailable
library_error_resource
library_error_stale
library_error_unsupported
library_favorite_add
library_favorite_add_description
library_favorite_error
library_favorite_off
library_favorite_on
library_favorite_pending
library_favorite_reconciling
library_favorite_remove
library_favorite_remove_description
library_folder_type
library_initializing
library_listing_error
library_loading
library_location
library_lookup_error
library_lookup_pending
library_metadata_error
library_metadata_loading
library_navigation_error
library_navigation_pending
library_nested_segment
library_no_root
library_not_loaded
library_open
library_open_accepted
library_open_description
library_open_error
library_open_pending
library_open_rejected
library_partial
library_pdf_type
library_refresh
library_register
library_register_description
library_registered
library_registration_error
library_registration_pending
library_restore_error
library_retry
library_retry_favorite
library_retry_lookup
library_retry_metadata
library_retry_open
library_retry_registration
library_root_segment
library_select_folder
library_select_folder_description
library_selection_canceled
library_selection_error
library_selection_pending
library_size
library_title
library_unknown_folder
library_unknown_pdf
library_unregistered
reader_awaiting_draw
reader_back
reader_canceled
reader_checkpoint_canceled
reader_checkpoint_failure
reader_checkpoint_scheduling
reader_checkpoint_transient
reader_checkpoint_unconfirmed
reader_error_closed
reader_error_deleted
reader_error_input_limit
reader_error_invalid
reader_error_io
reader_error_page
reader_error_password
reader_error_permission
reader_error_provider
reader_error_registration
reader_error_render_request
reader_error_resource
reader_error_revoked
reader_error_stale
reader_error_storage
reader_error_unsupported
reader_fit_page
reader_fit_width
reader_idle
reader_next
reader_no_visible_page
reader_opening
reader_options
reader_page_counter
reader_page_description
reader_page_unpresented
reader_paused
reader_previous
reader_recovery_back
reader_recovery_checkpoint
reader_recovery_open
reader_recovery_render
reader_rendering
reader_retired
reader_retry
reader_waiting_viewport
reader_zoom_in
reader_zoom_out
reader_zoom_reset
reader_zoom_state
settings_error_other
settings_error_storage
settings_fit_page
settings_fit_page_description
settings_fit_title
settings_fit_width
settings_fit_width_description
settings_fit_write_error
settings_loading
settings_pending
settings_read_error
settings_reconciling
settings_reconciling_uncertain
settings_requested
settings_retry_fit
settings_retry_read
settings_retry_theme
settings_theme_dark
settings_theme_light
settings_theme_system
settings_theme_title
settings_theme_write_error
settings_title
settings_verify_again
starter_greeting
starter_recipient
```

### Resource-file hashes

| File | SHA-256 |
|---|---|
| main/res/values/library.xml | `93b0476a52067cd08d81090f0e9db9fffcb50bdab41b70f43cf0e3e3b4c07417` |
| main/res/values/reader.xml | `72123913268cf78a7d15dc1eae36ca9a861a0b1d78f05f0c0144ac4771a4ec41` |
| main/res/values/settings.xml | `7f31a4085847bc8bcec40364e7cb027e8933340819ddac6f732e1b826de5cc3e` |
| main/res/values/strings.xml | `99c21af97b2cc6671cd9c70753b36138d0562d45cbeb17195fbf45243a94e0fc` |
| main/res/values-es/library.xml | `93b0476a52067cd08d81090f0e9db9fffcb50bdab41b70f43cf0e3e3b4c07417` |
| main/res/values-es/reader.xml | `72123913268cf78a7d15dc1eae36ca9a861a0b1d78f05f0c0144ac4771a4ec41` |
| main/res/values-es/settings.xml | `7f31a4085847bc8bcec40364e7cb027e8933340819ddac6f732e1b826de5cc3e` |
| main/res/values-es/strings.xml | `99c21af97b2cc6671cd9c70753b36138d0562d45cbeb17195fbf45243a94e0fc` |
| main/res/values-ca/library.xml | `22fe46ca7cb914fbcced4fd94e95490ad68733e1676e5e3c3231eea781cb42a7` |
| main/res/values-ca/reader.xml | `233f109bb05beeac4cc53496e8a89073d30ed4f6188d036e208c4a940c15392c` |
| main/res/values-ca/settings.xml | `44ef5635bda483a72bbf3513df2cfe646eb0a52ca6ff8725d8328a1816ebe0ea` |
| main/res/values-ca/strings.xml | `0dfae2b4c3233b6f67c5854334e142a85239e31e7defe2dffddee9c7f55a1387` |
| main/res/values-en/library.xml | `a4b7387c41dfbf4e4a921d994344775d41864e2c9817d2239ac01e8ce6c381a8` |
| main/res/values-en/reader.xml | `ec64dfeb9999958b7f44e0d001863da1d59d43227add753830c114b07aed8389` |
| main/res/values-en/settings.xml | `ef64b27c83f2742ac60d5b631209aa0fe0ffcc876c9399740226bc2bf5613e52` |
| main/res/values-en/strings.xml | `351e2109def96e95c5fc0544ab963f3c15550ca230fca961b60204b37e2b55fa` |

## debug IDs (same exact set in all four locales)

```text
saf_accept_cancelled
saf_accept_check
saf_accept_grant_error
saf_accept_grant_saved
saf_accept_grant_status
saf_accept_instructions
saf_accept_list_error
saf_accept_no
saf_accept_no_folder
saf_accept_no_pdf
saf_accept_preference_error
saf_accept_read_error
saf_accept_read_ok
saf_accept_ready
saf_accept_release
saf_accept_release_error
saf_accept_released
saf_accept_select
saf_accept_title
saf_accept_yes
```

### Resource-file hashes

| File | SHA-256 |
|---|---|
| debug/res/values/strings.xml | `081bb5a7b18b3a416508951d134a9cb21f41bcb2a99d7fa4715dd9cb1dc65c81` |
| debug/res/values-es/strings.xml | `081bb5a7b18b3a416508951d134a9cb21f41bcb2a99d7fa4715dd9cb1dc65c81` |
| debug/res/values-ca/strings.xml | `0050246aff398893ab2a8d872c2bdeb24d833bdf1b66503bae34a168e676adb5` |
| debug/res/values-en/strings.xml | `3db8798bade25b8b0edfac4adcb2501a2632382774eeee63a66ba6a85d022b33` |
