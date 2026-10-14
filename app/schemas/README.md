# Room schema history

The Room Gradle plugin exports database schema JSON to this directory through
KSP. Commit each generated database/version JSON when C01 introduces the first
database, and retain earlier versions for migration tests. Future databases must
keep schema export enabled (`exportSchema = true`). No database or generated
schema exists in C00.

This directory is an `androidTest` asset source for Room's migration test helper;
it is not a production asset source. Keep schema history here rather than under
`build/`, so clean builds and migration tests can use committed versions.

Room's plugin manages compiler schema arguments and Gradle task inputs/outputs.
Do not add a second `room.schemaLocation` KSP argument pointing elsewhere.
