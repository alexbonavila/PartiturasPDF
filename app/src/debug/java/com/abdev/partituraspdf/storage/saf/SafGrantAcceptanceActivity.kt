package com.abdev.partituraspdf.storage.saf

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.ReadAccessLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manual API 26 SAF acceptance harness. Only compiled into the debug APK. */
class SafGrantAcceptanceActivity : ComponentActivity() {
    private val preferences by lazy { getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private val grants by lazy { SafTreeGrantAccess(contentResolver) }
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var status: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            show(getString(R.string.saf_accept_cancelled))
            return@registerForActivityResult
        }
        val data = result.data
        when (val saved = grants.persistPickerReadGrant(data?.data, data?.flags ?: 0)) {
            is Outcome.Failure -> show(getString(R.string.saf_accept_grant_error, saved.error))
            is Outcome.Success -> {
                // commit() is intentional: the manual restart check immediately follows selection.
                val durable = preferences.edit().putString(KEY_TREE, saved.value.value).commit()
                if (durable) {
                    show(getString(R.string.saf_accept_grant_saved))
                    checkAccess()
                } else {
                    show(getString(R.string.saf_accept_preference_error))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inset = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(inset, inset, inset, inset)
        }
        val info = TextView(this).apply { text = getString(R.string.saf_accept_instructions) }
        info.setTextColor(android.graphics.Color.WHITE)
        layout.addView(info)
        fun addAction(title: Int, action: () -> Unit) {
            val button = Button(this).apply {
                setText(title)
                setOnClickListener { action() }
            }
            layout.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        addAction(R.string.saf_accept_select) { picker.launch(grants.treePickerIntent()) }
        addAction(R.string.saf_accept_check) { checkAccess() }
        addAction(R.string.saf_accept_release) { releaseAccess() }
        status = TextView(this).apply { text = getString(R.string.saf_accept_ready) }
        status.setTextColor(android.graphics.Color.WHITE)
        layout.addView(status)
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun savedFolder(): FolderRef? = preferences.getString(KEY_TREE, null)
        ?.let { FolderRef(SafUriCodec.PROVIDER_ID, it) }

    private fun show(message: String) {
        status.text = message
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun checkAccess() {
        val folder = savedFolder() ?: run {
            show(getString(R.string.saf_accept_no_folder))
            return
        }
        when (val grant = grants.hasPersistedReadGrant(folder)) {
            is Outcome.Failure -> show(getString(R.string.saf_accept_grant_error, grant.error))
            is Outcome.Success -> {
                val yesNo = getString(if (grant.value) R.string.saf_accept_yes else R.string.saf_accept_no)
                val prefix = getString(R.string.saf_accept_grant_status, yesNo)
                show(prefix)
                work.launch {
                    // Attempt real provider access even after release; denial does not prove deletion.
                    val detail = withContext(Dispatchers.IO) { probe(folder) }
                    show("$prefix\n$detail")
                }
            }
        }
    }

    private suspend fun probe(folder: FolderRef): String {
        val catalog = SafDocumentCatalog(contentResolver)
        return when (val listed = catalog.list(folder)) {
            is Outcome.Failure -> getString(R.string.saf_accept_list_error, listed.error)
            is Outcome.Success -> {
                val entries = listed.value.entries
                val firstPdf = entries.firstOrNull { it.info.mimeType.equals("application/pdf", true) }
                    ?: return getString(R.string.saf_accept_no_pdf, entries.size)
                val reader = SafDocumentReadAccess(contentResolver, this)
                when (val opened = reader.openSeekable(firstPdf.storage, ReadAccessLimits(MAX_COPY_BYTES))) {
                    is Outcome.Failure -> getString(R.string.saf_accept_read_error, entries.size, opened.error)
                    is Outcome.Success -> {
                        val seekable = opened.value.use { it.seekable }
                        val yesNo = getString(if (seekable) R.string.saf_accept_yes else R.string.saf_accept_no)
                        getString(R.string.saf_accept_read_ok, entries.size, yesNo)
                    }
                }
            }
        }
    }

    private fun releaseAccess() {
        val folder = savedFolder() ?: run {
            show(getString(R.string.saf_accept_no_folder))
            return
        }
        when (val result = grants.releasePersistedReadGrant(folder)) {
            is Outcome.Success -> {
                // Retain the opaque tree URI so a restart can test access after revocation.
                show(getString(R.string.saf_accept_released))
            }
            is Outcome.Failure -> show(getString(R.string.saf_accept_release_error, result.error))
        }
    }

    override fun onDestroy() {
        work.cancel()
        super.onDestroy()
    }

    private companion object {
        const val PREFERENCES = "saf-grant-acceptance-debug"
        const val KEY_TREE = "selected-tree-uri"
        const val MAX_COPY_BYTES = 64L * 1024 * 1024
    }
}
