╔══════════════════════════════════════════════════════════════╗
║           GEMMA CHAT — BUILD & SETUP GUIDE                   ║
║   Gemma 4 E4B  •  MTP Drafter  •  LiteRT Models Browser     ║
╚══════════════════════════════════════════════════════════════╝

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  WHAT'S IN THIS PROJECT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  GemmaEngine.kt     — MediaPipe LiteRT-LM inference + MTP Drafter
  ChatViewModel.kt   — App state management
  MainActivity.kt    — Full UI: Chat tab + Models browser tab
  ModelCatalogue.kt  — All LiteRT-compatible models with download links

  FEATURES:
  ✅ Gemma 4 E4B running fully on-device via LiteRT-LM
  ✅ MTP Drafter (speculative decoding) — up to 3x faster tokens/sec
     • 4-layer drafter predicts 5 tokens ahead
     • Main model verifies all 5 in ONE forward pass
     • Zero quality degradation guaranteed
  ✅ Live token streaming with blinking cursor
  ✅ Models browser tab — all LiteRT models with Kaggle/HF links
  ✅ Category filters: Gemma 4, MTP Drafters, Gemma 3n, Community
  ✅ One-tap model loading directly from the Models tab
  ✅ New Chat button clears session + KV cache

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  STEP 1 — DOWNLOAD MODELS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  MAIN MODEL (required):
  → https://kaggle.com/models/google/gemma
  → Look for: gemma-4-e4b-it-gpu-int4.task
  → Rename to: gemma4.task
  → Place at:  /sdcard/Download/gemma4.task
  → Size: ~3.5 GB (use WiFi)

  MTP DRAFTER (optional but STRONGLY recommended for 3x speed):
  → https://huggingface.co/google/gemma-4-E4B-it-assistant
  → Download the .task or .litertlm file
  → Rename to: gemma4-drafter.task
  → Place at:  /sdcard/Download/gemma4-drafter.task
  → Size: ~180 MB only!

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  STEP 2A — BUILD WITH TERMUX (RECOMMENDED)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Install Termux from F-Droid (NOT Play Store):
  → https://f-droid.org/packages/com.termux/

  Then run in Termux:

    # 1. Extract project
    cd /sdcard/Download
    unzip GemmaChat.zip -d GemmaChat
    cd GemmaChat

    # 2. Run the setup script (installs Java + gradle wrapper)
    chmod +x setup.sh
    ./setup.sh

    # 3. Build APK
    chmod +x gradlew
    ./gradlew assembleDebug

    # 4. Install
    # APK is at: app/build/outputs/apk/debug/app-debug.apk
    # Open it in your file manager to install

  NOTE: First build downloads ~200 MB dependencies — use WiFi.
  Subsequent builds are much faster.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  STEP 2B — BUILD WITH CODE ASSIST / AIDE APP
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Apps that work: AIDE, Codroid, Android IDE

  1. Install ZArchiver from Play Store
  2. Extract GemmaChat.zip → /sdcard/Download/GemmaChat/
  3. Open your IDE app (AIDE / Code Assist)
  4. Tap: File → Open Project
  5. Browse to /sdcard/Download/GemmaChat/
  6. AIDE auto-detects it as Gradle project
  7. IMPORTANT: Enable "Use Gradle Build" in AIDE settings
  8. First launch: let it download Gradle (may take 5-10 min on WiFi)
  9. Tap ▶ Run / Build APK

  If AIDE shows Kotlin errors:
  → Settings → Kotlin → Enable Kotlin support → Restart AIDE

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  STEP 2C — BUILD VIA GITHUB ACTIONS (CLOUD)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Easiest if Termux/AIDE doesn't work:

  1. Create free GitHub account
  2. New repo → upload this project folder
  3. Create: .github/workflows/build.yml with:

    name: Build APK
    on: [push]
    jobs:
      build:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v3
          - uses: actions/setup-java@v3
            with:
              java-version: '17'
              distribution: 'temurin'
          - run: chmod +x gradlew && ./gradlew assembleDebug
          - uses: actions/upload-artifact@v3
            with:
              name: GemmaChat-debug
              path: app/build/outputs/apk/debug/app-debug.apk

  4. Push code → GitHub builds automatically
  5. Go to Actions tab → Download APK from Artifacts

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  USING THE APP
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  FIRST LAUNCH:
  1. App opens to "Load Model" screen
  2. Model path is pre-filled: /sdcard/Download/gemma4.task
  3. Toggle "MTP Drafter" ON (recommended)
  4. Drafter path: /sdcard/Download/gemma4-drafter.task
  5. Tap "Load Model + MTP Drafter"
  6. Wait 10-30 seconds for model to load
  7. Start chatting!

  MODELS TAB:
  • Browse all LiteRT-compatible models
  • Filter by category: Gemma 4, MTP Drafters, Gemma 3n, Community
  • Tap "Kaggle" or "HF" to open download page in browser
  • Tap "Use" to load any model directly
  • MTP Drafter cards show the speedup and which model they pair with

  MTP DRAFTER INDICATOR:
  • When MTP Drafter is active: "MTP ⚡3x" badge shows in top bar
  • Status line shows: "MTP Drafter ON • up to 3x faster"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  DEVICE REQUIREMENTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Android 8.0+ (API 26+)
  6 GB RAM minimum (8 GB recommended for Gemma 4 E4B)
  ~4 GB storage for model files
  GPU with Vulkan support recommended (falls back to CPU)
  For MTP Drafter: +180 MB RAM overhead (worth it for 3x speed)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  TROUBLESHOOTING
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  "Model not found":
  → Check: ls /sdcard/Download/gemma4.task
  → File must be exactly at that path

  "Out of memory":
  → Close all background apps before launching
  → largeHeap=true is already enabled in Manifest

  Build fails "SDK not found":
  → In Termux: export ANDROID_HOME=$HOME/android-sdk
  → Or use GitHub Actions method — no SDK setup needed

  Gradle slow on first run:
  → Normal — downloading ~200 MB deps, use WiFi, leave it running
