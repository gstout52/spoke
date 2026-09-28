package com.gstout.murmur

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

/** Setup and settings screen. */
class MainActivity : AppCompatActivity() {

    private val prefs by lazy { Prefs(this) }

    private lateinit var btnMic: MaterialButton
    private lateinit var btnNotifications: MaterialButton
    private lateinit var btnAccessibility: MaterialButton
    private lateinit var providerGroup: RadioGroup
    private lateinit var groqKey: TextInputEditText
    private lateinit var openaiKey: TextInputEditText
    private lateinit var anthropicKey: TextInputEditText
    private lateinit var cleanupSwitch: MaterialSwitch
    private lateinit var vocabulary: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnMic = findViewById(R.id.btnMic)
        btnNotifications = findViewById(R.id.btnNotifications)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        providerGroup = findViewById(R.id.providerGroup)
        groqKey = findViewById(R.id.groqKey)
        openaiKey = findViewById(R.id.openaiKey)
        anthropicKey = findViewById(R.id.anthropicKey)
        cleanupSwitch = findViewById(R.id.cleanupSwitch)
        vocabulary = findViewById(R.id.vocabulary)

        btnMic.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
        btnNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
            }
        }
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<View>(R.id.btnAppInfo).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        providerGroup.setOnCheckedChangeListener { _, _ -> updateProviderFields() }
        findViewById<View>(R.id.btnSave).setOnClickListener {
            save()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        providerGroup.check(
            if (prefs.provider == Prefs.PROVIDER_OPENAI) R.id.providerOpenai else R.id.providerGroq
        )
        groqKey.setText(prefs.groqKey)
        openaiKey.setText(prefs.openaiKey)
        anthropicKey.setText(prefs.anthropicKey)
        cleanupSwitch.isChecked = prefs.cleanupEnabled
        vocabulary.setText(prefs.vocabulary)
        updateProviderFields()
    }

    override fun onResume() {
        super.onResume()
        updateSetupStatus()
    }

    override fun onPause() {
        save()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateSetupStatus()
    }

    private fun save() {
        prefs.provider = if (providerGroup.checkedRadioButtonId == R.id.providerOpenai) {
            Prefs.PROVIDER_OPENAI
        } else {
            Prefs.PROVIDER_GROQ
        }
        prefs.groqKey = groqKey.text?.toString().orEmpty()
        prefs.openaiKey = openaiKey.text?.toString().orEmpty()
        prefs.anthropicKey = anthropicKey.text?.toString().orEmpty()
        prefs.cleanupEnabled = cleanupSwitch.isChecked
        prefs.vocabulary = vocabulary.text?.toString().orEmpty()
    }

    private fun updateProviderFields() {
        val openai = providerGroup.checkedRadioButtonId == R.id.providerOpenai
        findViewById<View>(R.id.groqKeyLayout).visibility = if (openai) View.GONE else View.VISIBLE
        findViewById<View>(R.id.openaiKeyLayout).visibility = if (openai) View.VISIBLE else View.GONE
    }

    private fun updateSetupStatus() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        btnMic.text = if (mic) "✓ Microphone allowed" else "Allow microphone"
        btnMic.isEnabled = !mic

        val notify = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        btnNotifications.text = if (notify) "✓ Notifications allowed" else "Allow notifications"
        btnNotifications.isEnabled = !notify

        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val accessibilityOn = enabledServices.contains("$packageName/")
        btnAccessibility.text = if (accessibilityOn) {
            "✓ Spoke is on in Accessibility"
        } else {
            "Turn on Spoke in Accessibility"
        }
        findViewById<View>(R.id.restrictedHint).visibility = if (accessibilityOn) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnAppInfo).visibility = if (accessibilityOn) View.GONE else View.VISIBLE
    }

    companion object {
        private const val REQ_MIC = 1
        private const val REQ_NOTIFY = 2
    }
}
