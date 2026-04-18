package com.tvappbrowser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.tvappbrowser.repository.SettingsRepository

class PinActivity : AppCompatActivity() {

    private lateinit var inputPin: EditText
    private lateinit var textError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        inputPin = findViewById(R.id.inputPin)
        textError = findViewById(R.id.textError)

        val repository = SettingsRepository(this)
        val correctPin = repository.getSettings().pinCode

        inputPin.addTextChangedListener {
            val typed = it.toString()
            if (typed.length >= correctPin.length) {
                if (typed == correctPin) {
                    // Success, open settings
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                } else {
                    textError.visibility = View.VISIBLE
                    textError.text = "Incorrect PIN"
                    inputPin.setText("")
                }
            }
        }
    }
}
