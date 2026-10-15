package com.abdev.partituraspdf.app.integration;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Canonical assets, exposed only by the test APK. Java avoids relying on the target Kotlin
 * runtime during provider initialization. No grants, picker, arbitrary paths or writable data.
 */
public final class CanonicalPdfContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.abdev.partituraspdf.test.integration.pdf";
    private final ConcurrentHashMap<String, Delivery> deliveries = new ConcurrentHashMap<>();

    private static final class Delivery {
        final CountDownLatch done = new CountDownLatch(1);
        long bytes;
        String failure;
        boolean unlinked;
    }

    @Override public boolean onCreate() { return true; }

    private static String asset(String id) throws FileNotFoundException {
        switch (id) {
            case "01": return "pdf/basic/01-single-a4.pdf";
            case "02": return "pdf/basic/02-two-pages.pdf";
            case "08": return "pdf/page-sizes/08-mixed-sizes.pdf";
            case "10": return "pdf/complex/10-scanned-score.pdf";
            case "11": return "pdf/complex/11-vector-score.pdf";
            default: throw new FileNotFoundException("Unknown canonical fixture");
        }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only fixture");
        List<String> path = uri.getPathSegments();
        if (!AUTHORITY.equals(uri.getAuthority()) || path.size() != 2
                || !"document".equals(path.get(0))) throw new FileNotFoundException("Invalid URI");
        String[] parts = path.get(1).split("-", -1);
        if (parts.length != 2 || !("file".equals(parts[0]) || "pipe".equals(parts[0])
                || "broken".equals(parts[0]))) throw new FileNotFoundException("Invalid delivery");
        String source = asset(parts[1]);
        String token = uri.getQueryParameter("request");
        if (token == null || !token.matches("[a-f0-9-]{36}")) {
            throw new FileNotFoundException("A unique request token is required");
        }
        Delivery delivery = new Delivery();
        if (deliveries.putIfAbsent(token, delivery) != null) {
            throw new FileNotFoundException("Request token already used");
        }
        try {
            if ("file".equals(parts[0])) {
                ParcelFileDescriptor result = seekable(source, delivery);
                delivery.done.countDown();
                return result;
            }
            final ParcelFileDescriptor[] pair = ParcelFileDescriptor.createReliablePipe();
            try {
                Thread producer = new Thread(() -> produce(source, pair[1], delivery,
                        "broken".equals(parts[0])), "e03-pdf-pipe");
                producer.setDaemon(true);
                producer.start(); // Never fill a pipe synchronously inside openFile().
                return pair[0];
            } catch (RuntimeException | Error failure) {
                for (ParcelFileDescriptor endpoint : pair) {
                    try { endpoint.close(); }
                    catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                }
                delivery.failure = failure.toString();
                delivery.done.countDown();
                throw failure;
            }
        } catch (IOException failure) {
            delivery.failure = failure.toString();
            delivery.done.countDown();
            FileNotFoundException result = new FileNotFoundException("Fixture I/O failed");
            result.initCause(failure);
            throw result;
        }
    }

    private ParcelFileDescriptor seekable(String source, Delivery delivery) throws IOException {
        File file = File.createTempFile("e03-provider-", ".pdf", getContext().getCacheDir());
        ParcelFileDescriptor descriptor = null;
        try {
            try (InputStream input = getContext().getAssets().open(source);
                    FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    delivery.bytes += count;
                }
            }
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            // The open descriptor retains bytes; no provider cache entry survives delivery.
            if (!file.delete()) throw new IOException("Cannot unlink provider copy");
            delivery.unlinked = true;
            ParcelFileDescriptor result = descriptor;
            descriptor = null;
            return result;
        } finally {
            try {
                if (descriptor != null) descriptor.close();
            } finally {
                if (file.exists() && !file.delete()) {
                    throw new IOException("Provider copy cleanup failed");
                }
            }
        }
    }

    private void produce(String source, ParcelFileDescriptor writer, Delivery delivery, boolean broken) {
        try (InputStream input = getContext().getAssets().open(source);
                ParcelFileDescriptor.AutoCloseOutputStream output =
                        new ParcelFileDescriptor.AutoCloseOutputStream(writer)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                delivery.bytes += count;
                if (broken) {
                    writer.closeWithError("Deliberate provider read failure");
                    delivery.failure = "Deliberate provider read failure";
                    return;
                }
            }
        } catch (IOException failure) {
            // A bounded/cancelled consumer may close early. Preserve evidence, never claim EOF.
            delivery.failure = failure.toString();
            try { writer.closeWithError("Fixture producer failed"); }
            catch (IOException ignored) { /* writer may already be closed */ }
        } finally {
            delivery.done.countDown();
        }
    }

    /** Per-request completion handshake, not timing-based sleeps or shared resettable counters. */
    @Override public Bundle call(String method, String token, Bundle extras) {
        if (!"await".equals(method) || token == null) throw new IllegalArgumentException("Invalid call");
        Delivery delivery = deliveries.get(token);
        if (delivery == null) throw new IllegalArgumentException("Unknown request");
        try {
            if (!delivery.done.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Provider producer did not finish");
            }
            Bundle result = new Bundle();
            result.putLong("bytes", delivery.bytes);
            result.putString("failure", delivery.failure);
            result.putBoolean("unlinked", delivery.unlinked);
            deliveries.remove(token, delivery);
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted provider wait", interrupted);
        }
    }

    @Override public String getType(Uri uri) { return "application/pdf"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { throw readOnly(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw readOnly(); }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { throw readOnly(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw readOnly(); }
    private static UnsupportedOperationException readOnly() {
        return new UnsupportedOperationException("Read-only canonical fixture provider");
    }
}
