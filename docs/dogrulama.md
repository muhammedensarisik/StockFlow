# Doğrulama durumu — 1 Kasım 2026

## 1 Kasım ekleri

- PostgreSQL 17.6 (taşınabilir kurulum) üzerinde sunucu testleri: **10 test, 0 başarısız.** Aynı testler SQLite'ta da geçiyor.
- Sunucu `run-postgres.ps1` ile PostgreSQL üzerinde çalıştı; emülatörde demo senaryosu (giriş 100 → çevrimdışı 20 ve 50 çıkış → ikinci cihaz 70 → internet gelince 20 onay, 50 ret, merkezi stok 10) kaydedildi: `sunum/stockflow-demo-kisa.mp4`.

## 20 Ekim

## Başarılı kontroller

- `:app:assembleDebug` tamamlandı; `app-debug.apk` üretildi (Room/KSP kod üretimi dahil).
- `:app:testDebugUnitTest`: **21 test, 0 başarısız.**
  - `StockRulesTest` (7): stok kuralları.
  - `StockRepositoryTest` (9): gerçek Room veritabanında yerel davranış. 7 Ekim'de yazılmış, **ilk kez bugün çalıştırıldı ve geçti.**
  - `SyncManagerTest` (5): MockWebServer ile sahte sunucuya karşı senkronizasyon — kabul → `SYNCED`, ret → `REJECTED` + sebep, bağlantı yok → `PENDING` kalır, yanıt kaybında aynı hareket aynı kimlikle yeniden gönderilir, gönderim sırasında düzenlenen ürün `PENDING` kalır.
- `server`: `pytest` **10 test, 0 başarısız** (SQLite). Kapsam: giriş/çıkış, aynı hareketin iki kez gelmesi, aynı kimlik farklı içerik → 409, yetersiz stokta ret ve kaydın saklanması, sıfıra inme, iki cihaz çatışması, 8 paralel çıkışta stokun negatife düşmemesi, geçersiz miktar, bilinmeyen ürün, eski ürün düzenlemesinin yenisini ezmemesi.
- Gerçek sunucu (`uvicorn`) + curl: aynı hareket iki kez → ikinci yanıt `replayed: true`, stok bir kez değişti.
- Emülatör (`Medium_Phone`, API 36) uçtan uca:
  1. Eski sürümün (DB v1) verileri v2'ye otomatik geçişte korundu; uygulama açılınca bekleyen 3 hareket sunucuya gitti ve onaylandı.
  2. Uçak modunda 600 çıkış → cihaz 300, `Senkronizasyon bekliyor`.
  3. Bu sırada ikinci cihaz rolüyle sunucuya doğrudan 500 çıkış (sunucu 900 → 400).
  4. Uçak modu kapatılınca WorkManager hareketi kendiliğinden gönderdi; sunucu reddetti. Telefonda `Reddedildi: Sunucuda yetersiz stok.`, sunucu stoğu 400, cihaz/sunucu farkı uyarısı göründü.

## Çözülen ortam sorunu

Room testleri önceki denemelerde `UnsatisfiedLinkError: no conscrypt_openjdk_jni-wındows-x86_64` ile düşüyordu. Sebep: Türkçe sistem dilinde `"Windows".lowercase()` sonucu noktasız `ı` içeriyor. Test JVM'i `-Duser.language=en` ile çalıştırılıyor (`app/build.gradle.kts`).

## Henüz doğrulanmayanlar

- `:app:lintDebug` çalıştırılmadı.
- Fiziksel telefon (LAN IP ile) denenmedi.
- Kullanıcı girişi ve sunucudan veri çekme yok (bkz. README → Sonraki adım).
