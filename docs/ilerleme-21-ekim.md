# StockFlow — ilerleme özeti (21 Ekim 2026)

## Önceki görüşmeden bu yana

8 Ekim'de yalnızca yerel (Room) stok prototipi vardı; `PENDING` durumu sadece hazırlıktı. Şimdi:

1. **FastAPI sunucusu** (`server/`): kategori, ürün ve stok hareketi uç noktaları. SQLAlchemy ile SQLite (demo) veya PostgreSQL (`DATABASE_URL`).
2. **Sunucuda idempotency:** hareketin kimliği telefonda üretilen UUID. Aynı kimlik + aynı içerik tekrar gelirse sunucu saklı sonucu döndürür, stok ikinci kez değişmez. Aynı kimlik farklı içerikle gelirse `409`.
3. **Sunucuda eşzamanlı stok kontrolü:** kontrol ve düşüm tek koşullu `UPDATE products SET stock = stock - q WHERE id = ? AND stock >= q`. Satır kilitlendiği için iki cihaz aynı eski stokla kontrolü geçemez; ayrıca tabloda `CHECK (stock >= 0)`.
4. **Ret politikası:** stok yetmezse hareket silinmez, `REJECTED` ve sebebiyle saklanır; telefonda da görünür.
5. **Android senkronizasyonu:** Retrofit + kotlinx.serialization ile API istemcisi; `SyncManager` kuyruğu kategori → ürün → hareket sırasıyla gönderir; `WorkManager` ağ koşuluyla ve üstel geri çekilmeyle (backoff) yeniden dener, uygulama kapansa da iş kaybolmaz.
6. **Arayüz:** senkronizasyon durumu (sırada / gönderiliyor / çevrimdışı / senkronize), "Şimdi senkronize et", ürün ekranında "Sunucudaki stok", reddedilen hareketler kırmızı.
7. **Veritabanı v2:** `products.serverStock` sütunu, Room AutoMigration ile eski veriyi koruyarak.

## Kanıt

- Android: 21 birim testi geçti (7 kural, 9 Room — ilk kez çalıştırıldı, 5 senkronizasyon).
- Sunucu: 10 test geçti; 8 paralel çıkış isteğinde stok negatife düşmüyor.
- Emülatörde: çevrimdışı çıkış → başka cihaz aynı stoğu satıyor → bağlantı gelince telefonun hareketi otomatik gönderiliyor ve reddediliyor.
- Bulgu: bağlantı yanıt gelmeden koparsa OkHttp aynı POST'u kendiliğinden yeniden gönderiyor (`SyncManagerTest`). Sunucu idempotency'sinin neden şart olduğunu gösteriyor.

## Bilinen eksikler ve sıradaki işler

- Sunucudan telefona veri çekme yok: başka cihazın hareketleri telefona inmiyor, sadece "sunucudaki stok" değeri ve fark uyarısı var.
- Kullanıcı girişi yok.
- PostgreSQL ile demo/test henüz yapılmadı (kod hazır).
- Reddedilen hareket için kullanıcı akışı (düzeltip yeniden gönderme) tasarım aşamasında.
