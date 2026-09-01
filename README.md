# 📁 Bluetooth File Explorer

סייר קבצים מרחוק דרך Bluetooth לאנדרואיד 13, עם תמיכה ב-Root.

---

## ⚡ Build מהיר

```bash
git clone https://github.com/BSDBSDBSDBSD/Bluetooth-.git
cd Bluetooth-
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🏗️ דרישות Build

| כלי | גרסה |
|-----|-------|
| Android Studio | Hedgehog 2023.1.1+ |
| JDK | 17 |
| Android Gradle Plugin | 8.1.0 |
| compileSdk | 33 (Android 13) |
| minSdk | 28 (Android 9) |

---

## 📦 התקנה ב-ADB (בלי Play Store)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🚀 שימוש

### מצב שרת (המכשיר שנגישים אליו):
1. פתח האפליקציה
2. אשר הרשאת **"ניהול כל הקבצים"** (Settings → Apps → Special Access)
3. אם יש Root - הפעל **מתג Root**
4. לחץ **"הפעל שרת"**
5. השאר האפליקציה פתוחה ברקע

### מצב לקוח (המכשיר שמדפדף):
1. ודא שהמכשירים **מזווגים ב-Bluetooth** (הגדרות → Bluetooth)
2. פתח האפליקציה → **"התחבר למכשיר אחר"**
3. בחר מהרשימה
4. סייר הקבצים ייפתח

---

## 🗂️ מבנה הפרויקט

```
app/src/main/java/com/bsd/bluetoothexplorer/
├── bluetooth/
│   ├── BluetoothServerService.kt   # Foreground service - מאזין לחיבורים
│   └── BluetoothClient.kt          # לקוח - מתחבר לשרת
├── root/
│   └── RootManager.kt              # גישה לקבצים: root ורגיל
├── model/
│   └── FileItem.kt                 # מודלים + פרוטוקול JSON
└── ui/
    ├── MainActivity.kt             # מסך ראשי
    ├── DeviceScanActivity.kt       # סריקת מכשירים
    ├── FileExplorerActivity.kt     # סייר הקבצים
    └── FileAdapter.kt              # RecyclerView adapter
```

---

## 📡 פרוטוקול תקשורת

Bluetooth Classic RFCOMM עם פרמט:
```
[4 bytes - גודל JSON][JSON bytes][binary data לקבצים]
```

פקודות נתמכות:
| פקודה | תיאור |
|-------|--------|
| `LIST_DIR` | רשימת קבצים בתיקייה |
| `GET_FILE` | הורדת קובץ |
| `DELETE` | מחיקת קובץ/תיקייה |
| `RENAME` | שינוי שם |
| `MKDIR` | תיקייה חדשה |
| `ROOT_STATUS` | בדיקת root בשרת |

---

## 🔐 הרשאות

| הרשאה | מטרה |
|--------|-------|
| `BLUETOOTH_CONNECT` | חיבור למכשירים |
| `BLUETOOTH_SCAN` | סריקת מכשירים |
| `MANAGE_EXTERNAL_STORAGE` | גישה לכל האחסון |
| `FOREGROUND_SERVICE` | שירות רקע |
| `POST_NOTIFICATIONS` | התראת שירות |

---

## ⚠️ הגבלות ללא Root

- גישה רק ל-`/storage/emulated/0/` (אחסון חיצוני משותף)
- לא ניתן לגשת לנתוני אפליקציות (`/data/data/`)
- עם Root: גישה לכל מערכת הקבצים

---

## 🔧 libsu (Root API)

הפרויקט משתמש ב-[libsu](https://github.com/topjohnwu/libsu) של topjohnwu (יוצר Magisk).
מוסיף אוטומטית כ-dependency ב-Gradle.
