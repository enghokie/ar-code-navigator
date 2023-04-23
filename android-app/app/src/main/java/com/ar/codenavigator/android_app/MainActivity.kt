package com.ar.codenavigator.android_app

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MenuInflater
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.ar.codenavigator.utils.CodeData
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.Availability
import com.google.ar.core.ArCoreApk.InstallStatus
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


class MainActivity :
    FragmentActivity(),
    CodeListFragment.Callback,
    ARFragment.Callback {
    // Google Play Services for AR if necessary.
    private var _userRequestedInstall = true
    private var _initCallbacks = false
    private val _codeData: CodeData = CodeData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupTessdata(applicationContext.filesDir, assets.open("tessdata/eng.traineddata"))
    }

    override fun onResumeFragments() {
        super.onResumeFragments()

        if (!_initCallbacks) {
            val arFrag: ARFragment =
                supportFragmentManager.findFragmentById(R.id.fragmentAR) as ARFragment
            arFrag?.setupButtonCallback(findViewById<Button>(R.id.captureButton))

            val imageFrag: CodeListFragment = supportFragmentManager.findFragmentById(R.id.fragmentOne) as CodeListFragment
            val langButton: Button = findViewById<Button>(R.id.languageButton)
            langButton.setOnClickListener {button ->
                val popup = PopupMenu(applicationContext, button)
                popup.setOnMenuItemClickListener { menuItem ->
                    imageFrag.setListViewCodeLanguage(menuItem.title.toString())
                    langButton.text = "Code | ${menuItem.title}"
                    true
                }

                val inflater: MenuInflater = popup.menuInflater
                inflater.inflate(R.menu.code_menu, popup.menu)
                popup.show()
            }

            _initCallbacks = true
        }
    }

    override fun onItemSelected(id: Int) {
        GlobalScope.async {
            val detailsFrag: CodeTextFragment = supportFragmentManager.findFragmentById(R.id.fragmentTwo) as CodeTextFragment
            val arFrag: ARFragment = supportFragmentManager.findFragmentById(R.id.fragmentAR) as ARFragment

            val ocrText = detailsFrag.doOcr(id, _codeData)
            runOnUiThread(Runnable {
                findViewById<TextView>(R.id.ocrText)?.text = ocrText
                findViewById<TextView>(R.id.hierarchyText)?.text = _codeData.formattedText()

                arFrag.renderCodeModels(id, _codeData)
            })
        }
    }

    override fun onCapturedImage(bitmap: Bitmap) {
        GlobalScope.async {
            val imageFrag: CodeListFragment =
                supportFragmentManager.findFragmentById(R.id.fragmentOne) as CodeListFragment
            imageFrag.updateCapturedImage(bitmap)
        }
    }

    override fun onResume() {
        super.onResume()

        // ARCore requires camera permission to operate.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this)
            return
        }

        // Ensure that Google Play Services for AR and ARCore device profile data are
        // installed and up to date.
        try {
            if (_userRequestedInstall) {
                val arSupported =
                    isARCoreSupportedAndUpToDate(applicationContext, this, _userRequestedInstall)
                _userRequestedInstall = false
                if (!arSupported)  {
                    Toast.makeText(this, "ARCore not supported", Toast.LENGTH_LONG)
                        .show()
                    return
                }

                Toast.makeText(this, "ARCore supported!", Toast.LENGTH_LONG)
                    .show()
            }
        } catch (e: UnavailableUserDeclinedInstallationException) {
            // Display an appropriate message to the user and return gracefully.
            print("User declined AR session - unable to proceed: $e")
            Toast.makeText(this, "User declined AR session - unable to proceed: $e", Toast.LENGTH_LONG)
                .show()
            return
        } catch (e: Exception) {
            print("AR session exception: $e")
            Toast.makeText(this, "AR session exception: $e", Toast.LENGTH_LONG)
                .show()
            return  // mSession remains null, since session creation has failed.
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }
}

fun setupTessdata(appFileDir: File, engModelStrm: InputStream) {
    val localTessfolder = File(appFileDir, "tessdata")
    localTessfolder.mkdir()

    val outStrm = FileOutputStream(File(localTessfolder, "eng.traineddata"))
    val engModelBytes = engModelStrm.readBytes()
    outStrm.write(engModelBytes)
}

// Verify that ARCore is installed and using the current version.
private fun isARCoreSupportedAndUpToDate(context: Context, activity: MainActivity, userRequested: Boolean): Boolean {
    when (ArCoreApk.getInstance().checkAvailability(context)) {
        Availability.SUPPORTED_INSTALLED -> return true
        Availability.SUPPORTED_APK_TOO_OLD, Availability.SUPPORTED_NOT_INSTALLED -> {
            try {
                // Request ARCore installation or update if needed.
                return when (ArCoreApk.getInstance().requestInstall(activity, userRequested)) {
                    InstallStatus.INSTALL_REQUESTED -> {
                        print("ARCore installation requested.")
                        false
                    }
                    InstallStatus.INSTALLED -> true
                }
            } catch (e: UnavailableException) {
                print("ARCore not installed: $e")
                Toast.makeText(context, "ARCore not installed: $e", Toast.LENGTH_LONG)
                    .show()
            }
            return false
        }
        Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
            // This device is not supported for AR.
            print("ARCore not supported with this device")
            Toast.makeText(context, "ARCore not supported on this device", Toast.LENGTH_LONG)
                .show()
            return false
        }
        Availability.UNKNOWN_CHECKING, Availability.UNKNOWN_ERROR, Availability.UNKNOWN_TIMED_OUT -> {
            print("ARCore unknown error occurred")
            Toast.makeText(context, "ARCore unknown error occurred", Toast.LENGTH_LONG)
                .show()
            return false
        }
    }
}
