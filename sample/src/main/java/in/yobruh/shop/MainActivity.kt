package `in`.yobruh.shop

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.samvaads.widget.Samvaad

class MainActivity : AppCompatActivity() {

    private lateinit var badge: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        badge = findViewById(R.id.badge)
        status = findViewById(R.id.status)

        findViewById<Button>(R.id.help).setOnClickListener {
            Samvaad.present(this)
        }
        findViewById<Button>(R.id.ask_order).setOnClickListener {
            Samvaad.present(this, message = "Where is my order YB-1042?")
        }
        findViewById<Button>(R.id.sign_in).setOnClickListener {
            // In a real app your backend returns userHash after the customer signs in:
            // HMAC-SHA256(identity secret, lower-cased email). Never ship the secret in the app.
            Samvaad.identify(
                email = "asha@yobruh.in",
                name = "Asha Verma",
                userHash = "user-hash-from-your-backend",
            )
            status.text = getString(R.string.signed_in_as, "asha@yobruh.in")
        }
        findViewById<Button>(R.id.sign_out).setOnClickListener {
            Samvaad.logout()
            status.setText(R.string.signed_out)
        }

        // A tap on a Samvaad notification while the app was in the background opens this
        // activity with the push data as extras.
        //
        // intent.extras?.let { extras ->
        //     val data = extras.keySet().associateWith { extras.getString(it).orEmpty() }
        //     Samvaad.handlePush(this, data)
        // }
    }

    override fun onStart() {
        super.onStart()
        Samvaad.onUnreadChange { count -> showBadge(count) }
    }

    override fun onStop() {
        Samvaad.onUnreadChange(null)
        super.onStop()
    }

    private fun showBadge(count: Int) {
        badge.visibility = if (count > 0) View.VISIBLE else View.GONE
        badge.text = if (count > 9) "9+" else count.toString()
    }
}
