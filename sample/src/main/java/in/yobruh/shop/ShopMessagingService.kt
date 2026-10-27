package `in`.yobruh.shop

// Push wiring for Firebase Cloud Messaging. Commented out so the sample builds without
// Firebase. To turn it on: add the google-services plugin and firebase-messaging (see
// sample/build.gradle.kts), add your google-services.json, then uncomment this class and
// its <service> entry in AndroidManifest.xml.
//
// import com.google.firebase.messaging.FirebaseMessagingService
// import com.google.firebase.messaging.RemoteMessage
// import com.samvaads.widget.Samvaad
//
// class ShopMessagingService : FirebaseMessagingService() {
//
//     override fun onNewToken(token: String) {
//         Samvaad.setPushToken(token)
//     }
//
//     override fun onMessageReceived(message: RemoteMessage) {
//         // Samvaad pushes carry a notification (the system shows it while the app is in
//         // the background) plus data: samvaad = "1", tenant_id, session_id.
//         // With the app in the foreground this is called instead: open the chat, or show
//         // your own notification whose tap calls Samvaad.handlePush.
//         if (Samvaad.handlePush(this, message.data)) return
//
//         // Not a Samvaad push: handle your own messages here.
//     }
// }
