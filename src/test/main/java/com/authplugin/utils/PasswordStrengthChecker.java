package com.authplugin.utils;

/**
 * Evaluates password strength and returns a colored feedback string.
 */
public class PasswordStrengthChecker {

    public enum Strength { WEAK, FAIR, STRONG, VERY_STRONG }

    /**
     * Evaluate the strength of a password.
     */
    public static Strength evaluate(String password) {
        if (password == null || password.length() < 6) return Strength.WEAK;

        int score = 0;
        if (password.length() >= 8)  score++;
        if (password.length() >= 12) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*\\d.*"))   score++;
        if (password.matches(".*[^A-Za-z0-9].*")) score++;

        if (score <= 2) return Strength.WEAK;
        if (score <= 3) return Strength.FAIR;
        if (score <= 4) return Strength.STRONG;
        return Strength.VERY_STRONG;
    }

    /**
     * Return a colored Minecraft message describing the password strength.
     */
    public static String getFeedback(String password) {
        switch (evaluate(password)) {
            case WEAK:        return "§c비밀번호 강도: 약함 (숫자, 대소문자, 특수문자를 추가하세요)";
            case FAIR:        return "§e비밀번호 강도: 보통";
            case STRONG:      return "§a비밀번호 강도: 강함";
            case VERY_STRONG: return "§2비밀번호 강도: 매우 강함";
            default:          return "";
        }
    }
}
