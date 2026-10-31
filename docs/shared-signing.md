# Shared signing key

Android only installs an update over an existing app when both are signed with the same key.
Builds made on different machines are signed with each machine's own debug key, so they cannot
replace each other. To share builds, everyone signs with one shared keystore.

The keystore and its passwords never go into this repository (it is public). Pass them on
privately, e.g. through a password manager.

## Setup

1. Put the keystore somewhere outside the repository, e.g. `~/.config/nuvio-signing/nuvio-shared.jks`.
2. Add to `local.properties` (gitignored):

   ```properties
   NUVIO_RELEASE_STORE_FILE=/home/<you>/.config/nuvio-signing/nuvio-shared.jks
   NUVIO_RELEASE_STORE_PASSWORD=<store password>
   NUVIO_RELEASE_KEY_ALIAS=nuvio
   NUVIO_RELEASE_KEY_PASSWORD=<key password>
   ```

3. Build as usual. Release and debug builds are now signed with that key.
   `NUVIO_SIGN_DEBUG_WITH_RELEASE_KEY=false` keeps debug builds on the machine's debug key.

Check the signer of an APK with
`apksigner verify --print-certs app.apk`; the shared key's SHA-256 is
`324d485c07dd86019fb438b0903c6e6849f2d4b651c56dc346e95fbbbb5f6b36`.

## Switching an existing install

An app installed with a different key has to be uninstalled once before the first build with
the shared key can be installed. Data stored only on the device is lost then; account data
(sign-in, synced progress and settings) comes back after signing in.

For GitHub Actions, store the keystore as the `NUVIO_RELEASE_KEYSTORE_BASE64` secret
(`base64 -w0 nuvio-shared.jks`) together with the three password/alias secrets the release
workflow already reads.

## Automatic APK releases

`.github/workflows/fork-apk-release.yml` builds every push to `feature/tv-remote`, checks it is
signed with the shared key and publishes it as the latest release, so the newest build is always
at `https://github.com/MarlonH1/NuvioMobile/releases/latest/download/Nuvio.apk`.
`scripts/publish-apk-release.sh` does the same from a local machine.

It needs five repository secrets (only a repository admin can add them). With one file per
secret in a folder (the secret's name as file name), they can be set with:

```sh
for f in NUVIO_LOCAL_PROPERTIES_BASE64 NUVIO_SHARED_KEYSTORE_BASE64 \
         NUVIO_SHARED_STORE_PASSWORD NUVIO_SHARED_KEY_ALIAS NUVIO_SHARED_KEY_PASSWORD; do
  gh secret set "$f" -R MarlonH1/NuvioMobile < "$f"
done
```

Actions must also be enabled for the fork (repository settings > Actions). Until the secrets
exist, the workflow skips the build with a notice.
