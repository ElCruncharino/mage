# Mage

<p align="center"><img alt="The age logo, an wireframe of St. Peters dome in Rome, with the text: age, file encryption" width="600" src="https://user-images.githubusercontent.com/1225294/132245842-fda4da6a-1cea-4738-a3da-2dc860861c98.png"></p>

Mage is an Android GUI for [age] file encryption, built on [kage] (a Kotlin/JVM
implementation of the age protocol). "Mage" is short for *Mobile age*. Mage is not
officially affiliated with or endorsed by the age project.

The minimum supported Android version is API 26.

## Download

| Get it |
| --- |
| [`.apk`](https://github.com/ElCruncharino/mage/releases/latest) &nbsp;·&nbsp; or **[Obtainium](https://github.com/ImranR98/Obtainium)** for auto-updates: add an app with the URL `https://github.com/ElCruncharino/mage` |

Releases are signed and built by CI directly from a tagged commit (see
`.github/workflows/release.yml`) — nothing is hand-uploaded.

## What it does

- Encrypt/decrypt files to age recipients (`age1...`) or passphrases, including armor,
  multi-recipient, and encrypt-to-self.
- SSH keys (`ssh-ed25519`, `ssh-rsa`) work as recipients and identities alongside native
  age keys, and can be mixed on one file.
- Identities are sealed in the Android Keystore (AES-256-GCM, StrongBox where available)
  behind biometric/device-credential auth. Recipients get a small address book with QR
  encode/scan for sharing a public key.
- Batch mode for encrypting/decrypting more than one file at a time into a folder.
- Encrypted export/import of your identity vault, so it's not stuck on one device.
- Hooks into the system: share-sheet targets, a `.age` file-manager association, launcher
  shortcuts, a Quick Settings tile.

## Status

Early — version 0.1.0, no signed releases yet. Built and tested against real kage on a
JVM harness plus device testing; not independently audited. Treat it accordingly.

## Building

kage is pulled in as a git submodule, tracking upstream `android-password-store/kage`
directly, and built as a composite build (see `settings.gradle`); edits to the library
apply straight to the app with no publish step. That's handy for testing kage changes
before they're upstreamed or before a numbered release ships them — swap it for a plain
`com.github.android-password-store:kage` version coordinate if you don't need that.

```sh
git clone --recursive https://github.com/ElCruncharino/mage.git
cd mage
./gradlew assembleDebug
```

If you already cloned without `--recursive`, run `git submodule update --init` first.

## Non-goals

- Its own crypto implementation — that's kage's job; Mage is the GUI that was missing.
- A general-purpose file manager or password manager.
- age-plugin support — the reference plugin mechanism shells out to binaries on `$PATH`,
  which doesn't work on Android.

## License

Licensed under either of

 * Apache License, Version 2.0, ([LICENSE-APACHE](LICENSE-APACHE) or
   http://www.apache.org/licenses/LICENSE-2.0)
 * MIT license ([LICENSE-MIT](LICENSE-MIT) or http://opensource.org/licenses/MIT)

at your option.

### Contribution

Unless you explicitly state otherwise, any contribution intentionally submitted for
inclusion in the work by you, as defined in the Apache-2.0 license, shall be dual
licensed as above, without any additional terms or conditions.

[age]: https://age-encryption.org/v1
[kage]: https://github.com/android-password-store/kage
