# doorOBlock (Android)

1. پوشه را در Android Studio باز کن و اجرا کن.
2. در برنامه دکمه «فعال‌سازی سرویس دسترسی‌پذیری» را بزن و doorOBlock را روشن کن.
3. در Chrome آدرس gtaall.com را بزن؛ باید به quran.com بروی و ۱۰ ثانیه (دیباگ) قفل شوی.
4. تست‌ها: `./gradlew test`

زمان قفل در `core/Config.kt` است (دیباگ ۱۰ ثانیه، ریلیز ۳۰ دقیقه).

## بیلد بدون Android Studio
1. در github.com یک ریپازیتوری خصوصی بساز و محتویات این پوشه (با پوشه‌ی .github) را آپلود کن.
2. تب Actions -> Build APK -> بعد از سبز شدن، فایل doorOBlock-apk را دانلود کن.
3. فایل app-debug.apk را روی گوشی نصب کن.
