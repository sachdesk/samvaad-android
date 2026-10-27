# Samvaad for Android

Samvaad chat for Android apps. The SDK opens your Samvaad website chat in a full-screen
screen inside your app, signs your customer in, shows an unread badge and handles push.

Version 0.1.0. Android 7.0 (API 24) and up.

## Install

The SDK is on Maven Central. Make sure `mavenCentral()` is in your repositories (new
Android Studio projects already have it), then add the dependency:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.samvaads:widget:0.1.0")
}
```

## Configure

Call once, in `Application.onCreate`:

```kotlin
class ShopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Samvaad.configure(
            this,
            tenantId = "6f1c2b8e-4a3d-4e2f-9b7a-1c5d8e0f2a34", // your tenant id
            host = "yobruh.samvaads.com",
        )
    }
}
```

`host` is required: the host serving your chat widget, as `yobruh.samvaads.com` or
`https://yobruh.samvaads.com`. It must be https and must not include a path, query or
credentials.

## Identify the customer

When the customer is signed in to your app:

```kotlin
Samvaad.identify(email = "asha@yobruh.in", name = "Asha Verma", userHash = hashFromYourBackend)
```

`userHash` is HMAC-SHA256 of the lower-cased email, keyed with your identity secret, as hex.

**Compute `userHash` on your backend. Never put the identity secret in the app.**

Node example for your backend:

```js
const crypto = require('crypto')

function samvaadUserHash(email) {
  return crypto
    .createHmac('sha256', process.env.SAMVAAD_IDENTITY_SECRET)
    .update(email.trim().toLowerCase())
    .digest('hex')
}
```

Return it to the app with your sign-in response, then call `identify`.

## Open the chat

```kotlin
Samvaad.present(activity)
Samvaad.present(activity, message = "Where is my order YB-1042?") // prefilled, not sent
```

Back goes back inside the chat, then closes it.

## Unread badge

```kotlin
Samvaad.onUnreadChange { count -> badge.isVisible = count > 0; badge.text = "$count" }
val count = Samvaad.unreadCount
```

The listener runs on the main thread and is called once right away with the current count.
Pass `null` to stop listening (for example in `onStop`).

## Push

1. Add Firebase Cloud Messaging to your app as usual.
2. In Samvaad, go to **Admin → Website widget → Mobile apps** and paste your Firebase
   service account key (the JSON file from Firebase console → Project settings → Service
   accounts → Generate new private key). Samvaad sends pushes with your own Firebase project.
   For iOS apps without Firebase, enter the APNs key (.p8), key ID, team ID and bundle ID there.
3. Give the SDK the FCM token:

```kotlin
FirebaseMessaging.getInstance().token.addOnSuccessListener { Samvaad.setPushToken(it) }

class ShopMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Samvaad.setPushToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        if (Samvaad.handlePush(this, message.data)) return
        // not a Samvaad push: handle it yourself
    }
}
```

The chat registers the token for the customer's conversation. On Android 13+ ask for the
`POST_NOTIFICATIONS` permission.

## Handle a push tap

Samvaad pushes carry a notification plus data `samvaad = "1"`, `tenant_id`, `session_id`.
`handlePush` returns `false` for anything that isn't a Samvaad push, otherwise it opens the
chat and returns `true`. When the app was in the background, a tap opens your launcher
activity with the data as extras:

```kotlin
intent.extras?.let { extras ->
    val data = extras.keySet().associateWith { extras.getString(it).orEmpty() }
    Samvaad.handlePush(this, data)
}
```

## Log out

```kotlin
Samvaad.logout()
```

The chat forgets the customer (now if it is open, otherwise the next time it opens). The
device push token is kept for the next chat.

## Theme

```kotlin
Samvaad.theme = SamvaadTheme.AUTO // default: follows the app's light / dark mode
Samvaad.theme = SamvaadTheme.DARK
```

## File uploads

Customers can attach files from the system file picker. No permissions are needed.

## Local testing

In a debuggable build you can point the SDK at a local Samvaad UI (`http://localhost`, `127.0.0.1` or `10.0.2.2` only):

```kotlin
Samvaad.configure(this, tenantId, host = "http://10.0.2.2:5173") // emulator
Samvaad.configure(this, tenantId, host = "http://localhost:5173") // device + adb reverse
```

Plain http also needs a debug-only network security config in your app that allows cleartext
for `10.0.2.2` / `localhost` / `127.0.0.1`. Release builds accept https only.

## Sample app

`sample/` is a small Yobruh Shop app with a Help button, an unread badge, sign in and sign out.
Push wiring is included as commented code, so it builds without Firebase.

```
./gradlew :sample:installDebug
```

## Build

```
./gradlew assembleDebug testDebugUnitTest lintDebug
```

## License

Released under the MIT License. Copyright (c) 2026 Sachdesk. See [LICENSE](LICENSE).
