package com.github.fancyerii.wang.tool;

public class StringTool {
    public static boolean isEnLetter(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    public static boolean isEnLetter(String ch) {
        return ch.length() == 1 && isEnLetter(ch.charAt(0));
    }

    public static boolean isDigit(String ch) {
        return ch.length() == 1 && isDigit(ch.charAt(0));
    }

    public static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    public static boolean isChinese(char ch) {
        return (ch >= '\u4E00' && ch <= '\u9FA5') || (ch >= '\uF900' && ch <= '\uFA2D');
    }

    public static boolean isChinese(String ch) {
        return ch.length() == 1 && isChinese(ch.charAt(0));
    }

    public static boolean isAllChinese(String s) {
        for(int i=0;i<s.length();i++) {
            if(!isChinese(s.charAt(i))) return false;
        }
        return true;
    }

    public static boolean hasChinese(String s) {
        for(int i=0;i<s.length();i++) {
            if(isChinese(s.charAt(i))) return true;
        }
        return false;
    }
}