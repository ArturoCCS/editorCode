package com.modelo;

import java.util.*;
import java.util.regex.Pattern;

public class Tokenizer {
    public static boolean ACCEPT_UPPERCASE_IDS = true;

    public static class Token {
        public final String type;
        public final String value;
        public final int start;
        public final int end;
        public Token(String type, String value, int start, int end) {
            this.type = type; this.value = value; this.start = start; this.end = end;
        }
    }

    public static class TokenError {
        public final int start;
        public final int end;
        public final String message;
        public TokenError(int start, int end, String message) {
            this.start = start; this.end = end; this.message = message;
        }
    }

    public interface TokenSink {
        void onToken(Token t);
        void onError(TokenError e);
    }

    private static final Set<String> RESERVED = Set.of("START","END","INTEGER","DECIMAL","READ","PRINT");
    private static final Pattern ID_PATTERN  = Pattern.compile("^[A-Za-z][0-9]{1,3}$");
    private static final Pattern ENT_PATTERN = Pattern.compile("^[0-9]{1,6}$");
    private static final Pattern DEC_PATTERN = Pattern.compile("^[0-9]{1,6}\\.[0-9]{1,3}$");

    public static void scan(String input, TokenSink sink) {

        int pos = 0;
        int len = input.length();

        while (pos < len) {
            char c = input.charAt(pos);

            if (Character.isWhitespace(c)) { pos++; continue; }

            int start = pos;

            if (isSymbol(c)) {
                sink.onToken(new Token("SIMB", String.valueOf(c), start, start + 1));
                pos++;
                continue;
            }

            if (Character.isLetter(c)) {
                int s = pos;
                while (pos < len && Character.isLetterOrDigit(input.charAt(pos))) pos++;
                String lex = input.substring(s, pos);

                if (isAllLetters(lex) && lex.equals(lex.toUpperCase(Locale.ROOT)) && RESERVED.contains(lex)) {
                    sink.onToken(new Token("PR", lex, s, pos));
                    continue;
                }

                if (ID_PATTERN.matcher(lex).matches()) {
                    if (!ACCEPT_UPPERCASE_IDS && Character.isUpperCase(lex.charAt(0))) {
                        sink.onError(new TokenError(s, pos,
                                "Error Lexico, identificador con mayúscula inicial no permitido: '" + lex + "'"));
                    } else {
                        sink.onToken(new Token("ID", lex, s, pos));
                    }
                    continue;
                }

                sink.onError(new TokenError(s, pos, "Error Lexico, token inválido: '" + lex + "'"));
                continue;
            }

            if (Character.isDigit(c)) {
                int s = pos;
                int dots = 0;

                while (pos < len) {
                    char ch = input.charAt(pos);
                    if (Character.isDigit(ch)) { pos++; continue; }
                    if (ch == '.') { dots++; pos++; continue; }
                    break;
                }

                String lex = input.substring(s, pos);

                if (dots == 0) {
                    if (ENT_PATTERN.matcher(lex).matches()) {
                        sink.onToken(new Token("ENT", lex, s, pos));
                    } else {
                        sink.onError(new TokenError(s, pos, "Error Lexico, entero demasiado largo, deben ser máximo 6: '" + lex + "'"));
                    }
                } else if (dots == 1) {
                    if (DEC_PATTERN.matcher(lex).matches()) {
                        sink.onToken(new Token("DEC", lex, s, pos));
                    } else {
                        String[] parts = lex.split("\\.", -1);
                        String intPart = parts.length > 0 ? parts[0] : "";
                        String fracPart = parts.length > 1 ? parts[1] : "";

                        if (fracPart.isEmpty()) {
                            sink.onError(new TokenError(s, pos, "Error Lexico, número decimal inválido: falta parte decimal en '" + lex + "'"));
                        } else if (intPart.length() > 6) {
                            sink.onError(new TokenError(s, pos, "Error Lexico, parte entera demasiado larga (máximo 6): '" + lex + "'"));
                        } else if (fracPart.length() > 3) {
                            sink.onError(new TokenError(s, pos, "Error Lexico, demasiados decimales, máximo son 3: '" + lex + "'"));
                        } else {
                            sink.onError(new TokenError(s, pos, "Error Lexico, número decimal inválido: '" + lex + "'"));
                        }
                    }
                } else {
                    sink.onError(new TokenError(s, pos, "Error Lexico, número inválido: múltiples puntos en '" + lex + "'"));
                }
                continue;
            }

            sink.onError(new TokenError(start, start + 1, "Error Lexico, símbolo no válido: '" + c + "'"));
            pos++;
        }
    }

    private static boolean isAllLetters(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isLetter(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isSymbol(char c) {
        return c == '{' || c == '}' || c == '(' || c == ')' ||
                c == ';' || c == ',' || c == '.' || c == '=' ||
                c == '+' || c == '-' || c == '*' || c == '/';
    }
}