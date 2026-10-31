package com.harpreet.homeview

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private val fields = LinkedHashMap<String, EditText>()
    private lateinit var col: LinearLayout
    private lateinit var kiosk: CheckBox
    private lateinit var boot: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = Prefs.sp(this)

        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(24), dp(12), dp(24), dp(48))

        note("Tip: long-press any camera (or the weather box) to come back here.")

        val topBtnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        val topSave = Button(this).apply {
            text = "Save"
            setAllCaps(false)
            setTextColor(Color.parseColor("#FAC775"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { save() }
        }
        val topCancel = Button(this).apply {
            text = "Cancel"
            setAllCaps(false)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { finish() }
        }
        topBtnLayout.addView(topSave)
        topBtnLayout.addView(topCancel)
        col.addView(topBtnLayout)

        for (i in 0 until 4) {
            header("Camera ${i + 1}")
            field("cam${i}_name", "Name", "Camera ${i + 1}")
            val defaultHost = when (i) {
                0 -> "10.0.0.173"
                1 -> "10.0.0.209"
                else -> ""
            }
            field("cam${i}_host", "IP address (e.g. 192.168.1.50) — or paste a full rtsp:// link", defaultHost)
            field("cam${i}_port", "RTSP port", "554", InputType.TYPE_CLASS_NUMBER)
            field("cam${i}_user", "Username", "admin")
            field("cam${i}_pass", "Password", "",
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            field("cam${i}_main", "HD stream path (full screen)", "/11")
            field("cam${i}_sub", "Low-res stream path (grid)", "/12")
        }

        header("Google Calendar")
        note("Google Calendar → Settings → your calendar → \"Secret address in iCal format\". One link per line.")
        field("cal_urls", "Secret iCal links", "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI, lines = 3)

        header("Weather location")
        note("Brampton, ON by default.")
        field("lat", "Latitude", Prefs.DEFAULT_LAT,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        field("lon", "Longitude", Prefs.DEFAULT_LON,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)

        header("Tablet")
        kiosk = CheckBox(this)
        kiosk.text = "Use as home screen (opens on power-up and when Home is pressed)"
        kiosk.isChecked = sp.getBoolean("kiosk", false)
        col.addView(kiosk)

        boot = CheckBox(this)
        boot.text = "Also try to open after restart (needs \"Display over other apps\")"
        boot.isChecked = sp.getBoolean("boot", false)
        col.addView(boot)

        button("Allow \"Display over other apps\"") {
            if (Build.VERSION.SDK_INT >= 23) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        button("Choose default home app") { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        button("Open Android settings") { startActivity(Intent(Settings.ACTION_SETTINGS)) }

        header("")
        button("Save", primary = true) { save() }
        button("Cancel") { finish() }

        val scroll = ScrollView(this)
        scroll.addView(col)
        setContentView(scroll)
    }

    private fun save() {
        val e = Prefs.sp(this).edit()
        for ((k, v) in fields) e.putString(k, v.text.toString().trim())
        val kioskOn = kiosk.isChecked
        e.putBoolean("kiosk", kioskOn)
        e.putBoolean("boot", boot.isChecked)
        e.putBoolean("configured", true)
        e.apply()

        val wasKiosk = isHomeAliasEnabled(this)
        setHomeAlias(this, kioskOn)
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        if (kioskOn && !wasKiosk) {
            // let the user pick Home View as the default home app
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
        finish()
    }

    // ---------- small UI helpers ----------

    private fun header(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setTextColor(Color.parseColor("#FAC775"))
        tv.setPadding(0, dp(24), 0, dp(4))
        col.addView(tv)
    }

    private fun note(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        tv.alpha = 0.75f
        tv.setPadding(0, dp(4), 0, dp(4))
        col.addView(tv)
    }

    private fun field(
        key: String, label: String, def: String,
        type: Int = InputType.TYPE_CLASS_TEXT, lines: Int = 1
    ) {
        val lbl = TextView(this)
        lbl.text = label
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        lbl.alpha = 0.7f
        lbl.setPadding(0, dp(8), 0, 0)
        col.addView(lbl)

        val et = EditText(this)
        et.inputType = type
        val saved = Prefs.sp(this).getString(key, null)
        val initial = when (key) {
            "cam0_host" -> if (saved == null || saved.isBlank() || saved == "10.0.0.73" || saved == "localhost" || saved == "127.0.0.1") "10.0.0.173" else saved
            "cam1_host" -> if (saved == null || saved.isBlank() || saved == "localhost" || saved == "127.0.0.1") "10.0.0.209" else saved
            "cam0_main", "cam1_main" -> if (saved == null || saved.isBlank() || saved == "/11") "/live/ch0" else saved
            "cam0_sub", "cam1_sub" -> if (saved == null || saved.isBlank() || saved == "/12") "/live/ch1" else saved
            else -> saved ?: def
        }
        et.setText(initial)
        if (lines > 1) {
            et.minLines = lines
            et.setSingleLine(false)
        } else {
            et.setSingleLine(true)
        }
        col.addView(et)
        fields[key] = et
    }

    private fun button(text: String, primary: Boolean = false, onClick: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.setAllCaps(false)
        if (primary) b.setTextColor(Color.parseColor("#FAC775"))
        b.setOnClickListener { onClick() }
        col.addView(b)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private fun alias(c: Context) = ComponentName(c, "com.harpreet.homeview.HomeAlias")

        fun isHomeAliasEnabled(c: Context): Boolean =
            c.packageManager.getComponentEnabledSetting(alias(c)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        fun setHomeAlias(c: Context, on: Boolean) {
            c.packageManager.setComponentEnabledSetting(
                alias(c),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
