package com.doorblock.core

/**
 * فهرست‌های پایه‌ی دست‌نویس. این فهرست‌ها عمدا کوچک و محافظه‌کار هستند؛
 * پوشش گسترده‌ی دامنه‌ها از فهرست‌های عمومی (docs/LISTS.md) می‌آید.
 *
 * قالب قاعده‌های کلمه: w: کل کلمه | p: پیشوند | c: شامل | ph: عبارت (ببین KeywordRule)
 */
object DefaultLists {

    /**
     * دامنه‌های شناخته‌شده. علاوه بر خود دامنه، نام آنها (مثلا xnxx) در جستجوی ساده هم مسدود است.
     * فقط سایت‌های مشهور و بی‌ابهام؛ هیچ دامنه‌ی حدسی در این فهرست نیست.
     */
    val domains: Set<String> = setOf(
        "gtaall.com", // فهرست شخصی (برای تست)
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com",
        "spankbang.com", "tube8.com", "xtube.com", "youjizz.com", "eporner.com", "beeg.com",
        "porntrex.com", "hqporner.com", "thumbzilla.com", "brazzers.com",
        "chaturbate.com", "stripchat.com", "bongacams.com", "livejasmin.com", "camsoda.com",
        "myfreecams.com", "nhentai.net", "rule34.xxx",
    )

    /**
     * نام برند سایت‌ها (b:): فقط در متن جستجو. روی نام میزبان اعمال نمی‌شود، چون میزبان‌ها را
     * منطق دامنه و فهرست‌های عمومی پوشش می‌دهند (notxnxx.com نباید فقط به‌خاطر شباهت رشته بسته شود).
     */
    private val brandTerms = listOf(
        "b:xvideos", "b:xnxx", "b:xhamster", "b:redtube", "b:youporn", "b:brazzers",
        "b:spankbang", "b:chaturbate", "b:stripchat", "b:bongacams", "b:livejasmin",
        "b:camsoda", "b:myfreecams",
    )

    /** واژه‌های صریح و بی‌ابهام؛ داخل توکن هم قبول است (روی نام میزبان هم اعمال می‌شود). */
    private val explicitTerms = listOf(
        "c:porn", "c:hentai", "c:blowjob", "c:cumshot", "c:gangbang", "c:bukkake",
        "c:doggystyle", "c:lexilune", // دو مورد آخر: درخواست شخصی کاربر
    )

    /** کلمه‌ی کوتاه: فقط به‌صورت کل کلمه. */
    private val wholeWords = listOf("w:milf")

    /**
     * عبارت‌ها: فقط وقتی کنار هم بیایند. واژه‌های چندمعنا (sex، nude، xxx، anal...) عمدا
     * تنها مسدود نیستند، چون جستجوهای عادی (سلامت، هنر، فیلم) را خراب می‌کنند.
     */
    private val phrases = listOf(
        "ph:sex video", "ph:sex videos", "ph:sex movie", "ph:sex movies",
        "ph:xxx video", "ph:xxx videos", "ph:xxx movie", "ph:xxx movies",
        "ph:naked girl", "ph:naked girls", "ph:nude girl", "ph:nude girls",
        "ph:nude pics", "ph:naked pics",
        "ph:onlyfans leak", "ph:onlyfans leaks",
    )

    /** فارسی (محافظه‌کارانه). */
    private val persian = listOf("w:سکس", "c:پورن", "ph:فیلم سکسی")

    val keywords: List<String> = brandTerms + explicitTerms + wholeWords + phrases + persian
}
