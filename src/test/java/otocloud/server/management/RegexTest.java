package otocloud.server.management;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexTest {
	public static void main(String[] args) {
		String serverSummaryInfoRegex = "/api/servers/([^/]*?)/summaryinfo[/]?";
		Pattern pattern = Pattern.compile(serverSummaryInfoRegex);
		Matcher matcher = pattern.matcher("/api/servers/10.10.1.102/summaryinfo");
		if(matcher.matches()) {
			System.out.println(matcher.group(0));
			System.out.println(matcher.group(1));
			System.out.println("Y");
		} else {
			System.out.println("N");
		}
	}
}
