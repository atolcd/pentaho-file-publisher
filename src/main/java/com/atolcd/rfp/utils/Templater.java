package com.atolcd.rfp.utils;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Templater {

  private static final String fieldStart = "\\$\\{";
  private static final String fieldEnd = "\\}";

  private static final String regex = fieldStart + "([^}]+)" + fieldEnd;
  private static final Pattern pattern = Pattern.compile(regex);

  public static String substitute(String orig, Properties items) {

    Matcher m = pattern.matcher(orig);
    String result = orig;
    while (m.find()) {
      String found = m.group(1);
      String newVal = items.getProperty(found);
      result = result.replaceFirst(regex, newVal);
    }
    return result;

  }

}
