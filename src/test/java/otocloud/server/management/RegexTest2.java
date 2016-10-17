package otocloud.server.management;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexTest2 {
	public static void main(String[] args) {
		Pattern pattern = Pattern.compile("(\\d{3,4})-(\\d{7,8})");
		Matcher matcher = pattern.matcher("010-84382255");
		if (matcher.matches()) {
			System.out.println(matcher.group(1));
			System.out.println(matcher.group(2));
		}
	}
}
