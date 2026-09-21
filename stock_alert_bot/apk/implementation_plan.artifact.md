# Implementation Plan - Security, Privacy, and Automation

This plan extends the Stock Analysis app to include private access for specific users and ensures the Python web dashboard runs automatically every day.

## User Review Required

> [!IMPORTANT]
> **Firebase Authentication:** You will need to enable the **Email/Password** sign-in provider in the [Firebase Console](https://console.firebase.google.com/) under "Authentication".
> You will also need to manually add the email addresses of the "few users" you want to grant access to.

> [!CAUTION]
> **Firestore Security:** We will update your Firestore rules to `allow read: if request.auth != null;`. This ensures that nobody can see your data without logging into your app.

## Proposed Changes

### 1. Android Security (Private Access)

#### [MODIFY] [app/build.gradle](file:///C:/Users/Kunal/Desktop/Stock/android_app/app/build.gradle)
Add Firebase Authentication dependency.

#### [NEW] [LoginScreen.kt](file:///C:/Users/Kunal/Desktop/Stock/android_app/app/src/main/java/com/Kunal/stock/ui/LoginScreen.kt)
A simple screen for users to sign in.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Kunal/Desktop/Stock/android_app/app/src/main/java/com/Kunal/stock/MainActivity.kt)
Update the main entry point to check if a user is logged in. If not, show the `LoginScreen`.

---

### 2. Web Dashboard Automation & Privacy

#### [MODIFY] [web_dashboard.py](file:///C:/Users/Kunal/Desktop/Stock/stock_alert_bot/Option/scripts/web_dashboard.py)
Ensure the dashboard handles automatic startups gracefully and logs status for the Windows Task.

#### [MODIFY] [register_market_task.ps1](file:///C:/Users/Kunal/Desktop/Stock/stock_alert_bot/Option/scripts/register_market_task.ps1)
Refine the task registration to ensure it runs correctly every weekday at market open (09:10 AM).

---

### 3. Firestore Privacy

#### [NEW] [firestore.rules](file:///C:/Users/Kunal/Desktop/Stock/android_app/firestore.rules)
A file containing the security rules to be copy-pasted into the Firebase Console to lock down the data.

## Verification Plan

### Manual Verification
- **App Login**: Attempt to open the app. Verify it stays on the Login screen until a valid user signs in.
- **Data Privacy**: Attempt to access Firestore data via the web without authentication (if rules are applied) to verify it is blocked.
- **Automation**: Check "Windows Task Scheduler" to verify the "Stock Alert Bot - Market Session" task is active and correctly triggered.
