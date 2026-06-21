package shit.zen.music.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import shit.zen.music.model.LyricLine;

public final class LyricParser {
    private static final Pattern TIME_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]");

    private LyricParser() {
    }

    public static List<LyricLine> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<LyricLine> result = new ArrayList<>();
        for (String line : raw.split("\\R")) {
            Matcher matcher = TIME_PATTERN.matcher(line);
            List<Long> times = new ArrayList<>();
            int lastEnd = 0;
            while (matcher.find()) {
                times.add(toMillis(matcher));
                lastEnd = matcher.end();
            }
            if (times.isEmpty()) {
                continue;
            }
            String text = line.substring(Math.min(lastEnd, line.length())).trim();
            if (text.isEmpty()) {
                text = "...";
            }
            for (Long time : times) {
                result.add(new LyricLine(time, text));
            }
        }
        result.sort(Comparator.comparingLong(LyricLine::timeMs));
        return result;
    }

    public static int currentLineIndex(List<LyricLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) {
            return -1;
        }
        int index = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs() > positionMs) {
                break;
            }
            index = i;
        }
        return index;
    }

    private static long toMillis(Matcher matcher) {
        long minutes = parseLong(matcher.group(1));
        long seconds = parseLong(matcher.group(2));
        String msText = matcher.group(3);
        long ms = 0L;
        if (msText != null && !msText.isEmpty()) {
            ms = parseLong(msText);
            if (msText.length() == 1) {
                ms *= 100L;
            } else if (msText.length() == 2) {
                ms *= 10L;
            }
        }
        return minutes * 60_000L + seconds * 1000L + ms;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
