# Doğrulama durumu — 7 Ekim 2026

## Başarılı kontroller

- Uygulamadaki gerçek `StockRules.kt` ve `StockRulesTest.kt` dosyaları bağımsız Kotlin/JVM kontrolünde derlendi.
- **7 stok kuralı testi geçti; 0 hata, 0 başarısız test.** JUnit XML kanıtı `docs/verification/stock-rules-results.xml` içinde.
- Compose ekranları dahil uygulamanın Kotlin kaynakları `compileDebugKotlin` ile kontrol edildi. Bu kontrol Android kaynak paketlemesi ve Room kod üretimi hariç yapıldı; tam Android build anlamına gelmez.
- Gradle 8.13 dağıtımı indirildi ve resmi SHA-256 değeriyle doğrulandı; Gradle Wrapper eklendi.

## Çalıştırılamayan kontroller

- Tam `assembleDebug` ve `lintDebug` çalışması tamamlanamadı.
- AAPT2 hem Gradle içinden hem küçük bir bağımsız kaynak paketleme denemesinde kapandı (Windows process exit `-1073741819`).
- Room/KSP SQL doğrulaması, geçici klasörün yazılabilir ve çalıştırılabilir olmasını isteyen kontrolde durdu. Java bu çalışma ortamında klasör için `canWrite=false` ve `canExecute=false` bildirdi.
- **9 Room/Repository davranış testi yazıldı; yürütülemedi.** Testlerin hazır olması sonuçlarının geçtiği anlamına gelmez.
- APK kurulumu, Compose ekranlarının cihazdaki görünümü ve telefon/emülatör demosu henüz doğrulanmadı.

## Kaynak kontrolünün sınırı

Bu bilgisayardaki kısıtlı yürütme ortamında yalnızca Kotlin kaynak kontrolü için, uygulama kodunun kullanmadığı `R.jar` yerine geçici boş bir derleme girdisi oluşturuldu; kaynak paketleme ve KSP görevleri dışarıda bırakıldı. Bu tanılama girdisi kaynak kodun veya teslim arşivinin parçası değildir. Gradle yapılandırmasında tam build'i atlayan bir değişiklik yapılmadı. Normal Android Studio derlemesi gerçek R sınıflarını ve Room implementasyonunu üretmelidir.

Bu sonuç, Kotlin kodunun tip/sözdizimi açısından derlenebildiğini gösterir; SQL şemasının, generated Room kodunun veya uygulamanın çalışmasının doğrulaması değildir.

## Android Studio'da sıradaki kontrol

1. Proje kökünü aç, Gradle sync'i tamamla.
2. `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` çalıştır.
3. Telefon/emülatör üzerinde README'deki ilk demoyu uygula.
4. Başarılı sonuçlarla bu dosyayı güncelle. Hata olursa ilk hata metnini, dosya ve satır bilgisiyle birlikte incele.

## Mevcut test kapsamı

Stok kuralları (7): giriş, çıkış, sıfıra düşme, yetersiz stok, sıfır/negatif miktar, üst sınır, tutarsız başlangıç değeri.

Room/Repository (9; henüz yürütülmedi): sıfır başlangıç/PENDING, aynı hareketin tekrarı, aynı üründe farklı hareketler, kimlik-içerik uyuşmazlığı, yetersiz stokta kayıt oluşmaması, eşzamanlı çıkış, ürün düzenleme, kategori tekrarı ve disk verisinin yeniden açılınca korunması.
