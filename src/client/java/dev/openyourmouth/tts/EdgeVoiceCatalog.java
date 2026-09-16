package dev.openyourmouth.tts;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** In-memory catalog for Microsoft Edge Read Aloud voices. */
public final class EdgeVoiceCatalog {
    static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    static final String EDGE_VERSION = "143.0.3650.75";
    static final String GEC_VERSION = "1-" + EDGE_VERSION;
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";
    private static final String VOICES_PATH = "/consumer/speech/synthesize/readaloud/voices/list";
    private static final List<Voice> FALLBACK = List.of(
            v("pt-BR-FranciscaNeural", "pt-BR"), v("pt-BR-AntonioNeural", "pt-BR"),
            v("pt-PT-RaquelNeural", "pt-PT"), v("pt-PT-DuarteNeural", "pt-PT"),
            v("en-US-EmmaMultilingualNeural", "en-US"), v("en-US-AndrewMultilingualNeural", "en-US"), v("en-US-AriaNeural", "en-US"),
            v("en-GB-SoniaNeural", "en-GB"), v("en-GB-RyanNeural", "en-GB"), v("en-AU-NatashaNeural", "en-AU"), v("en-AU-WilliamNeural", "en-AU"),
            v("es-ES-ElviraNeural", "es-ES"), v("es-ES-AlvaroNeural", "es-ES"), v("es-MX-DaliaNeural", "es-MX"), v("es-MX-JorgeNeural", "es-MX"),
            v("fr-FR-DeniseNeural", "fr-FR"), v("fr-FR-HenriNeural", "fr-FR"), v("fr-CA-SylvieNeural", "fr-CA"), v("fr-CA-AntoineNeural", "fr-CA"),
            v("de-DE-KatjaNeural", "de-DE"), v("de-DE-ConradNeural", "de-DE"), v("it-IT-ElsaNeural", "it-IT"), v("it-IT-DiegoNeural", "it-IT"),
            v("ja-JP-NanamiNeural", "ja-JP"), v("ko-KR-SunHiNeural", "ko-KR"), v("ru-RU-SvetlanaNeural", "ru-RU"),
            v("zh-CN-XiaoxiaoNeural", "zh-CN"), v("zh-CN-YunxiNeural", "zh-CN"), v("zh-TW-HsiaoChenNeural", "zh-TW"),
            v("ar-EG-SalmaNeural", "ar-XA"), v("hi-IN-SwaraNeural", "hi-IN"), v("tr-TR-EmelNeural", "tr-TR"),
            v("uk-UA-PolinaNeural", "uk-UA"), v("nl-NL-ColetteNeural", "nl-NL"), v("pl-PL-ZofiaNeural", "pl-PL"),
            v("cs-CZ-VlastaNeural", "cs-CZ"), v("da-DK-ChristelNeural", "da-DK"), v("fi-FI-NooraNeural", "fi-FI"),
            v("nb-NO-PernilleNeural", "nb-NO"), v("sv-SE-SofieNeural", "sv-SE"), v("ro-RO-AlinaNeural", "ro-RO"),
            v("el-GR-AthinaNeural", "el-GR"), v("bn-IN-TanishaaNeural", "bn-IN"), v("id-ID-GadisNeural", "id-ID"),
            v("vi-VN-HoaiMyNeural", "vi-VN"), v("th-TH-PremwadeeNeural", "th-TH")
    );
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile List<Voice> voices = FALLBACK;
    private static CompletableFuture<List<Voice>> refresh;

    private EdgeVoiceCatalog() {}

    public static List<String> forLocale(String locale) {
        List<String> result = voices.stream().filter(voice -> localeMatches(locale, voice.locale()))
                .map(Voice::id).distinct().sorted().toList();
        if (!result.isEmpty()) return result;
        result = FALLBACK.stream().filter(voice -> localeMatches(locale, voice.locale())).map(Voice::id).toList();
        return result.isEmpty() ? List.of("en-US-EmmaMultilingualNeural") : result;
    }

    public static String selectedOrDefault(String locale, String selected) {
        List<String> choices = forLocale(locale);
        return choices.contains(selected) ? selected : choices.getFirst();
    }

    public static String next(String locale, String selected) {
        List<String> choices = forLocale(locale);
        int index = choices.indexOf(selected);
        return choices.get((Math.max(-1, index) + 1) % choices.size());
    }

    public static synchronized CompletableFuture<List<Voice>> refreshAsync() {
        if (refresh != null && !refresh.isCompletedExceptionally()) return refresh;
        refresh = CompletableFuture.supplyAsync(() -> {
            try {
                long now = System.currentTimeMillis() / 1000L;
                List<Voice> fetched;
                try {
                    fetched = fetch(now, true, false);
                } catch (Exception firstAddressFailure) {
                    fetched = fetch(now, true, true);
                }
                if (!fetched.isEmpty()) voices = fetched;
                return voices;
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        });
        return refresh;
    }

    private static List<Voice> fetch(long epochSeconds, boolean allowClockRetry, boolean alternateHost) throws Exception {
        String query = "trustedclienttoken=" + TRUSTED_CLIENT_TOKEN + "&Sec-MS-GEC=" + generateGec(epochSeconds) +
                "&Sec-MS-GEC-Version=" + GEC_VERSION;
        String host = alternateHost ? "Speech.Platform.Bing.Com" : "speech.platform.bing.com";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + host + VOICES_PATH + "?" + query)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT).header("Accept", "*/*").header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "muid=" + randomId() + ";").GET().build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (allowClockRetry && response.statusCode() == 403 && response.headers().firstValue("Date").isPresent()) {
            long serverTime = ZonedDateTime.parse(response.headers().firstValue("Date").orElseThrow(), DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond();
            return fetch(serverTime, false, alternateHost);
        }
        if (response.statusCode() / 100 != 2) throw new TtsHttpException(response.statusCode(), "Edge voice catalog request failed");
        List<Voice> fetched = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(response.body()).getAsJsonArray()) {
            var object = element.getAsJsonObject();
            if (object.has("ShortName") && object.has("Locale")) fetched.add(new Voice(object.get("ShortName").getAsString(), object.get("Locale").getAsString()));
        }
        fetched.sort(Comparator.comparing(Voice::locale).thenComparing(Voice::id));
        return List.copyOf(fetched);
    }

    static String generateGec(long epochSeconds) throws Exception {
        long rounded = epochSeconds - Math.floorMod(epochSeconds, 300L);
        long ticks = (rounded + 11_644_473_600L) * 10_000_000L;
        byte[] digest = MessageDigest.getInstance("SHA-256").digest((ticks + TRUSTED_CLIENT_TOKEN).getBytes(StandardCharsets.US_ASCII));
        return HexFormat.of().withUpperCase().formatHex(digest);
    }

    static String randomId() { return UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT); }
    private static boolean localeMatches(String requested, String actual) {
        if (requested.equalsIgnoreCase(actual)) return true;
        return requested.startsWith("ar-") && actual.startsWith("ar-");
    }
    private static Voice v(String id, String locale) { return new Voice(id, locale); }
    public record Voice(String id, String locale) {}
}
