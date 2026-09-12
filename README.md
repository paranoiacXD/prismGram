# PrismGram

custom telegram client for android. written from scratch in kotlin with jetpack compose + material 3, runs on tdlib

not affiliated with telegram in any way this is a hobby project

## what works so far

- login, country picker with flags + search (guesses as you type)
- code + 2fa password screens
- session survives restarts, no need to log in every time
- account screen with avatar, name, username, phone and bio
- paste button for the login code, resend, open telegram shortcut

## building

you need the android sdk + jdk 17 or newer. no android studio required, gradlew does everything

1. clone the repo
2. create `local.properties` in the root folder:

```
sdk.dir=C:/Users/you/AppData/Local/Android/Sdk
TG_API_ID=123456
TG_API_HASH=yourhash
```

get your own id/hash at https://my.telegram.org (api development tools) it takes like a minute


3. build:

```
gradlew.bat assembleDebug
```

apk ends up in `app/build/outputs/apk/debug/`

4. install on a phone:

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## notes

- tdlib is prebuilt and bundled for **arm64-v8a only**, so it runs on modern 64-bit phones. its in `app/src/main/jniLibs/`, ~25mb, thats why the apk is chunky
- min sdk 26, target 35
- if you want to build tdlib yourself for other abis, good luck, its a whole thing

## roadmap (roughly)

- chat list
- actual chatting
- media
- notifications, eventually

## license

tdlib is boost software licensed, its stuff is under `app/src/main/java/org/drinkless/tdlib/`. everything else here - do whatever
