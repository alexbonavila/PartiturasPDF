package com.abdev.partituraspdf.storage.saf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-APK-only provider for ContentResolver integration checks on API 26.
 * Written in Java because Android initializes test-APK providers before the
 * instrumentation runner makes the target APK's Kotlin runtime accessible.
 * It does not implement picker grants or the DocumentsProvider protocol.
 */
public final class SafFixtureContentProvider extends ContentProvider {
    private static final String ROOT = "root:Music";
    private static final String SCORE = "root:Music/score.pdf";
    private static final String CONCERT = "root:Music/Concert";
    private static final String STREAM = "root:Music/stream.pdf";
    private static final String EMPTY = "root:Music/Empty";
    private static final String PHOTO = "root:Music/photo.jpg";
    private static final String PART = "root:Music/Concert/part.pdf";
    private static final String SCORE_TEXT = "%PDF-1.4\nsaf-fixture-score\n";
    private static final String STREAM_TEXT = "%PDF-1.4\nsaf-fixture-stream\n";
    private static final String PART_TEXT = "%PDF-1.4\nsaf-fixture-part\n";
    private static final String DIRECTORY_MIME = DocumentsContract.Document.MIME_TYPE_DIR;
    private static final String[] COLUMNS = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    private static final class Item {
        final String id;
        final String name;
        final String mime;
        final Long size;

        Item(String id, String name, String mime, Long size) {
            this.id = id;
            this.name = name;
            this.mime = mime;
            this.size = size;
        }
    }

    private static final Map<String, Item> ITEMS = createItems();

    private static Map<String, Item> createItems() {
        Map<String, Item> items = new HashMap<>();
        add(items, new Item(ROOT, "Music", DIRECTORY_MIME, null));
        add(items, new Item(SCORE, "Score.pdf", "application/pdf", (long) SCORE_TEXT.getBytes(StandardCharsets.UTF_8).length));
        add(items, new Item(CONCERT, "Concert", DIRECTORY_MIME, null));
        add(items, new Item(STREAM, "Stream.pdf", "application/pdf", (long) STREAM_TEXT.getBytes(StandardCharsets.UTF_8).length));
        add(items, new Item(EMPTY, "Empty", DIRECTORY_MIME, null));
        add(items, new Item(PHOTO, "photo.jpg", "image/jpeg", 3L));
        add(items, new Item(PART, "Part.pdf", "application/pdf", (long) PART_TEXT.getBytes(StandardCharsets.UTF_8).length));
        return Collections.unmodifiableMap(items);
    }

    private static void add(Map<String, Item> items, Item item) {
        items.put(item.id, item);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        List<String> path = uri.getPathSegments();
        List<String> ids;
        if (path.size() == 5 && "tree".equals(path.get(0)) && ROOT.equals(path.get(1))
                && "document".equals(path.get(2)) && "children".equals(path.get(4))) {
            String folder = path.get(3);
            if (ROOT.equals(folder)) {
                ids = Arrays.asList(SCORE, CONCERT, STREAM, EMPTY, PHOTO);
            } else if (CONCERT.equals(folder)) {
                ids = Collections.singletonList(PART);
            } else if (EMPTY.equals(folder)) {
                ids = Collections.emptyList();
            } else {
                throw new IllegalArgumentException("Unknown fixture directory");
            }
        } else {
            ids = Collections.singletonList(documentId(uri));
        }

        String[] columns = projection != null ? projection : COLUMNS;
        MatrixCursor result = new MatrixCursor(columns);
        for (String id : ids) {
            Item item = ITEMS.get(id);
            if (item == null) {
                throw new IllegalArgumentException("Unknown fixture document");
            }
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                String column = columns[i];
                if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(column)) row[i] = item.id;
                else if (DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(column)) row[i] = item.name;
                else if (DocumentsContract.Document.COLUMN_MIME_TYPE.equals(column)) row[i] = item.mime;
                else if (DocumentsContract.Document.COLUMN_SIZE.equals(column)) row[i] = item.size;
                else if (DocumentsContract.Document.COLUMN_LAST_MODIFIED.equals(column)) row[i] = 1000L;
            }
            result.addRow(row);
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Fixture is read-only");
        String id = documentId(uri);
        final String value;
        if (SCORE.equals(id)) value = SCORE_TEXT;
        else if (STREAM.equals(id)) value = STREAM_TEXT;
        else if (PART.equals(id)) value = PART_TEXT;
        else throw new FileNotFoundException("Unknown fixture document");

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (STREAM.equals(id)) {
            ParcelFileDescriptor[] pipe = null;
            try {
                pipe = ParcelFileDescriptor.createPipe();
                // All fixture documents are smaller than the pipe buffer.
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                        new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(bytes);
                }
                return pipe[0];
            } catch (IOException failure) {
                if (pipe != null) {
                    try { pipe[0].close(); } catch (IOException ignored) { /* preserve original failure */ }
                }
                throw asNotFound(failure);
            }
        }

        Context context = getContext();
        if (context == null) throw new FileNotFoundException("Fixture context unavailable");
        File file = null;
        try {
            file = File.createTempFile("saf-07-fixture-", ".pdf", context.getCacheDir());
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(bytes);
            }
            // The descriptor keeps the bytes alive even after unlinking the cache entry.
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException failure) {
            throw asNotFound(failure);
        } finally {
            if (file != null) file.delete();
        }
    }

    private static FileNotFoundException asNotFound(IOException failure) {
        FileNotFoundException result = new FileNotFoundException("Fixture I/O failed");
        result.initCause(failure);
        return result;
    }

    @Override
    public String getType(Uri uri) {
        Item item = ITEMS.get(documentId(uri));
        return item == null ? null : item.mime;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only test fixture");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only test fixture");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only test fixture");
    }

    private static String documentId(Uri uri) {
        List<String> path = uri.getPathSegments();
        if (path.size() == 4 && "tree".equals(path.get(0)) && ROOT.equals(path.get(1))
                && "document".equals(path.get(2))) return path.get(3);
        if (path.size() == 2 && "document".equals(path.get(0))) return path.get(1);
        throw new IllegalArgumentException("Invalid fixture URI");
    }
}
