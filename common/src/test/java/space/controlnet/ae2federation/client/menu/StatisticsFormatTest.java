package space.controlnet.ae2federation.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.observability.statistics.StatisticsRange;

final class StatisticsFormatTest {
    private static final Path LANG = Path.of("..").toAbsolutePath().normalize()
            .resolve("common/src/main/resources/assets/ae2federation/lang");
    private final Language vanilla = Language.getInstance();

    @AfterEach
    void restoreLanguage() {
        Language.inject(vanilla);
    }

    @Test
    void countsKeepTheirShortForms() {
        assertEquals("–", FederationStatisticsView.fmt(Double.NaN));
        assertEquals("0", FederationStatisticsView.fmt(0));
        assertEquals("12", FederationStatisticsView.fmt(12));
        assertEquals("2.4", FederationStatisticsView.fmt(2.4));
        assertEquals("1.5k", FederationStatisticsView.fmt(1_500));
        assertEquals("69k", FederationStatisticsView.fmt(69_000));
        assertEquals("1.2M", FederationStatisticsView.fmt(1_200_000));
        assertEquals("12.3M", FederationStatisticsView.fmt(12_300_000));
        assertEquals("-1.5k", FederationStatisticsView.fmt(-1_500));
    }

    @Test
    void aCountRoundedUpToTheNextUnitShowsThatUnit() {
        assertEquals("10", FederationStatisticsView.fmt(9.96));
        assertEquals("1.0k", FederationStatisticsView.fmt(999.6));
        assertEquals("10k", FederationStatisticsView.fmt(9_960));
        assertEquals("1.0M", FederationStatisticsView.fmt(999_950));
        assertEquals("1.0G", FederationStatisticsView.fmt(999_950_000));
        assertEquals("1.0T", FederationStatisticsView.fmt(999_950_000_000d));
        assertEquals("-1.0M", FederationStatisticsView.fmt(-999_950));
    }

    @Test
    void countsAboveAGigaHaveTheirOwnUnits() {
        assertEquals("1.5T", FederationStatisticsView.fmt(1.5e12));
        assertEquals("2.0P", FederationStatisticsView.fmt(2e15));
        assertEquals("9.2E", FederationStatisticsView.fmt(Long.MAX_VALUE));
    }

    @Test
    void cpuStorageStepsByKibibytes() {
        assertEquals("512", FederationStatisticsView.bytes(512));
        assertEquals("1k", FederationStatisticsView.bytes(1024));
        assertEquals("1.5k", FederationStatisticsView.bytes(1536));
        assertEquals("64k", FederationStatisticsView.bytes(64 * 1024));
        assertEquals("1M", FederationStatisticsView.bytes(1 << 20));
        assertEquals("1.25M", FederationStatisticsView.bytes(1_310_720));
        assertEquals("1M", FederationStatisticsView.bytes((1 << 20) + 1024));
        assertEquals("1M", FederationStatisticsView.bytes((1L << 20) - 1));
        assertEquals("1G", FederationStatisticsView.bytes(1L << 30));
        assertEquals("1.5T", FederationStatisticsView.bytes(3L << 39));
    }

    @Test
    void timesReadInEnglish() throws IOException {
        Language.inject(language("en_us.json"));
        assertEquals("now", ago(0));
        assertEquals("-38s", ago(38));
        assertEquals("-9m 55s", ago(595));
        assertEquals("-10m", ago(600));
        assertEquals("-23h 50m", ago(85_800));
        assertEquals("-2h", ago(7_200));
        assertEquals("38s", FederationStatisticsView.elapsed(38_000_000_000L));
        assertEquals("1m 44s", FederationStatisticsView.elapsed(104_000_000_000L));
        assertEquals("2h 0m", FederationStatisticsView.elapsed(7_200_000_000_000L));
    }

    @Test
    void timesReadInChinese() throws IOException {
        Language.inject(language("zh_cn.json"));
        assertEquals("现在", ago(0));
        assertEquals("38秒前", ago(38));
        assertEquals("9分55秒前", ago(595));
        assertEquals("10分前", ago(600));
        assertEquals("23小时50分前", ago(85_800));
        assertEquals("2小时前", ago(7_200));
        assertEquals("38秒", FederationStatisticsView.elapsed(38_000_000_000L));
        assertEquals("1分44秒", FederationStatisticsView.elapsed(104_000_000_000L));
        assertEquals("2小时0分", FederationStatisticsView.elapsed(7_200_000_000_000L));
    }

    /** The label of the point {@code seconds} before the newest one of the range whose points are that far apart. */
    private static String ago(long seconds) {
        var range = seconds < StatisticsRange.SECONDS.buckets() ? StatisticsRange.SECONDS
                : seconds % 600 == 0 ? StatisticsRange.DAY : StatisticsRange.FIVE_SECONDS;
        return FederationStatisticsView.ago(range.buckets() - 1 - (int) (seconds / range.seconds()), range);
    }

    private static Language language(String file) throws IOException {
        var entries = new HashMap<String, String>();
        JsonParser.parseString(Files.readString(LANG.resolve(file))).getAsJsonObject().entrySet()
                .forEach(entry -> entries.put(entry.getKey(), entry.getValue().getAsString()));
        return new MapLanguage(entries);
    }

    private static final class MapLanguage extends Language {
        private final Map<String, String> entries;

        MapLanguage(Map<String, String> entries) {
            this.entries = entries;
        }

        @Override
        public String getOrDefault(String key, String fallback) {
            return entries.getOrDefault(key, fallback);
        }

        @Override
        public boolean has(String key) {
            return entries.containsKey(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return false;
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return FormattedCharSequence.EMPTY;
        }
    }
}
