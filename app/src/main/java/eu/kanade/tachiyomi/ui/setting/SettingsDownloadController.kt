package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.getFilePicker
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsDownloadController : SettingsController() {

    private val db: DatabaseHelper by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.pref_category_downloads

        preference {
            key = Keys.downloadsDirectory
            titleRes = R.string.pref_download_directory
            onClick {
                val ctrl = DownloadDirectoriesDialog()
                ctrl.targetController = this@SettingsDownloadController
                ctrl.showDialog(router)
            }

            preferences.downloadsDirectory().asObservable()
                    .subscribeUntilDestroy { path ->
                        val dir = UniFile.fromUri(context, Uri.parse(path))
                        summary = dir.filePath ?: path
                    }
        }
        switchPreference {
            key = Keys.downloadOnlyOverWifi
            titleRes = R.string.pref_download_only_over_wifi
            defaultValue = true
        }
        preferenceCategory {
            titleRes = R.string.pref_remove_after_read

            switchPreference {
                key = Keys.removeAfterMarkedAsRead
                titleRes = R.string.pref_remove_after_marked_as_read
                defaultValue = false
            }
            intListPreference {
                key = Keys.removeAfterReadSlots
                titleRes = R.string.pref_remove_after_read
                entriesRes = arrayOf(R.string.disabled, R.string.last_read_chapter,
                        R.string.second_to_last, R.string.third_to_last, R.string.fourth_to_last,
                        R.string.fifth_to_last)
                entryValues = arrayOf("-1", "0", "1", "2", "3", "4")
                defaultValue = "-1"
                summary = "%s"
            }
        }

        val dbCategories = db.getCategories().executeAsBlocking()

        preferenceCategory {
            titleRes = R.string.pref_download_new

            switchPreference {
                key = Keys.downloadNew
                titleRes = R.string.pref_download_new
                defaultValue = false
            }
            multiSelectListPreference {
                key = Keys.downloadNewCategories
                titleRes = R.string.pref_download_new_categories
                entries = dbCategories.map { it.name }.toTypedArray()
                entryValues = dbCategories.map { it.id.toString() }.toTypedArray()

                preferences.downloadNew().asObservable()
                        .subscribeUntilDestroy { isVisible = it }

                preferences.downloadNewCategories().asObservable()
                        .subscribeUntilDestroy {
                            val selectedCategories = it
                                    .mapNotNull { id -> dbCategories.find { it.id == id.toInt() } }
                                    .sortedBy { it.order }

                            summary = if (selectedCategories.isEmpty())
                                resources?.getString(R.string.all)
                            else
                                selectedCategories.joinToString { it.name }
                        }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            DOWNLOAD_DIR_PRE_L -> if (data != null && resultCode == Activity.RESULT_OK) {
                val uri = Uri.fromFile(File(data.data!!.path))
                preferences.downloadsDirectory().set(uri.toString())
            }
            DOWNLOAD_DIR_L -> if (data != null && resultCode == Activity.RESULT_OK) {
                val context = applicationContext ?: return
                val uri = data.data!!
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                @Suppress("NewApi")
                context.contentResolver.takePersistableUriPermission(uri, flags)

                val file = UniFile.fromUri(context, uri)
                preferences.downloadsDirectory().set(file.uri.toString())
            }
        }
    }

    fun predefinedDirectorySelected(selectedDir: String) {
        val path = Uri.fromFile(File(selectedDir))
        preferences.downloadsDirectory().set(path.toString())
    }

    fun customDirectorySelected(currentDir: String) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            startActivityForResult(preferences.context.getFilePicker(currentDir), DOWNLOAD_DIR_PRE_L)
        } else {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            try {
                startActivityForResult(intent, DOWNLOAD_DIR_L)
            } catch (e: ActivityNotFoundException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startActivityForResult(preferences.context.getFilePicker(currentDir), DOWNLOAD_DIR_L)
                }
            }

        }
    }

    class DownloadDirectoriesDialog : DialogController() {

        private val preferences: PreferencesHelper = Injekt.get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val activity = activity!!
            val currentDir = preferences.downloadsDirectory().getOrDefault()
            val externalDirs = getExternalDirs() + File(activity.getString(R.string.custom_dir))
            val selectedIndex = externalDirs.map(File::toString).indexOfFirst { it in currentDir }

            return MaterialDialog.Builder(activity)
                    .items(externalDirs)
                    .itemsCallbackSingleChoice(selectedIndex) { _, _, which, text ->
                        val target = targetController as? SettingsDownloadController
                        if (which == externalDirs.lastIndex) {
                            target?.customDirectorySelected(currentDir)
                        } else {
                            target?.predefinedDirectorySelected(text.toString())
                        }
                        true
                    }
                    .build()
        }

        private fun getExternalDirs(): List<File> {
            val defaultDir = Environment.getExternalStorageDirectory().absolutePath +
                    File.separator + resources?.getString(R.string.app_name) +
                    File.separator + "downloads"

            return mutableListOf(File(defaultDir)) +
                    ContextCompat.getExternalFilesDirs(activity!!, "").filterNotNull()
        }
    }

    private companion object {
        const val DOWNLOAD_DIR_PRE_L = 103
        const val DOWNLOAD_DIR_L = 104
    }
}
