package `in`.yobruh.shop

import android.app.Application
import com.samvaads.widget.Samvaad

class ShopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Your tenant id (Admin -> Website widget) and the host serving your chat widget.
        // Local testing (debug builds only): host = "http://10.0.2.2:5173"
        Samvaad.configure(
            this,
            tenantId = "6f1c2b8e-4a3d-4e2f-9b7a-1c5d8e0f2a34",
            host = "yobruh.samvaads.com",
        )

        // Push: hand the current FCM token to Samvaad (refreshes arrive in
        // ShopMessagingService.onNewToken).
        //
        // FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
        //     Samvaad.setPushToken(token)
        // }
    }
}
