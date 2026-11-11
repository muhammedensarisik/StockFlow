# StockFlow

Çevrimdışı çalışabilen mobil stok yönetimi bitirme projesi.

**Bu sürüm (0.2, 20 Ekim):** Android (Kotlin + Compose + Room) yerel stok uygulaması **ve** FastAPI sunucusu. Bekleyen (`PENDING`) kategori, ürün ve hareketler WorkManager ile internet gelince sunucuya gönderilir. Sunucu her hareketi telefonda üretilen UUID ile tanır: aynı hareket iki kez gelirse stoğu bir kez değiştirir (idempotency). Stok yetmezse hareketi reddeder; telefonda hareket `Reddedildi` ve sebebiyle görünür, silinmez.

**Henüz yok:** kullanıcı girişi, sunucudan telefona veri çekme (başka cihazın hareketleri telefona inmez, yalnızca "sunucudaki stok" gösterilir), kategori düzenleme.

**Doğrulama:** 21 Android testi + 10 sunucu testi geçti, APK derlendi, emülatörde uçtan uca senkronizasyon denendi. Ayrıntılar `docs/dogrulama.md` içinde.

## Sunucuyu çalıştırma

İlk kurulum (bir kez):

```powershell
cd server
py -3.12 -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
```

Çalıştırma ve test:

```powershell
cd server
.\.venv\Scripts\python -m uvicorn app.main:app --port 8000
.\.venv\Scripts\python -m pytest
```

- API belgeleri ve elle deneme: http://localhost:8000/docs
- Varsayılan veritabanı `server/stockflow.db` (SQLite). PostgreSQL için: `$env:DATABASE_URL="postgresql+psycopg://kullanici:parola@localhost:5432/stockflow"`.
- Bu bilgisayarda taşınabilir PostgreSQL 17 `%LOCALAPPDATA%\StockFlowPostgres` altında kurulu (yalnızca localhost, şifresiz). `powershell -ExecutionPolicy Bypass -File serverun-postgres.ps1` veritabanını başlatıp sunucuyu PostgreSQL ile açar; `http://localhost:8000/health` → `"database":"postgresql"`.
- Testleri PostgreSQL'de çalıştırmak için: `$env:TEST_DATABASE_URL="postgresql+psycopg://stockflow@localhost:5432/stockflow_test"` sonra `pytest`.
- Demo videosu: `sunum/stockflow-demo-kisa.mp4` (1:50, altyazılı).
- Emülatör bilgisayardaki sunucuya `http://10.0.2.2:8000/` adresiyle ulaşır (varsayılan). Gerçek telefonda sunucuyu `--host 0.0.0.0` ile başlat ve uygulamayı `.\gradlew.bat :app:installDebug -Pstockflow.serverUrl=http://<bilgisayar-IP>:8000/` ile kur.

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

İlk çalıştırma Gradle ve kütüphane indirmeleri için internet gerektirir. Uygulama yalnızca senkronizasyon için internet kullanır; ağ olmadan da tüm yerel işlemler çalışır.

## Demo (offline → online senkronizasyon)

Hazırlık: sunucuyu başlat, emülatörde uygulamayı aç. Tarayıcıda http://localhost:8000/docs açık dursun.

1. Kategoriler → `Malzeme` ekle. Ürünler → `Vida` oluştur, `Malzeme` seç.
2. Ürüne gir, `Giriş` 100 kaydet. Birkaç saniye içinde hareket `Sunucu tarafından onaylandı`, `Sunucudaki stok: 100`.
3. Emülatörü uçak moduna al. `Çıkış` 20, açıklama `Satış`. Cihaz stoğu 80, hareket `Senkronizasyon bekliyor`, ana ekranda banner `Sırada`/`Çevrimdışı`.
4. `81` çıkış dene → `Yetersiz stok`, kayıt oluşmaz (yerel kural).
5. Uçak modunu kapat. Hareket kendiliğinden `onaylandı` olur, sunucu stoğu 80. (`/docs` → `GET /products` ile göster.)
6. **İki cihaz çatışması:** tekrar uçak modu, `Çıkış` 70 kaydet (cihaz 10 gösterir). Bu sırada "ikinci cihaz" olarak `/docs` → `POST /movements` ile aynı ürüne başka `id` ile 50 çıkış gönder (sunucu 30). Uçak modunu kapat → telefondaki 70'lik hareket `Reddedildi: Sunucuda yetersiz stok.` olur; sunucu stoğu negatife düşmez.
7. **Aynı istek iki kez:** `/docs` → `POST /movements` ile aynı `id` ve içeriği iki kez gönder. İkinci yanıt `"replayed": true`, stok yalnızca bir kez değişir. Aynı `id` farklı miktarla → `409`.
8. Uygulamayı kapatıp aç: veriler ve hareket durumları korunur.

## Okuma sırası

1. `domain/StockRules.kt`: giriş/çıkış kuralları; Android'den bağımsız.
2. `data/local/Entities.kt`: kategori, ürün ve hareket kayıtları.
3. `data/local/StockDao.kt`: SQL sorguları ve Flow akışları.
4. `data/StockRepository.kt`: doğrulama, transaction ve yerel tekrar koruması.
5. `ui/StockViewModel.kt`: UI durumu, coroutine ve işlem hataları.
6. `ui/StockFlowApp.kt`: Compose ekranları ve navigation.
7. `StockFlowApplication.kt` ve `MainActivity.kt`: bağımlılıkların kurulması ve uygulamanın açılması.
8. `data/remote/StockApi.kt`: Retrofit ile sunucu uç noktaları ve JSON modelleri.
9. `data/sync/SyncManager.kt` ve `SyncWorker.kt`: kuyruğun sırayla gönderilmesi, sonuçların işlenmesi, WorkManager.
10. `server/app/main.py`: FastAPI uç noktaları, idempotency ve atomik stok kontrolü.
11. `app/src/test` ve `server/tests`: Android ve sunucu testleri.

Yol ön eki: `app/src/main/java/com/stockflow/app/`.

## Temel kararlar

- UI ağ yanıtı beklemez; Repository'nin Room akışlarını gözlemler.
- Stok doğrudan değiştirilen bir ürün alanı değil, hareketlerin toplamıdır.
- Çıkış kontrolü ve hareket kaydı tek transaction içinde yapılır.
- Miktar pozitif tam sayıdır; yön `IN` veya `OUT` ile belirtilir. Fire, açıklamalı bir `OUT` hareketidir.
- Yeni kategori/ürün/hareket kimliği cihazda UUID olarak oluşturulur.
- Aynı hareket kimliği ve içerikle tekrar, ne yerelde ne sunucuda ikinci etki üretmez; içerik farklıysa hata verir. OkHttp bağlantı koparsa POST isteğini kendiliğinden yeniden gönderebilir; bu yüzden sunucu idempotency'si zorunludur (`SyncManagerTest`).
- Sunucu stok kontrolünü tek bir koşullu `UPDATE ... WHERE stock >= miktar` ile yapar; veritabanı satırı kilitlediği için iki cihaz aynı eski stokla kontrolü geçemez.
- Ürün bilgisi için basit "son yazan kazanır" (`updated_at`). Stok bu yolla asla üzerine yazılmaz.
- Ürün ve kategori silme, hareket düzeltme ve gelişmiş roller bu ilk adımın dışında.
- Stok üst sınırı 1 milyar adet; bu tercih iş kuralı olarak daha sonra değiştirilebilir.
- Çevrimdışı stok, cihazın bildiği veriye dayanır. Çok cihazda kesin stok rezervasyonu sağlamaz; çatışmada sunucu ilk geleni kabul eder, sonrakini reddeder.
- Yalnızca küçük MVP için tüm yerel hareketler gözlemlenir. Büyük veri için sayfalama ve hedefli sorgular sonraki aşamadır.

## Sonraki adım

1. Sunucudan veri çekme (pull): başka cihazların hareketlerini telefona indirip cihaz stoğunu sunucuyla uzlaştırmak.
2. Temel kullanıcı girişi (parola hash + token) ve korunan uç noktalar.
3. Reddedilen hareket için kullanıcı akışı (ör. yeniden düzenleyip yeni hareket olarak gönderme).
