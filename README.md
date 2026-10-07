# StockFlow — ilk geliştirme adımı

Çevrimdışı çalışabilen mobil stok ve tedarik yönetimi bitirme projesi.

Bu sürüm: Kotlin + Compose + Navigation + ViewModel + StateFlow + Room ile **yerel stok prototipi**. FastAPI, PostgreSQL, authentication ve gerçek sunucu senkronizasyonu henüz eklenmedi. `PENDING` kayıtlar ilerideki senkronizasyona hazırlıktır; bu sürümde sunucuya gönderilmez.

**Doğrulama:** 7 stok kuralı testi geçti; Kotlin kaynak kontrolü başarılı. Tam APK derlemesi, Room testleri ve cihazda çalışma bu ortamın araç/izin sorunları nedeniyle henüz doğrulanamadı. Ayrıntılar `docs/dogrulama.md` içinde. İlk Android Studio derlemesi yapılmadan bu sürüm çalışan cihaz demosu olarak sunulmamalı.

## Açılış

1. Android Studio → Open → bu klasörü (`StockFlow`) seç.
2. Gradle senkronizasyonunun bitmesini bekle. Gradle JDK olarak Android Studio'nun Embedded JDK'sını kullan (JDK 17 veya üstü).
3. SDK Platform **Android 36.1** ve Build Tools **36.0.0** kurulu olmalı. Bu bilgisayardaki kurulum bunları içeriyor.
4. En az Android 8 / API 26 çalıştıran telefon ya da emülatör seç, Run'a bas.

`local.properties` makineye özeldir ve Git'e girmez. Başka bilgisayarda Android Studio SDK yolunu oluşturur.

Windows komutları:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

İlk çalıştırma Gradle ve kütüphane indirmeleri için internet gerektirir. Kurulan uygulamanın bu sürümü internet kullanmaz.

## İlk demo

1. Kategoriler → `Malzeme` kategorisi ekle.
2. Ürünler → `Vida` ürünü oluştur, `Malzeme` kategorisini seç.
3. Ürüne dokun. Başlangıç stoğunun 0 olduğunu gör.
4. `Giriş` seç, miktar `100`, açıklama `İlk teslimat`, kaydet.
5. Telefonu uçak moduna al.
6. `Çıkış` seç, miktar `20`, açıklama `Satış`, kaydet. Stok 80 olmalı.
7. `81` adet çıkış dene. `Yetersiz stok` gösterilmeli; stok 80 kalmalı, yeni hareket oluşmamalı.
8. Uygulamayı tamamen kapatıp yeniden aç. Ürün, stok 80 ve iki hareket korunmalı.
9. Ürün adını değiştir. Stok ve hareket geçmişi aynı kalmalı.

Bu adımlar fiziksel cihaz/emülatör üzerinde ayrıca uygulanmalıdır. Otomatik veritabanı testleri cihazdaki manuel gösterimin yerine geçmez.

## Okuma sırası

1. `domain/StockRules.kt`: giriş/çıkış kuralları; Android'den bağımsız.
2. `data/local/Entities.kt`: kategori, ürün ve hareket kayıtları.
3. `data/local/StockDao.kt`: SQL sorguları ve Flow akışları.
4. `data/StockRepository.kt`: doğrulama, transaction ve yerel tekrar koruması.
5. `ui/StockViewModel.kt`: UI durumu, coroutine ve işlem hataları.
6. `ui/StockFlowApp.kt`: Compose ekranları ve navigation.
7. `StockFlowApplication.kt` ve `MainActivity.kt`: bağımlılıkların kurulması ve uygulamanın açılması.
8. `app/src/test`: stok kuralları ve gerçek Room veritabanı davranış testleri.

Yol ön eki: `app/src/main/java/com/stockflow/app/`.

## Temel kararlar

- UI ağ yanıtı beklemez; Repository'nin Room akışlarını gözlemler.
- Stok doğrudan değiştirilen bir ürün alanı değil, hareketlerin toplamıdır.
- Çıkış kontrolü ve hareket kaydı tek transaction içinde yapılır.
- Miktar pozitif tam sayıdır; yön `IN` veya `OUT` ile belirtilir. Fire, açıklamalı bir `OUT` hareketidir.
- Yeni kategori/ürün/hareket kimliği cihazda UUID olarak oluşturulur.
- Aynı hareket kimliği ve içerikle yerel tekrar, ikinci kayıt üretmez; içerik farklıysa hata verir. **Sunucudaki idempotency henüz geliştirilmedi.**
- Ürün ve kategori silme, hareket düzeltme ve gelişmiş roller bu ilk adımın dışında.
- Stok üst sınırı 1 milyar adet; bu tercih iş kuralı olarak daha sonra değiştirilebilir.
- Çevrimdışı stok, cihazın bildiği veriye dayanır. Çok cihazda kesin stok rezervasyonu sağlamaz.
- Yalnızca küçük MVP için tüm yerel hareketler gözlemlenir. Büyük veri için sayfalama ve hedefli sorgular sonraki aşamadır.

## Sonraki adım

Önce bu sürümü Android Studio'da çalıştırıp demoyu doğrula. Sonra kategori düzenleme, ekran hata davranışları ve backend API sözleşmesini tamamla. Detaylı günlük plan `docs/uc-haftalik-plan.md` içinde.
