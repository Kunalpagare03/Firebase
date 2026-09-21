# Cloud Website & Automation Setup

This guide helps you launch your private stock dashboard on the cloud and automate the daily reports.

## 1. Cloud Website (Firebase Hosting)

### [NEW] [index.html](file:///C:/Users/Kunal/Desktop/Stock/android_app/public/index.html)
A mobile-friendly dashboard that shows your Stock Scans and NIFTY Sentiment.

### [NEW] [firebase.json](file:///C:/Users/Kunal/Desktop/Stock/android_app/firebase.json)
Configuration for hosting the website on Firebase.

---

## 2. Daily Automation (GitHub Actions)

### [NEW] [daily_stock_sync.yml](file:///C:/Users/Kunal/Desktop/Stock/.github/workflows/daily_stock_sync.yml)
A "Worker" that runs your Python scripts automatically in the cloud every weekday at 4:00 PM IST.

---

## Final Steps for You

### Step A: Provide Web Config
Paste your Firebase Web Configuration (found in Firebase Console > Project Settings > Your Apps > Web App) into the chat. I will update your `index.html` with it.

### Step B: Setup GitHub Secret
To allow the cloud "Worker" to talk to your database:
1. Go to your **GitHub Repository** > **Settings** > **Secrets and variables** > **Actions**.
2. Click **New repository secret**.
3. Name: `FIREBASE_SERVICE_ACCOUNT`.
4. Value: Paste the ENTIRE content of your `service-account.json` file.

### Step C: Deploy the Website
Once the config is updated, run this command in your PowerShell to launch the website:
```powershell
npm install -g firebase-tools
firebase login
cd android_app
firebase deploy --only hosting
```

Your dashboard will then be live at `https://kunal-analysis.web.app`!
