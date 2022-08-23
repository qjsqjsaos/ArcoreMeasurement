package com.shibuiwilliam.arcoremeasurement

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button

class ArcoreMeasurement : AppCompatActivity() {
    private val TAG = "ArcoreMeasurement"
    private val buttonArrayList = ArrayList<String>()
    private lateinit var toMeasurement: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arcore_measurement)

        val buttonArray = resources
            .getStringArray(R.array.arcore_measurement_buttons)

        buttonArray.map{it->
            buttonArrayList.add(it)
        }
        toMeasurement = findViewById(R.id.to_measurement)
        toMeasurement.text = buttonArrayList[0]
        toMeasurement.setOnClickListener {
            val intent = Intent(application, Measurement::class.java)
            startActivity(intent)
        }
    }
}
