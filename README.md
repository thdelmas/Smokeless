# Smokeless

Per-cigarette substance-use and craving tracker. Companion in the Bios ecosystem — owns the capture surface for tobacco/weed events and pushes computed events to Bios's metric bus.

For the broader picture: [docs/ECOSYSTEM.md](docs/ECOSYSTEM.md).

## Install

### Option 1: Direct download

Grab the latest signed APK from [Releases](https://github.com/thdelmas/Smokeless/releases/latest). Verify the SHA-256 against the published `.sha256` file, then install. You'll need "Install unknown apps" enabled for your browser or file manager.

### Option 2: Obtainium (recommended for updates)

[Obtainium](https://github.com/ImranR98/Obtainium) tracks GitHub Releases and auto-updates apps without any store. Install Obtainium, add this repo URL:

```
https://github.com/thdelmas/Smokeless
```

Future releases install automatically once you tap update.

### Signing identity

All apps in the Bios ecosystem (Bios, Fil, W2F, Smokeless, Virgil, SoulRadio) share one signing key. Cert SHA-256:

```
D4:18:F5:1B:E9:D0:28:5D:0B:A8:27:4B:0E:E9:67:8F:F9:DB:DC:1D:32:D5:97:3C:ED:F3:23:59:3F:55:46:33
```

Compare against `apksigner verify --print-certs Smokeless-vX.Y.Z.apk` before trusting an install.

## Release flow

Push a tag `vX.Y.Z` from `master` → GitHub Actions builds and signs the APK, publishes a Release with auto-generated notes. See [.github/workflows/release.yml](.github/workflows/release.yml).

## License

See [docs/](docs/) for design notes, ecosystem boundaries, and the broader roadmap.
