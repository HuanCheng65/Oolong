package oolong.client.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions.getExtensionVersion
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract
import oolong.client.App
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.ui.picker.ImagePickerActivity

fun isPhotoPickerAvailable(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        true
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getExtensionVersion(Build.VERSION_CODES.R) >= 2
    } else {
        false
    }
}

fun shouldUsePhotoPicker(): Boolean {
    return !Settings.of(App.INSTANCE)[SettingKeys.DoNotUsePhotoPicker] && isPhotoPickerAvailable()
}

fun Intent.getClipDataUris(): List<Uri> {
    // Use a LinkedHashSet to maintain any ordering that may be
    // present in the ClipData
    val resultSet = LinkedHashSet<Uri>()
    data?.let { data ->
        resultSet.add(data)
    }
    val clipData = clipData
    if (clipData == null && resultSet.isEmpty()) {
        return emptyList()
    } else if (clipData != null) {
        for (i in 0 until clipData.itemCount) {
            val uri = clipData.getItemAt(i).uri
            if (uri != null) {
                resultSet.add(uri)
            }
        }
    }
    return resultSet.toList()
}

/** Picks up to the given number of pictures, in the system photo picker or the app's own. */
object PickMediasContract : ActivityResultContract<Int, List<Uri>>() {
    override fun createIntent(context: Context, input: Int): Intent {
        if (shouldUsePhotoPicker()) {
            return Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
                if (input > 1) {
                    putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, input)
                }
            }
        }
        return ImagePickerActivity.intent(context, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        return intent.getClipDataUris()
    }
}
