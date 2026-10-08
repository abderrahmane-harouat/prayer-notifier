<div dir="rtl">

# التطوير

[← الصفحة الرئيسية](../README.ar.md) · [English](development.md)

المتطلبات: Android Studio (أو سطر الأوامر)، و**JDK من 17 إلى 21** (الإصدارات الأحدث لا يدعمها إصدار Gradle/AGP المستخدم)، وAndroid SDK 36. يعمل التطبيق على أندرويد 8.0 (API 26) فأحدث؛ ويتطلب «عدم الإزعاج» وقت الصلاة أندرويد 10.

<div dir="ltr">

```sh
git clone https://github.com/abderrahmane-harouat/prayer-notifier.git
cd prayer-notifier
./gradlew :app:assembleDebug        # build the APK
./gradlew :app:testDebugUnitTest    # run the unit tests
./gradlew :app:installDebug         # install on a connected device or emulator
```



أو افتح المجلد في Android Studio واضغط **Run**.

قبل أي commit، فعّل أداة الحماية التي تمنع رفع مفاتيح التوقيع وكلمات المرور وملفات الخطوط المرخّصة (المستودع عام) بالأمر: `git config core.hooksPath .githooks`

## بناء نسخة الإصدار الموقّعة

تُوقَّع نسخ الإصدار فقط على الأجهزة التي تملك المفتاح الخاص. أنشئ ملف `keystore.properties` في جذر المشروع (مستثنى من Git) يشير إلى مخزن مفاتيح محفوظ **خارج** المستودع (الحقول: `storeFile` و`storePassword` و`keyAlias` و`keyPassword`)، ثم شغّل `./gradlew :app:assembleRelease`. ودون هذا الملف تُبنى نسخة الإصدار أيضًا لكن دون توقيع.

## تجربة تذكير (نسخ التطوير فقط)

تُثبَّت نسخ التطوير باسم الحزمة `com.example.prayernotifier.debug` إلى جانب نسخة الإصدار، فيحتفظ الهاتف بتذكيراته الحقيقية أثناء تجربة نسخة التطوير. وتتضمّن أداة صغيرة تطلق تذكيرات حقيقية وفترات صمت بالمسار نفسه للمجدولة دون تغيير ساعة الهاتف، وليست جزءًا من نسخة الإصدار. الأوامر في قسم [Test a reminder](development.md#test-a-reminder-debug-builds-only) في النسخة الإنجليزية.

## محاكاة أيام كثيرة

يشغّل الاختبار `MultiDaySimulationTest` المخطِّط والمجدوِل ومعالجة المنبّهات الحقيقية على سنوات من الأيام في ثوانٍ، بساعة افتراضية وطابور منبّهات يتصرّف مثل أندرويد. ويتحقق من كل تذكير وكل بداية ونهاية لـ«عدم الإزعاج» مقابل الأوقات المستخرجة من المواقيت: عشر سنوات في الجزائر العاصمة، وثلاث في لندن (التوقيت الصيفي) وأوسلو (العشاء بعد منتصف الليل) ومكة (رمضان)، وسنتان في ترومسو (نهار القطب وليله)، ومن 2099 إلى 2101، مع إعدادات متنوعة منها مدد صمت معدّلة، وإعادات تشغيل وإعادات تخطيط عشوائية. نحو 180 ألف حدث، كلٌّ بالدقيقة.

## اختياري: خط ثمانية للعربية

صُمّمت الواجهة العربية لـ[خط ثمانية](https://font.thmanyah.com/). ترخيصه يسمح بتضمينه داخل التطبيق لكنه **يمنع استضافة ملفات الخط**، لذلك ليست موجودة في هذا المستودع. ودونها تستخدم العربية خط «أميري» المرفق، ويعمل كل شيء آخر كما هو.

للبناء بخط ثمانية:

1. نزّل الخط من [font.thmanyah.com](https://font.thmanyah.com/) (مجاني؛ يطلب الموقع بريدًا إلكترونيًا).
2. انسخ هذه الملفات الخمسة من مجلدات `otf/` إلى `app/src/main/res-licensed/font/` (مجلد مستثنى من Git) بالأسماء التالية:

   | من ملف التنزيل | احفظه باسم |
   |---|---|
   | `thmanyahserifdisplay-Regular.otf` | `thmanyah_serif_display.otf` |
   | `thmanyahserifdisplay-Bold.otf` | `thmanyah_serif_display_bold.otf` |
   | `thmanyahsans-Regular.otf` | `thmanyah_sans.otf` |
   | `thmanyahsans-Medium.otf` | `thmanyah_sans_medium.otf` |
   | `thmanyahsans-Bold.otf` | `thmanyah_sans_bold.otf` |

3. أعد البناء، وسيكتشف التطبيق الملفات تلقائيًا.

## بنية المشروع

راجع قسم [Project structure](development.md#project-structure) في النسخة الإنجليزية؛ أسماء الملفات والمجلدات هي نفسها.

باختصار: الشيفرة في `app/src/main/java/com/example/prayernotifier/` (البيانات في `data/`، والحساب في `data/calculation/`، والتذكيرات و«عدم الإزعاج» في `data/notifications/`، واللغة في `i18n/`، والواجهات في `ui/`)، واختبارات الوحدة ومنها محاكاة الأيام الكثيرة في `app/src/test/`، والنصوص العربية في `app/src/main/res/values-ar/`.

</div>
