package cn.pupperclient.utils.misc;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

public class SearchUtils {

	private static int levenshteinDistance(String s1, String s2) {
		int m = s1.length();
		int n = s2.length();

		int[][] dp = new int[m + 1][n + 1];

		for (int i = 0; i <= m; i++) {
			dp[i][0] = i;
		}

		for (int j = 0; j <= n; j++) {
			dp[0][j] = j;
		}

		for (int i = 1; i <= m; i++) {
			for (int j = 1; j <= n; j++) {
				int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
				dp[i][j] = Math.min(dp[i - 1][j] + 1, Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost));
			}
		}

		return dp[m][n];
	}

	public static boolean isSimilar(String s1, String s2) {
		return isSimilar(s1, s2, 2);
	}

	public static boolean isSimilar(String s1, String s2, int searchDistance) {

		s1 = s1.toLowerCase(Locale.ENGLISH);
		s2 = s2.toLowerCase(Locale.ENGLISH);

		if (s1.length() <= searchDistance) {
			return s1.contains(s2);
		}

		for (String word : StringUtils.split(s1)) {
			if (word.contains(s2) || levenshteinDistance(word, s2) <= searchDistance) {
				return true;
			}
		}

		return s1.contains(s2) || levenshteinDistance(s1, s2) <= searchDistance;
	}
}
