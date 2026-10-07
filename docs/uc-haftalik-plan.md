# StockFlow — 7–28 Ekim 2026 geliştirme planı

Hedef: 28 Ekim 2026'da gösterilebilir Android + FastAPI + PostgreSQL MVP.
Çalışma kapasitesi: günde 4–6 saat. Başlangıçta 4 saatlik zorunlu işi planla; kalan 1–2 saati öğrenme, hata çözme ve tekrar için kullan. Günleri doldurmak için yeni özellik ekleme.

## MVP'nin bitmiş sayılması

- Ürün oluşturma, düzenleme ve listeleme; kategori oluşturma, düzenleme ve listeleme.
- Çevrimdışı ürün değişiklikleri ve stok hareketleri Room'da kalıcıdır.
- Stok giriş/çıkışı ve hareket geçmişi; yerelde yetersiz stok engeli.
- FastAPI + PostgreSQL üzerinden merkezi ürün/kategori/hareket verisi.
- Tek işletme, temel kullanıcı girişi; gelişmiş roller ve işletmeler arası yetkilendirme yok.
- İlk giriş bağlantı gerektirir. Daha önce giriş yapılmış cihaz çevrimdışı çekirdek işlevlerini kullanabilir. Oturum süresi dolarsa kayıtlar korunur, gönderim yeniden girişe kadar durur.
- Kalıcı gönderim kuyruğu; WorkManager ile bağlantı uygun olduğunda gönderme.
- Sunucuda hareket kimliği ile idempotency. Aynı kimlik + aynı içerik aynı sonucu verir; farklı içerik çatışma hatasıdır.
- Sunucuda stok kontrolü ve hareket işleme atomiktir. Eşzamanlı çıkışlar negatif merkezi stok oluşturamaz.
- Ağ hatası/yanıt kaybı yeniden denenir. İş kuralı reddi tekrar denenmez; hareket ve ret nedeni görünür kalır.
- Sunucudaki onay ve yerel stok güncellemesi aynı hareketi iki kez hesaba katmaz.
- Kritik unit/integration testleri, tekrar üretilebilir demo, README ve kısa teknik rapor.

## Bilinçli kapsam sınırları

QR/barkod, tedarikçi, satın alma talebi, push bildirimleri, gelişmiş raporlar, roller, stok rezervasyonu, kısmi stok çıkışı ve yayınlama bu tarihin dışında.

Çok cihaz için MVP politikası: merkezi sunucu kabul sırasına göre stok yetersizliği halinde tüm hareketi reddeder. Otomatik birleştirme veya cihazlara stok kotası dağıtımı yok. Gerçekte mal teslim edilmişse reddi görmek tek başına fiziksel uyuşmazlığı çözmez; kullanıcı incelemesi gerekir.

## 8 Ekim hocaya gösterilecek ilk çıktı

Problem tanımı, kapsam ve iş kuralları; mimari/veri modeli; yerel Android prototipi; test kanıtı; üç haftalık plan. Sonraki haftalarda yapılacak backend ve senkronizasyon tamamlanmış gibi sunulmaz.

## Günlük akış

Her gün: 15 dk önceki durumu kontrol → 2–3 saat tek geliştirme hedefi → 45–60 dk test/debug → 30–45 dk kodu açıklama ve not alma → uygun Git commit. Anlamadığın noktaları dosya ve fonksiyon adıyla sor. Bir sonraki parçaya geçmeden mevcut parça derlenmeli.

| Tarih | Geliştirme hedefi | Gün sonunda gösterilecek kanıt |
|---|---|---|
| 7 Ekim | Yeni proje, stok kuralları, Room, Repository, temel Compose ekranları | Kaynak kod; derleme/test sonuçları |
| 8 Ekim | Hocaya ilk çıktıyı sun; telefonda offline akışı çalıştır; ilk kod okuması | 100 giriş → 20 çıkış → 80; kapanıp açılınca korunma |
| 9 Ekim | Kategori düzenleme; form ve hata davranışlarını tamamla | Geçersiz miktar/ad ve kategori düzenleme kontrolü |
| 10 Ekim | Yerel transaction, tekrar koruması ve veri kalıcılığı testlerini incele | Testleri kendi cümlelerinle açıklayabilme |
| 11 Ekim | Ürün detay/hareket ekranı kullanılabilirliği; cihaz hataları | Manuel test listesi ve düzeltilen sorunlar |
| 12 Ekim | API sözleşmesi, senkronizasyon durumları ve bağımlılık sırası | İstek/yanıt örnekleri; ürün oluşturma → hareket gönderme |
| 13 Ekim | Android yerel akışı için tampon ve kontrol noktası | Yerel akış güvenilir; yeni özellik eklemeyi durdur |
| 14 Ekim | FastAPI iskeleti, PostgreSQL bağlantısı ve migration | Tek komutla backend çalıştırma; health endpoint |
| 15 Ekim | Kullanıcı modeli, parola hashleme, giriş ve korunan endpoint | Geçerli/geçersiz giriş testleri; parolalar düz metin değil |
| 16 Ekim | Ürün ve kategori API'ları; Android Retrofit bağlantısı | Cihazdan sunucuya bir ürünün gönderilmesi |
| 17 Ekim | Stok hareketi API'sı ve sunucu transaction'ı | 100 → 80; yetersiz stok isteği veriyi değiştirmez |
| 18 Ekim | İdempotency ve eşzamanlılık testleri | Aynı hareket iki kez → tek etki; iki yarışan çıkış güvenli |
| 19 Ekim | Sunucudan veri alma ve yerel kayıt eşleştirme | Ürün/hareket ID'leri korunur; veri iki kez sayılmaz |
| 20 Ekim | Backend/Android entegrasyonu için tampon | Giriş + ürün + hareket tek akışta çalışır |
| 21 Ekim | Room'da kalıcı gönderim kuyruğu; kategori/ürün/hareket bağımlılıkları | Uygulama kapanınca kuyruk kaybolmaz |
| 22 Ekim | WorkManager; bağlantı koşulu ve retry | Offline işlem yeniden bağlantıda gönderilir; anında çalışma garantisi yok |
| 23 Ekim | Yanıt kaybı ve tekrar gönderim; kalıcı işlem kimliği | Sunucu kabul etti/yanıt kayboldu senaryosunda stok tek kez değişir |
| 24 Ekim | Ret, oturum süresi dolması, yeniden giriş ve kullanıcı bildirimi | İşlem kaybolmaz; iş kuralı hatası sonsuz retry üretmez |
| 25 Ekim | Gönderme/alma uzlaştırması; iki cihazla temel çatışma denemesi | Onaylanan hareket çift sayılmaz; reddedilen hareket görünür |
| 26 Ekim | Özellik dondurma; uçtan uca test ve hatalar | Kabul kriterleriyle eşleşen kontrol listesi |
| 27 Ekim | Demo kaydı, README, mimari ve test raporu | Tekrar üretilebilir gösterim; açıklanabilir kod |
| 28 Ekim | Son kontrol, teslim paketi ve bilinen eksikler | APK + backend çalıştırma talimatı + rapor |

## Üç kontrol noktası

**13 Ekim:** Telefon internetsiz çalışır, kayıtlar kalıcıdır ve negatif stok engellenir.

**20 Ekim:** Backend çalışır; Android API'ya ulaşır; tekrar gönderim ve eşzamanlı stok kontrolleri sunucuda test edilmiştir.

**25 Ekim:** Gerçek offline → online senkronizasyon çalışır. Son üç gün yalnızca hatalar ve teslim için ayrılır.

Bir kontrol noktası kaçarsa önce görsel iyileştirme ve ek yönetim ekranları ertelenir. İdempotency, kalıcı kayıt ve negatif stok koruması çıkarılmaz. Süre hedefi bir tahmindir; sonuç kontrol noktalarında ölçülür.

## Demo kabul senaryoları

1. Ürün çevrimdışı oluşturulur, stok girişi/çıkışı kaydedilir; uygulama yeniden açılınca korunur.
2. 100 giriş ve 20 çıkış sonrası yerel stok 80; bağlantı sonrası merkezi stok 80.
3. Aynı çıkış kimliği tekrar gönderilir; merkezi stok 80 kalır.
4. Sunucu hareketi kabul eder ancak yanıt kaybolur; retry aynı sonucu döndürür.
5. Yerelde stoktan fazla çıkış reddedilir; kayıt oluşmaz.
6. İki cihazın toplam çıkışı merkezi stoğu aşar; bir hareket reddedilir, merkezi stok negatif olmaz.
7. Kuyruk işlerken uygulama kapanır; yeniden çalışınca işlemler kaybolmaz.
8. Geçersiz/bitmiş oturum kayıtları silmez; yeniden giriş sonrası gönderim devam eder.

## Kaynaklar

- Android offline-first veri katmanı: https://developer.android.com/topic/architecture/data-layer/offline-first
- Room: https://developer.android.com/jetpack/androidx/releases/room
- Android Gradle Plugin / JDK / Gradle uyumu: https://developer.android.com/build/releases/agp-8-13-0-release-notes
- PostgreSQL transaction isolation: https://www.postgresql.org/docs/current/transaction-iso.html
