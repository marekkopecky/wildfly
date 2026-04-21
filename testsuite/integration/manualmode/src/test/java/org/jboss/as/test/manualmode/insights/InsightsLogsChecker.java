package org.jboss.as.test.manualmode.insights;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InsightsLogsChecker {
    public static List<String> searchTextInLog(final String msg, Path logFile, Duration timeout) throws IOException {
        return searchInLog((line) -> line.contains(msg), logFile, timeout);
    }

    public static List<String> searchRegexInLog(final String regex, Path logFile) throws IOException {
        return searchInLog((line) -> Pattern.matches(regex, line), logFile);
    }

    public static List<String> searchRegexInLog(final String regex, Path logFile, Duration timeout) throws IOException {
        return searchInLog((line) -> Pattern.matches(regex, line), logFile, timeout);
    }

    private static List<String> searchInLog(Predicate<String> predicate, Path logFile) throws IOException {
        try (final BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            return reader.lines().filter(predicate).collect(Collectors.toList());
        }
    }

    private static List<String> searchInLog(Predicate<String> predicate, Path logFile, Duration timeout) throws IOException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - timeout.toMillis() < startTime) {
            List<String> foundLogs = searchInLog(predicate, logFile);
            if (!foundLogs.isEmpty()) {
                return foundLogs;
            }
        }
        return new ArrayList<>();
    }

    public static void assertPatternMatched(String regex, Path logFile) throws IOException {
        Assert.assertFalse(searchRegexInLog(regex, logFile).isEmpty());
    }
}
