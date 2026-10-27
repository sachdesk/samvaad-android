package `in`.yobruh.shop

import android.app.Application
import com.samvaads.widget.Samvaad

class ShopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Your tenant id and the host serving your chat widget.
        // Local testing (debug builds only): host = "http://10.0.2.2:5173"
        Samvaad.configure(this, tenantId = "yobruh", host = "yobruh.samvaads.com")

        // Push: hand the current FCM token to Samvaad (refreshes arrive in
        // ShopMessagingService.onNewToken).
        //
        // FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
        //     Samvaad.setPushToken(token)
        // }
    }
}
