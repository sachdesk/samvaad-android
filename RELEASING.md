# Releasing

Releases go to Maven Central as `com.samvaads:widget:<version>` through the
[Central Portal](https://central.sonatype.com). Pushing a `v<version>` tag runs
`.github/workflows/release.yml`, which checks the tag, runs the tests, signs the
artifacts and publishes them. The release is published automatically once Central
validates it.

## One-time setup

1. **Central Portal user token.** Sign in at https://central.sonatype.com, open
   **Account** (top right) and click **Generate User Token**. Keep the shown
   username and password; they are only shown once.

2. **GPG signing key.**

   ```
   gpg --full-generate-key        # RSA and RSA, 4096 bits, set a passphrase
   gpg --list-secret-keys --keyid-format=long
   ```

   The key id is the part after `rsa4096/` on the `sec` line (16 hex characters).

3. **Publish the public key** so Central can verify signatures:

   ```
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

4. **Export the private key** (armored) for the `SIGNING_KEY` secret:

   ```
   gpg --armor --export-secret-keys <KEY_ID> > signing-key.asc
   ```

   Delete `signing-key.asc` once the secret is set.

5. **Add the repository secrets:**

   ```
   gh secret set MAVEN_CENTRAL_USERNAME -R sachdesk/samvaad-android   # token username
   gh secret set MAVEN_CENTRAL_PASSWORD -R sachdesk/samvaad-android   # token password
   gh secret set SIGNING_KEY -R sachdesk/samvaad-android < signing-key.asc
   gh secret set SIGNING_KEY_ID -R sachdesk/samvaad-android           # optional: last 8 hex chars of the key id
   gh secret set SIGNING_KEY_PASSWORD -R sachdesk/samvaad-android     # the GPG passphrase
   ```

## Each release

1. Set the version in `gradle.properties` (`VERSION_NAME`) and in
   `Samvaad.VERSION` (`samvaad/src/main/java/com/samvaads/widget/Samvaad.kt`),
   update the README, and merge to `main`.
2. Tag `main` and push the tag:

   ```
   git checkout main && git pull
   git tag v0.1.0 && git push origin v0.1.0
   ```

3. Watch the **Release** workflow under the repository's Actions tab. It fails
   early if the tag does not match `VERSION_NAME` or a secret is missing.
4. Check the deployment at https://central.sonatype.com/publishing/deployments.
   It moves through *Validated* and *Publishing* to *Published*. If it fails
   validation, the page lists why; fix it, bump the version and tag again
   (a published version can never be replaced).
5. Once published, the artifact shows at
   https://central.sonatype.com/artifact/com.samvaads/widget. It can take up to
   about 30 minutes more before Gradle can resolve it from `mavenCentral()`.

## Local check

`./gradlew :samvaad:publishToMavenLocal` builds the aar, sources jar, javadoc jar
and POM into `~/.m2/repository/com/samvaads/widget/` without any keys. Signing is
only switched on when a signing key is provided.
