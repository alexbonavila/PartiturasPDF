package com.abdev.partituraspdf.pdf.android.isolated;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
/** Private, versioned control plane. Pixel bytes travel only over a reliable pipe. */
interface IPdfWorker {
    Bundle identity();
    Bundle open(String session, in ParcelFileDescriptor input, int dimension, long pixels);
    Bundle info(String epoch, String session, int page);
    Bundle render(String epoch, String session, String request, int page, int width, int height, long generation, in ParcelFileDescriptor output);
    Bundle closeSession(String epoch, String session);
    oneway void retire();
}
