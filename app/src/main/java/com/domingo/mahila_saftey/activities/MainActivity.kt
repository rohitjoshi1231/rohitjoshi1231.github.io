package com.domingo.mahila_saftey.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.domingo.mahila_saftey.R
import com.domingo.mahila_saftey.SharedViewModel
import com.domingo.mahila_saftey.databinding.ActivityMainBinding
import com.domingo.mahila_saftey.fragments.ContactFragment
import com.domingo.mahila_saftey.fragments.HomeFragment
import com.domingo.mahila_saftey.fragments.SettingsFragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MahilaSafety"
        const val PERMISSION_REQUEST_SMS = 101
        const val REQUEST_LOCATION_PERMISSION = 1
    }

    private var isWhatsappIntentActive = false
    private var whatsappIntent: Intent? = null
    private lateinit var gestureDetector: GestureDetector
    private var job: Job? = null
    private var message = "Empty"
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private val sharedViewModel: SharedViewModel by lazy {
        ViewModelProvider(this@MainActivity)[SharedViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        window.statusBarColor = customColor(this, R.color.app_background)
        toolbar = binding.toolbar
        loadFragment(HomeFragment(), "Home")

        binding.bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.home -> loadFragment(HomeFragment(), "Home")
                R.id.contact -> loadFragment(ContactFragment(), "Contact")
                R.id.settings -> loadFragment(SettingsFragment(), "Settings")
            }
            true
        }


        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                val builder: AlertDialog.Builder =
                    AlertDialog.Builder(this@MainActivity, R.style.CustomAlertDialogTheme)
                builder.setTitle("Ready to send your location through WhatsApp? \uD83D\uDCCD")

                builder.setPositiveButton("Sure") { _, _ ->
                    getLocation()
                }
                builder.setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }

                builder.show()
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
            ): Boolean {

                if (abs(distanceY) > abs(distanceX)) {
                    if (distanceY > 0) {
                        val builder: AlertDialog.Builder =
                            AlertDialog.Builder(this@MainActivity, R.style.CustomAlertDialogTheme)
                        builder.setTitle("Ready to make a call? ☎\uFE0F")

                        builder.setPositiveButton("Sure") { _, _ ->
                            makeCall(sharedViewModel.sharedNumber)
                        }
                        builder.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }

                        builder.show()

                    } else {
                        val builder: AlertDialog.Builder =
                            AlertDialog.Builder(this@MainActivity, R.style.CustomAlertDialogTheme)
                        builder.setTitle("Send location via SMS? \uD83D\uDCCD")

                        builder.setPositiveButton("Sure") { _, _ ->
                            send()
                        }
                        builder.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }

                        builder.show()
                    }
                }
                return super.onScroll(e1, e2, distanceX, distanceY)
            }
        })
    }


    fun customColor(context: Context, colorResourceId: Int): Int {
        return ContextCompat.getColor(context, colorResourceId)
    }

    private fun loadFragment(fragment: Fragment, title: String) {
        setUpToolbar(title)
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    private fun setUpToolbar(title: String) {
        setSupportActionBar(toolbar)
        supportActionBar?.title = title
    }


    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Pass touch events to gesture detector
        if (event != null) {
            gestureDetector.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    private fun send() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.SEND_SMS), PERMISSION_REQUEST_SMS
            )
        } else {
            try {
                // Check if the message is empty or null
                if (message.isNotEmpty()) {
                    try {
                        val smsManager = SmsManager.getDefault()
                        smsManager.sendTextMessage(
                            sharedViewModel.sharedNumber, null, message, null, null
                        )
                        Toast.makeText(this, "Message sent successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Error sending SMS: ${e.message}")
                    }
                } else {
                    Toast.makeText(this, "Message body is empty", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error sending SMS: ${e.message}")
            }
        }
    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION
            )
        } else {
            job = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    startLocationUpdates()
                    delay(5000)
                }
            }
        }
    }

    fun makeCall(number: String) {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CALL_PHONE), 101
            )
        } else {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
            }
            startActivity(intent)
        }
    }

    private fun sendWhatsapp(numbers: String, message: String) {
        val uri = Uri.parse(
            "https://api.whatsapp.com/send?phone=$numbers&text=${
                URLEncoder.encode(
                    message, "UTF-8"
                )
            }"
        )
        whatsappIntent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(whatsappIntent)
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val sharedPreferences = getSharedPreferences("sharedContact", Context.MODE_PRIVATE)


        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            // Handle the retrieved location
            if (location != null && !isWhatsappIntentActive) { // Ensure WhatsApp intent is not active
                isWhatsappIntentActive = true
                val latitude = location.latitude
                val longitude = location.longitude
                val url = "https://www.google.com/maps?q=$latitude,$longitude"
                val time = getCurrentTime()
                val message = "$url\n$time"
                val contactNumber = sharedPreferences.getString("sharedNumber", "")
                if (contactNumber != null) {
                    sendWhatsapp(contactNumber.toString(), message)
                }
            }

        }.addOnFailureListener { exception: Exception ->
            Log.e("Error", exception.message.toString())
        }
    }

    private fun getCurrentTime(): String {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
        val day = c.get(Calendar.DAY_OF_MONTH)
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        val second = c.get(Calendar.SECOND)
        val amOrPm = c.get(Calendar.AM_PM)
        val period = if (amOrPm == Calendar.AM) "AM" else "PM"

        return "$day/$month/$year - $hour:$minute:$second $period"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isWhatsappIntentActive) {
            // WhatsApp intent is active, cancel it
            isWhatsappIntentActive = false
            job?.cancel()
            Toast.makeText(this, "WhatsApp message sending cancelled", Toast.LENGTH_SHORT).show()
        } else {
            // WhatsApp intent is not active, proceed with default back button behavior
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        // Cancel any ongoing job (if applicable)
        job?.cancel()

        // Reset WhatsApp intent state
        isWhatsappIntentActive = false
        whatsappIntent = null

        super.onDestroy()
    }

    override fun onPause() {
        // Reset WhatsApp intent state
        isWhatsappIntentActive = false
        job?.cancel()
        super.onPause()
    }

}


