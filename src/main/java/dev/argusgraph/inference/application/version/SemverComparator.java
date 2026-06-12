package dev.argusgraph.inference.application.version;

import java.util.Comparator;

/**
 * SemVer 2.0.0 precedence for OSV {@code SEMVER} ranges: numeric core compared numerically,
 * a pre-release version is lower than its release, and pre-release identifiers compare per
 * the spec (numeric < alphanumeric, more identifiers win ties). Build metadata is ignored.
 */
public final class SemverComparator implements Comparator<String> {

	public static final SemverComparator INSTANCE = new SemverComparator();

	private SemverComparator() {
	}

	@Override
	public int compare(String a, String b) {
		String[] aCore = core(a), bCore = core(b);
		for (int i = 0; i < 3; i++) {
			int cmp = Long.compare(num(aCore, i), num(bCore, i));
			if (cmp != 0) {
				return cmp;
			}
		}
		String aPre = prerelease(a), bPre = prerelease(b);
		if (aPre.isEmpty() && bPre.isEmpty()) {
			return 0;
		}
		if (aPre.isEmpty()) {
			return 1; // no pre-release > pre-release
		}
		if (bPre.isEmpty()) {
			return -1;
		}
		return comparePrerelease(aPre.split("\\."), bPre.split("\\."));
	}

	private static String[] core(String v) {
		String s = strip(v);
		int dash = s.indexOf('-');
		return (dash >= 0 ? s.substring(0, dash) : s).split("\\.");
	}

	private static String prerelease(String v) {
		String s = strip(v);
		int dash = s.indexOf('-');
		return dash >= 0 ? s.substring(dash + 1) : "";
	}

	private static String strip(String v) {
		String s = v.startsWith("v") || v.startsWith("V") ? v.substring(1) : v;
		int plus = s.indexOf('+');
		return plus >= 0 ? s.substring(0, plus) : s;
	}

	private static long num(String[] core, int i) {
		if (i >= core.length) {
			return 0;
		}
		try {
			return Long.parseLong(core[i]);
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static int comparePrerelease(String[] a, String[] b) {
		int len = Math.min(a.length, b.length);
		for (int i = 0; i < len; i++) {
			int cmp = compareIdentifier(a[i], b[i]);
			if (cmp != 0) {
				return cmp;
			}
		}
		return Integer.compare(a.length, b.length);
	}

	private static int compareIdentifier(String a, String b) {
		boolean an = a.chars().allMatch(Character::isDigit);
		boolean bn = b.chars().allMatch(Character::isDigit);
		if (an && bn) {
			return Long.compare(Long.parseLong(a), Long.parseLong(b));
		}
		if (an) {
			return -1; // numeric < alphanumeric
		}
		if (bn) {
			return 1;
		}
		return a.compareTo(b);
	}
}
