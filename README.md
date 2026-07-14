# Supernote-Nomad-Chauvet-Custom-Apps

## Chauvet OS base (main branch)

This repository now tracks the Chauvet OS foundation work from:

- Supernote firmware package: https://download-firmware.supernote.com/694655/update.zip
- Reference reverse-engineering/dev base: https://github.com/dwongdev/sugoi-supernote.git

The main branch is intended to host shared Supernote Nomad platform notes, extraction findings, and integration points used by all custom apps.

## App development branches and APK release targets

Use one dedicated branch per app track:

1. `feature/supernote-ganttproject`
   - Upstream base: https://github.com/bardsoftware/ganttproject.git
   - Goal: adapt and package a Supernote Nomad compatible APK release.

2. `feature/supernote-einkbro`
   - Upstream base: https://github.com/plateaukao/einkbro.git
   - Goal: adapt and package a Supernote Nomad compatible APK release.

3. `feature/supernote-icloudfiles`
   - Upstream bases:
     - https://github.com/Chieko-Seren/iCloud-Android.git
     - https://github.com/asahiqin/icloud_for_android.git
   - Goal: build an iCloud Files-style file manager app and release a Supernote Nomad compatible APK.

4. `feature/supernote-casio-cfx9960gt`
   - Upstream base: https://github.com/gfso2000/casio991.git
   - Goal: build a scientific calculator app emulating a Casio CFX-9960GT and release a Supernote Nomad compatible APK.

## Release output convention

For each branch, publish signed APK artifacts through the branch CI/release process and keep release notes aligned with Supernote Nomad compatibility constraints.
