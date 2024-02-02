package ch.freshbits.pathshare.example

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ch.freshbits.pathshare.sdk.Pathshare
import ch.freshbits.pathshare.sdk.helper.*
import ch.freshbits.pathshare.sdk.model.Destination
import ch.freshbits.pathshare.sdk.model.Session
import ch.freshbits.pathshare.sdk.model.UserType
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG_PERMISSIONS_REQUEST_LOCATION_ACCESS = 1
        private const val SESSION_PREFERENCES = "session"
        private const val SESSION_ID_KEY = "session_id"
    }

    private lateinit var createButton: Button
    private lateinit var joinButton: Button
    private lateinit var inviteButton: Button
    private lateinit var leaveButton: Button

    private lateinit var session: Session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeCreateButton()
        initializeJoinButton()
        initializeInviteButton()
        initializeLeaveButton()

        findSession()
    }

    private fun initializeCreateButton() {
        createButton = findViewById<View>(R.id.create_session) as Button
        createButton.isEnabled = true
        createButton.setOnClickListener {
            Pathshare.client().saveUser(
                    "SDK User Android",
                    "me@email.com",
                    "+12345678901",
                    UserType.TECHNICIAN,
                    resources.getDrawable(R.drawable.face, null),
                    object: ResponseListener {
                override fun onSuccess() {
                    Log.d("User", "Success")
                    createSession()
                }

                override fun onError(throwable: Throwable) {
                    Log.e("User", "Error: ${throwable.localizedMessage}")
                }
            })
        }
    }

    private fun initializeJoinButton() {
        joinButton = findViewById<View>(R.id.join_session) as Button
        joinButton.isEnabled = false
        joinButton.setOnClickListener { joinSession() }
    }

    private fun initializeInviteButton() {
        inviteButton = findViewById<View>(R.id.invite_customer) as Button
        inviteButton.isEnabled = false
        inviteButton.setOnClickListener { inviteCustomer() }
    }

    private fun initializeLeaveButton() {
        leaveButton = findViewById<View>(R.id.leave_session) as Button
        leaveButton.isEnabled = false
        leaveButton.setOnClickListener { leaveSession() }
    }

    private fun findSession() {
        val identifier = preferences().getString(SESSION_ID_KEY, null) ?: return

        Pathshare.client().findSession(identifier, object: SessionResponseListener {
            override fun onSuccess(session: Session?) = if (session == null || session.isExpired) {
                deleteSessionIdentifier()

                createButton.isEnabled = true
                joinButton.isEnabled = false
                inviteButton.isEnabled = false
                leaveButton.isEnabled = false
            } else {
                Log.d("Session", "Name: ${session.name}")
                session.sessionExpirationListener = SessionExpirationListener { handleSessionExpiration() }

                createButton.isEnabled = false
                joinButton.isEnabled = true
                inviteButton.isEnabled = false
                leaveButton.isEnabled = true
            }

            override fun onError(throwable: Throwable) {
                showToast("Something went wrong")
            }
        })
    }

    private fun createSession() {
        val destination = Destination.Builder()
                .setIdentifier("w9823")
                .setLatitude(37.7875694)
                .setLongitude(-122.4112239)
                .build()

        val expirationDate = Date(System.currentTimeMillis() + 3600000)

        session = Session.Builder()
                .setDestination(destination)
                .setExpirationDate(expirationDate)
                .setName("simple session")
                .setSessionExpirationListener { handleSessionExpiration() }
                .build()

        session.save(object: ResponseListener {
            override fun onSuccess() {
                Log.d("Session", "Success")
                saveSessionIdentifier()

                createButton.isEnabled = false
                joinButton.isEnabled = true
                leaveButton.isEnabled = true
            }

            override fun onError(throwable: Throwable) {
                Log.e("Session", "Error: ${throwable.localizedMessage}")
            }
        })
    }

    private fun joinSession() {
        if (session.isExpired) return

        if (hasLocationPermission() && hasAlarmPermission()) {
            performJoinSession()
        } else {
            if (!hasLocationPermission()) { requestLocationPermission() }
            if (!hasAlarmPermission()) { requestAlarmPermission() }
        }
    }

    private fun inviteCustomer() {
        if (session.isExpired) return

        session.inviteUser(
                "Customer",
                UserType.MOTORIST,
                "customer@me.com",
                "+12345678901",
                true,
                object: InvitationResponseListener {
            override fun onSuccess(url: URL?) {
                Log.d("Invite", "Success")
                Log.d("URL", url.toString())
                inviteButton.isEnabled = false
                leaveButton.isEnabled = true
            }

            override fun onError(throwable: Throwable) {
                Log.e("Invite", "Error: ${throwable.localizedMessage}")
            }
        })
    }

    private fun leaveSession() {
        if (session.isExpired) return

        session.leave(object: ResponseListener {
            override fun onSuccess() {
                Log.d("Leave", "Success")
                createButton.isEnabled = true
                joinButton.isEnabled = false
                inviteButton.isEnabled = false
                leaveButton.isEnabled = false

                deleteSessionIdentifier()
            }

            override fun onError(throwable: Throwable) {
                Log.e("Leave", "Error: ${throwable.localizedMessage}")
            }
        })
    }

    private fun requestLocationPermission() {
        PermissionRequester.requestPermission(this, TAG_PERMISSIONS_REQUEST_LOCATION_ACCESS, android.Manifest.permission.ACCESS_FINE_LOCATION, R.string.permission_access_fine_location_rationale)
    }

    private fun performJoinSession() {
        if (session.isUserJoined) return

        session.join(object: ResponseListener {
            override fun onSuccess() {
                Log.d("Join", "Success")
                createButton.isEnabled = false
                joinButton.isEnabled = false
                inviteButton.isEnabled = true
                leaveButton.isEnabled = true
            }

            override fun onError(throwable: Throwable) {
                Log.e("Join", "Error: ${throwable.localizedMessage}")
            }
        })
    }

    private fun hasLocationPermission(): Boolean {
        return PermissionRequester.hasPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun requestAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        startActivity(intent)
    }

    private fun hasAlarmPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || getAlarmManager().canScheduleExactAlarms()
    }

    private fun getAlarmManager(): AlarmManager {
        return getContext().getSystemService(ALARM_SERVICE) as AlarmManager
    }

    private fun getContext(): Context {
        return Pathshare.client().context
    }

    private fun saveSessionIdentifier() {
        val editor = preferences().edit()
        editor.putString(SESSION_ID_KEY, session.identifier)
        editor.apply()
    }

    private fun handleSessionExpiration() {
        this.runOnUiThread {
            inviteButton.isEnabled = false
            leaveButton.isEnabled = false
            createButton.isEnabled = true
            deleteSessionIdentifier()
            showToast("Session expired")
        }
    }

    private fun showToast(message: String) {
        val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT)
        toast.show()
    }

    private fun deleteSessionIdentifier() {
        preferences().edit().clear().apply()
    }

    private fun preferences(): SharedPreferences {
        return applicationContext.getSharedPreferences(SESSION_PREFERENCES, MODE_PRIVATE)
    }
}
